/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.intentresolver.platform

import android.content.Context
import android.content.pm.UserInfo
import android.content.pm.UserInfo.FLAG_FULL
import android.content.pm.UserInfo.FLAG_INITIALIZED
import android.content.pm.UserInfo.FLAG_PROFILE
import android.content.pm.UserInfo.NO_PROFILE_GROUP_ID
import android.os.IUserManager
import android.os.UserHandle
import android.os.UserManager
import androidx.annotation.NonNull
import com.android.intentresolver.data.repository.AvailabilityChange
import com.android.intentresolver.data.repository.ProfileAdded
import com.android.intentresolver.data.repository.ProfileRemoved
import com.android.intentresolver.data.repository.UserEvent
import com.android.intentresolver.platform.FakeUserManager.State
import kotlin.random.Random
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * A stand-in for [UserManager] to support testing of data layer components which depend on it.
 *
 * This fake targets system applications which need to interact with any or all of the current
 * user's associated profiles (as reported by [getEnabledProfiles]). Support for manipulating
 * non-profile (full) secondary users (switching active foreground user, adding or removing users)
 * is not included.
 *
 * Upon creation [FakeUserManager] contains a single primary (full) user with a randomized ID. This
 * is available from [FakeUserManager.state] using [primaryUserHandle][State.primaryUserHandle] or
 * [getPrimaryUser][State.getPrimaryUser].
 *
 * To make state changes, use functions available from [FakeUserManager.state]:
 * * [createProfile][State.createProfile]
 * * [removeProfile][State.removeProfile]
 * * [setQuietMode][State.setQuietMode]
 *
 * Any functionality not explicitly overridden here is guaranteed to throw an exception when
 * accessed (access to the real system service is prevented).
 */
class FakeUserManager(val state: State = State()) :
    UserManager(/* context = */ mockContext(), /* service = */ mockService()) {

    enum class ProfileType {
        WORK,
        CLONE,
        PRIVATE
    }

    override fun getProfileParent(userHandle: UserHandle): UserHandle? {
        return state.getUserOrNull(userHandle)?.let { user ->
            if (user.isProfile) {
                state.getUserOrNull(UserHandle.of(user.profileGroupId))?.userHandle
            } else {
                null
            }
        }
    }

    override fun getUserInfo(userId: Int): UserInfo? {
        return state.getUserOrNull(UserHandle.of(userId))
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getEnabledProfiles(userId: Int): List<UserInfo> {
        val user = state.users.single { it.id == userId }
        return state.users.filter { other ->
            user.id == other.id || user.profileGroupId == other.profileGroupId
        }
    }

    override fun requestQuietModeEnabled(
        enableQuietMode: Boolean,
        @NonNull userHandle: UserHandle
    ): Boolean {
        state.setQuietMode(userHandle, enableQuietMode)
        return true
    }

    override fun isQuietModeEnabled(userHandle: UserHandle): Boolean {
        return state.getUser(userHandle).isQuietModeEnabled
    }

    override fun toString(): String {
        return "FakeUserManager(state=$state)"
    }

    class State {
        private val eventChannel = Channel<UserEvent>()
        private val userInfoMap: MutableMap<UserHandle, UserInfo> = mutableMapOf()

        /** The id of the primary/full/system user, which is automatically created. */
        val primaryUserHandle: UserHandle

        /**
         * Retrieves the primary user. The value returned changes, but the values are immutable.
         *
         * Do not cache this value in tests, between operations.
         */
        fun getPrimaryUser(): UserInfo = getUser(primaryUserHandle)

        private var nextUserId: Int = 100 + Random.nextInt(0, 900)

        /**
         * A flow of [UserEvent] which emulates those normally generated from system broadcasts.
         *
         * Events are produced by calls to [createPrimaryUser], [createProfile], [removeProfile].
         */
        val userEvents: Flow<UserEvent>

        val users: List<UserInfo>
            get() = userInfoMap.values.toList()

        val userHandles: List<UserHandle>
            get() = userInfoMap.keys.toList()

        init {
            primaryUserHandle = createPrimaryUser(allocateNextId())
            userEvents = eventChannel.consumeAsFlow()
        }

        private fun allocateNextId() = nextUserId++

        private fun createPrimaryUser(id: Int): UserHandle {
            val userInfo =
                UserInfo(id, "", "", FLAG_INITIALIZED or FLAG_FULL, USER_TYPE_FULL_SYSTEM)
            userInfoMap[userInfo.userHandle] = userInfo
            return userInfo.userHandle
        }

        fun getUserOrNull(handle: UserHandle): UserInfo? = userInfoMap[handle]

        fun getUser(handle: UserHandle): UserInfo =
            requireNotNull(getUserOrNull(handle)) {
                "Expected userInfoMap to contain an entry for $handle"
            }

        fun setQuietMode(user: UserHandle, quietMode: Boolean) {
            userInfoMap[user]?.also {
                it.flags =
                    if (quietMode) {
                        it.flags or UserInfo.FLAG_QUIET_MODE
                    } else {
                        it.flags and UserInfo.FLAG_QUIET_MODE.inv()
                    }
                eventChannel.trySend(AvailabilityChange(user, quietMode))
            }
        }

        fun createProfile(type: ProfileType, parent: UserHandle = primaryUserHandle): UserHandle {
            val parentUser = getUser(parent)
            require(!parentUser.isProfile) { "Parent user cannot be a profile" }

            // Ensure the parent user has a valid profileGroupId
            if (parentUser.profileGroupId == NO_PROFILE_GROUP_ID) {
                parentUser.profileGroupId = parentUser.id
            }
            val id = allocateNextId()
            val userInfo =
                UserInfo(id, "", "", FLAG_INITIALIZED or FLAG_PROFILE, type.toUserType()).apply {
                    profileGroupId = parentUser.profileGroupId
                }
            userInfoMap[userInfo.userHandle] = userInfo
            eventChannel.trySend(ProfileAdded(userInfo.userHandle))
            return userInfo.userHandle
        }

        fun removeProfile(handle: UserHandle): Boolean {
            return userInfoMap[handle]?.let { user ->
                require(user.isProfile) { "Only profiles can be removed" }
                userInfoMap.remove(user.userHandle)
                eventChannel.trySend(ProfileRemoved(user.userHandle))
                return true
            }
                ?: false
        }

        override fun toString() = buildString {
            append("State(nextUserId=$nextUserId, userInfoMap=[")
            userInfoMap.entries.forEach {
                append("UserHandle[${it.key.identifier}] = ${it.value.debugString},")
            }
            append("])")
        }
    }
}

/** A safe mock of [Context] which throws on any unstubbed method call. */
private fun mockContext(userHandle: UserHandle = UserHandle.SYSTEM): Context {
    return mock<Context>(
        defaultAnswer = {
            error("Unstubbed behavior invoked! (${it.method}(${it.arguments.asList()})")
        }
    ) {
        // Careful! Specify behaviors *first* to avoid throwing while stubbing!
        doReturn(mock).whenever(mock).applicationContext
        doReturn(userHandle).whenever(mock).user
        doReturn(userHandle.identifier).whenever(mock).userId
    }
}

private fun FakeUserManager.ProfileType.toUserType(): String {
    return when (this) {
        FakeUserManager.ProfileType.WORK -> UserManager.USER_TYPE_PROFILE_MANAGED
        FakeUserManager.ProfileType.CLONE -> UserManager.USER_TYPE_PROFILE_CLONE
        FakeUserManager.ProfileType.PRIVATE -> UserManager.USER_TYPE_PROFILE_PRIVATE
    }
}

/** A safe mock of [IUserManager] which throws on any unstubbed method call. */
fun mockService(): IUserManager {
    return mock<IUserManager>(
        defaultAnswer = {
            error("Unstubbed behavior invoked! ${it.method}(${it.arguments.asList()}")
        }
    )
}

val UserInfo.debugString: String
    get() =
        "UserInfo(id=$id, profileGroupId=$profileGroupId, name=$name, " +
            "type=$userType, flags=${UserInfo.flagsToString(flags)})"

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

package com.android.intentresolver.data.repository

import android.content.Intent
import android.content.Intent.ACTION_MANAGED_PROFILE_AVAILABLE
import android.content.Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE
import android.content.Intent.ACTION_PROFILE_ADDED
import android.content.Intent.ACTION_PROFILE_AVAILABLE
import android.content.Intent.ACTION_PROFILE_REMOVED
import android.content.Intent.ACTION_PROFILE_UNAVAILABLE
import android.content.Intent.EXTRA_QUIET_MODE
import android.content.Intent.EXTRA_USER
import android.content.IntentFilter
import android.content.pm.UserInfo
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.android.intentresolver.data.BroadcastSubscriber
import com.android.intentresolver.inject.Background
import com.android.intentresolver.inject.Main
import com.android.intentresolver.inject.ProfileParent
import com.android.intentresolver.shared.model.User
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

interface UserRepository {
    /**
     * A [Flow] user profile groups. Each list contains the context user along with all members of
     * the profile group. This includes the (Full) parent user, if the context user is a profile.
     */
    val users: Flow<List<User>>

    /**
     * A [Flow] of availability. Only profile users may become unavailable.
     *
     * Availability is currently defined as not being in [quietMode][UserInfo.isQuietModeEnabled].
     */
    val availability: Flow<Map<User, Boolean>>

    /**
     * Request that availability be updated to the requested state. This currently includes toggling
     * quiet mode as needed. This may involve additional background actions, such as starting or
     * stopping a profile user (along with their many associated processes).
     *
     * If successful, the change will be applied after the call returns and can be observed using
     * [UserRepository.availability] for the given user.
     *
     * No actions are taken if the user is already in requested state.
     *
     * @throws IllegalArgumentException if called for an unsupported user type
     */
    suspend fun requestState(user: User, available: Boolean)
}

private const val TAG = "UserRepository"

/** The delay between entering the cached process state and entering the frozen cgroup */
private val cachedProcessFreezeDelay: Duration = 10.seconds

/** How long to continue listening for user state broadcasts while unsubscribed */
private val stateFlowTimeout = cachedProcessFreezeDelay - 2.seconds

/** How long to retain the previous user state after the state flow stops. */
private val stateCacheTimeout = 2.seconds

internal data class UserWithState(val user: User, val available: Boolean)

internal typealias UserStates = List<UserWithState>

internal val userBroadcastActions =
    setOf(
        ACTION_PROFILE_ADDED,
        ACTION_PROFILE_REMOVED,

        // Quiet mode enabled/disabled for managed
        // From: UserController.broadcastProfileAvailabilityChanges
        // In response to setQuietModeEnabled
        ACTION_MANAGED_PROFILE_AVAILABLE, // quiet mode, sent for manage profiles only
        ACTION_MANAGED_PROFILE_UNAVAILABLE, // quiet mode, sent for manage profiles only

        // Quiet mode toggled for profile type, requires flag 'android.os.allow_private_profile
        // true'
        ACTION_PROFILE_AVAILABLE, // quiet mode,
        ACTION_PROFILE_UNAVAILABLE, // quiet mode, sent for any profile type
    )

/** Tracks and publishes state for the parent user and associated profiles. */
class UserRepositoryImpl
@VisibleForTesting
constructor(
    private val profileParent: UserHandle,
    private val userManager: UserManager,
    /** A flow of events which represent user-state changes from [UserManager]. */
    private val userEvents: Flow<UserEvent>,
    scope: CoroutineScope,
    private val backgroundDispatcher: CoroutineDispatcher,
) : UserRepository {
    @Inject
    constructor(
        @ProfileParent profileParent: UserHandle,
        userManager: UserManager,
        @Main scope: CoroutineScope,
        @Background background: CoroutineDispatcher,
        broadcastSubscriber: BroadcastSubscriber,
    ) : this(
        profileParent,
        userManager,
        userEvents =
            broadcastSubscriber.createFlow(
                createFilter(userBroadcastActions),
                profileParent,
                Intent::toUserEvent
            ),
        scope,
        background,
    )

    private fun debugLog(msg: () -> String) {
        if (Build.IS_USERDEBUG || Build.IS_ENG) {
            Log.d(TAG, msg())
        }
    }

    private fun errorLog(msg: String, caught: Throwable? = null) {
        Log.e(TAG, msg, caught)
    }

    /**
     * An exception which indicates that an inconsistency exists between the user state map and the
     * rest of the system.
     */
    private class UserStateException(
        override val message: String,
        val event: UserEvent,
        override val cause: Throwable? = null,
    ) : RuntimeException("$message: event=$event", cause)

    private val sharingScope = CoroutineScope(scope.coroutineContext + backgroundDispatcher)
    private val usersWithState: Flow<UserStates> =
        userEvents
            .onStart { emit(Initialize) }
            .onEach { debugLog { "userEvent: $it" } }
            .runningFold(emptyList(), ::handleEvent)
            .distinctUntilChanged()
            .onEach { debugLog { "userStateList: $it" } }
            .stateIn(
                sharingScope,
                started =
                    WhileSubscribed(
                        stopTimeoutMillis = stateFlowTimeout.inWholeMilliseconds,
                        replayExpirationMillis = 0
                        /** Immediately on stop */
                    ),
                listOf()
            )
            .filterNot { it.isEmpty() }

    private suspend fun handleEvent(users: UserStates, event: UserEvent): UserStates {
        return try {
            // Handle an action by performing some operation, then returning a new map
            when (event) {
                is Initialize -> createNewUserStates(profileParent)
                is ProfileAdded -> handleProfileAdded(event, users)
                is ProfileRemoved -> handleProfileRemoved(event, users)
                is AvailabilityChange -> handleAvailability(event, users)
                is UnknownEvent -> {
                    debugLog { "Unhandled event: $event)" }
                    users
                }
            }
        } catch (e: UserStateException) {
            errorLog("An error occurred handling an event: ${e.event}")
            errorLog("Attempting to recover...", e)
            createNewUserStates(profileParent)
        }
    }

    override val users: Flow<List<User>> =
        usersWithState.map { userStates -> userStates.map { it.user } }.distinctUntilChanged()

    override val availability: Flow<Map<User, Boolean>> =
        usersWithState
            .map { list -> list.associate { it.user to it.available } }
            .distinctUntilChanged()

    override suspend fun requestState(user: User, available: Boolean) {
        return withContext(backgroundDispatcher) {
            debugLog { "requestQuietModeEnabled: ${!available} for user $user" }
            userManager.requestQuietModeEnabled(/* enableQuietMode = */ !available, user.handle)
        }
    }

    private fun List<UserWithState>.update(handle: UserHandle, user: UserWithState) =
        filter { it.user.id != handle.identifier } + user

    private fun handleAvailability(event: AvailabilityChange, current: UserStates): UserStates {
        val userEntry =
            current.firstOrNull { it.user.id == event.user.identifier }
                ?: throw UserStateException("User was not present in the map", event)
        return current.update(event.user, userEntry.copy(available = !event.quietMode))
    }

    private fun handleProfileRemoved(event: ProfileRemoved, current: UserStates): UserStates {
        if (!current.any { it.user.id == event.user.identifier }) {
            throw UserStateException("User was not present in the map", event)
        }
        return current.filter { it.user.id != event.user.identifier }
    }

    private suspend fun handleProfileAdded(event: ProfileAdded, current: UserStates): UserStates {
        val user =
            try {
                requireNotNull(readUser(event.user))
            } catch (e: Exception) {
                throw UserStateException("Failed to read user from UserManager", event, e)
            }
        return current + UserWithState(user, true)
    }

    private suspend fun createNewUserStates(user: UserHandle): UserStates {
        val profiles = readProfileGroup(user)
        return profiles.mapNotNull { userInfo ->
            userInfo.toUser()?.let { user -> UserWithState(user, userInfo.isAvailable()) }
        }
    }

    private suspend fun readProfileGroup(member: UserHandle): List<UserInfo> {
        return withContext(backgroundDispatcher) {
                @Suppress("DEPRECATION") userManager.getEnabledProfiles(member.identifier)
            }
            .toList()
    }

    /** Read [UserInfo] from [UserManager], or null if not found or an unsupported type. */
    private suspend fun readUser(user: UserHandle): User? {
        val userInfo =
            withContext(backgroundDispatcher) { userManager.getUserInfo(user.identifier) }
        return userInfo?.let { info ->
            info.getSupportedUserRole()?.let { role -> User(info.id, role) }
        }
    }
}

/** A Model representing changes to profiles and availability */
sealed interface UserEvent

/** Used as a an initial value to trigger a fetch of all profile data. */
data object Initialize : UserEvent

/** A profile was added to the profile group. */
data class ProfileAdded(
    /** The handle for the added profile. */
    val user: UserHandle,
) : UserEvent

/** A profile was removed from the profile group. */
data class ProfileRemoved(
    /** The handle for the removed profile. */
    val user: UserHandle,
) : UserEvent

/** A profile has changed availability. */
data class AvailabilityChange(
    /** THe handle for the profile with availability change. */
    val user: UserHandle,
    /** The new quietMode state. */
    val quietMode: Boolean = false,
) : UserEvent

/** An unhandled event, logged and ignored. */
data class UnknownEvent(
    /** The broadcast intent action received */
    val action: String?,
) : UserEvent

/** Used with [broadcastFlow] to transform a UserManager broadcast action into a [UserEvent]. */
internal fun Intent.toUserEvent(): UserEvent {
    val action = action
    val user = extras?.getParcelable(EXTRA_USER, UserHandle::class.java)
    val quietMode = extras?.getBoolean(EXTRA_QUIET_MODE, false)
    return when (action) {
        ACTION_PROFILE_ADDED -> ProfileAdded(requireNotNull(user))
        ACTION_PROFILE_REMOVED -> ProfileRemoved(requireNotNull(user))
        ACTION_MANAGED_PROFILE_UNAVAILABLE,
        ACTION_MANAGED_PROFILE_AVAILABLE,
        ACTION_PROFILE_AVAILABLE,
        ACTION_PROFILE_UNAVAILABLE ->
            AvailabilityChange(requireNotNull(user), requireNotNull(quietMode))
        else -> UnknownEvent(action)
    }
}

internal fun createFilter(actions: Iterable<String>): IntentFilter {
    return IntentFilter().apply { actions.forEach(::addAction) }
}

internal fun UserInfo?.isAvailable(): Boolean {
    return this?.isQuietModeEnabled != true
}

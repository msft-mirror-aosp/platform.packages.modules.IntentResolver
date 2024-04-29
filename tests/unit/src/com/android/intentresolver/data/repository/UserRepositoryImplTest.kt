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

import android.content.pm.UserInfo
import android.os.UserHandle
import android.os.UserHandle.SYSTEM
import android.os.UserHandle.USER_SYSTEM
import android.os.UserManager
import com.android.intentresolver.coroutines.collectLastValue
import com.android.intentresolver.platform.FakeUserManager
import com.android.intentresolver.platform.FakeUserManager.ProfileType
import com.android.intentresolver.shared.model.User
import com.android.intentresolver.shared.model.User.Role
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

internal class UserRepositoryImplTest {
    private val userManager = FakeUserManager()
    private val userState = userManager.state

    @Test
    fun initialization() = runTest {
        val repo = createUserRepository(userManager)
        val users by collectLastValue(repo.users)

        assertWithMessage("collectLastValue(repo.users)").that(users).isNotNull()
        assertThat(users)
            .containsExactly(User(userState.primaryUserHandle.identifier, Role.PERSONAL))
    }

    @Test
    fun createProfile() = runTest {
        val repo = createUserRepository(userManager)
        val users by collectLastValue(repo.users)

        assertWithMessage("collectLastValue(repo.users)").that(users).isNotNull()
        assertThat(users).hasSize(1)

        val profile = userState.createProfile(ProfileType.WORK)
        assertThat(users).hasSize(2)
        assertThat(users).contains(User(profile.identifier, Role.WORK))
    }

    @Test
    fun removeProfile() = runTest {
        val repo = createUserRepository(userManager)
        val users by collectLastValue(repo.users)

        assertWithMessage("collectLastValue(repo.users)").that(users).isNotNull()
        val work = userState.createProfile(ProfileType.WORK)
        assertThat(users).contains(User(work.identifier, Role.WORK))

        userState.removeProfile(work)
        assertThat(users).doesNotContain(User(work.identifier, Role.WORK))
    }

    @Test
    fun isAvailable() = runTest {
        val repo = createUserRepository(userManager)
        val work = userState.createProfile(ProfileType.WORK)
        val workUser = User(work.identifier, Role.WORK)

        val available by collectLastValue(repo.availability)
        assertThat(available?.get(workUser)).isTrue()

        userState.setQuietMode(work, true)
        assertThat(available?.get(workUser)).isFalse()

        userState.setQuietMode(work, false)
        assertThat(available?.get(workUser)).isTrue()
    }

    @Test
    fun onHandleAvailabilityChange_userStateMaintained() = runTest {
        val repo = createUserRepository(userManager)
        val private = userState.createProfile(ProfileType.PRIVATE)
        val privateUser = User(private.identifier, Role.PRIVATE)

        val users by collectLastValue(repo.users)

        repo.requestState(privateUser, false)
        repo.requestState(privateUser, true)

        assertWithMessage("users.size").that(users?.size ?: 0).isEqualTo(2) // personal + private

        assertWithMessage("No duplicate IDs")
            .that(users?.count { it.id == private.identifier })
            .isEqualTo(1)
    }

    @Test
    fun requestState() = runTest {
        val repo = createUserRepository(userManager)
        val work = userState.createProfile(ProfileType.WORK)
        val workUser = User(work.identifier, Role.WORK)

        val available by collectLastValue(repo.availability)
        assertThat(available?.get(workUser)).isTrue()

        repo.requestState(workUser, false)
        assertThat(available?.get(workUser)).isFalse()

        repo.requestState(workUser, true)
        assertThat(available?.get(workUser)).isTrue()
    }

    /**
     * This and all the 'recovers_from_*' tests below all configure a static event flow instead of
     * using [FakeUserManager]. These tests verify that a invalid broadcast causes the flow to
     * reinitialize with the user profile group.
     */
    @Test
    fun recovers_from_invalid_profile_added_event() = runTest {
        val userManager =
            mockUserManager(validUser = USER_SYSTEM, invalidUser = UserHandle.USER_NULL)
        val events = flowOf(ProfileAdded(UserHandle.of(UserHandle.USER_NULL)))
        val repo =
            UserRepositoryImpl(
                profileParent = SYSTEM,
                userManager = userManager,
                userEvents = events,
                scope = backgroundScope,
                backgroundDispatcher = Dispatchers.Unconfined
            )
        val users by collectLastValue(repo.users)

        assertWithMessage("collectLastValue(repo.users)").that(users).isNotNull()
        assertThat(users).containsExactly(User(USER_SYSTEM, Role.PERSONAL))
    }

    @Test
    fun recovers_from_invalid_profile_removed_event() = runTest {
        val userManager =
            mockUserManager(validUser = USER_SYSTEM, invalidUser = UserHandle.USER_NULL)
        val events = flowOf(ProfileRemoved(UserHandle.of(UserHandle.USER_NULL)))
        val repo =
            UserRepositoryImpl(
                profileParent = SYSTEM,
                userManager = userManager,
                userEvents = events,
                scope = backgroundScope,
                backgroundDispatcher = Dispatchers.Unconfined
            )
        val users by collectLastValue(repo.users)

        assertWithMessage("collectLastValue(repo.users)").that(users).isNotNull()
        assertThat(users).containsExactly(User(USER_SYSTEM, Role.PERSONAL))
    }

    @Test
    fun recovers_from_invalid_profile_available_event() = runTest {
        val userManager =
            mockUserManager(validUser = USER_SYSTEM, invalidUser = UserHandle.USER_NULL)
        val events = flowOf(AvailabilityChange(UserHandle.of(UserHandle.USER_NULL)))
        val repo =
            UserRepositoryImpl(SYSTEM, userManager, events, backgroundScope, Dispatchers.Unconfined)
        val users by collectLastValue(repo.users)

        assertWithMessage("collectLastValue(repo.users)").that(users).isNotNull()
        assertThat(users).containsExactly(User(USER_SYSTEM, Role.PERSONAL))
    }

    @Test
    fun recovers_from_unknown_event() = runTest {
        val userManager =
            mockUserManager(validUser = USER_SYSTEM, invalidUser = UserHandle.USER_NULL)
        val events = flowOf(UnknownEvent("UNKNOWN_EVENT"))
        val repo =
            UserRepositoryImpl(
                profileParent = SYSTEM,
                userManager = userManager,
                userEvents = events,
                scope = backgroundScope,
                backgroundDispatcher = Dispatchers.Unconfined
            )
        val users by collectLastValue(repo.users)

        assertWithMessage("collectLastValue(repo.users)").that(users).isNotNull()
        assertThat(users).containsExactly(User(USER_SYSTEM, Role.PERSONAL))
    }
}

@Suppress("SameParameterValue")
private fun mockUserManager(validUser: Int, invalidUser: Int) =
    mock<UserManager> {
        val info = UserInfo(validUser, "", "", UserInfo.FLAG_FULL)
        on { getEnabledProfiles(any()) } doReturn listOf(info)
        on { getUserInfo(validUser) } doReturn info
        on { getEnabledProfiles(invalidUser) } doReturn listOf()
        on { getUserInfo(invalidUser) } doReturn null
    }

private fun TestScope.createUserRepository(userManager: FakeUserManager): UserRepositoryImpl {
    return UserRepositoryImpl(
        profileParent = userManager.state.primaryUserHandle,
        userManager = userManager,
        userEvents = userManager.state.userEvents,
        scope = backgroundScope,
        backgroundDispatcher = Dispatchers.Unconfined
    )
}

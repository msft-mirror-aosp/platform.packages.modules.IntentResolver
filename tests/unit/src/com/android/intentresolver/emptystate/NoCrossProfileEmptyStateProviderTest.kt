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

package com.android.intentresolver.emptystate

import android.content.Intent
import com.android.intentresolver.ProfileHelper
import com.android.intentresolver.ResolverListAdapter
import com.android.intentresolver.annotation.JavaInterop
import com.android.intentresolver.data.repository.DevicePolicyResources
import com.android.intentresolver.data.repository.FakeUserRepository
import com.android.intentresolver.domain.interactor.UserInteractor
import com.android.intentresolver.inject.FakeIntentResolverFlags
import com.android.intentresolver.shared.model.User
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Test
import org.mockito.Mockito.never
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.verification.VerificationMode

@OptIn(JavaInterop::class)
class NoCrossProfileEmptyStateProviderTest {

    private val personalUser = User(0, User.Role.PERSONAL)
    private val workUser = User(10, User.Role.WORK)
    private val privateUser = User(11, User.Role.PRIVATE)
    private val flags = FakeIntentResolverFlags()

    private val userRepository = FakeUserRepository(listOf(personalUser, workUser, privateUser))

    private val personalIntents = listOf(Intent("PERSONAL"))
    private val personalListAdapter =
        mock<ResolverListAdapter> {
            on { userHandle } doReturn personalUser.handle
            on { intents } doReturn personalIntents
        }
    private val workIntents = listOf(Intent("WORK"))
    private val workListAdapter =
        mock<ResolverListAdapter> {
            on { userHandle } doReturn workUser.handle
            on { intents } doReturn workIntents
        }
    private val privateIntents = listOf(Intent("PRIVATE"))
    private val privateListAdapter =
        mock<ResolverListAdapter> {
            on { userHandle } doReturn privateUser.handle
            on { intents } doReturn privateIntents
        }

    private val devicePolicyResources =
        mock<DevicePolicyResources> {
            on { crossProfileBlocked } doReturn "Cross profile blocked"
            on { toPersonalBlockedByPolicyMessage(any()) } doReturn "Blocked to Personal"
            on { toWorkBlockedByPolicyMessage(any()) } doReturn "Blocked to Work"
            on { toPrivateBlockedByPolicyMessage(any()) } doReturn "Blocked to Private"
        }

    // If asked, no intent can ever be forwarded between any pair of users.
    private val crossProfileIntentsChecker =
        mock<CrossProfileIntentsChecker> {
            on {
                hasCrossProfileIntents(
                    /* intents = */ any(),
                    /* source = */ any(),
                    /* target = */ any()
                )
            } doReturn false /* Never allow */
        }

    @Test
    fun verifyTestSetup() {
        assertThat(workListAdapter.userHandle).isEqualTo(workUser.handle)
        assertThat(personalListAdapter.userHandle).isEqualTo(personalUser.handle)
        assertThat(privateListAdapter.userHandle).isEqualTo(privateUser.handle)
    }

    @Test
    fun sameProfilePermitted() {
        val profileHelper = createProfileHelper(launchedAs = workUser)

        val provider =
            NoCrossProfileEmptyStateProvider(
                profileHelper,
                devicePolicyResources,
                crossProfileIntentsChecker,
                /* isShare = */ true
            )

        // Work to work, not blocked
        assertThat(provider.getEmptyState(workListAdapter)).isNull()

        crossProfileIntentsChecker.verifyCalled(never())
    }

    @Test
    fun testPersonalToWork() {
        val profileHelper = createProfileHelper(launchedAs = personalUser)

        val provider =
            NoCrossProfileEmptyStateProvider(
                profileHelper,
                devicePolicyResources,
                crossProfileIntentsChecker,
                /* isShare = */ true
            )

        val result = provider.getEmptyState(workListAdapter)
        assertThat(result).isNotNull()
        assertThat(result?.title).isEqualTo("Cross profile blocked")
        assertThat(result?.subtitle).isEqualTo("Blocked to Work")

        crossProfileIntentsChecker.verifyCalled(times(1), workIntents, personalUser, workUser)
    }

    @Test
    fun testWorkToPersonal() {
        val profileHelper = createProfileHelper(launchedAs = workUser)

        val provider =
            NoCrossProfileEmptyStateProvider(
                profileHelper,
                devicePolicyResources,
                crossProfileIntentsChecker,
                /* isShare = */ true
            )

        val result = provider.getEmptyState(personalListAdapter)
        assertThat(result).isNotNull()
        assertThat(result?.title).isEqualTo("Cross profile blocked")
        assertThat(result?.subtitle).isEqualTo("Blocked to Personal")

        crossProfileIntentsChecker.verifyCalled(times(1), personalIntents, workUser, personalUser)
    }

    @Test
    fun testWorkToPrivate() {
        val profileHelper = createProfileHelper(launchedAs = workUser)

        val provider =
            NoCrossProfileEmptyStateProvider(
                profileHelper,
                devicePolicyResources,
                crossProfileIntentsChecker,
                /* isShare = */ true
            )

        val result = provider.getEmptyState(privateListAdapter)
        assertThat(result).isNotNull()
        assertThat(result?.title).isEqualTo("Cross profile blocked")
        assertThat(result?.subtitle).isEqualTo("Blocked to Private")

        // effective target user is personalUser due to "delegate from parent"
        crossProfileIntentsChecker.verifyCalled(times(1), privateIntents, workUser, personalUser)
    }

    @Test
    fun testPrivateToPersonal() {
        val profileHelper = createProfileHelper(launchedAs = privateUser)

        val provider =
            NoCrossProfileEmptyStateProvider(
                profileHelper,
                devicePolicyResources,
                crossProfileIntentsChecker,
                /* isShare = */ true
            )

        // Private -> Personal is always allowed:
        // Private delegates to the parent profile for policy; so personal->personal is allowed.
        assertThat(provider.getEmptyState(personalListAdapter)).isNull()

        crossProfileIntentsChecker.verifyCalled(never())
    }

    private fun createProfileHelper(launchedAs: User): ProfileHelper {
        val userInteractor = UserInteractor(userRepository, launchedAs = launchedAs.handle)

        return ProfileHelper(
            userInteractor,
            CoroutineScope(Dispatchers.Unconfined),
            Dispatchers.Unconfined,
            flags
        )
    }

    private fun CrossProfileIntentsChecker.verifyCalled(
        mode: VerificationMode,
        list: List<Intent>? = null,
        sourceUser: User? = null,
        targetUser: User? = null,
    ) {
        val sourceUserId = argumentCaptor<Int>()
        val targetUserId = argumentCaptor<Int>()

        verify(this, mode)
            .hasCrossProfileIntents(same(list), sourceUserId.capture(), targetUserId.capture())
        sourceUser?.apply {
            assertWithMessage("hasCrossProfileIntents: source")
                .that(sourceUserId.firstValue)
                .isEqualTo(id)
        }
        targetUser?.apply {
            assertWithMessage("hasCrossProfileIntents: target")
                .that(targetUserId.firstValue)
                .isEqualTo(id)
        }
    }
}

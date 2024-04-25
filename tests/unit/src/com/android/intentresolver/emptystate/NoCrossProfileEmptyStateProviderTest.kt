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
import com.android.intentresolver.data.repository.FakeUserRepository
import com.android.intentresolver.domain.interactor.UserInteractor
import com.android.intentresolver.inject.FakeIntentResolverFlags
import com.android.intentresolver.shared.model.User
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyList
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.same
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@OptIn(JavaInterop::class)
class NoCrossProfileEmptyStateProviderTest {

    private val personalUser = User(0, User.Role.PERSONAL)
    private val workUser = User(10, User.Role.WORK)
    private val flags = FakeIntentResolverFlags()
    private val personalBlocker = mock<EmptyState>()
    private val workBlocker = mock<EmptyState>()

    private val userRepository = FakeUserRepository(listOf(personalUser, workUser))

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

    // Pretend that no intent can ever be forwarded
    val crossProfileIntentsChecker =
        mock<CrossProfileIntentsChecker> {
            on {
                hasCrossProfileIntents(
                    /* intents = */ anyList(),
                    /* source = */ anyInt(),
                    /* target = */ anyInt()
                )
            } doReturn false
        }
    private val sourceUserId = argumentCaptor<Int>()
    private val targetUserId = argumentCaptor<Int>()

    @Test
    fun testPersonalToWork() {
        val userInteractor = UserInteractor(userRepository, launchedAs = personalUser.handle)

        val profileHelper =
            ProfileHelper(
                userInteractor,
                CoroutineScope(Dispatchers.Unconfined),
                Dispatchers.Unconfined,
                flags
            )

        val provider =
            NoCrossProfileEmptyStateProvider(
                /* profileHelper = */ profileHelper,
                /* noWorkToPersonalEmptyState = */ personalBlocker,
                /* noPersonalToWorkEmptyState = */ workBlocker,
                /* crossProfileIntentsChecker = */ crossProfileIntentsChecker
            )

        // Personal to personal, not blocked
        assertThat(provider.getEmptyState(personalListAdapter)).isNull()
        // Not called because sourceUser == targetUser
        verify(crossProfileIntentsChecker, never())
            .hasCrossProfileIntents(anyList(), anyInt(), anyInt())

        // Personal to work, blocked
        assertThat(provider.getEmptyState(workListAdapter)).isSameInstanceAs(workBlocker)

        verify(crossProfileIntentsChecker, times(1))
            .hasCrossProfileIntents(
                same(workIntents),
                sourceUserId.capture(),
                targetUserId.capture()
            )
        assertThat(sourceUserId.firstValue).isEqualTo(personalUser.id)
        assertThat(targetUserId.firstValue).isEqualTo(workUser.id)
    }

    @Test
    fun testWorkToPersonal() {
        val userInteractor = UserInteractor(userRepository, launchedAs = workUser.handle)

        val profileHelper =
            ProfileHelper(
                userInteractor,
                CoroutineScope(Dispatchers.Unconfined),
                Dispatchers.Unconfined,
                flags
            )

        val provider =
            NoCrossProfileEmptyStateProvider(
                /* profileHelper = */ profileHelper,
                /* noWorkToPersonalEmptyState = */ personalBlocker,
                /* noPersonalToWorkEmptyState = */ workBlocker,
                /* crossProfileIntentsChecker = */ crossProfileIntentsChecker
            )

        // Work to work, not blocked
        assertThat(provider.getEmptyState(workListAdapter)).isNull()
        // Not called because sourceUser == targetUser
        verify(crossProfileIntentsChecker, never())
            .hasCrossProfileIntents(anyList(), anyInt(), anyInt())

        // Work to personal, blocked
        assertThat(provider.getEmptyState(personalListAdapter)).isSameInstanceAs(personalBlocker)

        verify(crossProfileIntentsChecker, times(1))
            .hasCrossProfileIntents(
                same(personalIntents),
                sourceUserId.capture(),
                targetUserId.capture()
            )
        assertThat(sourceUserId.firstValue).isEqualTo(workUser.id)
        assertThat(targetUserId.firstValue).isEqualTo(personalUser.id)
    }
}

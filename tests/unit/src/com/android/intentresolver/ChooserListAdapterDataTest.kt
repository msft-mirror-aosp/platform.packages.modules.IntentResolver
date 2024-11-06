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

package com.android.intentresolver

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.ComponentInfoFlags
import android.os.UserHandle
import android.os.UserManager
import android.view.LayoutInflater
import com.android.intentresolver.ResolverDataProvider.createActivityInfo
import com.android.intentresolver.ResolverDataProvider.createResolvedComponentInfo
import com.android.intentresolver.icons.TargetDataLoader
import com.android.intentresolver.logging.FakeEventLog
import com.android.intentresolver.util.TestExecutor
import com.android.internal.logging.InstanceId
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ChooserListAdapterDataTest {
    private val layoutInflater = mock<LayoutInflater>()
    private val packageManager = mock<PackageManager>()
    private val userManager = mock<UserManager> { on { isManagedProfile } doReturn false }
    private val resources =
        mock<android.content.res.Resources> {
            on { getInteger(R.integer.config_maxShortcutTargetsPerApp) } doReturn 2
        }
    private val context =
        mock<Context> {
            on { getSystemService(Context.LAYOUT_INFLATER_SERVICE) } doReturn layoutInflater
            on { getSystemService(Context.USER_SERVICE) } doReturn userManager
            on { packageManager } doReturn this@ChooserListAdapterDataTest.packageManager
            on { resources } doReturn this@ChooserListAdapterDataTest.resources
        }
    private val targetIntent = Intent(Intent.ACTION_SEND)
    private val payloadIntents = listOf(targetIntent)
    private val resolverListController =
        mock<ResolverListController> {
            on { filterIneligibleActivities(any(), any()) } doReturn null
            on { filterLowPriority(any(), any()) } doReturn null
        }
    private val resolverListCommunicator = FakeResolverListCommunicator()
    private val userHandle = UserHandle.of(UserHandle.USER_CURRENT)
    private val targetDataLoader = mock<TargetDataLoader>()
    private val backgroundExecutor = TestExecutor()
    private val immediateExecutor = TestExecutor(immediate = true)
    private val referrerFillInIntent =
        Intent().putExtra(Intent.EXTRA_REFERRER, "org.referrer.package")

    @Test
    fun test_twoTargetsWithNonOverlappingInitialIntent_threeTargetsInResolverAdapter() {
        val resolvedTargets =
            listOf(
                createResolvedComponentInfo(1),
                createResolvedComponentInfo(2),
            )
        val targetIntent = Intent(Intent.ACTION_SEND)
        whenever(
                resolverListController.getResolversForIntentAsUser(
                    true,
                    resolverListCommunicator.shouldGetActivityMetadata(),
                    resolverListCommunicator.shouldGetOnlyDefaultActivities(),
                    payloadIntents,
                    userHandle
                )
            )
            .thenReturn(ArrayList(resolvedTargets))
        val initialActivityInfo = createActivityInfo(3)
        val initialIntents =
            arrayOf(
                Intent(Intent.ACTION_SEND).apply { component = initialActivityInfo.componentName }
            )
        whenever(
                packageManager.getActivityInfo(
                    eq(initialActivityInfo.componentName),
                    any<ComponentInfoFlags>()
                )
            )
            .thenReturn(initialActivityInfo)
        val testSubject =
            ChooserListAdapter(
                context,
                payloadIntents,
                initialIntents,
                /*rList=*/ null,
                /*filterLastUsed=*/ false,
                resolverListController,
                userHandle,
                targetIntent,
                referrerFillInIntent,
                resolverListCommunicator,
                packageManager,
                FakeEventLog(InstanceId.fakeInstanceId(1)),
                /*maxRankedTargets=*/ 2,
                /*initialIntentsUserSpace=*/ userHandle,
                targetDataLoader,
                null,
                backgroundExecutor,
                immediateExecutor,
            )
        val doPostProcessing = true

        val isLoaded = testSubject.rebuildList(doPostProcessing)

        assertThat(isLoaded).isFalse()
        assertThat(testSubject.displayResolveInfoCount).isEqualTo(0)
        assertThat(backgroundExecutor.pendingCommandCount).isEqualTo(1)

        backgroundExecutor.runUntilIdle()

        // we don't reset placeholder count (legacy logic, likely an oversight?)
        assertThat(testSubject.displayResolveInfoCount).isEqualTo(resolvedTargets.size)
    }

    @Test
    fun test_twoTargetsWithOverlappingInitialIntent_oneTargetsInResolverAdapter() {
        val resolvedTargets =
            listOf(
                createResolvedComponentInfo(1),
                createResolvedComponentInfo(2),
            )
        val targetIntent = Intent(Intent.ACTION_SEND)
        whenever(
                resolverListController.getResolversForIntentAsUser(
                    true,
                    resolverListCommunicator.shouldGetActivityMetadata(),
                    resolverListCommunicator.shouldGetOnlyDefaultActivities(),
                    payloadIntents,
                    userHandle
                )
            )
            .thenReturn(ArrayList(resolvedTargets))
        val activityInfo = resolvedTargets[1].getResolveInfoAt(0).activityInfo
        val initialIntents =
            arrayOf(Intent(Intent.ACTION_SEND).apply { component = activityInfo.componentName })
        whenever(
                packageManager.getActivityInfo(
                    eq(activityInfo.componentName),
                    any<ComponentInfoFlags>()
                )
            )
            .thenReturn(activityInfo)
        val testSubject =
            ChooserListAdapter(
                context,
                payloadIntents,
                initialIntents,
                /*rList=*/ null,
                /*filterLastUsed=*/ false,
                resolverListController,
                userHandle,
                targetIntent,
                referrerFillInIntent,
                resolverListCommunicator,
                packageManager,
                FakeEventLog(InstanceId.fakeInstanceId(1)),
                /*maxRankedTargets=*/ 2,
                /*initialIntentsUserSpace=*/ userHandle,
                targetDataLoader,
                null,
                backgroundExecutor,
                immediateExecutor,
            )
        val doPostProcessing = true

        val isLoaded = testSubject.rebuildList(doPostProcessing)

        assertThat(isLoaded).isFalse()
        assertThat(testSubject.displayResolveInfoCount).isEqualTo(0)
        assertThat(backgroundExecutor.pendingCommandCount).isEqualTo(1)

        backgroundExecutor.runUntilIdle()

        // we don't reset placeholder count (legacy logic, likely an oversight?)
        assertThat(testSubject.displayResolveInfoCount).isEqualTo(resolvedTargets.size - 1)
    }
}

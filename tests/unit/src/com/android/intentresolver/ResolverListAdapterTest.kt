/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.UserHandle
import android.os.UserManager
import android.view.LayoutInflater
import com.android.intentresolver.ResolverDataProvider.createActivityInfo
import com.android.intentresolver.ResolverListAdapter.ResolverListCommunicator
import com.android.intentresolver.icons.TargetDataLoader
import com.android.intentresolver.util.TestExecutor
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

private const val PKG_NAME = "org.pkg.app"
private const val PKG_NAME_TWO = "org.pkg.two.app"
private const val PKG_NAME_THREE = "org.pkg.three.app"
private const val CLASS_NAME = "org.pkg.app.TheClass"

class ResolverListAdapterTest {
    private val layoutInflater = mock<LayoutInflater>()
    private val packageManager = mock<PackageManager>()
    private val userManager = mock<UserManager> { on { isManagedProfile } doReturn (false) }
    private val context =
        mock<Context> {
            on { getSystemService(Context.LAYOUT_INFLATER_SERVICE) } doReturn layoutInflater
            on { getSystemService(Context.USER_SERVICE) } doReturn userManager
            on { packageManager } doReturn this@ResolverListAdapterTest.packageManager
        }
    private val targetIntent = Intent(Intent.ACTION_SEND)
    private val payloadIntents = listOf(targetIntent)
    private val resolverListCommunicator = FakeResolverListCommunicator()
    private val userHandle = UserHandle.of(UserHandle.USER_CURRENT)
    private val targetDataLoader = mock<TargetDataLoader>()
    private val backgroundExecutor = TestExecutor()
    private val immediateExecutor = TestExecutor(immediate = true)

    @Test
    fun test_oneTargetNoLastChosen_oneTargetInAdapter() {
        val resolvedTargets = createResolvedComponents(ComponentName(PKG_NAME, CLASS_NAME))
        val resolverListController =
            mock<ResolverListController> {
                on { filterIneligibleActivities(any(), any()) } doReturn null
                on { filterLowPriority(any(), any()) } doReturn null
                on {
                    getResolversForIntentAsUser(
                        true,
                        resolverListCommunicator.shouldGetActivityMetadata(),
                        resolverListCommunicator.shouldGetOnlyDefaultActivities(),
                        payloadIntents,
                        userHandle
                    )
                } doReturn resolvedTargets
            }
        val testSubject =
            ResolverListAdapter(
                context,
                payloadIntents,
                /*initialIntents=*/ null,
                /*rList=*/ null,
                /*filterLastUsed=*/ true,
                resolverListController,
                userHandle,
                targetIntent,
                resolverListCommunicator,
                /*initialIntentsUserSpace=*/ userHandle,
                targetDataLoader,
                backgroundExecutor,
                immediateExecutor,
            )
        val doPostProcessing = true

        val isLoaded = testSubject.rebuildList(doPostProcessing)

        assertThat(isLoaded).isTrue()
        assertThat(testSubject.count).isEqualTo(resolvedTargets.size)
        assertThat(testSubject.placeholderCount).isEqualTo(0)
        assertThat(testSubject.hasFilteredItem()).isFalse()
        assertThat(testSubject.filteredItem).isNull()
        assertThat(testSubject.filteredPosition).isLessThan(0)
        assertThat(testSubject.unfilteredResolveList).containsExactlyElementsIn(resolvedTargets)
        assertThat(testSubject.isTabLoaded).isTrue()
        assertThat(backgroundExecutor.pendingCommandCount).isEqualTo(0)
        assertThat(resolverListCommunicator.sendVoiceCommandCount).isEqualTo(1)
    }

    @Test
    fun test_oneTargetThatWasLastChosen_NoTargetsInAdapter() {
        val resolvedTargets = createResolvedComponents(ComponentName(PKG_NAME, CLASS_NAME))
        val resolverListController =
            mock<ResolverListController> {
                on { filterIneligibleActivities(any(), any()) } doReturn null
                on { filterLowPriority(any(), any()) } doReturn null
                on {
                    getResolversForIntentAsUser(
                        true,
                        resolverListCommunicator.shouldGetActivityMetadata(),
                        resolverListCommunicator.shouldGetOnlyDefaultActivities(),
                        payloadIntents,
                        userHandle
                    )
                } doReturn resolvedTargets
                on { lastChosen } doReturn resolvedTargets[0].getResolveInfoAt(0)
            }
        val testSubject =
            ResolverListAdapter(
                context,
                payloadIntents,
                /*initialIntents=*/ null,
                /*rList=*/ null,
                /*filterLastUsed=*/ true,
                resolverListController,
                userHandle,
                targetIntent,
                resolverListCommunicator,
                /*initialIntentsUserSpace=*/ userHandle,
                targetDataLoader,
                backgroundExecutor,
                immediateExecutor,
            )
        val doPostProcessing = true

        val isLoaded = testSubject.rebuildList(doPostProcessing)

        assertThat(isLoaded).isTrue()
        assertThat(testSubject.count).isEqualTo(0)
        assertThat(testSubject.placeholderCount).isEqualTo(0)
        assertThat(testSubject.hasFilteredItem()).isTrue()
        assertThat(testSubject.filteredItem).isNotNull()
        assertThat(testSubject.filteredPosition).isEqualTo(0)
        assertThat(testSubject.unfilteredResolveList).containsExactlyElementsIn(resolvedTargets)
        assertThat(testSubject.isTabLoaded).isTrue()
        assertThat(backgroundExecutor.pendingCommandCount).isEqualTo(0)
    }

    @Test
    fun test_oneTargetLastChosenNotInTheList_oneTargetInAdapter() {
        val resolvedTargets = createResolvedComponents(ComponentName(PKG_NAME, CLASS_NAME))
        val resolverListController =
            mock<ResolverListController> {
                on { filterIneligibleActivities(any(), any()) } doReturn null
                on { filterLowPriority(any(), any()) } doReturn null
                on {
                    getResolversForIntentAsUser(
                        true,
                        resolverListCommunicator.shouldGetActivityMetadata(),
                        resolverListCommunicator.shouldGetOnlyDefaultActivities(),
                        payloadIntents,
                        userHandle
                    )
                } doReturn resolvedTargets
                on { lastChosen } doReturn createResolveInfo(PKG_NAME_TWO, CLASS_NAME, userHandle)
            }
        val testSubject =
            ResolverListAdapter(
                context,
                payloadIntents,
                /*initialIntents=*/ null,
                /*rList=*/ null,
                /*filterLastUsed=*/ true,
                resolverListController,
                userHandle,
                targetIntent,
                resolverListCommunicator,
                /*initialIntentsUserSpace=*/ userHandle,
                targetDataLoader,
                backgroundExecutor,
                immediateExecutor,
            )
        val doPostProcessing = true

        val isLoaded = testSubject.rebuildList(doPostProcessing)

        assertThat(isLoaded).isTrue()
        assertThat(testSubject.count).isEqualTo(resolvedTargets.size)
        assertThat(testSubject.placeholderCount).isEqualTo(0)
        assertThat(testSubject.hasFilteredItem()).isTrue()
        assertThat(testSubject.filteredItem).isNull()
        assertThat(testSubject.filteredPosition).isLessThan(0)
        assertWithMessage("unfilteredResolveList")
            .that(testSubject.unfilteredResolveList)
            .containsExactlyElementsIn(resolvedTargets)
        assertThat(testSubject.isTabLoaded).isTrue()
        assertThat(backgroundExecutor.pendingCommandCount).isEqualTo(0)
    }

    @Test
    fun test_oneTargetThatWasLastChosenFilteringDisabled_oneTargetInAdapter() {
        val resolvedTargets = createResolvedComponents(ComponentName(PKG_NAME, CLASS_NAME))
        val resolverListController =
            mock<ResolverListController> {
                on { filterIneligibleActivities(any(), any()) } doReturn null
                on { filterLowPriority(any(), any()) } doReturn null
                on {
                    getResolversForIntentAsUser(
                        true,
                        resolverListCommunicator.shouldGetActivityMetadata(),
                        resolverListCommunicator.shouldGetOnlyDefaultActivities(),
                        payloadIntents,
                        userHandle
                    )
                } doReturn resolvedTargets
                on { lastChosen } doReturn resolvedTargets[0].getResolveInfoAt(0)
            }
        val testSubject =
            ResolverListAdapter(
                context,
                payloadIntents,
                /*initialIntents=*/ null,
                /*rList=*/ null,
                /*filterLastUsed=*/ false,
                resolverListController,
                userHandle,
                targetIntent,
                resolverListCommunicator,
                /*initialIntentsUserSpace=*/ userHandle,
                targetDataLoader,
                backgroundExecutor,
                immediateExecutor,
            )
        val doPostProcessing = true

        val isLoaded = testSubject.rebuildList(doPostProcessing)

        assertThat(isLoaded).isTrue()
        assertThat(testSubject.count).isEqualTo(resolvedTargets.size)
        // we don't reset placeholder count
        assertThat(testSubject.placeholderCount).isEqualTo(0)
        assertThat(testSubject.hasFilteredItem()).isFalse()
        assertThat(testSubject.filteredItem).isNull()
        assertThat(testSubject.filteredPosition).isLessThan(0)
        assertWithMessage("unfilteredResolveList")
            .that(testSubject.unfilteredResolveList)
            .containsExactlyElementsIn(resolvedTargets)
        assertThat(testSubject.isTabLoaded).isTrue()
    }

    @Test
    fun test_twoTargetsNoLastChosenUseLayoutWithDefaults_twoTargetsInAdapter() {
        testTwoTargets(hasLastChosen = false, useLayoutWithDefaults = true)
    }

    @Test
    fun test_twoTargetsNoLastChosenDontUseLayoutWithDefaults_twoTargetsInAdapter() {
        testTwoTargets(hasLastChosen = false, useLayoutWithDefaults = false)
    }

    @Test
    fun test_twoTargetsLastChosenUseLayoutWithDefaults_oneTargetInAdapter() {
        testTwoTargets(hasLastChosen = true, useLayoutWithDefaults = true)
    }

    @Test
    fun test_twoTargetsLastChosenDontUseLayoutWithDefaults_oneTargetInAdapter() {
        testTwoTargets(hasLastChosen = true, useLayoutWithDefaults = false)
    }

    private fun testTwoTargets(hasLastChosen: Boolean, useLayoutWithDefaults: Boolean) {
        val resolvedTargets =
            createResolvedComponents(
                ComponentName(PKG_NAME, CLASS_NAME),
                ComponentName(PKG_NAME_TWO, CLASS_NAME),
            )
        val resolverListController =
            mock<ResolverListController> {
                on { filterIneligibleActivities(any(), any()) } doReturn null
                on { filterLowPriority(any(), any()) } doReturn null
                on {
                    getResolversForIntentAsUser(
                        true,
                        resolverListCommunicator.shouldGetActivityMetadata(),
                        resolverListCommunicator.shouldGetOnlyDefaultActivities(),
                        payloadIntents,
                        userHandle
                    )
                } doReturn resolvedTargets
                if (hasLastChosen) {
                    on { lastChosen } doReturn resolvedTargets[0].getResolveInfoAt(0)
                }
            }
        val resolverListCommunicator = FakeResolverListCommunicator(useLayoutWithDefaults)
        val testSubject =
            ResolverListAdapter(
                context,
                payloadIntents,
                /*initialIntents=*/ null,
                /*rList=*/ null,
                /*filterLastUsed=*/ true,
                resolverListController,
                userHandle,
                targetIntent,
                resolverListCommunicator,
                /*initialIntentsUserSpace=*/ userHandle,
                targetDataLoader,
                backgroundExecutor,
                immediateExecutor,
            )
        val doPostProcessing = true

        val isLoaded = testSubject.rebuildList(doPostProcessing)

        assertThat(isLoaded).isFalse()
        val placeholderCount = resolvedTargets.size - (if (useLayoutWithDefaults) 1 else 0)
        assertThat(testSubject.count).isEqualTo(placeholderCount)
        assertThat(testSubject.placeholderCount).isEqualTo(placeholderCount)
        assertThat(testSubject.hasFilteredItem()).isEqualTo(hasLastChosen)
        assertThat(testSubject.filteredItem).isNull()
        assertThat(testSubject.filteredPosition).isLessThan(0)
        assertThat(testSubject.unfilteredResolveList).containsExactlyElementsIn(resolvedTargets)
        assertThat(testSubject.isTabLoaded).isFalse()
        assertThat(backgroundExecutor.pendingCommandCount).isEqualTo(1)
        assertThat(resolverListCommunicator.sendVoiceCommandCount).isEqualTo(0)

        backgroundExecutor.runUntilIdle()

        // we don't reset placeholder count (legacy logic, likely an oversight?)
        assertThat(testSubject.placeholderCount).isEqualTo(placeholderCount)
        assertThat(testSubject.hasFilteredItem()).isEqualTo(hasLastChosen)
        if (hasLastChosen) {
            assertThat(testSubject.count).isEqualTo(resolvedTargets.size - 1)
            assertThat(testSubject.filteredItem).isNotNull()
            assertThat(testSubject.filteredPosition).isEqualTo(0)
        } else {
            assertThat(testSubject.count).isEqualTo(resolvedTargets.size)
            assertThat(testSubject.filteredItem).isNull()
            assertThat(testSubject.filteredPosition).isLessThan(0)
        }
        assertThat(testSubject.unfilteredResolveList).containsExactlyElementsIn(resolvedTargets)
        assertThat(testSubject.isTabLoaded).isTrue()
        assertThat(resolverListCommunicator.sendVoiceCommandCount).isEqualTo(1)
        assertThat(backgroundExecutor.pendingCommandCount).isEqualTo(0)
    }

    @Test
    fun test_twoTargetsLastChosenNotInTheList_twoTargetsInAdapter() {
        val resolvedTargets =
            createResolvedComponents(
                ComponentName(PKG_NAME, CLASS_NAME),
                ComponentName(PKG_NAME_TWO, CLASS_NAME),
            )
        val resolverListController =
            mock<ResolverListController> {
                on { filterIneligibleActivities(any(), any()) } doReturn null
                on { filterLowPriority(any(), any()) } doReturn null
                on {
                    getResolversForIntentAsUser(
                        true,
                        resolverListCommunicator.shouldGetActivityMetadata(),
                        resolverListCommunicator.shouldGetOnlyDefaultActivities(),
                        payloadIntents,
                        userHandle
                    )
                } doReturn resolvedTargets
                on { lastChosen } doReturn createResolveInfo(PKG_NAME, CLASS_NAME + "2", userHandle)
            }
        val testSubject =
            ResolverListAdapter(
                context,
                payloadIntents,
                /*initialIntents=*/ null,
                /*rList=*/ null,
                /*filterLastUsed=*/ true,
                resolverListController,
                userHandle,
                targetIntent,
                resolverListCommunicator,
                /*initialIntentsUserSpace=*/ userHandle,
                targetDataLoader,
                backgroundExecutor,
                immediateExecutor,
            )
        val doPostProcessing = false

        val isLoaded = testSubject.rebuildList(doPostProcessing)

        assertThat(isLoaded).isFalse()
        val placeholderCount = resolvedTargets.size - 1
        assertThat(testSubject.count).isEqualTo(placeholderCount)
        assertThat(testSubject.placeholderCount).isEqualTo(placeholderCount)
        assertThat(testSubject.hasFilteredItem()).isTrue()
        assertThat(testSubject.filteredItem).isNull()
        assertThat(testSubject.filteredPosition).isLessThan(0)
        assertThat(testSubject.unfilteredResolveList).containsExactlyElementsIn(resolvedTargets)
        assertThat(testSubject.isTabLoaded).isFalse()
        assertThat(backgroundExecutor.pendingCommandCount).isEqualTo(1)

        backgroundExecutor.runUntilIdle()

        // we don't reset placeholder count (legacy logic, likely an oversight?)
        assertThat(testSubject.placeholderCount).isEqualTo(placeholderCount)
        assertThat(testSubject.hasFilteredItem()).isTrue()
        assertThat(testSubject.count).isEqualTo(resolvedTargets.size)
        assertThat(testSubject.filteredItem).isNull()
        assertThat(testSubject.filteredPosition).isLessThan(0)
        assertThat(testSubject.unfilteredResolveList).containsExactlyElementsIn(resolvedTargets)
        assertThat(testSubject.isTabLoaded).isTrue()
        assertThat(backgroundExecutor.pendingCommandCount).isEqualTo(0)
    }

    @Test
    fun test_twoTargetsWithOtherProfileAndLastChosen_oneTargetInAdapter() {
        val resolvedTargets =
            createResolvedComponents(
                ComponentName(PKG_NAME, CLASS_NAME),
                ComponentName(PKG_NAME_TWO, CLASS_NAME),
            )
        resolvedTargets[1].getResolveInfoAt(0).targetUserId = 10
        // whenever(resolvedTargets[1].getResolveInfoAt(0).loadLabel(any())).thenReturn("Label")
        val resolverListController =
            mock<ResolverListController> {
                on { filterIneligibleActivities(any(), any()) } doReturn null
                on { filterLowPriority(any(), any()) } doReturn null
                on {
                    getResolversForIntentAsUser(
                        true,
                        resolverListCommunicator.shouldGetActivityMetadata(),
                        resolverListCommunicator.shouldGetOnlyDefaultActivities(),
                        payloadIntents,
                        userHandle
                    )
                } doReturn resolvedTargets
                on { lastChosen } doReturn resolvedTargets[0].getResolveInfoAt(0)
            }
        val testSubject =
            ResolverListAdapter(
                context,
                payloadIntents,
                /*initialIntents=*/ null,
                /*rList=*/ null,
                /*filterLastUsed=*/ true,
                resolverListController,
                userHandle,
                targetIntent,
                resolverListCommunicator,
                /*initialIntentsUserSpace=*/ userHandle,
                targetDataLoader,
                backgroundExecutor,
                immediateExecutor,
            )
        val doPostProcessing = true

        val isLoaded = testSubject.rebuildList(doPostProcessing)

        assertThat(isLoaded).isTrue()
        assertThat(testSubject.count).isEqualTo(1)
        assertThat(testSubject.placeholderCount).isEqualTo(0)
        assertThat(testSubject.otherProfile).isNotNull()
        assertThat(testSubject.hasFilteredItem()).isFalse()
        assertThat(testSubject.filteredItem).isNull()
        assertThat(testSubject.filteredPosition).isLessThan(0)
        assertThat(testSubject.unfilteredResolveList).containsExactlyElementsIn(resolvedTargets)
        assertThat(testSubject.isTabLoaded).isTrue()
        assertThat(backgroundExecutor.pendingCommandCount).isEqualTo(0)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun test_resultsSorted_appearInSortedOrderInAdapter() {
        val resolvedTargets =
            createResolvedComponents(
                ComponentName(PKG_NAME, CLASS_NAME),
                ComponentName(PKG_NAME_TWO, CLASS_NAME),
            )
        val resolverListController =
            mock<ResolverListController> {
                on { filterIneligibleActivities(any(), any()) } doReturn null
                on { filterLowPriority(any(), any()) } doReturn null
                on {
                    getResolversForIntentAsUser(
                        true,
                        resolverListCommunicator.shouldGetActivityMetadata(),
                        resolverListCommunicator.shouldGetOnlyDefaultActivities(),
                        payloadIntents,
                        userHandle
                    )
                } doReturn resolvedTargets
                on { sort(any()) } doAnswer
                    {
                        val components = it.arguments[0] as MutableList<ResolvedComponentInfo>
                        components[0] = components[1].also { components[1] = components[0] }
                        null
                    }
            }
        val testSubject =
            ResolverListAdapter(
                context,
                payloadIntents,
                /*initialIntents=*/ null,
                /*rList=*/ null,
                /*filterLastUsed=*/ true,
                resolverListController,
                userHandle,
                targetIntent,
                resolverListCommunicator,
                /*initialIntentsUserSpace=*/ userHandle,
                targetDataLoader,
                backgroundExecutor,
                immediateExecutor,
            )
        val doPostProcessing = true

        testSubject.rebuildList(doPostProcessing)

        backgroundExecutor.runUntilIdle()

        // we don't reset placeholder count (legacy logic, likely an oversight?)
        assertThat(testSubject.count).isEqualTo(resolvedTargets.size)
        assertThat(resolvedTargets[0].getResolveInfoAt(0).activityInfo.packageName)
            .isEqualTo(PKG_NAME_TWO)
        assertThat(resolvedTargets[1].getResolveInfoAt(0).activityInfo.packageName)
            .isEqualTo(PKG_NAME)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun test_ineligibleActivityFilteredOut_filteredComponentNotPresentInAdapter() {
        val resolvedTargets =
            createResolvedComponents(
                ComponentName(PKG_NAME, CLASS_NAME),
                ComponentName(PKG_NAME_TWO, CLASS_NAME),
            )
        val resolverListController =
            mock<ResolverListController> {
                on { filterIneligibleActivities(any(), any()) } doReturn null
                on { filterLowPriority(any(), any()) } doReturn null
                on {
                    getResolversForIntentAsUser(
                        true,
                        resolverListCommunicator.shouldGetActivityMetadata(),
                        resolverListCommunicator.shouldGetOnlyDefaultActivities(),
                        payloadIntents,
                        userHandle
                    )
                } doReturn resolvedTargets
                on { filterIneligibleActivities(any(), any()) } doAnswer
                    {
                        val components = it.arguments[0] as MutableList<ResolvedComponentInfo>
                        val original = ArrayList(components)
                        components.removeAt(1)
                        original
                    }
            }
        val testSubject =
            ResolverListAdapter(
                context,
                payloadIntents,
                /*initialIntents=*/ null,
                /*rList=*/ null,
                /*filterLastUsed=*/ true,
                resolverListController,
                userHandle,
                targetIntent,
                resolverListCommunicator,
                /*initialIntentsUserSpace=*/ userHandle,
                targetDataLoader,
                backgroundExecutor,
                immediateExecutor,
            )
        val doPostProcessing = true

        testSubject.rebuildList(doPostProcessing)

        backgroundExecutor.runUntilIdle()

        // we don't reset placeholder count (legacy logic, likely an oversight?)
        assertThat(testSubject.count).isEqualTo(1)
        assertThat(testSubject.getItem(0)?.resolveInfo)
            .isEqualTo(resolvedTargets[0].getResolveInfoAt(0))
        assertThat(testSubject.unfilteredResolveList).hasSize(2)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun test_baseResolveList_excludedFromIneligibleActivityFiltering() {
        val rList = listOf(createResolveInfo(PKG_NAME, CLASS_NAME, userHandle))
        val resolverListController =
            mock<ResolverListController> {
                on { filterLowPriority(any(), any()) } doReturn null
                on { addResolveListDedupe(any(), eq(targetIntent), eq(rList)) } doAnswer
                    {
                        val result = it.arguments[0] as MutableList<ResolvedComponentInfo>
                        result.addAll(
                            createResolvedComponents(
                                ComponentName(PKG_NAME, CLASS_NAME),
                                ComponentName(PKG_NAME_TWO, CLASS_NAME),
                            )
                        )
                        null
                    }
                on { filterIneligibleActivities(any(), any()) } doAnswer
                    {
                        val components = it.arguments[0] as MutableList<ResolvedComponentInfo>
                        val original = ArrayList(components)
                        components.clear()
                        original
                    }
            }
        val testSubject =
            ResolverListAdapter(
                context,
                payloadIntents,
                /*initialIntents=*/ null,
                rList,
                /*filterLastUsed=*/ true,
                resolverListController,
                userHandle,
                targetIntent,
                resolverListCommunicator,
                /*initialIntentsUserSpace=*/ userHandle,
                targetDataLoader,
                backgroundExecutor,
                immediateExecutor,
            )
        val doPostProcessing = true

        testSubject.rebuildList(doPostProcessing)

        backgroundExecutor.runUntilIdle()

        // we don't reset placeholder count (legacy logic, likely an oversight?)
        assertThat(testSubject.count).isEqualTo(2)
        assertThat(testSubject.unfilteredResolveList).hasSize(2)
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun test_lowPriorityComponentFilteredOut_filteredComponentNotPresentInAdapter() {
        val resolvedTargets =
            createResolvedComponents(
                ComponentName(PKG_NAME, CLASS_NAME),
                ComponentName(PKG_NAME_TWO, CLASS_NAME),
            )
        val resolverListController =
            mock<ResolverListController> {
                on { filterIneligibleActivities(any(), any()) } doReturn null
                on {
                    getResolversForIntentAsUser(
                        true,
                        resolverListCommunicator.shouldGetActivityMetadata(),
                        resolverListCommunicator.shouldGetOnlyDefaultActivities(),
                        payloadIntents,
                        userHandle
                    )
                } doReturn resolvedTargets
                on { filterLowPriority(any(), any()) } doAnswer
                    {
                        val components = it.arguments[0] as MutableList<ResolvedComponentInfo>
                        val original = ArrayList(components)
                        components.removeAt(1)
                        original
                    }
            }
        val testSubject =
            ResolverListAdapter(
                context,
                payloadIntents,
                /*initialIntents=*/ null,
                /*rList=*/ null,
                /*filterLastUsed=*/ true,
                resolverListController,
                userHandle,
                targetIntent,
                resolverListCommunicator,
                /*initialIntentsUserSpace=*/ userHandle,
                targetDataLoader,
                backgroundExecutor,
                immediateExecutor,
            )
        val doPostProcessing = true

        testSubject.rebuildList(doPostProcessing)

        backgroundExecutor.runUntilIdle()

        // we don't reset placeholder count (legacy logic, likely an oversight?)
        assertThat(testSubject.count).isEqualTo(1)
        assertThat(testSubject.getItem(0)?.resolveInfo)
            .isEqualTo(resolvedTargets[0].getResolveInfoAt(0))
        assertThat(testSubject.unfilteredResolveList).hasSize(2)
    }

    @Test
    fun test_twoTargetsWithNonOverlappingInitialIntent_threeTargetsInAdapter() {
        val resolvedTargets =
            createResolvedComponents(
                ComponentName(PKG_NAME, CLASS_NAME),
                ComponentName(PKG_NAME_TWO, CLASS_NAME),
            )
        val initialComponent = ComponentName(PKG_NAME_THREE, CLASS_NAME)
        val initialIntents =
            arrayOf(Intent(Intent.ACTION_SEND).apply { component = initialComponent })
        val resolverListController =
            mock<ResolverListController> {
                on { filterIneligibleActivities(any(), any()) } doReturn null
                on { filterLowPriority(any(), any()) } doReturn null
                on {
                    getResolversForIntentAsUser(
                        true,
                        resolverListCommunicator.shouldGetActivityMetadata(),
                        resolverListCommunicator.shouldGetOnlyDefaultActivities(),
                        payloadIntents,
                        userHandle
                    )
                } doReturn resolvedTargets
            }
        whenever(packageManager.getActivityInfo(eq(initialComponent), eq(0)))
            .thenReturn(createActivityInfo(initialComponent))
        val testSubject =
            ResolverListAdapter(
                context,
                payloadIntents,
                initialIntents,
                /*rList=*/ null,
                /*filterLastUsed=*/ true,
                resolverListController,
                userHandle,
                targetIntent,
                resolverListCommunicator,
                /*initialIntentsUserSpace=*/ userHandle,
                targetDataLoader,
                backgroundExecutor,
                immediateExecutor,
            )
        val doPostProcessing = true

        val isLoaded = testSubject.rebuildList(doPostProcessing)

        assertThat(isLoaded).isFalse()
        val placeholderCount = resolvedTargets.size - 1
        assertThat(testSubject.count).isEqualTo(placeholderCount)
        assertThat(testSubject.placeholderCount).isEqualTo(placeholderCount)
        assertThat(testSubject.hasFilteredItem()).isFalse()
        assertThat(testSubject.filteredItem).isNull()
        assertThat(testSubject.filteredPosition).isLessThan(0)
        assertThat(testSubject.unfilteredResolveList).containsExactlyElementsIn(resolvedTargets)
        assertThat(testSubject.isTabLoaded).isFalse()
        assertThat(backgroundExecutor.pendingCommandCount).isEqualTo(1)
        assertThat(resolverListCommunicator.sendVoiceCommandCount).isEqualTo(0)

        backgroundExecutor.runUntilIdle()

        // we don't reset placeholder count (legacy logic, likely an oversight?)
        assertThat(testSubject.placeholderCount).isEqualTo(placeholderCount)
        assertThat(testSubject.hasFilteredItem()).isFalse()
        assertThat(testSubject.count).isEqualTo(resolvedTargets.size + initialIntents.size)
        assertThat(testSubject.getItem(0)?.targetIntent?.component)
            .isEqualTo(initialIntents[0].component)
        assertThat(testSubject.filteredItem).isNull()
        assertThat(testSubject.filteredPosition).isLessThan(0)
        assertThat(testSubject.unfilteredResolveList).containsExactlyElementsIn(resolvedTargets)
        assertThat(testSubject.isTabLoaded).isTrue()
        assertThat(resolverListCommunicator.sendVoiceCommandCount).isEqualTo(1)
        assertThat(backgroundExecutor.pendingCommandCount).isEqualTo(0)
    }

    @Test
    fun test_twoTargetsWithOverlappingInitialIntent_twoTargetsInAdapter() {
        val resolvedTargets =
            createResolvedComponents(
                ComponentName(PKG_NAME, CLASS_NAME),
                ComponentName(PKG_NAME_TWO, CLASS_NAME),
            )
        val resolverListController =
            mock<ResolverListController> {
                on { filterIneligibleActivities(any(), any()) } doReturn null
                on { filterLowPriority(any(), any()) } doReturn null
                on {
                    getResolversForIntentAsUser(
                        true,
                        resolverListCommunicator.shouldGetActivityMetadata(),
                        resolverListCommunicator.shouldGetOnlyDefaultActivities(),
                        payloadIntents,
                        userHandle
                    )
                } doReturn resolvedTargets
            }
        val initialComponent = ComponentName(PKG_NAME_TWO, CLASS_NAME)
        val initialIntents =
            arrayOf(Intent(Intent.ACTION_SEND).apply { component = initialComponent })
        whenever(packageManager.getActivityInfo(eq(initialComponent), eq(0)))
            .thenReturn(createActivityInfo(initialComponent))
        val testSubject =
            ResolverListAdapter(
                context,
                payloadIntents,
                initialIntents,
                /*rList=*/ null,
                /*filterLastUsed=*/ true,
                resolverListController,
                userHandle,
                targetIntent,
                resolverListCommunicator,
                /*initialIntentsUserSpace=*/ userHandle,
                targetDataLoader,
                backgroundExecutor,
                immediateExecutor,
            )
        val doPostProcessing = true

        val isLoaded = testSubject.rebuildList(doPostProcessing)

        assertThat(isLoaded).isFalse()
        val placeholderCount = resolvedTargets.size - 1
        assertThat(testSubject.count).isEqualTo(placeholderCount)
        assertThat(testSubject.placeholderCount).isEqualTo(placeholderCount)
        assertThat(testSubject.hasFilteredItem()).isFalse()
        assertThat(testSubject.filteredItem).isNull()
        assertThat(testSubject.filteredPosition).isLessThan(0)
        assertThat(testSubject.unfilteredResolveList).containsExactlyElementsIn(resolvedTargets)
        assertThat(testSubject.isTabLoaded).isFalse()
        assertThat(backgroundExecutor.pendingCommandCount).isEqualTo(1)
        assertThat(resolverListCommunicator.sendVoiceCommandCount).isEqualTo(0)

        backgroundExecutor.runUntilIdle()

        // we don't reset placeholder count (legacy logic, likely an oversight?)
        assertThat(testSubject.placeholderCount).isEqualTo(placeholderCount)
        assertThat(testSubject.hasFilteredItem()).isFalse()
        assertThat(testSubject.count).isEqualTo(resolvedTargets.size)
        assertThat(testSubject.getItem(0)?.targetIntent?.component)
            .isEqualTo(initialIntents[0].component)
        assertThat(testSubject.filteredItem).isNull()
        assertThat(testSubject.filteredPosition).isLessThan(0)
        assertThat(testSubject.unfilteredResolveList).containsExactlyElementsIn(resolvedTargets)
        assertThat(testSubject.isTabLoaded).isTrue()
        assertThat(resolverListCommunicator.sendVoiceCommandCount).isEqualTo(1)
        assertThat(backgroundExecutor.pendingCommandCount).isEqualTo(0)
    }

    @Test
    fun testPostListReadyAtEndOfRebuild_synchronous() {
        val communicator = mock<ResolverListCommunicator> {}
        val resolverListController = mock<ResolverListController>()
        val testSubject =
            ResolverListAdapter(
                context,
                payloadIntents,
                /*initialIntents=*/ null,
                /*rList=*/ null,
                /*filterLastUsed=*/ true,
                resolverListController,
                userHandle,
                targetIntent,
                communicator,
                /*initialIntentsUserSpace=*/ userHandle,
                targetDataLoader,
                backgroundExecutor,
                immediateExecutor,
            )
        val doPostProcessing = false

        testSubject.rebuildList(doPostProcessing)

        verify(communicator).onPostListReady(testSubject, doPostProcessing, true)
    }

    @Test
    fun testPostListReadyAtEndOfRebuild_stages() {
        // We need at least two targets to trigger asynchronous sorting/"staged" progress callbacks.
        val resolvedTargets =
            createResolvedComponents(
                ComponentName(PKG_NAME, CLASS_NAME),
                ComponentName(PKG_NAME_TWO, CLASS_NAME),
            )
        val resolverListController =
            mock<ResolverListController> {
                on { filterIneligibleActivities(any(), any()) } doReturn null
                on { filterLowPriority(any(), any()) } doReturn null
                on { getResolversForIntentAsUser(any(), any(), any(), any(), any()) } doReturn
                    resolvedTargets
            }
        val communicator =
            mock<ResolverListCommunicator> {
                on { getReplacementIntent(any(), any()) } doAnswer { it.arguments[1] as Intent }
            }
        val testSubject =
            ResolverListAdapter(
                context,
                payloadIntents,
                /*initialIntents=*/ null,
                /*rList=*/ null,
                /*filterLastUsed=*/ true,
                resolverListController,
                userHandle,
                targetIntent,
                communicator,
                /*initialIntentsUserSpace=*/ userHandle,
                targetDataLoader,
                backgroundExecutor,
                immediateExecutor,
            )
        val doPostProcessing = false

        testSubject.rebuildList(doPostProcessing)

        backgroundExecutor.runUntilIdle()

        val inOrder = inOrder(communicator)
        inOrder.verify(communicator).onPostListReady(testSubject, doPostProcessing, false)
        inOrder.verify(communicator).onPostListReady(testSubject, doPostProcessing, true)
    }

    @Test
    fun testPostListReadyAtEndOfRebuild_queued() {
        val queuedCallbacksExecutor = TestExecutor()

        // We need at least two targets to trigger asynchronous sorting/"staged" progress callbacks.
        val resolvedTargets =
            createResolvedComponents(
                ComponentName(PKG_NAME, CLASS_NAME),
                ComponentName(PKG_NAME_TWO, CLASS_NAME),
            )
        val resolverListController =
            mock<ResolverListController> {
                on { filterIneligibleActivities(any(), any()) } doReturn null
                on { filterLowPriority(any(), any()) } doReturn null
                on { getResolversForIntentAsUser(any(), any(), any(), any(), any()) } doReturn
                    resolvedTargets
            }
        val communicator =
            mock<ResolverListCommunicator> {
                on { getReplacementIntent(any(), any()) } doAnswer { it.arguments[1] as Intent }
            }
        val testSubject =
            ResolverListAdapter(
                context,
                payloadIntents,
                /*initialIntents=*/ null,
                /*rList=*/ null,
                /*filterLastUsed=*/ true,
                resolverListController,
                userHandle,
                targetIntent,
                communicator,
                /*initialIntentsUserSpace=*/ userHandle,
                targetDataLoader,
                backgroundExecutor,
                queuedCallbacksExecutor
            )
        val doPostProcessing = false
        testSubject.rebuildList(doPostProcessing)

        // Finish all the background work (enqueueing both the "partial" and "complete" progress
        // callbacks) before dequeueing either callback.
        backgroundExecutor.runUntilIdle()
        queuedCallbacksExecutor.runUntilIdle()

        // TODO: we may not necessarily care to assert that there's a "partial progress" callback in
        // this case, since there won't be a chance to reflect the "partial" state in the UI before
        // the "completion" is queued (and if we depend on seeing an intermediate state, that could
        // be a bad sign for our handling in the "synchronous" case?). But we should probably at
        // least assert that the "partial" callback never arrives *after* the completion?
        val inOrder = inOrder(communicator)
        inOrder.verify(communicator).onPostListReady(testSubject, doPostProcessing, false)
        inOrder.verify(communicator).onPostListReady(testSubject, doPostProcessing, true)
    }

    @Test
    fun testPostListReadyAtEndOfRebuild_skippedIfStillQueuedOnDestroy() {
        val queuedCallbacksExecutor = TestExecutor()

        // We need at least two targets to trigger asynchronous sorting/"staged" progress callbacks.
        val resolvedTargets =
            createResolvedComponents(
                ComponentName(PKG_NAME, CLASS_NAME),
                ComponentName(PKG_NAME_TWO, CLASS_NAME),
            )
        val resolverListController =
            mock<ResolverListController> {
                on { filterIneligibleActivities(any(), any()) } doReturn null
                on { filterLowPriority(any(), any()) } doReturn null
                on { getResolversForIntentAsUser(any(), any(), any(), any(), any()) } doReturn
                    resolvedTargets
            }
        val communicator =
            mock<ResolverListCommunicator> {
                on { getReplacementIntent(any(), any()) } doAnswer { it.arguments[1] as Intent }
            }
        val testSubject =
            ResolverListAdapter(
                context,
                payloadIntents,
                /*initialIntents=*/ null,
                /*rList=*/ null,
                /*filterLastUsed=*/ true,
                resolverListController,
                userHandle,
                targetIntent,
                communicator,
                /*initialIntentsUserSpace=*/ userHandle,
                targetDataLoader,
                backgroundExecutor,
                queuedCallbacksExecutor
            )
        val doPostProcessing = false
        testSubject.rebuildList(doPostProcessing)

        // Finish all the background work (enqueueing both the "partial" and "complete" progress
        // callbacks) before dequeueing either callback.
        backgroundExecutor.runUntilIdle()

        // Notify that our activity is being destroyed while the callbacks are still queued.
        testSubject.onDestroy()

        queuedCallbacksExecutor.runUntilIdle()

        verify(communicator, never()).onPostListReady(eq(testSubject), eq(doPostProcessing), any())
    }

    private fun createResolvedComponents(
        vararg components: ComponentName
    ): List<ResolvedComponentInfo> {
        val result = ArrayList<ResolvedComponentInfo>(components.size)
        for (component in components) {
            val resolvedComponentInfo =
                ResolvedComponentInfo(
                    ComponentName(PKG_NAME, CLASS_NAME),
                    targetIntent,
                    createResolveInfo(component.packageName, component.className, userHandle)
                )
            result.add(resolvedComponentInfo)
        }
        return result
    }

    private fun createResolveInfo(
        packageName: String,
        className: String,
        handle: UserHandle,
        label: String? = null
    ): ResolveInfo =
        ResolveInfo().apply {
            activityInfo = createActivityInfo(ComponentName(packageName, className))
            targetUserId = handle.identifier
            userHandle = handle
            nonLocalizedLabel = label
        }
}

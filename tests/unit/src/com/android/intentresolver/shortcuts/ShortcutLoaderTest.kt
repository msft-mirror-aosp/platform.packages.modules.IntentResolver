/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.intentresolver.shortcuts

import android.app.prediction.AppPredictor
import android.content.ComponentName
import android.content.Context
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.ApplicationInfoFlags
import android.content.pm.ShortcutManager
import android.os.UserHandle
import android.os.UserManager
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.filters.SmallTest
import com.android.intentresolver.Flags.FLAG_FIX_SHORTCUTS_FLASHING
import com.android.intentresolver.chooser.DisplayResolveInfo
import com.android.intentresolver.createAppTarget
import com.android.intentresolver.createShareShortcutInfo
import com.android.intentresolver.createShortcutInfo
import com.google.common.truth.Truth.assertWithMessage
import java.util.function.Consumer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
class ShortcutLoaderTest {
    @get:Rule val flagRule = SetFlagsRule()

    private val appInfo =
        ApplicationInfo().apply {
            enabled = true
            flags = 0
        }
    private val pm =
        mock<PackageManager> {
            on { getApplicationInfo(any(), any<ApplicationInfoFlags>()) } doReturn appInfo
        }
    private val userManager =
        mock<UserManager> {
            on { isUserRunning(any<UserHandle>()) } doReturn true
            on { isUserUnlocked(any<UserHandle>()) } doReturn true
            on { isQuietModeEnabled(any<UserHandle>()) } doReturn false
        }
    private val context =
        mock<Context> {
            on { packageManager } doReturn pm
            on { createContextAsUser(any(), any()) } doReturn mock
            on { getSystemService(Context.USER_SERVICE) } doReturn userManager
        }
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = UnconfinedTestDispatcher(scheduler)
    private val scope = TestScope(dispatcher)
    private val intentFilter = mock<IntentFilter>()
    private val appPredictor = mock<ShortcutLoader.AppPredictorProxy>()
    private val callback = mock<Consumer<ShortcutLoader.Result>>()
    private val componentName = ComponentName("pkg", "Class")
    private val appTarget =
        mock<DisplayResolveInfo> { on { resolvedComponentName } doReturn componentName }
    private val appTargets = arrayOf(appTarget)
    private val matchingShortcutInfo = createShortcutInfo("id-0", componentName, 1)

    @Test
    fun test_loadShortcutsWithAppPredictor_resultIntegrity() =
        scope.runTest {
            val testSubject =
                ShortcutLoader(
                    context,
                    backgroundScope,
                    appPredictor,
                    UserHandle.of(0),
                    true,
                    intentFilter,
                    dispatcher,
                    callback,
                )

            testSubject.updateAppTargets(appTargets)

            val matchingAppTarget = createAppTarget(matchingShortcutInfo)
            val shortcuts =
                listOf(
                    matchingAppTarget,
                    // an AppTarget that does not belong to any resolved application; should be
                    // ignored
                    createAppTarget(
                        createShortcutInfo("id-1", ComponentName("mismatching.pkg", "Class"), 1)
                    ),
                )
            val appPredictorCallbackCaptor = argumentCaptor<AppPredictor.Callback>()
            verify(appPredictor, atLeastOnce())
                .registerPredictionUpdates(any(), appPredictorCallbackCaptor.capture())
            appPredictorCallbackCaptor.firstValue.onTargetsAvailable(shortcuts)

            val resultCaptor = argumentCaptor<ShortcutLoader.Result>()
            verify(callback, times(1)).accept(resultCaptor.capture())

            val result = resultCaptor.firstValue
            assertTrue("An app predictor result is expected", result.isFromAppPredictor)
            assertArrayEquals(
                "Wrong input app targets in the result",
                appTargets,
                result.appTargets,
            )
            assertEquals("Wrong shortcut count", 1, result.shortcutsByApp.size)
            assertEquals("Wrong app target", appTarget, result.shortcutsByApp[0].appTarget)
            for (shortcut in result.shortcutsByApp[0].shortcuts) {
                assertEquals(
                    "Wrong AppTarget in the cache",
                    matchingAppTarget,
                    result.directShareAppTargetCache[shortcut],
                )
                assertEquals(
                    "Wrong ShortcutInfo in the cache",
                    matchingShortcutInfo,
                    result.directShareShortcutInfoCache[shortcut],
                )
            }
        }

    @Test
    fun test_loadShortcutsWithShortcutManager_resultIntegrity() =
        scope.runTest {
            val shortcutManagerResult =
                listOf(
                    ShortcutManager.ShareShortcutInfo(matchingShortcutInfo, componentName),
                    // mismatching shortcut
                    createShareShortcutInfo("id-1", ComponentName("mismatching.pkg", "Class"), 1),
                )
            val shortcutManager =
                mock<ShortcutManager> {
                    on { getShareTargets(intentFilter) } doReturn shortcutManagerResult
                }
            whenever(context.getSystemService(Context.SHORTCUT_SERVICE)).thenReturn(shortcutManager)
            val testSubject =
                ShortcutLoader(
                    context,
                    backgroundScope,
                    null,
                    UserHandle.of(0),
                    true,
                    intentFilter,
                    dispatcher,
                    callback,
                )

            testSubject.updateAppTargets(appTargets)

            val resultCaptor = argumentCaptor<ShortcutLoader.Result>()
            verify(callback, times(1)).accept(resultCaptor.capture())

            val result = resultCaptor.firstValue
            assertFalse("An ShortcutManager result is expected", result.isFromAppPredictor)
            assertArrayEquals(
                "Wrong input app targets in the result",
                appTargets,
                result.appTargets,
            )
            assertEquals("Wrong shortcut count", 1, result.shortcutsByApp.size)
            assertEquals("Wrong app target", appTarget, result.shortcutsByApp[0].appTarget)
            for (shortcut in result.shortcutsByApp[0].shortcuts) {
                assertTrue(
                    "AppTargets are not expected the cache of a ShortcutManager result",
                    result.directShareAppTargetCache.isEmpty(),
                )
                assertEquals(
                    "Wrong ShortcutInfo in the cache",
                    matchingShortcutInfo,
                    result.directShareShortcutInfoCache[shortcut],
                )
            }
        }

    @Test
    fun test_appPredictorReturnsEmptyList_fallbackToShortcutManager() =
        scope.runTest {
            val shortcutManagerResult =
                listOf(
                    ShortcutManager.ShareShortcutInfo(matchingShortcutInfo, componentName),
                    // mismatching shortcut
                    createShareShortcutInfo("id-1", ComponentName("mismatching.pkg", "Class"), 1),
                )
            val shortcutManager =
                mock<ShortcutManager> {
                    on { getShareTargets(intentFilter) } doReturn (shortcutManagerResult)
                }
            whenever(context.getSystemService(Context.SHORTCUT_SERVICE)).thenReturn(shortcutManager)
            val testSubject =
                ShortcutLoader(
                    context,
                    backgroundScope,
                    appPredictor,
                    UserHandle.of(0),
                    true,
                    intentFilter,
                    dispatcher,
                    callback,
                )

            testSubject.updateAppTargets(appTargets)

            verify(appPredictor, times(1)).requestPredictionUpdate()
            val appPredictorCallbackCaptor = argumentCaptor<AppPredictor.Callback>()
            verify(appPredictor, times(1))
                .registerPredictionUpdates(any(), appPredictorCallbackCaptor.capture())
            appPredictorCallbackCaptor.firstValue.onTargetsAvailable(emptyList())

            val resultCaptor = argumentCaptor<ShortcutLoader.Result>()
            verify(callback, times(1)).accept(resultCaptor.capture())

            val result = resultCaptor.firstValue
            assertFalse("An ShortcutManager result is expected", result.isFromAppPredictor)
            assertArrayEquals(
                "Wrong input app targets in the result",
                appTargets,
                result.appTargets,
            )
            assertEquals("Wrong shortcut count", 1, result.shortcutsByApp.size)
            assertEquals("Wrong app target", appTarget, result.shortcutsByApp[0].appTarget)
            for (shortcut in result.shortcutsByApp[0].shortcuts) {
                assertTrue(
                    "AppTargets are not expected the cache of a ShortcutManager result",
                    result.directShareAppTargetCache.isEmpty(),
                )
                assertEquals(
                    "Wrong ShortcutInfo in the cache",
                    matchingShortcutInfo,
                    result.directShareShortcutInfoCache[shortcut],
                )
            }
        }

    @Test
    fun test_appPredictor_requestPredictionUpdateFailure_fallbackToShortcutManager() =
        scope.runTest {
            val shortcutManagerResult =
                listOf(
                    ShortcutManager.ShareShortcutInfo(matchingShortcutInfo, componentName),
                    // mismatching shortcut
                    createShareShortcutInfo("id-1", ComponentName("mismatching.pkg", "Class"), 1),
                )
            val shortcutManager =
                mock<ShortcutManager> {
                    on { getShareTargets(intentFilter) } doReturn shortcutManagerResult
                }
            whenever(context.getSystemService(Context.SHORTCUT_SERVICE)).thenReturn(shortcutManager)
            whenever(appPredictor.requestPredictionUpdate())
                .thenThrow(IllegalStateException("Test exception"))
            val testSubject =
                ShortcutLoader(
                    context,
                    backgroundScope,
                    appPredictor,
                    UserHandle.of(0),
                    true,
                    intentFilter,
                    dispatcher,
                    callback,
                )

            testSubject.updateAppTargets(appTargets)

            verify(appPredictor, times(1)).requestPredictionUpdate()

            val resultCaptor = argumentCaptor<ShortcutLoader.Result>()
            verify(callback, times(1)).accept(resultCaptor.capture())

            val result = resultCaptor.firstValue
            assertFalse("An ShortcutManager result is expected", result.isFromAppPredictor)
            assertArrayEquals(
                "Wrong input app targets in the result",
                appTargets,
                result.appTargets,
            )
            assertEquals("Wrong shortcut count", 1, result.shortcutsByApp.size)
            assertEquals("Wrong app target", appTarget, result.shortcutsByApp[0].appTarget)
            for (shortcut in result.shortcutsByApp[0].shortcuts) {
                assertTrue(
                    "AppTargets are not expected the cache of a ShortcutManager result",
                    result.directShareAppTargetCache.isEmpty(),
                )
                assertEquals(
                    "Wrong ShortcutInfo in the cache",
                    matchingShortcutInfo,
                    result.directShareShortcutInfoCache[shortcut],
                )
            }
        }

    @Test
    @DisableFlags(FLAG_FIX_SHORTCUTS_FLASHING)
    fun test_appPredictorNotResponding_noCallbackFromShortcutLoader() {
        scope.runTest {
            val shortcutManagerResult =
                listOf(
                    ShortcutManager.ShareShortcutInfo(matchingShortcutInfo, componentName),
                    // mismatching shortcut
                    createShareShortcutInfo("id-1", ComponentName("mismatching.pkg", "Class"), 1),
                )
            val shortcutManager =
                mock<ShortcutManager> {
                    on { getShareTargets(intentFilter) } doReturn shortcutManagerResult
                }
            whenever(context.getSystemService(Context.SHORTCUT_SERVICE)).thenReturn(shortcutManager)
            val testSubject =
                ShortcutLoader(
                    context,
                    backgroundScope,
                    appPredictor,
                    UserHandle.of(0),
                    true,
                    intentFilter,
                    dispatcher,
                    callback,
                )

            testSubject.updateAppTargets(appTargets)

            verify(appPredictor, times(1)).requestPredictionUpdate()

            scheduler.advanceTimeBy(ShortcutLoader.APP_PREDICTOR_RESPONSE_TIMEOUT_MS * 2)
            verify(callback, never()).accept(any())
        }
    }

    @Test
    @EnableFlags(FLAG_FIX_SHORTCUTS_FLASHING)
    fun test_appPredictorNotResponding_timeoutAndFallbackToShortcutManager() {
        scope.runTest {
            val testSubject =
                ShortcutLoader(
                    context,
                    backgroundScope,
                    appPredictor,
                    UserHandle.of(0),
                    true,
                    intentFilter,
                    dispatcher,
                    callback,
                )

            testSubject.updateAppTargets(appTargets)

            val matchingAppTarget = createAppTarget(matchingShortcutInfo)
            val shortcuts =
                listOf(
                    matchingAppTarget,
                    // an AppTarget that does not belong to any resolved application; should be
                    // ignored
                    createAppTarget(
                        createShortcutInfo("id-1", ComponentName("mismatching.pkg", "Class"), 1)
                    ),
                )
            val appPredictorCallbackCaptor = argumentCaptor<AppPredictor.Callback>()
            verify(appPredictor, atLeastOnce())
                .registerPredictionUpdates(any(), appPredictorCallbackCaptor.capture())
            appPredictorCallbackCaptor.firstValue.onTargetsAvailable(shortcuts)

            scheduler.advanceTimeBy(ShortcutLoader.APP_PREDICTOR_RESPONSE_TIMEOUT_MS * 2)
            verify(callback, times(1)).accept(any())
        }
    }

    @Test
    @EnableFlags(FLAG_FIX_SHORTCUTS_FLASHING)
    fun test_appPredictorResponding_appPredictorTimeoutJobIsCancelled() {
        scope.runTest {
            val shortcutManagerResult =
                listOf(
                    ShortcutManager.ShareShortcutInfo(matchingShortcutInfo, componentName),
                    // mismatching shortcut
                    createShareShortcutInfo("id-1", ComponentName("mismatching.pkg", "Class"), 1),
                )
            val shortcutManager =
                mock<ShortcutManager> {
                    on { getShareTargets(intentFilter) } doReturn shortcutManagerResult
                }
            whenever(context.getSystemService(Context.SHORTCUT_SERVICE)).thenReturn(shortcutManager)
            val testSubject =
                ShortcutLoader(
                    context,
                    backgroundScope,
                    appPredictor,
                    UserHandle.of(0),
                    true,
                    intentFilter,
                    dispatcher,
                    callback,
                )

            testSubject.updateAppTargets(appTargets)

            verify(appPredictor, times(1)).requestPredictionUpdate()

            scheduler.advanceTimeBy(ShortcutLoader.APP_PREDICTOR_RESPONSE_TIMEOUT_MS / 2)
            verify(callback, never()).accept(any())

            val resultCaptor = argumentCaptor<ShortcutLoader.Result>()
            scheduler.advanceTimeBy(ShortcutLoader.APP_PREDICTOR_RESPONSE_TIMEOUT_MS)
            verify(callback, times(1)).accept(resultCaptor.capture())
            val result = resultCaptor.firstValue
            assertWithMessage("An ShortcutManager result is expected")
                .that(result.isFromAppPredictor)
                .isFalse()
            assertWithMessage("Wrong input app targets in the result")
                .that(appTargets)
                .asList()
                .containsExactlyElementsIn(result.appTargets)
                .inOrder()
            assertWithMessage("Wrong shortcut count").that(result.shortcutsByApp).hasLength(1)
            assertWithMessage("Wrong app target")
                .that(appTarget)
                .isEqualTo(result.shortcutsByApp[0].appTarget)
            for (shortcut in result.shortcutsByApp[0].shortcuts) {
                assertWithMessage(
                        "AppTargets are not expected the cache of a ShortcutManager result"
                    )
                    .that(result.directShareAppTargetCache)
                    .isEmpty()
                assertWithMessage("Wrong ShortcutInfo in the cache")
                    .that(matchingShortcutInfo)
                    .isEqualTo(result.directShareShortcutInfoCache[shortcut])
            }
        }
    }

    @Test
    fun test_ShortcutLoader_shortcutsRequestedIndependentlyFromAppTargets() =
        scope.runTest {
            ShortcutLoader(
                context,
                backgroundScope,
                appPredictor,
                UserHandle.of(0),
                true,
                intentFilter,
                dispatcher,
                callback,
            )

            verify(appPredictor, times(1)).requestPredictionUpdate()
            verify(callback, never()).accept(any())
        }

    @Test
    fun test_ShortcutLoader_noResultsWithoutAppTargets() =
        scope.runTest {
            val shortcutManagerResult =
                listOf(
                    ShortcutManager.ShareShortcutInfo(matchingShortcutInfo, componentName),
                    // mismatching shortcut
                    createShareShortcutInfo("id-1", ComponentName("mismatching.pkg", "Class"), 1),
                )
            val shortcutManager =
                mock<ShortcutManager> {
                    on { getShareTargets(intentFilter) } doReturn shortcutManagerResult
                }
            whenever(context.getSystemService(Context.SHORTCUT_SERVICE)).thenReturn(shortcutManager)
            val testSubject =
                ShortcutLoader(
                    context,
                    backgroundScope,
                    null,
                    UserHandle.of(0),
                    true,
                    intentFilter,
                    dispatcher,
                    callback,
                )

            verify(shortcutManager, times(1)).getShareTargets(any())
            verify(callback, never()).accept(any())

            testSubject.reset()

            verify(shortcutManager, times(2)).getShareTargets(any())
            verify(callback, never()).accept(any())

            testSubject.updateAppTargets(appTargets)

            verify(shortcutManager, times(2)).getShareTargets(any())
            verify(callback, times(1)).accept(any())
        }

    @Test
    fun test_OnScopeCancellation_unsubscribeFromAppPredictor() {
        scope.runTest {
            ShortcutLoader(
                context,
                backgroundScope,
                appPredictor,
                UserHandle.of(0),
                true,
                intentFilter,
                dispatcher,
                callback,
            )

            verify(appPredictor, never()).unregisterPredictionUpdates(any())
        }

        verify(appPredictor, times(1)).unregisterPredictionUpdates(any())
    }

    @Test
    fun test_nullIntentFilterNoAppAppPredictorResults_returnEmptyResult() =
        scope.runTest {
            val shortcutManager = mock<ShortcutManager>()
            whenever(context.getSystemService(Context.SHORTCUT_SERVICE)).thenReturn(shortcutManager)
            val testSubject =
                ShortcutLoader(
                    context,
                    backgroundScope,
                    appPredictor,
                    UserHandle.of(0),
                    isPersonalProfile = true,
                    targetIntentFilter = null,
                    dispatcher,
                    callback,
                )

            testSubject.updateAppTargets(appTargets)

            verify(appPredictor, times(1)).requestPredictionUpdate()
            val appPredictorCallbackCaptor = argumentCaptor<AppPredictor.Callback>()
            verify(appPredictor, times(1))
                .registerPredictionUpdates(any(), appPredictorCallbackCaptor.capture())
            appPredictorCallbackCaptor.firstValue.onTargetsAvailable(emptyList())

            verify(shortcutManager, never()).getShareTargets(any())
            val resultCaptor = argumentCaptor<ShortcutLoader.Result>()
            verify(callback, times(1)).accept(resultCaptor.capture())

            val result = resultCaptor.firstValue
            assertWithMessage("A ShortcutManager result is expected")
                .that(result.isFromAppPredictor)
                .isFalse()
            assertArrayEquals(
                "Wrong input app targets in the result",
                appTargets,
                result.appTargets,
            )
            assertWithMessage("An empty result is expected").that(result.shortcutsByApp).isEmpty()
        }

    @Test
    fun test_workProfileNotRunning_doNotCallServices() {
        testDisabledWorkProfileDoNotCallSystem(isUserRunning = false)
    }

    @Test
    fun test_workProfileLocked_doNotCallServices() {
        testDisabledWorkProfileDoNotCallSystem(isUserUnlocked = false)
    }

    @Test
    fun test_workProfileQuiteModeEnabled_doNotCallServices() {
        testDisabledWorkProfileDoNotCallSystem(isQuietModeEnabled = true)
    }

    @Test
    fun test_mainProfileNotRunning_callServicesAnyway() {
        testAlwaysCallSystemForMainProfile(isUserRunning = false)
    }

    @Test
    fun test_mainProfileLocked_callServicesAnyway() {
        testAlwaysCallSystemForMainProfile(isUserUnlocked = false)
    }

    @Test
    fun test_mainProfileQuiteModeEnabled_callServicesAnyway() {
        testAlwaysCallSystemForMainProfile(isQuietModeEnabled = true)
    }

    @Test
    fun test_ShortcutLoaderDestroyed_appPredictorCallbackUnregisteredAndWatchdogCancelled() {
        scope.runTest {
            val testSubject =
                ShortcutLoader(
                    context,
                    backgroundScope,
                    appPredictor,
                    UserHandle.of(0),
                    true,
                    intentFilter,
                    dispatcher,
                    callback,
                )

            testSubject.updateAppTargets(appTargets)
            testSubject.destroy()

            verify(appPredictor, times(1)).registerPredictionUpdates(any(), any())
            verify(appPredictor, times(1)).unregisterPredictionUpdates(any())
        }
    }

    private fun testDisabledWorkProfileDoNotCallSystem(
        isUserRunning: Boolean = true,
        isUserUnlocked: Boolean = true,
        isQuietModeEnabled: Boolean = false,
    ) =
        scope.runTest {
            val userHandle = UserHandle.of(10)
            with(userManager) {
                whenever(isUserRunning(userHandle)).thenReturn(isUserRunning)
                whenever(isUserUnlocked(userHandle)).thenReturn(isUserUnlocked)
                whenever(isQuietModeEnabled(userHandle)).thenReturn(isQuietModeEnabled)
            }
            whenever(context.getSystemService(Context.USER_SERVICE)).thenReturn(userManager)
            val appPredictor = mock<ShortcutLoader.AppPredictorProxy>()
            val callback = mock<Consumer<ShortcutLoader.Result>>()
            val testSubject =
                ShortcutLoader(
                    context,
                    backgroundScope,
                    appPredictor,
                    userHandle,
                    false,
                    intentFilter,
                    dispatcher,
                    callback,
                )

            testSubject.updateAppTargets(arrayOf<DisplayResolveInfo>(mock()))

            verify(appPredictor, never()).requestPredictionUpdate()
        }

    private fun testAlwaysCallSystemForMainProfile(
        isUserRunning: Boolean = true,
        isUserUnlocked: Boolean = true,
        isQuietModeEnabled: Boolean = false,
    ) =
        scope.runTest {
            val userHandle = UserHandle.of(10)
            with(userManager) {
                whenever(isUserRunning(userHandle)).thenReturn(isUserRunning)
                whenever(isUserUnlocked(userHandle)).thenReturn(isUserUnlocked)
                whenever(isQuietModeEnabled(userHandle)).thenReturn(isQuietModeEnabled)
            }
            whenever(context.getSystemService(Context.USER_SERVICE)).thenReturn(userManager)
            val appPredictor = mock<ShortcutLoader.AppPredictorProxy>()
            val callback = mock<Consumer<ShortcutLoader.Result>>()
            val testSubject =
                ShortcutLoader(
                    context,
                    backgroundScope,
                    appPredictor,
                    userHandle,
                    true,
                    intentFilter,
                    dispatcher,
                    callback,
                )

            testSubject.updateAppTargets(arrayOf<DisplayResolveInfo>(mock()))

            verify(appPredictor, times(1)).requestPredictionUpdate()
        }
}

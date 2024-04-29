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

import android.content.res.Resources
import android.view.View
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

private const val TIMEOUT_MS = 200

@OptIn(ExperimentalCoroutinesApi::class)
class EnterTransitionAnimationDelegateTest {
    private val elementName = "shared-element"
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val lifecycleOwner = TestLifecycleOwner()

    private val transitionTargetView =
        mock<View> {
            // avoid the request-layout path in the delegate
            on { isInLayout } doReturn true
        }

    private val windowMock = mock<Window>()
    private val resourcesMock =
        mock<Resources> { on { getInteger(any<Int>()) } doReturn TIMEOUT_MS }
    private val activity =
        mock<ComponentActivity> {
            on { lifecycle } doReturn lifecycleOwner.lifecycle
            on { resources } doReturn resourcesMock
            on { isActivityTransitionRunning } doReturn true
            on { window } doReturn windowMock
        }

    private val testSubject = EnterTransitionAnimationDelegate(activity) { transitionTargetView }

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    @After
    fun cleanup() {
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        Dispatchers.resetMain()
    }

    @Test
    fun test_postponeTransition_timeout() {
        testSubject.postponeTransition()
        testSubject.markOffsetCalculated()

        scheduler.advanceTimeBy(TIMEOUT_MS + 1L)
        verify(activity) { 1 * { mock.startPostponedEnterTransition() } }
        verify(windowMock) { 0 * { setWindowAnimations(any<Int>()) } }
    }

    @Test
    fun test_postponeTransition_animation_resumes_only_once() {
        testSubject.postponeTransition()
        testSubject.markOffsetCalculated()
        testSubject.onTransitionElementReady(elementName)
        testSubject.markOffsetCalculated()
        testSubject.onTransitionElementReady(elementName)

        scheduler.advanceTimeBy(TIMEOUT_MS + 1L)
        verify(activity, times(1)).startPostponedEnterTransition()
    }

    @Test
    fun test_postponeTransition_resume_animation_conditions() {
        testSubject.postponeTransition()
        verify(activity) { 0 * { startPostponedEnterTransition() } }

        testSubject.markOffsetCalculated()
        verify(activity) { 0 * { startPostponedEnterTransition() } }

        testSubject.onAllTransitionElementsReady()
        verify(activity) { 1 * { startPostponedEnterTransition() } }
    }
}

/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.intentresolver.v2.domain.interactor

import android.content.Intent
import android.content.Intent.ACTION_SEND
import android.content.Intent.ACTION_SEND_MULTIPLE
import android.content.Intent.EXTRA_STREAM
import android.content.IntentSender
import android.net.Uri
import android.service.chooser.Flags
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.ChooserParamsUpdateRepository
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.TargetIntentRepository
import com.android.intentresolver.contentpreview.payloadtoggle.domain.model.ShareouselUpdate
import com.android.intentresolver.inject.FakeChooserServiceFlags
import com.android.intentresolver.mock
import com.android.intentresolver.v2.ui.model.ActivityModel
import com.android.intentresolver.v2.ui.model.ChooserRequest
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ChooserRequestUpdateInteractorTest {
    private val targetIntent =
        Intent(ACTION_SEND_MULTIPLE).apply {
            putExtra(
                EXTRA_STREAM,
                ArrayList<Uri>().apply {
                    add(createUri(1))
                    add(createUri(2))
                }
            )
            type = "image/png"
        }
    val initialRequest = createSomeChooserRequest(targetIntent)
    private val chooserIntent = Intent.createChooser(targetIntent, null)
    private val activityModel =
        ActivityModel(
            chooserIntent,
            launchedFromUid = 1,
            launchedFromPackage = "org.pkg.app",
            referrer = null,
        )
    private val targetIntentRepository =
        TargetIntentRepository(
            targetIntent,
            emptyList(),
        )
    private val chooserParamsUpdateRepository = ChooserParamsUpdateRepository()
    private val fakeFlags =
        FakeChooserServiceFlags().apply {
            setFlag(Flags.FLAG_CHOOSER_PAYLOAD_TOGGLING, true)
            setFlag(Flags.FLAG_CHOOSER_ALBUM_TEXT, false)
            setFlag(Flags.FLAG_ENABLE_SHARESHEET_METADATA_EXTRA, false)
        }
    private val testScope = TestScope()

    @Test
    fun testInitialIntentOnly_noUpdates() =
        testScope.runTest {
            val requestFlow = MutableStateFlow(initialRequest)
            val testSubject =
                ChooserRequestUpdateInteractor(
                    activityModel,
                    targetIntentRepository,
                    chooserParamsUpdateRepository,
                    requestFlow,
                    fakeFlags,
                )
            backgroundScope.launch { testSubject.launch() }
            testScheduler.runCurrent()

            assertWithMessage("No updates expected")
                .that(requestFlow.value)
                .isSameInstanceAs(initialRequest)
        }

    @Test
    fun testIntentUpdate_newRequestPublished() =
        testScope.runTest {
            val requestFlow = MutableStateFlow(initialRequest)
            val testSubject =
                ChooserRequestUpdateInteractor(
                    activityModel,
                    targetIntentRepository,
                    chooserParamsUpdateRepository,
                    requestFlow,
                    fakeFlags,
                )
            backgroundScope.launch { testSubject.launch() }
            targetIntentRepository.updateTargetIntent(
                Intent(targetIntent).apply {
                    action = ACTION_SEND
                    putExtra(EXTRA_STREAM, createUri(2))
                }
            )
            testScheduler.runCurrent()

            assertWithMessage("Another chooser request is expected")
                .that(requestFlow.value)
                .isNotEqualTo(initialRequest)
        }

    @Test
    fun testChooserParamsUpdate_newRequestPublished() =
        testScope.runTest {
            val requestFlow = MutableStateFlow(initialRequest)
            val testSubject =
                ChooserRequestUpdateInteractor(
                    activityModel,
                    targetIntentRepository,
                    chooserParamsUpdateRepository,
                    requestFlow,
                    fakeFlags,
                )
            backgroundScope.launch { testSubject.launch() }
            val newResultSender = mock<IntentSender>()
            chooserParamsUpdateRepository.setUpdates(
                ShareouselUpdate(
                    resultIntentSender = newResultSender,
                )
            )
            testScheduler.runCurrent()

            assertWithMessage("Another chooser request is expected")
                .that(requestFlow.value)
                .isNotEqualTo(initialRequest)

            assertWithMessage("Another chooser request is expected")
                .that(requestFlow.value.chosenComponentSender)
                .isSameInstanceAs(newResultSender)
        }
}

private fun createSomeChooserRequest(targetIntent: Intent) =
    ChooserRequest(
        targetIntent = targetIntent,
        targetAction = targetIntent.action,
        isSendActionTarget = true,
        targetType = null,
        launchedFromPackage = "",
        referrer = null,
    )

private fun createUri(id: Int) = Uri.parse("content://org.pkg.app/image-$id.png")

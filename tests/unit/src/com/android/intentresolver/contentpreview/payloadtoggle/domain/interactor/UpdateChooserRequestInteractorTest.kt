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

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.android.intentresolver.contentpreview.payloadtoggle.domain.interactor

import android.content.Intent
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.PendingSelectionCallbackRepository
import com.android.intentresolver.contentpreview.payloadtoggle.domain.intent.PendingIntentSender
import com.android.intentresolver.contentpreview.payloadtoggle.domain.model.ShareouselUpdate
import com.android.intentresolver.contentpreview.payloadtoggle.domain.model.ValueUpdate
import com.android.intentresolver.v2.data.model.fakeChooserRequest
import com.android.intentresolver.v2.data.repository.ChooserRequestRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class UpdateChooserRequestInteractorTest {
    @Test
    fun updateTargetIntentWithSelection() = runTest {
        val pendingIntentSender = PendingIntentSender {}
        val chooserRequestRepository =
            ChooserRequestRepository(
                initialRequest = fakeChooserRequest(),
                initialActions = emptyList(),
            )
        val selectionCallbackResult = ShareouselUpdate(metadataText = ValueUpdate.Value("update"))
        val pendingSelectionCallbackRepository = PendingSelectionCallbackRepository()
        val updateTargetIntentInteractor =
            UpdateTargetIntentInteractor(
                repository = pendingSelectionCallbackRepository,
                chooserRequestInteractor =
                    UpdateChooserRequestInteractor(
                        repository = chooserRequestRepository,
                        pendingIntentSender = pendingIntentSender,
                    )
            )
        val processTargetIntentUpdatesInteractor =
            ProcessTargetIntentUpdatesInteractor(
                selectionCallback = { selectionCallbackResult },
                repository = pendingSelectionCallbackRepository,
                chooserRequestInteractor =
                    UpdateChooserRequestInteractor(
                        repository = chooserRequestRepository,
                        pendingIntentSender = pendingIntentSender,
                    )
            )

        backgroundScope.launch { processTargetIntentUpdatesInteractor.activate() }

        updateTargetIntentInteractor.updateTargetIntent(Intent())
        runCurrent()

        assertThat(pendingSelectionCallbackRepository.pendingTargetIntent.value).isNull()
        assertThat(chooserRequestRepository.chooserRequest.value.metadataText).isEqualTo("update")
    }
}

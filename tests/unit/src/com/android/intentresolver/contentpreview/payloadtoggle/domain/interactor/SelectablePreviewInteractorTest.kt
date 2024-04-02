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
import android.net.Uri
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.PendingSelectionCallbackRepository
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.PreviewSelectionsRepository
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewModel
import com.android.intentresolver.v2.data.model.fakeChooserRequest
import com.android.intentresolver.v2.data.repository.ChooserRequestRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SelectablePreviewInteractorTest {

    @Test
    fun reflectPreviewRepo_initState() = runTest {
        val selectionRepo = PreviewSelectionsRepository()
        val chooserRequestRepo =
            ChooserRequestRepository(
                initialRequest = fakeChooserRequest(),
                initialActions = emptyList(),
            )
        val pendingSelectionCallbackRepo = PendingSelectionCallbackRepository()
        val underTest =
            SelectablePreviewInteractor(
                key = PreviewModel(Uri.fromParts("scheme", "ssp", "fragment"), null),
                selectionInteractor =
                    SelectionInteractor(
                        selectionsRepo = selectionRepo,
                        targetIntentModifier = { error("unexpected invocation") },
                        updateTargetIntentInteractor =
                            UpdateTargetIntentInteractor(
                                repository = pendingSelectionCallbackRepo,
                                chooserRequestInteractor =
                                    UpdateChooserRequestInteractor(
                                        repository = chooserRequestRepo,
                                        pendingIntentSender = { error("unexpected invocation") },
                                    )
                            )
                    ),
            )
        runCurrent()

        assertThat(underTest.isSelected.first()).isFalse()
    }

    @Test
    fun reflectPreviewRepo_updatedState() = runTest {
        val selectionRepo = PreviewSelectionsRepository()
        val chooserRequestRepo =
            ChooserRequestRepository(
                initialRequest = fakeChooserRequest(),
                initialActions = emptyList(),
            )
        val pendingSelectionCallbackRepo = PendingSelectionCallbackRepository()
        val underTest =
            SelectablePreviewInteractor(
                key = PreviewModel(Uri.fromParts("scheme", "ssp", "fragment"), "image/bitmap"),
                selectionInteractor =
                    SelectionInteractor(
                        selectionsRepo = selectionRepo,
                        targetIntentModifier = { error("unexpected invocation") },
                        updateTargetIntentInteractor =
                            UpdateTargetIntentInteractor(
                                repository = pendingSelectionCallbackRepo,
                                chooserRequestInteractor =
                                    UpdateChooserRequestInteractor(
                                        repository = chooserRequestRepo,
                                        pendingIntentSender = { error("unexpected invocation") },
                                    )
                            )
                    ),
            )

        assertThat(underTest.isSelected.first()).isFalse()

        selectionRepo.selections.value =
            setOf(PreviewModel(Uri.fromParts("scheme", "ssp", "fragment"), "image/bitmap"))
        runCurrent()

        assertThat(underTest.isSelected.first()).isTrue()
    }

    @Test
    fun setSelected_updatesChooserRequestRepo() = runTest {
        val modifiedIntent = Intent()
        val selectionRepo = PreviewSelectionsRepository()
        val chooserRequestRepo =
            ChooserRequestRepository(
                initialRequest = fakeChooserRequest(),
                initialActions = emptyList(),
            )
        val pendingSelectionCallbackRepo = PendingSelectionCallbackRepository()
        val underTest =
            SelectablePreviewInteractor(
                key = PreviewModel(Uri.fromParts("scheme", "ssp", "fragment"), "image/bitmap"),
                selectionInteractor =
                    SelectionInteractor(
                        selectionsRepo = selectionRepo,
                        targetIntentModifier = { modifiedIntent },
                        updateTargetIntentInteractor =
                            UpdateTargetIntentInteractor(
                                repository = pendingSelectionCallbackRepo,
                                chooserRequestInteractor =
                                    UpdateChooserRequestInteractor(
                                        repository = chooserRequestRepo,
                                        pendingIntentSender = { error("unexpected invocation") },
                                    )
                            )
                    ),
            )

        underTest.setSelected(true)
        runCurrent()

        assertThat(selectionRepo.selections.value)
            .containsExactly(
                PreviewModel(Uri.fromParts("scheme", "ssp", "fragment"), "image/bitmap")
            )

        assertThat(chooserRequestRepo.chooserRequest.value.targetIntent)
            .isSameInstanceAs(modifiedIntent)
        assertThat(pendingSelectionCallbackRepo.pendingTargetIntent.value)
            .isSameInstanceAs(modifiedIntent)
    }
}

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
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.pendingSelectionCallbackRepository
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.previewSelectionsRepository
import com.android.intentresolver.contentpreview.payloadtoggle.domain.intent.TargetIntentModifier
import com.android.intentresolver.contentpreview.payloadtoggle.domain.intent.targetIntentModifier
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewModel
import com.android.intentresolver.data.repository.chooserRequestRepository
import com.android.intentresolver.util.runKosmosTest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import org.junit.Test

class SelectablePreviewInteractorTest {

    @Test
    fun reflectPreviewRepo_initState() = runKosmosTest {
        targetIntentModifier = TargetIntentModifier { error("unexpected invocation") }
        val underTest =
            SelectablePreviewInteractor(
                key =
                    PreviewModel(
                        uri = Uri.fromParts("scheme", "ssp", "fragment"),
                        mimeType = null,
                        order = 0,
                    ),
                selectionInteractor = selectionInteractor,
            )
        runCurrent()

        assertThat(underTest.isSelected.first()).isFalse()
    }

    @Test
    fun reflectPreviewRepo_updatedState() = runKosmosTest {
        targetIntentModifier = TargetIntentModifier { error("unexpected invocation") }
        val underTest =
            SelectablePreviewInteractor(
                key =
                    PreviewModel(
                        uri = Uri.fromParts("scheme", "ssp", "fragment"),
                        mimeType = "image/bitmap",
                        order = 0,
                    ),
                selectionInteractor = selectionInteractor,
            )

        assertThat(underTest.isSelected.first()).isFalse()

        previewSelectionsRepository.selections.value =
            PreviewModel(
                    uri = Uri.fromParts("scheme", "ssp", "fragment"),
                    mimeType = "image/bitmap",
                    order = 0,
                )
                .let { mapOf(it.uri to it) }
        runCurrent()

        assertThat(underTest.isSelected.first()).isTrue()
    }

    @Test
    fun setSelected_updatesChooserRequestRepo() = runKosmosTest {
        val modifiedIntent = Intent()
        targetIntentModifier = TargetIntentModifier { modifiedIntent }
        val underTest =
            SelectablePreviewInteractor(
                key =
                    PreviewModel(
                        uri = Uri.fromParts("scheme", "ssp", "fragment"),
                        mimeType = "image/bitmap",
                        order = 0,
                    ),
                selectionInteractor = selectionInteractor,
            )

        underTest.setSelected(true)
        runCurrent()

        assertThat(previewSelectionsRepository.selections.value.keys)
            .containsExactly(Uri.fromParts("scheme", "ssp", "fragment"))

        assertThat(chooserRequestRepository.chooserRequest.value.targetIntent)
            .isSameInstanceAs(modifiedIntent)
        assertThat(pendingSelectionCallbackRepository.pendingTargetIntent.value)
            .isSameInstanceAs(modifiedIntent)
    }
}

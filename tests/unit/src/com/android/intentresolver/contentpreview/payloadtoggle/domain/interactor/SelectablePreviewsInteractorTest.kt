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

import android.net.Uri
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.cursorPreviewsRepository
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.previewSelectionsRepository
import com.android.intentresolver.contentpreview.payloadtoggle.domain.intent.TargetIntentModifier
import com.android.intentresolver.contentpreview.payloadtoggle.domain.intent.targetIntentModifier
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewModel
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewsModel
import com.android.intentresolver.util.runKosmosTest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import org.junit.Test

class SelectablePreviewsInteractorTest {

    @Test
    fun keySet_reflectsRepositoryInit() = runKosmosTest {
        cursorPreviewsRepository.previewsModel.value =
            PreviewsModel(
                previewModels =
                    listOf(
                        PreviewModel(
                            uri = Uri.fromParts("scheme", "ssp", "fragment"),
                            mimeType = "image/bitmap",
                            order = 0,
                        ),
                        PreviewModel(
                            uri = Uri.fromParts("scheme2", "ssp2", "fragment2"),
                            mimeType = "image/bitmap",
                            order = 1,
                        ),
                    ),
                startIdx = 0,
                loadMoreLeft = null,
                loadMoreRight = null,
                leftTriggerIndex = 0,
                rightTriggerIndex = 1,
            )
        previewSelectionsRepository.selections.value =
            PreviewModel(
                    uri = Uri.fromParts("scheme", "ssp", "fragment"),
                    mimeType = null,
                    order = 0,
                )
                .let { mapOf(it.uri to it) }
        targetIntentModifier = TargetIntentModifier { error("unexpected invocation") }
        val underTest = selectablePreviewsInteractor
        val keySet = underTest.previews.stateIn(backgroundScope)

        assertThat(keySet.value).isNotNull()
        assertThat(keySet.value!!.previewModels)
            .containsExactly(
                PreviewModel(
                    uri = Uri.fromParts("scheme", "ssp", "fragment"),
                    mimeType = "image/bitmap",
                    order = 0,
                ),
                PreviewModel(
                    uri = Uri.fromParts("scheme2", "ssp2", "fragment2"),
                    mimeType = "image/bitmap",
                    order = 1,
                ),
            )
            .inOrder()
        assertThat(keySet.value!!.startIdx).isEqualTo(0)
        assertThat(keySet.value!!.loadMoreLeft).isNull()
        assertThat(keySet.value!!.loadMoreRight).isNull()

        val firstModel =
            underTest.preview(
                PreviewModel(
                    uri = Uri.fromParts("scheme", "ssp", "fragment"),
                    mimeType = null,
                    order = 0,
                )
            )
        assertThat(firstModel.isSelected.first()).isTrue()

        val secondModel =
            underTest.preview(
                PreviewModel(
                    uri = Uri.fromParts("scheme2", "ssp2", "fragment2"),
                    mimeType = null,
                    order = 1,
                )
            )
        assertThat(secondModel.isSelected.first()).isFalse()
    }

    @Test
    fun keySet_reflectsRepositoryUpdate() = runKosmosTest {
        previewSelectionsRepository.selections.value =
            PreviewModel(
                    uri = Uri.fromParts("scheme", "ssp", "fragment"),
                    mimeType = null,
                    order = 0,
                )
                .let { mapOf(it.uri to it) }
        targetIntentModifier = TargetIntentModifier { error("unexpected invocation") }
        val underTest = selectablePreviewsInteractor

        val previews = underTest.previews.stateIn(backgroundScope)
        val firstModel =
            underTest.preview(
                PreviewModel(
                    uri = Uri.fromParts("scheme", "ssp", "fragment"),
                    mimeType = null,
                    order = 0,
                )
            )

        assertThat(previews.value).isNull()
        assertThat(firstModel.isSelected.first()).isTrue()

        var loadRequested = false

        cursorPreviewsRepository.previewsModel.value =
            PreviewsModel(
                previewModels =
                    listOf(
                        PreviewModel(
                            uri = Uri.fromParts("scheme", "ssp", "fragment"),
                            mimeType = "image/bitmap",
                            order = 0,
                        ),
                        PreviewModel(
                            uri = Uri.fromParts("scheme2", "ssp2", "fragment2"),
                            mimeType = "image/bitmap",
                            order = 1,
                        ),
                    ),
                startIdx = 5,
                loadMoreLeft = null,
                loadMoreRight = { loadRequested = true },
                leftTriggerIndex = 0,
                rightTriggerIndex = 1,
            )
        previewSelectionsRepository.selections.value = emptyMap()
        runCurrent()

        assertThat(previews.value).isNotNull()
        assertThat(previews.value!!.previewModels)
            .containsExactly(
                PreviewModel(
                    uri = Uri.fromParts("scheme", "ssp", "fragment"),
                    mimeType = "image/bitmap",
                    order = 0,
                ),
                PreviewModel(
                    uri = Uri.fromParts("scheme2", "ssp2", "fragment2"),
                    mimeType = "image/bitmap",
                    order = 1,
                ),
            )
            .inOrder()
        assertThat(previews.value!!.startIdx).isEqualTo(5)
        assertThat(previews.value!!.loadMoreLeft).isNull()
        assertThat(previews.value!!.loadMoreRight).isNotNull()

        assertThat(firstModel.isSelected.first()).isFalse()

        previews.value!!.loadMoreRight!!.invoke()

        assertThat(loadRequested).isTrue()
    }
}

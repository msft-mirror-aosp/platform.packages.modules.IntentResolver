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
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.CursorPreviewsRepository
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.PreviewSelectionsRepository
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewModel
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewsModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SelectablePreviewsInteractorTest {

    @Test
    fun keySet_reflectsRepositoryInit() = runTest {
        val repo =
            CursorPreviewsRepository().apply {
                previewsModel.value =
                    PreviewsModel(
                        previewModels =
                            setOf(
                                PreviewModel(
                                    Uri.fromParts("scheme", "ssp", "fragment"),
                                    "image/bitmap",
                                ),
                                PreviewModel(
                                    Uri.fromParts("scheme2", "ssp2", "fragment2"),
                                    "image/bitmap",
                                ),
                            ),
                        startIdx = 0,
                        loadMoreLeft = null,
                        loadMoreRight = null,
                    )
            }
        val selectionRepo =
            PreviewSelectionsRepository().apply {
                setSelection(
                    setOf(
                        PreviewModel(Uri.fromParts("scheme", "ssp", "fragment"), null),
                    )
                )
            }
        val underTest =
            SelectablePreviewsInteractor(
                previewsRepo = repo,
                selectionRepo = selectionRepo,
            )
        val keySet = underTest.previews.stateIn(backgroundScope)

        assertThat(keySet.value).isNotNull()
        assertThat(keySet.value!!.previewModels)
            .containsExactly(
                PreviewModel(Uri.fromParts("scheme", "ssp", "fragment"), "image/bitmap"),
                PreviewModel(Uri.fromParts("scheme2", "ssp2", "fragment2"), "image/bitmap"),
            )
            .inOrder()
        assertThat(keySet.value!!.startIdx).isEqualTo(0)
        assertThat(keySet.value!!.loadMoreLeft).isNull()
        assertThat(keySet.value!!.loadMoreRight).isNull()

        val firstModel =
            underTest.preview(PreviewModel(Uri.fromParts("scheme", "ssp", "fragment"), null))
        assertThat(firstModel.isSelected.first()).isTrue()

        val secondModel =
            underTest.preview(PreviewModel(Uri.fromParts("scheme2", "ssp2", "fragment2"), null))
        assertThat(secondModel.isSelected.first()).isFalse()
    }

    @Test
    fun keySet_reflectsRepositoryUpdate() = runTest {
        val previewsRepo = CursorPreviewsRepository()
        val selectionRepo =
            PreviewSelectionsRepository().apply {
                setSelection(setOf(PreviewModel(Uri.fromParts("scheme", "ssp", "fragment"), null)))
            }
        val underTest = SelectablePreviewsInteractor(previewsRepo, selectionRepo)
        val previews = underTest.previews.stateIn(backgroundScope)
        val firstModel =
            underTest.preview(PreviewModel(Uri.fromParts("scheme", "ssp", "fragment"), null))

        assertThat(previews.value).isNull()
        assertThat(firstModel.isSelected.first()).isTrue()

        var loadRequested = false

        previewsRepo.previewsModel.value =
            PreviewsModel(
                previewModels =
                    setOf(
                        PreviewModel(
                            Uri.fromParts("scheme", "ssp", "fragment"),
                            "image/bitmap",
                        ),
                        PreviewModel(
                            Uri.fromParts("scheme2", "ssp2", "fragment2"),
                            "image/bitmap",
                        ),
                    ),
                startIdx = 5,
                loadMoreLeft = null,
                loadMoreRight = { loadRequested = true },
            )
        selectionRepo.setSelection(emptySet())
        runCurrent()

        assertThat(previews.value).isNotNull()
        assertThat(previews.value!!.previewModels)
            .containsExactly(
                PreviewModel(Uri.fromParts("scheme", "ssp", "fragment"), "image/bitmap"),
                PreviewModel(Uri.fromParts("scheme2", "ssp2", "fragment2"), "image/bitmap"),
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

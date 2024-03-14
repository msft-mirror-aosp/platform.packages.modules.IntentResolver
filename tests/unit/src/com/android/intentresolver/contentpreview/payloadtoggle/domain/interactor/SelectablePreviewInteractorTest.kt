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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SelectablePreviewInteractorTest {

    @Test
    fun reflectPreviewRepo_initState() = runTest {
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
        val selectionRepo = PreviewSelectionsRepository()
        val underTest =
            SelectablePreviewInteractor(
                key = PreviewModel(Uri.fromParts("scheme", "ssp", "fragment"), null),
                selectionRepo = selectionRepo,
            )
        selectionRepo.setSelection(emptySet())
        testScheduler.runCurrent()

        assertThat(underTest.isSelected.first()).isFalse()
    }

    @Test
    fun reflectPreviewRepo_updatedState() = runTest {
        val repo = CursorPreviewsRepository()
        val selectionRepository = PreviewSelectionsRepository()
        val underTest =
            SelectablePreviewInteractor(
                key = PreviewModel(Uri.fromParts("scheme", "ssp", "fragment"), "image/bitmap"),
                selectionRepo = selectionRepository,
            )
        selectionRepository.setSelection(emptySet())

        assertThat(underTest.isSelected.first()).isFalse()

        repo.previewsModel.value =
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

        selectionRepository.setSelection(
            setOf(PreviewModel(Uri.fromParts("scheme", "ssp", "fragment"), "image/bitmap"))
        )
        runCurrent()

        assertThat(underTest.isSelected.first()).isTrue()
    }
}

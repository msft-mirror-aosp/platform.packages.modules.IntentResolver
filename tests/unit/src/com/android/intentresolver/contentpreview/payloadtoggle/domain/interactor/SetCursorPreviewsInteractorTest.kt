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
import com.android.intentresolver.contentpreview.payloadtoggle.domain.model.LoadDirection
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SetCursorPreviewsInteractorTest {
    @Test
    fun setPreviews_noAdditionalData() = runTest {
        val repo = CursorPreviewsRepository()
        val underTest = SetCursorPreviewsInteractor(repo)

        val loadState =
            underTest.setPreviews(
                previewsByKey =
                    setOf(
                        PreviewModel(
                            uri = Uri.fromParts("scheme", "ssp", "fragment"),
                            mimeType = null,
                        )
                    ),
                startIndex = 100,
                hasMoreLeft = false,
                hasMoreRight = false,
            )

        assertThat(loadState.first()).isNull()
        repo.previewsModel.value.let {
            assertThat(it).isNotNull()
            it!!
            assertThat(it.loadMoreRight).isNull()
            assertThat(it.loadMoreLeft).isNull()
            assertThat(it.startIdx).isEqualTo(100)
            assertThat(it.previewModels)
                .containsExactly(
                    PreviewModel(
                        uri = Uri.fromParts("scheme", "ssp", "fragment"),
                        mimeType = null,
                    )
                )
                .inOrder()
        }
    }

    @Test
    fun setPreviews_additionalData() = runTest {
        val repo = CursorPreviewsRepository()
        val underTest = SetCursorPreviewsInteractor(repo)

        val loadState =
            underTest
                .setPreviews(
                    previewsByKey =
                        setOf(
                            PreviewModel(
                                uri = Uri.fromParts("scheme", "ssp", "fragment"),
                                mimeType = null,
                            )
                        ),
                    startIndex = 100,
                    hasMoreLeft = true,
                    hasMoreRight = true,
                )
                .stateIn(backgroundScope)

        assertThat(loadState.value).isNull()
        repo.previewsModel.value.let {
            assertThat(it).isNotNull()
            it!!
            assertThat(it.loadMoreRight).isNotNull()
            assertThat(it.loadMoreLeft).isNotNull()

            it.loadMoreRight!!.invoke()
            runCurrent()
            assertThat(loadState.value).isEqualTo(LoadDirection.Right)

            it.loadMoreLeft!!.invoke()
            runCurrent()
            assertThat(loadState.value).isEqualTo(LoadDirection.Left)
        }
    }
}

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
import com.android.intentresolver.contentpreview.payloadtoggle.domain.model.LoadDirection
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewModel
import com.android.intentresolver.util.runKosmosTest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import org.junit.Test

class SetCursorPreviewsInteractorTest {
    @Test
    fun setPreviews_noAdditionalData() = runKosmosTest {
        val loadState =
            setCursorPreviewsInteractor.setPreviews(
                previews =
                    listOf(
                        PreviewModel(
                            uri = Uri.fromParts("scheme", "ssp", "fragment"),
                            mimeType = null,
                            order = 0,
                        )
                    ),
                startIndex = 100,
                hasMoreLeft = false,
                hasMoreRight = false,
                leftTriggerIndex = 0,
                rightTriggerIndex = 0,
            )

        assertThat(loadState.first()).isNull()
        cursorPreviewsRepository.previewsModel.value.let {
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
                        order = 0
                    )
                )
                .inOrder()
        }
    }

    @Test
    fun setPreviews_additionalData() = runKosmosTest {
        val loadState =
            setCursorPreviewsInteractor
                .setPreviews(
                    previews =
                        listOf(
                            PreviewModel(
                                uri = Uri.fromParts("scheme", "ssp", "fragment"),
                                mimeType = null,
                                order = 0,
                            )
                        ),
                    startIndex = 100,
                    hasMoreLeft = true,
                    hasMoreRight = true,
                    leftTriggerIndex = 0,
                    rightTriggerIndex = 0,
                )
                .stateIn(backgroundScope)

        assertThat(loadState.value).isNull()
        cursorPreviewsRepository.previewsModel.value.let {
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

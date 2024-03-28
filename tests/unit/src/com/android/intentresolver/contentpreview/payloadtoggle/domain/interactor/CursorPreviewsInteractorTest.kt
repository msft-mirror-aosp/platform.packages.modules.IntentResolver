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

import android.database.MatrixCursor
import android.net.Uri
import androidx.core.os.bundleOf
import com.android.intentresolver.contentpreview.FileInfo
import com.android.intentresolver.contentpreview.UriMetadataReader
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.cursorPreviewsRepository
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewModel
import com.android.intentresolver.contentpreview.uriMetadataReader
import com.android.intentresolver.util.KosmosTestScope
import com.android.intentresolver.util.cursor.viewBy
import com.android.intentresolver.util.runTest
import com.android.systemui.kosmos.Kosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import org.junit.Test

class CursorPreviewsInteractorTest {

    private fun runTestWithDeps(
        initialSelection: Iterable<Int> = (1..2),
        focusedItemIndex: Int = initialSelection.count() / 2,
        cursor: Iterable<Int> = (0 until 4),
        cursorStartPosition: Int = cursor.count() / 2,
        pageSize: Int = 16,
        maxLoadedPages: Int = 3,
        block: KosmosTestScope.(TestDeps) -> Unit,
    ) {
        with(Kosmos()) {
            this.focusedItemIndex = focusedItemIndex
            this.pageSize = pageSize
            this.maxLoadedPages = maxLoadedPages
            uriMetadataReader = UriMetadataReader {
                FileInfo.Builder(it).withMimeType("image/bitmap").build()
            }
            runTest {
                block(
                    TestDeps(
                        initialSelection,
                        cursor,
                        cursorStartPosition,
                    )
                )
            }
        }
    }

    private class TestDeps(
        initialSelectionRange: Iterable<Int>,
        private val cursorRange: Iterable<Int>,
        private val cursorStartPosition: Int,
    ) {
        val cursor =
            MatrixCursor(arrayOf("uri"))
                .apply {
                    extras = bundleOf("position" to cursorStartPosition)
                    for (i in cursorRange) {
                        newRow().add("uri", uri(i).toString())
                    }
                }
                .viewBy { getString(0)?.let(Uri::parse) }
        val initialPreviews: List<PreviewModel> =
            initialSelectionRange.map { i -> PreviewModel(uri = uri(i), mimeType = "image/bitmap") }

        private fun uri(index: Int) = Uri.fromParts("scheme$index", "ssp$index", "fragment$index")
    }

    @Test
    fun initialCursorLoad() = runTestWithDeps { deps ->
        backgroundScope.launch {
            cursorPreviewsInteractor.launch(deps.cursor, deps.initialPreviews)
        }
        runCurrent()

        assertThat(cursorPreviewsRepository.previewsModel.value).isNotNull()
        assertThat(cursorPreviewsRepository.previewsModel.value!!.startIdx).isEqualTo(0)
        assertThat(cursorPreviewsRepository.previewsModel.value!!.loadMoreLeft).isNull()
        assertThat(cursorPreviewsRepository.previewsModel.value!!.loadMoreRight).isNull()
        assertThat(cursorPreviewsRepository.previewsModel.value!!.previewModels)
            .containsExactly(
                PreviewModel(Uri.fromParts("scheme0", "ssp0", "fragment0"), "image/bitmap"),
                PreviewModel(Uri.fromParts("scheme1", "ssp1", "fragment1"), "image/bitmap"),
                PreviewModel(Uri.fromParts("scheme2", "ssp2", "fragment2"), "image/bitmap"),
                PreviewModel(Uri.fromParts("scheme3", "ssp3", "fragment3"), "image/bitmap"),
            )
            .inOrder()
    }

    @Test
    fun loadMoreLeft_evictRight() =
        runTestWithDeps(
            initialSelection = listOf(24),
            cursor = (0 until 48),
            pageSize = 16,
            maxLoadedPages = 1,
        ) { deps ->
            backgroundScope.launch {
                cursorPreviewsInteractor.launch(deps.cursor, deps.initialPreviews)
            }
            runCurrent()

            assertThat(cursorPreviewsRepository.previewsModel.value).isNotNull()
            assertThat(cursorPreviewsRepository.previewsModel.value!!.previewModels).hasSize(16)
            assertThat(cursorPreviewsRepository.previewsModel.value!!.previewModels.first().uri)
                .isEqualTo(Uri.fromParts("scheme16", "ssp16", "fragment16"))
            assertThat(cursorPreviewsRepository.previewsModel.value!!.previewModels.last().uri)
                .isEqualTo(Uri.fromParts("scheme31", "ssp31", "fragment31"))
            assertThat(cursorPreviewsRepository.previewsModel.value!!.loadMoreLeft).isNotNull()

            cursorPreviewsRepository.previewsModel.value!!.loadMoreLeft!!.invoke()
            runCurrent()

            assertThat(cursorPreviewsRepository.previewsModel.value).isNotNull()
            assertThat(cursorPreviewsRepository.previewsModel.value!!.previewModels).hasSize(16)
            assertThat(cursorPreviewsRepository.previewsModel.value!!.previewModels.first().uri)
                .isEqualTo(Uri.fromParts("scheme0", "ssp0", "fragment0"))
            assertThat(cursorPreviewsRepository.previewsModel.value!!.previewModels.last().uri)
                .isEqualTo(Uri.fromParts("scheme15", "ssp15", "fragment15"))
            assertThat(cursorPreviewsRepository.previewsModel.value!!.loadMoreLeft).isNull()
        }

    @Test
    fun loadMoreLeft_keepRight() =
        runTestWithDeps(
            initialSelection = listOf(24),
            cursor = (0 until 48),
            pageSize = 16,
            maxLoadedPages = 2,
        ) { deps ->
            backgroundScope.launch {
                cursorPreviewsInteractor.launch(deps.cursor, deps.initialPreviews)
            }
            runCurrent()

            assertThat(cursorPreviewsRepository.previewsModel.value).isNotNull()
            assertThat(cursorPreviewsRepository.previewsModel.value!!.previewModels).hasSize(16)
            assertThat(cursorPreviewsRepository.previewsModel.value!!.previewModels.first().uri)
                .isEqualTo(Uri.fromParts("scheme16", "ssp16", "fragment16"))
            assertThat(cursorPreviewsRepository.previewsModel.value!!.previewModels.last().uri)
                .isEqualTo(Uri.fromParts("scheme31", "ssp31", "fragment31"))
            assertThat(cursorPreviewsRepository.previewsModel.value!!.loadMoreLeft).isNotNull()

            cursorPreviewsRepository.previewsModel.value!!.loadMoreLeft!!.invoke()
            runCurrent()

            assertThat(cursorPreviewsRepository.previewsModel.value).isNotNull()
            assertThat(cursorPreviewsRepository.previewsModel.value!!.previewModels).hasSize(32)
            assertThat(cursorPreviewsRepository.previewsModel.value!!.previewModels.first().uri)
                .isEqualTo(Uri.fromParts("scheme0", "ssp0", "fragment0"))
            assertThat(cursorPreviewsRepository.previewsModel.value!!.previewModels.last().uri)
                .isEqualTo(Uri.fromParts("scheme31", "ssp31", "fragment31"))
            assertThat(cursorPreviewsRepository.previewsModel.value!!.loadMoreLeft).isNull()
        }

    @Test
    fun loadMoreRight_evictLeft() =
        runTestWithDeps(
            initialSelection = listOf(24),
            cursor = (0 until 48),
            pageSize = 16,
            maxLoadedPages = 1,
        ) { deps ->
            backgroundScope.launch {
                cursorPreviewsInteractor.launch(deps.cursor, deps.initialPreviews)
            }
            runCurrent()

            assertThat(cursorPreviewsRepository.previewsModel.value).isNotNull()
            assertThat(cursorPreviewsRepository.previewsModel.value!!.previewModels).hasSize(16)
            assertThat(cursorPreviewsRepository.previewsModel.value!!.previewModels.first().uri)
                .isEqualTo(Uri.fromParts("scheme16", "ssp16", "fragment16"))
            assertThat(cursorPreviewsRepository.previewsModel.value!!.previewModels.last().uri)
                .isEqualTo(Uri.fromParts("scheme31", "ssp31", "fragment31"))
            assertThat(cursorPreviewsRepository.previewsModel.value!!.loadMoreRight).isNotNull()

            cursorPreviewsRepository.previewsModel.value!!.loadMoreRight!!.invoke()
            runCurrent()

            assertThat(cursorPreviewsRepository.previewsModel.value).isNotNull()
            assertThat(cursorPreviewsRepository.previewsModel.value!!.previewModels).hasSize(16)
            assertThat(cursorPreviewsRepository.previewsModel.value!!.previewModels.first().uri)
                .isEqualTo(Uri.fromParts("scheme32", "ssp32", "fragment32"))
            assertThat(cursorPreviewsRepository.previewsModel.value!!.previewModels.last().uri)
                .isEqualTo(Uri.fromParts("scheme47", "ssp47", "fragment47"))
        }

    @Test
    fun loadMoreRight_keepLeft() =
        runTestWithDeps(
            initialSelection = listOf(24),
            cursor = (0 until 48),
            pageSize = 16,
            maxLoadedPages = 2,
        ) { deps ->
            backgroundScope.launch {
                cursorPreviewsInteractor.launch(deps.cursor, deps.initialPreviews)
            }
            runCurrent()

            assertThat(cursorPreviewsRepository.previewsModel.value).isNotNull()
            assertThat(cursorPreviewsRepository.previewsModel.value!!.previewModels).hasSize(16)
            assertThat(cursorPreviewsRepository.previewsModel.value!!.previewModels.first().uri)
                .isEqualTo(Uri.fromParts("scheme16", "ssp16", "fragment16"))
            assertThat(cursorPreviewsRepository.previewsModel.value!!.previewModels.last().uri)
                .isEqualTo(Uri.fromParts("scheme31", "ssp31", "fragment31"))
            assertThat(cursorPreviewsRepository.previewsModel.value!!.loadMoreRight).isNotNull()

            cursorPreviewsRepository.previewsModel.value!!.loadMoreRight!!.invoke()
            runCurrent()

            assertThat(cursorPreviewsRepository.previewsModel.value).isNotNull()
            assertThat(cursorPreviewsRepository.previewsModel.value!!.previewModels).hasSize(32)
            assertThat(cursorPreviewsRepository.previewsModel.value!!.previewModels.first().uri)
                .isEqualTo(Uri.fromParts("scheme16", "ssp16", "fragment16"))
            assertThat(cursorPreviewsRepository.previewsModel.value!!.previewModels.last().uri)
                .isEqualTo(Uri.fromParts("scheme47", "ssp47", "fragment47"))
        }

    @Test
    fun noMoreRight_appendUnclaimedFromInitialSelection() =
        runTestWithDeps(
            initialSelection = listOf(24, 50),
            cursor = listOf(24),
            pageSize = 16,
            maxLoadedPages = 2,
        ) { deps ->
            backgroundScope.launch {
                cursorPreviewsInteractor.launch(deps.cursor, deps.initialPreviews)
            }
            runCurrent()

            assertThat(cursorPreviewsRepository.previewsModel.value).isNotNull()
            assertThat(cursorPreviewsRepository.previewsModel.value!!.previewModels).hasSize(2)
            assertThat(cursorPreviewsRepository.previewsModel.value!!.previewModels.first().uri)
                .isEqualTo(Uri.fromParts("scheme24", "ssp24", "fragment24"))
            assertThat(cursorPreviewsRepository.previewsModel.value!!.previewModels.last().uri)
                .isEqualTo(Uri.fromParts("scheme50", "ssp50", "fragment50"))
            assertThat(cursorPreviewsRepository.previewsModel.value!!.loadMoreRight).isNull()
        }

    @Test
    fun noMoreLeft_appendUnclaimedFromInitialSelection() =
        runTestWithDeps(
            initialSelection = listOf(0, 24),
            cursor = listOf(24),
            pageSize = 16,
            maxLoadedPages = 2,
        ) { deps ->
            backgroundScope.launch {
                cursorPreviewsInteractor.launch(deps.cursor, deps.initialPreviews)
            }
            runCurrent()

            assertThat(cursorPreviewsRepository.previewsModel.value).isNotNull()
            assertThat(cursorPreviewsRepository.previewsModel.value!!.previewModels).hasSize(2)
            assertThat(cursorPreviewsRepository.previewsModel.value!!.previewModels.first().uri)
                .isEqualTo(Uri.fromParts("scheme0", "ssp0", "fragment0"))
            assertThat(cursorPreviewsRepository.previewsModel.value!!.previewModels.last().uri)
                .isEqualTo(Uri.fromParts("scheme24", "ssp24", "fragment24"))
            assertThat(cursorPreviewsRepository.previewsModel.value!!.loadMoreLeft).isNull()
        }
}

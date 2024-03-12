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
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.CursorPreviewsRepository
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.PreviewSelectionsRepository
import com.android.intentresolver.contentpreview.payloadtoggle.domain.cursor.CursorResolver
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewModel
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewsModel
import com.android.intentresolver.util.cursor.CursorView
import com.android.intentresolver.util.cursor.viewBy
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FetchPreviewsInteractorTest {

    private fun runTestWithDeps(
        initialSelection: Iterable<Int> = (1..2),
        focusedItemIndex: Int = initialSelection.count() / 2,
        cursor: Iterable<Int> = (0 until 4),
        cursorStartPosition: Int = cursor.count() / 2,
        pageSize: Int = 16,
        maxLoadedPages: Int = 3,
        block: TestScope.(TestDeps) -> Unit,
    ): Unit = runTest {
        block(
            TestDeps(
                initialSelection,
                focusedItemIndex,
                cursor,
                cursorStartPosition,
                pageSize,
                maxLoadedPages,
            )
        )
    }

    private class TestDeps(
        initialSelectionRange: Iterable<Int>,
        focusedItemIndex: Int,
        private val cursorRange: Iterable<Int>,
        private val cursorStartPosition: Int,
        pageSize: Int,
        maxLoadedPages: Int,
    ) {

        private fun uri(index: Int) = Uri.fromParts("scheme$index", "ssp$index", "fragment$index")

        val previewsRepo = CursorPreviewsRepository()

        val cursorResolver = FakeCursorResolver()

        private val uriMetadataReader = UriMetadataReader {
            FileInfo.Builder(it).withMimeType("image/bitmap").build()
        }

        val underTest =
            FetchPreviewsInteractor(
                setCursorPreviews = SetCursorPreviewsInteractor(previewsRepo),
                selectionRepository = PreviewSelectionsRepository(),
                cursorInteractor =
                    CursorPreviewsInteractor(
                        interactor = SetCursorPreviewsInteractor(previewsRepo = previewsRepo),
                        focusedItemIdx = focusedItemIndex,
                        uriMetadataReader = uriMetadataReader,
                        pageSize = pageSize,
                        maxLoadedPages = maxLoadedPages,
                    ),
                focusedItemIdx = focusedItemIndex,
                selectedItems = initialSelectionRange.map { idx -> uri(idx) },
                uriMetadataReader = uriMetadataReader,
                cursorResolver = cursorResolver,
            )

        inner class FakeCursorResolver : CursorResolver<Uri?> {
            private val mutex = Mutex(locked = true)

            fun complete() = mutex.unlock()

            override suspend fun getCursor(): CursorView<Uri?> =
                mutex.withLock {
                    MatrixCursor(arrayOf("uri"))
                        .apply {
                            extras = bundleOf("position" to cursorStartPosition)
                            for (i in cursorRange) {
                                newRow().add("uri", uri(i).toString())
                            }
                        }
                        .viewBy { getString(0)?.let(Uri::parse) }
                }
        }
    }

    @Test
    fun setsInitialPreviews() = runTestWithDeps { deps ->
        backgroundScope.launch { deps.underTest.launch() }
        runCurrent()

        assertThat(deps.previewsRepo.previewsModel.value)
            .isEqualTo(
                PreviewsModel(
                    previewModels =
                        setOf(
                            PreviewModel(
                                Uri.fromParts("scheme1", "ssp1", "fragment1"),
                                "image/bitmap",
                            ),
                            PreviewModel(
                                Uri.fromParts("scheme2", "ssp2", "fragment2"),
                                "image/bitmap",
                            ),
                        ),
                    startIdx = 1,
                    loadMoreLeft = null,
                    loadMoreRight = null,
                )
            )
    }

    @Test
    fun lookupCursorFromContentResolver() = runTestWithDeps { deps ->
        backgroundScope.launch { deps.underTest.launch() }
        deps.cursorResolver.complete()
        runCurrent()

        assertThat(deps.previewsRepo.previewsModel.value).isNotNull()
        assertThat(deps.previewsRepo.previewsModel.value!!.startIdx).isEqualTo(0)
        assertThat(deps.previewsRepo.previewsModel.value!!.loadMoreLeft).isNull()
        assertThat(deps.previewsRepo.previewsModel.value!!.loadMoreRight).isNull()
        assertThat(deps.previewsRepo.previewsModel.value!!.previewModels)
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
            backgroundScope.launch { deps.underTest.launch() }
            deps.cursorResolver.complete()
            runCurrent()

            assertThat(deps.previewsRepo.previewsModel.value).isNotNull()
            assertThat(deps.previewsRepo.previewsModel.value!!.previewModels).hasSize(16)
            assertThat(deps.previewsRepo.previewsModel.value!!.previewModels.first().uri)
                .isEqualTo(Uri.fromParts("scheme16", "ssp16", "fragment16"))
            assertThat(deps.previewsRepo.previewsModel.value!!.previewModels.last().uri)
                .isEqualTo(Uri.fromParts("scheme31", "ssp31", "fragment31"))
            assertThat(deps.previewsRepo.previewsModel.value!!.loadMoreLeft).isNotNull()

            deps.previewsRepo.previewsModel.value!!.loadMoreLeft!!.invoke()
            runCurrent()

            assertThat(deps.previewsRepo.previewsModel.value).isNotNull()
            assertThat(deps.previewsRepo.previewsModel.value!!.previewModels).hasSize(16)
            assertThat(deps.previewsRepo.previewsModel.value!!.previewModels.first().uri)
                .isEqualTo(Uri.fromParts("scheme0", "ssp0", "fragment0"))
            assertThat(deps.previewsRepo.previewsModel.value!!.previewModels.last().uri)
                .isEqualTo(Uri.fromParts("scheme15", "ssp15", "fragment15"))
            assertThat(deps.previewsRepo.previewsModel.value!!.loadMoreLeft).isNull()
        }

    @Test
    fun loadMoreLeft_keepRight() =
        runTestWithDeps(
            initialSelection = listOf(24),
            cursor = (0 until 48),
            pageSize = 16,
            maxLoadedPages = 2,
        ) { deps ->
            backgroundScope.launch { deps.underTest.launch() }
            deps.cursorResolver.complete()
            runCurrent()

            assertThat(deps.previewsRepo.previewsModel.value).isNotNull()
            assertThat(deps.previewsRepo.previewsModel.value!!.previewModels).hasSize(16)
            assertThat(deps.previewsRepo.previewsModel.value!!.previewModels.first().uri)
                .isEqualTo(Uri.fromParts("scheme16", "ssp16", "fragment16"))
            assertThat(deps.previewsRepo.previewsModel.value!!.previewModels.last().uri)
                .isEqualTo(Uri.fromParts("scheme31", "ssp31", "fragment31"))
            assertThat(deps.previewsRepo.previewsModel.value!!.loadMoreLeft).isNotNull()

            deps.previewsRepo.previewsModel.value!!.loadMoreLeft!!.invoke()
            runCurrent()

            assertThat(deps.previewsRepo.previewsModel.value).isNotNull()
            assertThat(deps.previewsRepo.previewsModel.value!!.previewModels).hasSize(32)
            assertThat(deps.previewsRepo.previewsModel.value!!.previewModels.first().uri)
                .isEqualTo(Uri.fromParts("scheme0", "ssp0", "fragment0"))
            assertThat(deps.previewsRepo.previewsModel.value!!.previewModels.last().uri)
                .isEqualTo(Uri.fromParts("scheme31", "ssp31", "fragment31"))
            assertThat(deps.previewsRepo.previewsModel.value!!.loadMoreLeft).isNull()
        }

    @Test
    fun loadMoreRight_evictLeft() =
        runTestWithDeps(
            initialSelection = listOf(24),
            cursor = (0 until 48),
            pageSize = 16,
            maxLoadedPages = 1,
        ) { deps ->
            backgroundScope.launch { deps.underTest.launch() }
            deps.cursorResolver.complete()
            runCurrent()

            assertThat(deps.previewsRepo.previewsModel.value).isNotNull()
            assertThat(deps.previewsRepo.previewsModel.value!!.previewModels).hasSize(16)
            assertThat(deps.previewsRepo.previewsModel.value!!.previewModels.first().uri)
                .isEqualTo(Uri.fromParts("scheme16", "ssp16", "fragment16"))
            assertThat(deps.previewsRepo.previewsModel.value!!.previewModels.last().uri)
                .isEqualTo(Uri.fromParts("scheme31", "ssp31", "fragment31"))
            assertThat(deps.previewsRepo.previewsModel.value!!.loadMoreRight).isNotNull()

            deps.previewsRepo.previewsModel.value!!.loadMoreRight!!.invoke()
            runCurrent()

            assertThat(deps.previewsRepo.previewsModel.value).isNotNull()
            assertThat(deps.previewsRepo.previewsModel.value!!.previewModels).hasSize(16)
            assertThat(deps.previewsRepo.previewsModel.value!!.previewModels.first().uri)
                .isEqualTo(Uri.fromParts("scheme32", "ssp32", "fragment32"))
            assertThat(deps.previewsRepo.previewsModel.value!!.previewModels.last().uri)
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
            backgroundScope.launch { deps.underTest.launch() }
            deps.cursorResolver.complete()
            runCurrent()

            assertThat(deps.previewsRepo.previewsModel.value).isNotNull()
            assertThat(deps.previewsRepo.previewsModel.value!!.previewModels).hasSize(16)
            assertThat(deps.previewsRepo.previewsModel.value!!.previewModels.first().uri)
                .isEqualTo(Uri.fromParts("scheme16", "ssp16", "fragment16"))
            assertThat(deps.previewsRepo.previewsModel.value!!.previewModels.last().uri)
                .isEqualTo(Uri.fromParts("scheme31", "ssp31", "fragment31"))
            assertThat(deps.previewsRepo.previewsModel.value!!.loadMoreRight).isNotNull()

            deps.previewsRepo.previewsModel.value!!.loadMoreRight!!.invoke()
            runCurrent()

            assertThat(deps.previewsRepo.previewsModel.value).isNotNull()
            assertThat(deps.previewsRepo.previewsModel.value!!.previewModels).hasSize(32)
            assertThat(deps.previewsRepo.previewsModel.value!!.previewModels.first().uri)
                .isEqualTo(Uri.fromParts("scheme16", "ssp16", "fragment16"))
            assertThat(deps.previewsRepo.previewsModel.value!!.previewModels.last().uri)
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
            backgroundScope.launch { deps.underTest.launch() }
            deps.cursorResolver.complete()
            runCurrent()

            assertThat(deps.previewsRepo.previewsModel.value).isNotNull()
            assertThat(deps.previewsRepo.previewsModel.value!!.previewModels).hasSize(2)
            assertThat(deps.previewsRepo.previewsModel.value!!.previewModels.first().uri)
                .isEqualTo(Uri.fromParts("scheme24", "ssp24", "fragment24"))
            assertThat(deps.previewsRepo.previewsModel.value!!.previewModels.last().uri)
                .isEqualTo(Uri.fromParts("scheme50", "ssp50", "fragment50"))
            assertThat(deps.previewsRepo.previewsModel.value!!.loadMoreRight).isNull()
        }

    @Test
    fun noMoreLeft_appendUnclaimedFromInitialSelection() =
        runTestWithDeps(
            initialSelection = listOf(0, 24),
            cursor = listOf(24),
            pageSize = 16,
            maxLoadedPages = 2,
        ) { deps ->
            backgroundScope.launch { deps.underTest.launch() }
            deps.cursorResolver.complete()
            runCurrent()

            assertThat(deps.previewsRepo.previewsModel.value).isNotNull()
            assertThat(deps.previewsRepo.previewsModel.value!!.previewModels).hasSize(2)
            assertThat(deps.previewsRepo.previewsModel.value!!.previewModels.first().uri)
                .isEqualTo(Uri.fromParts("scheme0", "ssp0", "fragment0"))
            assertThat(deps.previewsRepo.previewsModel.value!!.previewModels.last().uri)
                .isEqualTo(Uri.fromParts("scheme24", "ssp24", "fragment24"))
            assertThat(deps.previewsRepo.previewsModel.value!!.loadMoreLeft).isNull()
        }
}

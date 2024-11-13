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

import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore.MediaColumns.HEIGHT
import android.provider.MediaStore.MediaColumns.WIDTH
import android.service.chooser.AdditionalContentContract.Columns.URI
import android.service.chooser.AdditionalContentContract.CursorExtraKeys.POSITION
import android.util.Size
import androidx.core.os.bundleOf
import com.android.intentresolver.contentpreview.FileInfo
import com.android.intentresolver.contentpreview.UriMetadataReader
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.cursorPreviewsRepository
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.previewSelectionsRepository
import com.android.intentresolver.contentpreview.payloadtoggle.domain.intent.TargetIntentModifier
import com.android.intentresolver.contentpreview.payloadtoggle.domain.intent.targetIntentModifier
import com.android.intentresolver.contentpreview.payloadtoggle.domain.model.CursorRow
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewKey
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewModel
import com.android.intentresolver.contentpreview.readSize
import com.android.intentresolver.contentpreview.uriMetadataReader
import com.android.intentresolver.util.KosmosTestScope
import com.android.intentresolver.util.cursor.CursorView
import com.android.intentresolver.util.cursor.viewBy
import com.android.intentresolver.util.runTest
import com.android.systemui.kosmos.Kosmos
import com.google.common.truth.Correspondence
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import org.junit.Test

class CursorPreviewsInteractorTest {

    private fun runTestWithDeps(
        initialSelection: Iterable<Int>,
        focusedItemIndex: Int,
        cursor: Iterable<Int>,
        cursorStartPosition: Int,
        pageSize: Int = 16,
        maxLoadedPages: Int = 3,
        cursorSizes: Map<Int, Size> = emptyMap(),
        metadatSizes: Map<Int, Size> = emptyMap(),
        block: KosmosTestScope.(TestDeps) -> Unit,
    ) {
        val metadataUriToSize = metadatSizes.mapKeys { uri(it.key) }
        with(Kosmos()) {
            this.focusedItemIndex = focusedItemIndex
            this.pageSize = pageSize
            this.maxLoadedPages = maxLoadedPages
            this.targetIntentModifier = TargetIntentModifier { error("unexpected invocation") }
            uriMetadataReader =
                object : UriMetadataReader {
                    override fun getMetadata(uri: Uri): FileInfo =
                        FileInfo.Builder(uri)
                            .withPreviewUri(uri)
                            .withMimeType("image/bitmap")
                            .build()

                    override fun readPreviewSize(uri: Uri): Size? = metadataUriToSize[uri]
                }
            runTest {
                block(
                    TestDeps(
                        initialSelection,
                        focusedItemIndex,
                        cursor,
                        cursorStartPosition,
                        cursorSizes,
                    )
                )
            }
        }
    }

    private class TestDeps(
        initialSelectionRange: Iterable<Int>,
        focusedItemIndex: Int,
        private val cursorRange: Iterable<Int>,
        private val cursorStartPosition: Int,
        private val cursorSizes: Map<Int, Size>,
    ) {
        val cursor: CursorView<CursorRow?> =
            MatrixCursor(arrayOf(URI, WIDTH, HEIGHT))
                .apply {
                    extras = bundleOf(POSITION to cursorStartPosition)
                    for (i in cursorRange) {
                        val size = cursorSizes[i]
                        addRow(
                            arrayOf(
                                uri(i).toString(),
                                size?.width?.toString(),
                                size?.height?.toString(),
                            )
                        )
                    }
                }
                .viewBy {
                    getString(0)?.let { uriStr ->
                        CursorRow(Uri.parse(uriStr), readSize(), position)
                    }
                }
        val initialPreviews: List<PreviewModel> =
            initialSelectionRange.mapIndexed { index, i ->
                PreviewModel(
                    key =
                        if (index == focusedItemIndex) {
                            PreviewKey.final(0)
                        } else {
                            PreviewKey.temp(index)
                        },
                    uri = uri(i),
                    mimeType = "image/bitmap",
                    order = i,
                )
            }
    }

    @Test
    fun initialCursorLoad() =
        runTestWithDeps(
            initialSelection = (1..2),
            focusedItemIndex = 1,
            cursor = (0 until 10),
            cursorStartPosition = 2,
            cursorSizes = mapOf(0 to (200 x 100)),
            metadatSizes = mapOf(0 to (300 x 100), 3 to (400 x 100)),
            pageSize = 2,
            maxLoadedPages = 3,
        ) { deps ->
            backgroundScope.launch {
                cursorPreviewsInteractor.launch(deps.cursor, deps.initialPreviews)
            }
            runCurrent()

            assertThat(cursorPreviewsRepository.previewsModel.value).isNotNull()
            with(cursorPreviewsRepository.previewsModel.value!!) {
                assertThat(previewModels)
                    .containsExactlyElementsIn(
                        List(6) {
                            PreviewModel(
                                key = PreviewKey.final((it - 2)),
                                uri = Uri.fromParts("scheme$it", "ssp$it", "fragment$it"),
                                mimeType = "image/bitmap",
                                aspectRatio =
                                    when (it) {
                                        0 -> 2f
                                        3 -> 4f
                                        else -> 1f
                                    },
                                order = it,
                            )
                        }
                    )
                    .inOrder()
                assertThat(startIdx).isEqualTo(0)
                assertThat(loadMoreLeft).isNull()
                assertThat(loadMoreRight).isNotNull()
                assertThat(leftTriggerIndex).isEqualTo(2)
                assertThat(rightTriggerIndex).isEqualTo(4)
            }
        }

    @Test
    fun loadMoreLeft_evictRight() =
        runTestWithDeps(
            initialSelection = listOf(24),
            focusedItemIndex = 0,
            cursor = (0 until 48),
            cursorStartPosition = 24,
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
    fun loadMoreRight_evictLeft() =
        runTestWithDeps(
            initialSelection = listOf(24),
            focusedItemIndex = 0,
            cursor = (0 until 48),
            cursorStartPosition = 24,
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
    fun noMoreRight_appendUnclaimedFromInitialSelection() =
        runTestWithDeps(
            initialSelection = listOf(24, 50),
            focusedItemIndex = 0,
            cursor = listOf(24),
            cursorStartPosition = 0,
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
            focusedItemIndex = 1,
            cursor = listOf(24),
            cursorStartPosition = 0,
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

    @Test
    fun unclaimedRecordsGotUpdatedInSelectionInteractor() =
        runTestWithDeps(
            initialSelection = listOf(1),
            focusedItemIndex = 0,
            cursor = listOf(0, 1),
            cursorStartPosition = 1,
        ) { deps ->
            previewSelectionsRepository.selections.value =
                PreviewModel(
                        key = PreviewKey.final(0),
                        uri = uri(1),
                        mimeType = "image/png",
                        order = 0,
                    )
                    .let { mapOf(it.uri to it) }
            backgroundScope.launch {
                cursorPreviewsInteractor.launch(deps.cursor, deps.initialPreviews)
            }
            runCurrent()

            assertThat(previewSelectionsRepository.selections.value.values)
                .containsExactly(
                    PreviewModel(
                        key = PreviewKey.final(0),
                        uri = uri(1),
                        mimeType = "image/bitmap",
                        order = 1,
                    )
                )
        }

    @Test
    fun testReadFailedPages() =
        runTestWithDeps(
            initialSelection = listOf(4),
            focusedItemIndex = 0,
            cursor = emptyList(),
            cursorStartPosition = 0,
            pageSize = 2,
            maxLoadedPages = 5,
        ) { deps ->
            val cursor =
                MatrixCursor(arrayOf(URI)).apply {
                    extras = bundleOf(POSITION to 4)
                    for (i in 0 until 10) {
                        addRow(arrayOf(uri(i)))
                    }
                }
            val failingPositions = setOf(1, 5, 8)
            val failingCursor =
                object : Cursor by cursor {
                        override fun move(offset: Int): Boolean = moveToPosition(position + offset)

                        override fun moveToPosition(position: Int): Boolean {
                            if (failingPositions.contains(position)) {
                                throw RuntimeException(
                                    "A test exception when moving the cursor to position $position"
                                )
                            }
                            return cursor.moveToPosition(position)
                        }

                        override fun moveToFirst(): Boolean = moveToPosition(0)

                        override fun moveToLast(): Boolean = moveToPosition(count - 1)

                        override fun moveToNext(): Boolean = move(1)

                        override fun moveToPrevious(): Boolean = move(-1)
                    }
                    .viewBy {
                        getString(0)?.let { uriStr ->
                            CursorRow(Uri.parse(uriStr), readSize(), position)
                        }
                    }
            backgroundScope.launch {
                cursorPreviewsInteractor.launch(failingCursor, deps.initialPreviews)
            }
            runCurrent()

            assertThat(cursorPreviewsRepository.previewsModel.value).isNotNull()
            assertThat(cursorPreviewsRepository.previewsModel.value!!.previewModels)
                .comparingElementsUsing<PreviewModel, Uri>(
                    Correspondence.transforming({ it.uri }, "has a Uri of")
                )
                .containsExactlyElementsIn(
                    (0..7).filterNot { failingPositions.contains(it) }.map { uri(it) }
                )
                .inOrder()
        }
}

private fun uri(index: Int) = Uri.fromParts("scheme$index", "ssp$index", "fragment$index")

private infix fun Int.x(height: Int) = Size(this, height)

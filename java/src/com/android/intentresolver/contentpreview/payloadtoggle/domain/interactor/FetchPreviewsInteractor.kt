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

package com.android.intentresolver.contentpreview.payloadtoggle.domain.interactor

import android.net.Uri
import com.android.intentresolver.contentpreview.UriMetadataReader
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.PreviewSelectionsRepository
import com.android.intentresolver.contentpreview.payloadtoggle.domain.cursor.CursorResolver
import com.android.intentresolver.contentpreview.payloadtoggle.domain.cursor.PayloadToggle
import com.android.intentresolver.contentpreview.payloadtoggle.domain.model.CursorRow
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewKey
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewModel
import com.android.intentresolver.inject.ContentUris
import com.android.intentresolver.inject.FocusedItemIndex
import com.android.intentresolver.util.mapParallelIndexed
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Populates the data displayed in Shareousel. */
class FetchPreviewsInteractor
@Inject
constructor(
    private val setCursorPreviews: SetCursorPreviewsInteractor,
    private val selectionRepository: PreviewSelectionsRepository,
    private val cursorInteractor: CursorPreviewsInteractor,
    @FocusedItemIndex private val focusedItemIdx: Int,
    @ContentUris private val selectedItems: List<@JvmSuppressWildcards Uri>,
    private val uriMetadataReader: UriMetadataReader,
    @PayloadToggle private val cursorResolver: CursorResolver<@JvmSuppressWildcards CursorRow?>,
) {
    suspend fun activate() = coroutineScope {
        val cursor = async { cursorResolver.getCursor() }
        val initialPreviewMap = getInitialPreviews()
        selectionRepository.selections.value = initialPreviewMap.associateBy { it.uri }
        setCursorPreviews.setPreviews(
            previews = initialPreviewMap,
            startIndex = focusedItemIdx,
            hasMoreLeft = false,
            hasMoreRight = false,
            leftTriggerIndex = initialPreviewMap.indices.first(),
            rightTriggerIndex = initialPreviewMap.indices.last(),
        )
        cursorInteractor.launch(cursor.await() ?: return@coroutineScope, initialPreviewMap)
    }

    private suspend fun getInitialPreviews(): List<PreviewModel> =
        selectedItems
            // Restrict parallelism so as to not overload the metadata reader; anecdotally, too
            // many parallel queries causes failures.
            .mapParallelIndexed(parallelism = 4) { index, uri ->
                val metadata = uriMetadataReader.getMetadata(uri)
                PreviewModel(
                    key =
                        if (index == focusedItemIdx) {
                            PreviewKey.final(0)
                        } else {
                            PreviewKey.temp(index)
                        },
                    uri = uri,
                    previewUri = metadata.previewUri,
                    mimeType = metadata.mimeType,
                    aspectRatio =
                        metadata.previewUri?.let {
                            uriMetadataReader.readPreviewSize(it).aspectRatioOrDefault(1f)
                        } ?: 1f,
                    order =
                        when {
                            index < focusedItemIdx -> Int.MIN_VALUE + index
                            index == focusedItemIdx -> 0
                            else -> Int.MAX_VALUE - selectedItems.size + index + 1
                        },
                )
            }
}

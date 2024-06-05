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

import com.android.intentresolver.contentpreview.MimeTypeClassifier
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.PreviewSelectionsRepository
import com.android.intentresolver.contentpreview.payloadtoggle.domain.intent.TargetIntentModifier
import com.android.intentresolver.contentpreview.payloadtoggle.shared.ContentType
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.updateAndGet

class SelectionInteractor
@Inject
constructor(
    private val selectionsRepo: PreviewSelectionsRepository,
    private val targetIntentModifier: TargetIntentModifier<PreviewModel>,
    private val updateTargetIntentInteractor: UpdateTargetIntentInteractor,
    private val mimeTypeClassifier: MimeTypeClassifier,
) {
    /** List of selected previews. */
    val selections: StateFlow<List<PreviewModel>>
        get() = selectionsRepo.selections

    /** Amount of selected previews. */
    val amountSelected: Flow<Int> = selectionsRepo.selections.map { it.size }

    val aggregateContentType: Flow<ContentType> = selections.map { aggregateContentType(it) }

    fun select(model: PreviewModel) {
        updateChooserRequest(selectionsRepo.selections.updateAndGet { it + model })
    }

    fun unselect(model: PreviewModel) {
        if (selectionsRepo.selections.value.size > 1) {
            updateChooserRequest(selectionsRepo.selections.updateAndGet { it - model })
        }
    }

    private fun updateChooserRequest(selections: List<PreviewModel>) {
        val intent = targetIntentModifier.intentFromSelection(selections)
        updateTargetIntentInteractor.updateTargetIntent(intent)
    }

    private fun aggregateContentType(
        items: List<PreviewModel>,
    ): ContentType {
        if (items.isEmpty()) {
            return ContentType.Other
        }

        var allImages = true
        var allVideos = true
        for (item in items) {
            allImages = allImages && mimeTypeClassifier.isImageType(item.mimeType)
            allVideos = allVideos && mimeTypeClassifier.isVideoType(item.mimeType)

            if (!allImages && !allVideos) {
                break
            }
        }

        return when {
            allImages -> ContentType.Image
            allVideos -> ContentType.Video
            else -> ContentType.Other
        }
    }
}

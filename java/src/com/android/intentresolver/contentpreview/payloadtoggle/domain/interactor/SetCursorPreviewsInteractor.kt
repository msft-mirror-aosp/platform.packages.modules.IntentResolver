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

import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.CursorPreviewsRepository
import com.android.intentresolver.contentpreview.payloadtoggle.domain.model.LoadDirection
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewModel
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewsModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Updates [CursorPreviewsRepository] with new previews. */
class SetCursorPreviewsInteractor
@Inject
constructor(private val previewsRepo: CursorPreviewsRepository) {
    /** Stores new [previews], and returns a flow of load requests triggered by Shareousel. */
    fun setPreviews(
        previews: List<PreviewModel>,
        startIndex: Int,
        hasMoreLeft: Boolean,
        hasMoreRight: Boolean,
        leftTriggerIndex: Int,
        rightTriggerIndex: Int
    ): Flow<LoadDirection?> {
        val loadingState = MutableStateFlow<LoadDirection?>(null)
        previewsRepo.previewsModel.value =
            PreviewsModel(
                previewModels = previews,
                startIdx = startIndex,
                loadMoreLeft =
                    if (hasMoreLeft) {
                        ({ loadingState.value = LoadDirection.Left })
                    } else {
                        null
                    },
                loadMoreRight =
                    if (hasMoreRight) {
                        ({ loadingState.value = LoadDirection.Right })
                    } else {
                        null
                    },
                leftTriggerIndex = leftTriggerIndex,
                rightTriggerIndex = rightTriggerIndex,
            )
        return loadingState.asStateFlow()
    }
}

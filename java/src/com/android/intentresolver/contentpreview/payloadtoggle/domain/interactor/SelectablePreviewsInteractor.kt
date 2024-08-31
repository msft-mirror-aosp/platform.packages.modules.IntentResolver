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
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewModel
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewsModel
import com.android.intentresolver.logging.EventLog
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class SelectablePreviewsInteractor
@Inject
constructor(
    private val previewsRepo: CursorPreviewsRepository,
    private val selectionInteractor: SelectionInteractor,
    private val eventLog: EventLog,
) {
    /** Keys of previews available for display in Shareousel. */
    val previews: Flow<PreviewsModel?>
        get() = previewsRepo.previewsModel

    /**
     * Returns a [SelectablePreviewInteractor] that can be used to interact with the individual
     * preview associated with [key].
     */
    fun preview(key: PreviewModel) = SelectablePreviewInteractor(key, selectionInteractor, eventLog)
}

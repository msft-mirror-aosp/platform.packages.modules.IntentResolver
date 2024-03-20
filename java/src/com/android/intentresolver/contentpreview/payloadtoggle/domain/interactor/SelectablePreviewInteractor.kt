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
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.PreviewSelectionsRepository
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** An individual preview in Shareousel. */
class SelectablePreviewInteractor(
    private val key: PreviewModel,
    private val selectionRepo: PreviewSelectionsRepository,
) {
    val uri: Uri = key.uri

    /** Whether or not this preview is selected by the user. */
    val isSelected: Flow<Boolean> = selectionRepo.selections.map { key in it.selection }

    /** Sets whether this preview is selected by the user. */
    fun setSelected(isSelected: Boolean) {
        if (isSelected) {
            selectionRepo.select(key)
        } else {
            selectionRepo.unselect(key)
        }
    }
}

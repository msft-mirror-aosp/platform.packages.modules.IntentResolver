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

package com.android.intentresolver.contentpreview.payloadtoggle.data.repository

import android.util.Log
import com.android.intentresolver.contentpreview.payloadtoggle.data.model.SelectionRecord
import com.android.intentresolver.contentpreview.payloadtoggle.data.model.SelectionRecordType.Initial
import com.android.intentresolver.contentpreview.payloadtoggle.data.model.SelectionRecordType.Uninitialized
import com.android.intentresolver.contentpreview.payloadtoggle.data.model.SelectionRecordType.Updated
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewModel
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val TAG = "PreviewSelectionsRep"

/** Stores set of selected previews. */
@ViewModelScoped
class PreviewSelectionsRepository @Inject constructor() {
    private val _selections = MutableStateFlow(SelectionRecord(Uninitialized, emptySet()))

    /** Selected previews data */
    val selections: StateFlow<SelectionRecord> = _selections.asStateFlow()

    fun setSelection(selection: Set<PreviewModel>) {
        _selections.value = SelectionRecord(Initial, selection)
    }

    fun select(item: PreviewModel) {
        _selections.update { record ->
            if (record.type == Uninitialized) {
                Log.w(TAG, "Changing selection before it is initialized")
                record
            } else {
                SelectionRecord(Updated, record.selection + item)
            }
        }
    }

    fun unselect(item: PreviewModel) {
        _selections.update { record ->
            if (record.type == Uninitialized) {
                Log.w(TAG, "Changing selection before it is initialized")
                record
            } else {
                SelectionRecord(Updated, record.selection - item)
            }
        }
    }
}

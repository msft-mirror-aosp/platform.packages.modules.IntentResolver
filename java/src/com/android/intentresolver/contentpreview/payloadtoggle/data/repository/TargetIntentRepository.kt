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

import android.content.Intent
import com.android.intentresolver.contentpreview.payloadtoggle.data.model.CustomActionModel
import com.android.intentresolver.contentpreview.payloadtoggle.data.model.TargetIntentRecord
import com.android.intentresolver.inject.TargetIntent
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow

/** Stores the target intent of the share sheet, and custom actions derived from the intent. */
@ViewModelScoped
class TargetIntentRepository
@Inject
constructor(
    @TargetIntent initialIntent: Intent,
    initialActions: List<CustomActionModel>,
) {
    val targetIntent = MutableStateFlow(TargetIntentRecord(isInitial = true, initialIntent))

    // TODO: this can probably be derived from [targetIntent]; right now, the [initialActions] are
    //  coming from a different place (ChooserRequest) than later ones (SelectionChangeCallback)
    //  and so this serves as the source of truth between the two.
    val customActions = MutableStateFlow(initialActions)

    fun updateTargetIntent(intent: Intent) {
        targetIntent.value = TargetIntentRecord(isInitial = false, intent)
    }
}

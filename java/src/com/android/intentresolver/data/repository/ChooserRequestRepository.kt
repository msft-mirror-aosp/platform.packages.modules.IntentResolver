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

package com.android.intentresolver.data.repository

import com.android.intentresolver.contentpreview.payloadtoggle.data.model.CustomActionModel
import com.android.intentresolver.data.model.ChooserRequest
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow

@ViewModelScoped
class ChooserRequestRepository
@Inject
constructor(val initialRequest: ChooserRequest, initialActions: List<CustomActionModel>) {
    /** All information from the sharing application pertaining to the chooser. */
    val chooserRequest: MutableStateFlow<ChooserRequest> = MutableStateFlow(initialRequest)

    /** Custom actions from the sharing app to be presented in the chooser. */
    // NOTE: this could be derived directly from chooserRequest, but that would require working
    //  directly with PendingIntents, which complicates testing.
    val customActions: MutableStateFlow<List<CustomActionModel>> = MutableStateFlow(initialActions)
}

/*
 * Copyright 2024 The Android Open Source Project
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

import com.android.intentresolver.contentpreview.payloadtoggle.domain.model.ShareouselUpdate
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow

/** Chooser parameters Updates received from the sharing application payload change callback */
// TODO: a scaffolding repository to deliver chooser parameter updates before we developed some
//  other, more thought-through solution.
@ViewModelScoped
class ChooserParamsUpdateRepository @Inject constructor() {
    val updates = MutableStateFlow<ShareouselUpdate?>(null)

    fun setUpdates(update: ShareouselUpdate) {
        updates.tryEmit(update)
    }
}

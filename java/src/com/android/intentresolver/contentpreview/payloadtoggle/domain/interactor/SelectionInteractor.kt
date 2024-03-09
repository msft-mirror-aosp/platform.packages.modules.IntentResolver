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

import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.PreviewSelectionsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SelectionInteractor
@Inject
constructor(
    private val selectionRepo: PreviewSelectionsRepository,
) {
    /** Amount of selected previews. */
    val amountSelected: Flow<Int>
        get() = selectionRepo.selections.map { it.size }
}

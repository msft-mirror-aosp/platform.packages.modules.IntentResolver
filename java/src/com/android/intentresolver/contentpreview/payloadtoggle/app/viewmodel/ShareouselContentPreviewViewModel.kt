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

package com.android.intentresolver.contentpreview.payloadtoggle.app.viewmodel

import androidx.lifecycle.ViewModel
import com.android.intentresolver.contentpreview.payloadtoggle.domain.interactor.FetchPreviewsInteractor
import com.android.intentresolver.contentpreview.payloadtoggle.domain.interactor.UpdateTargetIntentInteractor
import com.android.intentresolver.contentpreview.payloadtoggle.ui.viewmodel.ShareouselViewModel
import com.android.intentresolver.inject.Background
import com.android.intentresolver.inject.ViewModelOwned
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** View-model for [com.android.intentresolver.contentpreview.ShareouselContentPreviewUi]. */
@HiltViewModel
class ShareouselContentPreviewViewModel
@Inject
constructor(
    val viewModel: ShareouselViewModel,
    updateTargetIntentInteractor: UpdateTargetIntentInteractor,
    fetchPreviewsInteractor: FetchPreviewsInteractor,
    @Background private val bgDispatcher: CoroutineDispatcher,
    @ViewModelOwned private val scope: CoroutineScope,
) : ViewModel() {
    init {
        scope.launch(bgDispatcher) { updateTargetIntentInteractor.launch() }
        scope.launch(bgDispatcher) { fetchPreviewsInteractor.launch() }
    }
}

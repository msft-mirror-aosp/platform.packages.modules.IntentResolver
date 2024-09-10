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
package com.android.intentresolver.ui.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.intentresolver.contentpreview.payloadtoggle.domain.interactor.FetchPreviewsInteractor
import com.android.intentresolver.contentpreview.payloadtoggle.domain.interactor.ProcessTargetIntentUpdatesInteractor
import com.android.intentresolver.contentpreview.payloadtoggle.ui.viewmodel.ShareouselViewModel
import com.android.intentresolver.data.model.ChooserRequest
import com.android.intentresolver.data.repository.ChooserRequestRepository
import com.android.intentresolver.inject.Background
import com.android.intentresolver.inject.ChooserServiceFlags
import com.android.intentresolver.ui.model.ActivityModel
import com.android.intentresolver.ui.model.ActivityModel.Companion.ACTIVITY_MODEL_KEY
import com.android.intentresolver.validation.Invalid
import com.android.intentresolver.validation.Valid
import com.android.intentresolver.validation.ValidationResult
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "ChooserViewModel"

@HiltViewModel
class ChooserViewModel
@Inject
constructor(
    args: SavedStateHandle,
    private val shareouselViewModelProvider: Lazy<ShareouselViewModel>,
    private val processUpdatesInteractor: Lazy<ProcessTargetIntentUpdatesInteractor>,
    private val fetchPreviewsInteractor: Lazy<FetchPreviewsInteractor>,
    @Background private val bgDispatcher: CoroutineDispatcher,
    private val flags: ChooserServiceFlags,
    /**
     * Provided only for the express purpose of early exit in the event of an invalid request.
     *
     * Note: [request] can only be safely accessed after checking if this value is [Valid].
     */
    val initialRequest: ValidationResult<ChooserRequest>,
    private val chooserRequestRepository: Lazy<ChooserRequestRepository>,
) : ViewModel() {

    /** Parcelable-only references provided from the creating Activity */
    val activityModel: ActivityModel =
        requireNotNull(args[ACTIVITY_MODEL_KEY]) {
            "ActivityModel missing in SavedStateHandle! ($ACTIVITY_MODEL_KEY)"
        }

    val shareouselViewModel: ShareouselViewModel by lazy {
        // TODO: consolidate this logic, this would require a consolidated preview view model but
        //  for now just postpone starting the payload selection preview machinery until it's needed
        assert(flags.chooserPayloadToggling()) {
            "An attempt to use payload selection preview with the disabled flag"
        }

        viewModelScope.launch(bgDispatcher) { processUpdatesInteractor.get().activate() }
        viewModelScope.launch(bgDispatcher) { fetchPreviewsInteractor.get().activate() }
        shareouselViewModelProvider.get()
    }

    /**
     * A [StateFlow] of [ChooserRequest].
     *
     * Note: Only safe to access after checking if [initialRequest] is [Valid].
     */
    val request: StateFlow<ChooserRequest>
        get() = chooserRequestRepository.get().chooserRequest.asStateFlow()

    init {
        if (initialRequest is Invalid) {
            Log.w(TAG, "initialRequest is Invalid, initialization failed")
        }
    }
}

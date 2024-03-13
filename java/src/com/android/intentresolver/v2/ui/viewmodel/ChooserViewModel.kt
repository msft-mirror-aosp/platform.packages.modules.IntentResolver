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
package com.android.intentresolver.v2.ui.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.intentresolver.contentpreview.payloadtoggle.domain.interactor.FetchPreviewsInteractor
import com.android.intentresolver.contentpreview.payloadtoggle.domain.interactor.UpdateTargetIntentInteractor
import com.android.intentresolver.contentpreview.payloadtoggle.ui.viewmodel.ShareouselViewModel
import com.android.intentresolver.inject.Background
import com.android.intentresolver.inject.ChooserServiceFlags
import com.android.intentresolver.v2.domain.interactor.ChooserRequestUpdateInteractorFactory
import com.android.intentresolver.v2.ui.model.ActivityModel
import com.android.intentresolver.v2.ui.model.ActivityModel.Companion.ACTIVITY_MODEL_KEY
import com.android.intentresolver.v2.ui.model.ChooserRequest
import com.android.intentresolver.v2.validation.Invalid
import com.android.intentresolver.v2.validation.Valid
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val updateTargetIntentInteractor: Lazy<UpdateTargetIntentInteractor>,
    private val fetchPreviewsInteractor: Lazy<FetchPreviewsInteractor>,
    @Background private val bgDispatcher: CoroutineDispatcher,
    private val chooserRequestUpdateInteractorFactory: ChooserRequestUpdateInteractorFactory,
    private val flags: ChooserServiceFlags,
) : ViewModel() {

    /** Parcelable-only references provided from the creating Activity */
    val activityModel: ActivityModel =
        requireNotNull(args[ACTIVITY_MODEL_KEY]) {
            "ActivityModel missing in SavedStateHandle! ($ACTIVITY_MODEL_KEY)"
        }

    val shareouselViewModel by lazy {
        // TODO: consolidate this logic, this would require a consolidated preview view model but
        //  for now just postpone starting the payload selection preview machinery until it's needed
        assert(flags.chooserPayloadToggling()) {
            "An attempt to use payload selection preview with the disabled flag"
        }

        viewModelScope.launch(bgDispatcher) { updateTargetIntentInteractor.get().launch() }
        viewModelScope.launch(bgDispatcher) { fetchPreviewsInteractor.get().launch() }
        viewModelScope.launch { chooserRequestUpdateInteractorFactory.create(_request).launch() }
        shareouselViewModelProvider.get()
    }

    /**
     * Provided only for the express purpose of early exit in the event of an invalid request.
     *
     * Note: [request] can only be safely accessed after checking if this value is [Valid].
     */
    internal val initialRequest = readChooserRequest(activityModel, flags)

    private lateinit var _request: MutableStateFlow<ChooserRequest>

    /**
     * A [StateFlow] of [ChooserRequest].
     *
     * Note: Only safe to access after checking if [initialRequest] is [Valid].
     */
    lateinit var request: StateFlow<ChooserRequest>
        private set

    init {
        when (initialRequest) {
            is Valid -> {
                _request = MutableStateFlow(initialRequest.value)
                request = _request.asStateFlow()
            }
            is Invalid -> Log.w(TAG, "initialRequest is Invalid, initialization failed")
        }
    }
}

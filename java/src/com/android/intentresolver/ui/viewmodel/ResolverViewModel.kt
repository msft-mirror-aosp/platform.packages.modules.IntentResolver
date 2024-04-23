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
import com.android.intentresolver.ui.model.ActivityModel
import com.android.intentresolver.ui.model.ActivityModel.Companion.ACTIVITY_MODEL_KEY
import com.android.intentresolver.ui.model.ResolverRequest
import com.android.intentresolver.validation.Invalid
import com.android.intentresolver.validation.Valid
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "ResolverViewModel"

@HiltViewModel
class ResolverViewModel @Inject constructor(args: SavedStateHandle) : ViewModel() {

    /** Parcelable-only references provided from the creating Activity */
    val activityModel: ActivityModel =
        requireNotNull(args[ACTIVITY_MODEL_KEY]) {
            "ActivityModel missing in SavedStateHandle! ($ACTIVITY_MODEL_KEY)"
        }

    /**
     * Provided only for the express purpose of early exit in the event of an invalid request.
     *
     * Note: [request] can only be safely accessed after checking if this value is [Valid].
     */
    internal val initialRequest = readResolverRequest(activityModel)

    private lateinit var _request: MutableStateFlow<ResolverRequest>

    /**
     * A [StateFlow] of [ResolverRequest].
     *
     * Note: Only safe to access after checking if [initialRequest] is [Valid].
     */
    lateinit var request: StateFlow<ResolverRequest>
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

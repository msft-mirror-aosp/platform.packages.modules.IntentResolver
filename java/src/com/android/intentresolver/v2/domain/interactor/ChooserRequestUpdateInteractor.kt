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

package com.android.intentresolver.v2.domain.interactor

import android.content.Intent
import android.util.Log
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.TargetIntentRepository
import com.android.intentresolver.inject.ChooserServiceFlags
import com.android.intentresolver.inject.TargetIntent
import com.android.intentresolver.v2.ui.model.ActivityModel
import com.android.intentresolver.v2.ui.model.ChooserRequest
import com.android.intentresolver.v2.ui.viewmodel.readChooserRequest
import com.android.intentresolver.v2.validation.Invalid
import com.android.intentresolver.v2.validation.Valid
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter

private const val TAG = "ChooserRequestUpdate"

/** Updates updates ChooserRequest with a new target intent */
// TODO: make fully injectable
class ChooserRequestUpdateInteractor
@AssistedInject
constructor(
    private val activityModel: ActivityModel,
    @TargetIntent private val initialIntent: Intent,
    private val targetIntentRepository: TargetIntentRepository,
    // TODO: replace with a proper repository, when available
    @Assisted private val chooserRequestRepository: MutableStateFlow<ChooserRequest>,
    private val flags: ChooserServiceFlags,
) {

    suspend fun launch() {
        targetIntentRepository.targetIntent
            // TODO: maybe find a better way to exclude the initial intent (as here it's compared by
            //  reference)
            .filter { it !== initialIntent }
            .collect(::updateTargetIntent)
    }

    private fun updateTargetIntent(targetIntent: Intent) {
        val updatedActivityModel = activityModel.updateWithTargetIntent(targetIntent)
        when (val updatedChooserRequest = readChooserRequest(updatedActivityModel, flags)) {
            is Valid -> chooserRequestRepository.value = updatedChooserRequest.value
            is Invalid -> Log.w(TAG, "Failed to apply payload selection changes")
        }
    }

    private fun ActivityModel.updateWithTargetIntent(targetIntent: Intent) =
        ActivityModel(
            Intent(intent).apply { putExtra(Intent.EXTRA_INTENT, targetIntent) },
            launchedFromUid,
            launchedFromPackage,
            referrer,
        )
}

@AssistedFactory
@ViewModelScoped
interface ChooserRequestUpdateInteractorFactory {
    fun create(
        chooserRequestRepository: MutableStateFlow<ChooserRequest>
    ): ChooserRequestUpdateInteractor
}

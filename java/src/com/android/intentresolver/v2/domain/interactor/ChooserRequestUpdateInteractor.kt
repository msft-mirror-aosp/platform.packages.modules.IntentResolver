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
import android.content.IntentSender
import android.service.chooser.ChooserAction
import android.service.chooser.ChooserTarget
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.ChooserParamsUpdateRepository
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.TargetIntentRepository
import com.android.intentresolver.contentpreview.payloadtoggle.domain.model.ShareouselUpdate
import com.android.intentresolver.v2.ui.model.ChooserRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Updates updates ChooserRequest with a new target intent */
// TODO: make fully injectable
class ChooserRequestUpdateInteractor
@AssistedInject
constructor(
    private val targetIntentRepository: TargetIntentRepository,
    private val paramsUpdateRepository: ChooserParamsUpdateRepository,
    // TODO: replace with a proper repository, when available
    @Assisted private val chooserRequestRepository: MutableStateFlow<ChooserRequest>,
) {

    suspend fun launch() {
        coroutineScope {
            launch {
                targetIntentRepository.targetIntent
                    .filter { !it.isInitial }
                    .map { it.intent }
                    .collect(::updateTargetIntent)
            }

            launch {
                paramsUpdateRepository.updates.filterNotNull().collect(::updateChooserParameters)
            }
        }
    }

    private fun updateTargetIntent(targetIntent: Intent) {
        chooserRequestRepository.update { current ->
            current.updatedWith(targetIntent = targetIntent)
        }
    }

    private fun updateChooserParameters(update: ShareouselUpdate) {
        chooserRequestRepository.update { current ->
            current.updatedWith(
                callerChooserTargets = update.callerTargets,
                modifyShareAction = update.modifyShareAction,
                additionalTargets = update.alternateIntents,
                chosenComponentSender = update.resultIntentSender,
                refinementIntentSender = update.refinementIntentSender,
                metadataText = update.metadataText,
            )
        }
    }
}

private fun ChooserRequest.updatedWith(
    targetIntent: Intent? = null,
    callerChooserTargets: List<ChooserTarget>? = null,
    modifyShareAction: ChooserAction? = null,
    additionalTargets: List<Intent>? = null,
    chosenComponentSender: IntentSender? = null,
    refinementIntentSender: IntentSender? = null,
    metadataText: CharSequence? = null,
) =
    ChooserRequest(
        targetIntent ?: this.targetIntent,
        this.targetAction,
        this.isSendActionTarget,
        this.targetType,
        this.launchedFromPackage,
        this.title,
        this.defaultTitleResource,
        this.referrer,
        this.filteredComponentNames,
        callerChooserTargets ?: this.callerChooserTargets,
        this.chooserActions,
        modifyShareAction ?: this.modifyShareAction,
        this.shouldRetainInOnStop,
        additionalTargets ?: this.additionalTargets,
        this.replacementExtras,
        this.initialIntents,
        chosenComponentSender ?: this.chosenComponentSender,
        refinementIntentSender ?: this.refinementIntentSender,
        this.sharedText,
        this.shareTargetFilter,
        this.additionalContentUri,
        this.focusedItemPosition,
        this.contentTypeHint,
        metadataText ?: this.metadataText,
    )

@AssistedFactory
@ViewModelScoped
interface ChooserRequestUpdateInteractorFactory {
    fun create(
        chooserRequestRepository: MutableStateFlow<ChooserRequest>
    ): ChooserRequestUpdateInteractor
}

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

import android.content.Intent
import com.android.intentresolver.contentpreview.payloadtoggle.domain.intent.CustomAction
import com.android.intentresolver.contentpreview.payloadtoggle.domain.intent.PendingIntentSender
import com.android.intentresolver.contentpreview.payloadtoggle.domain.intent.toCustomActionModel
import com.android.intentresolver.contentpreview.payloadtoggle.domain.model.ShareouselUpdate
import com.android.intentresolver.contentpreview.payloadtoggle.domain.model.getOrDefault
import com.android.intentresolver.contentpreview.payloadtoggle.domain.model.onValue
import com.android.intentresolver.data.repository.ChooserRequestRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.update

/** Updates the tracked chooser request. */
class UpdateChooserRequestInteractor
@Inject
constructor(
    private val repository: ChooserRequestRepository,
    @CustomAction private val pendingIntentSender: PendingIntentSender,
) {
    fun applyUpdate(targetIntent: Intent, update: ShareouselUpdate) {
        repository.chooserRequest.update { current ->
            current.copy(
                targetIntent = targetIntent,
                callerChooserTargets =
                    update.callerTargets.getOrDefault(current.callerChooserTargets),
                modifyShareAction =
                    update.modifyShareAction.getOrDefault(current.modifyShareAction),
                additionalTargets = update.alternateIntents.getOrDefault(current.additionalTargets),
                chosenComponentSender =
                    update.resultIntentSender.getOrDefault(current.chosenComponentSender),
                refinementIntentSender =
                    update.refinementIntentSender.getOrDefault(current.refinementIntentSender),
                metadataText = update.metadataText.getOrDefault(current.metadataText),
                chooserActions = update.customActions.getOrDefault(current.chooserActions),
            )
        }
        update.customActions.onValue { actions ->
            repository.customActions.value =
                actions.map { it.toCustomActionModel(pendingIntentSender) }
        }
    }

    fun setTargetIntent(targetIntent: Intent) {
        repository.chooserRequest.update { it.copy(targetIntent = targetIntent) }
    }
}

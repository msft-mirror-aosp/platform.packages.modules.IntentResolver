/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.intentresolver.domain

import android.content.Intent
import android.content.Intent.EXTRA_ALTERNATE_INTENTS
import android.content.Intent.EXTRA_CHOOSER_CUSTOM_ACTIONS
import android.content.Intent.EXTRA_CHOOSER_MODIFY_SHARE_ACTION
import android.content.Intent.EXTRA_CHOOSER_REFINEMENT_INTENT_SENDER
import android.content.Intent.EXTRA_CHOOSER_RESULT_INTENT_SENDER
import android.content.Intent.EXTRA_CHOOSER_TARGETS
import android.content.Intent.EXTRA_CHOSEN_COMPONENT_INTENT_SENDER
import android.content.Intent.EXTRA_EXCLUDE_COMPONENTS
import android.content.Intent.EXTRA_INTENT
import android.content.Intent.EXTRA_METADATA_TEXT
import android.os.Bundle
import com.android.intentresolver.Flags.shareouselUpdateExcludeComponentsExtra
import com.android.intentresolver.contentpreview.payloadtoggle.domain.model.ShareouselUpdate
import com.android.intentresolver.contentpreview.payloadtoggle.domain.model.getOrDefault
import com.android.intentresolver.data.model.ChooserRequest

/** Creates a new ChooserRequest with the target intent and updates from a Shareousel callback */
fun ChooserRequest.updateWith(targetIntent: Intent, update: ShareouselUpdate): ChooserRequest =
    copy(
        targetIntent = targetIntent,
        callerChooserTargets = update.callerTargets.getOrDefault(callerChooserTargets),
        modifyShareAction = update.modifyShareAction.getOrDefault(modifyShareAction),
        additionalTargets = update.alternateIntents.getOrDefault(additionalTargets),
        chosenComponentSender = update.resultIntentSender.getOrDefault(chosenComponentSender),
        refinementIntentSender = update.refinementIntentSender.getOrDefault(refinementIntentSender),
        metadataText = update.metadataText.getOrDefault(metadataText),
        chooserActions = update.customActions.getOrDefault(chooserActions),
        filteredComponentNames =
            if (shareouselUpdateExcludeComponentsExtra()) {
                update.excludeComponents.getOrDefault(filteredComponentNames)
            } else {
                filteredComponentNames
            },
    )

/** Save ChooserRequest values that can be updated by the Shareousel into a Bundle */
fun ChooserRequest.saveUpdates(bundle: Bundle): Bundle {
    bundle.putParcelable(EXTRA_INTENT, targetIntent)
    bundle.putParcelableArray(EXTRA_CHOOSER_TARGETS, callerChooserTargets.toTypedArray())
    bundle.putParcelable(EXTRA_CHOOSER_MODIFY_SHARE_ACTION, modifyShareAction)
    bundle.putParcelableArray(EXTRA_ALTERNATE_INTENTS, additionalTargets.toTypedArray())
    bundle.putParcelable(EXTRA_CHOOSER_RESULT_INTENT_SENDER, chosenComponentSender)
    bundle.putParcelable(EXTRA_CHOSEN_COMPONENT_INTENT_SENDER, chosenComponentSender)
    bundle.putParcelable(EXTRA_CHOOSER_REFINEMENT_INTENT_SENDER, refinementIntentSender)
    bundle.putCharSequence(EXTRA_METADATA_TEXT, metadataText)
    bundle.putParcelableArray(EXTRA_CHOOSER_CUSTOM_ACTIONS, chooserActions.toTypedArray())
    if (shareouselUpdateExcludeComponentsExtra()) {
        bundle.putParcelableArray(EXTRA_EXCLUDE_COMPONENTS, filteredComponentNames.toTypedArray())
    }
    return bundle
}

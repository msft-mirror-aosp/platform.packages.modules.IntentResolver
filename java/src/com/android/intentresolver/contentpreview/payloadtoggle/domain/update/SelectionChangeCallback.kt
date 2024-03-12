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

package com.android.intentresolver.contentpreview.payloadtoggle.domain.update

import android.content.ContentInterface
import android.content.Intent
import android.content.Intent.EXTRA_CHOOSER_MODIFY_SHARE_ACTION
import android.content.Intent.EXTRA_CHOOSER_REFINEMENT_INTENT_SENDER
import android.content.Intent.EXTRA_CHOOSER_TARGETS
import android.content.Intent.EXTRA_INTENT
import android.content.IntentSender
import android.net.Uri
import android.os.Bundle
import android.service.chooser.AdditionalContentContract.MethodNames.ON_SELECTION_CHANGED
import android.service.chooser.ChooserAction
import android.service.chooser.ChooserTarget
import com.android.intentresolver.contentpreview.payloadtoggle.domain.update.SelectionChangeCallback.ShareouselUpdate
import com.android.intentresolver.inject.AdditionalContent
import com.android.intentresolver.inject.ChooserIntent
import com.android.intentresolver.v2.ui.viewmodel.readAlternateIntents
import com.android.intentresolver.v2.ui.viewmodel.readChooserActions
import com.android.intentresolver.v2.validation.Invalid
import com.android.intentresolver.v2.validation.Valid
import com.android.intentresolver.v2.validation.ValidationResult
import com.android.intentresolver.v2.validation.log
import com.android.intentresolver.v2.validation.types.array
import com.android.intentresolver.v2.validation.types.value
import com.android.intentresolver.v2.validation.validateFrom
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import javax.inject.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "SelectionChangeCallback"

/**
 * Encapsulates payload change callback invocation to the sharing app; handles callback arguments
 * and result format mapping.
 */
fun interface SelectionChangeCallback {
    suspend fun onSelectionChanged(targetIntent: Intent): ShareouselUpdate?

    data class ShareouselUpdate(
        // for all properties, null value means no change
        val customActions: List<ChooserAction>? = null,
        val modifyShareAction: ChooserAction? = null,
        val alternateIntents: List<Intent>? = null,
        val callerTargets: List<ChooserTarget>? = null,
        val refinementIntentSender: IntentSender? = null,
    )
}

class SelectionChangeCallbackImpl
@Inject
constructor(
    @AdditionalContent private val uri: Uri,
    @ChooserIntent private val chooserIntent: Intent,
    private val contentResolver: ContentInterface,
) : SelectionChangeCallback {
    private val mutex = Mutex()

    override suspend fun onSelectionChanged(targetIntent: Intent): ShareouselUpdate? =
        mutex
            .withLock {
                contentResolver.call(
                    requireNotNull(uri.authority) { "URI authority can not be null" },
                    ON_SELECTION_CHANGED,
                    uri.toString(),
                    Bundle().apply {
                        putParcelable(
                            EXTRA_INTENT,
                            Intent(chooserIntent).apply { putExtra(EXTRA_INTENT, targetIntent) }
                        )
                    }
                )
            }
            ?.let { bundle ->
                return when (val result = readCallbackResponse(bundle)) {
                    is Valid -> result.value
                    is Invalid -> {
                        result.errors.forEach { it.log(TAG) }
                        null
                    }
                }
            }
}

private fun readCallbackResponse(bundle: Bundle): ValidationResult<ShareouselUpdate> {
    return validateFrom(bundle::get) {
        val customActions = readChooserActions()
        val modifyShareAction = optional(value<ChooserAction>(EXTRA_CHOOSER_MODIFY_SHARE_ACTION))
        val alternateIntents = readAlternateIntents()
        val callerTargets = optional(array<ChooserTarget>(EXTRA_CHOOSER_TARGETS))
        val refinementIntentSender =
            optional(value<IntentSender>(EXTRA_CHOOSER_REFINEMENT_INTENT_SENDER))

        ShareouselUpdate(
            customActions,
            modifyShareAction,
            alternateIntents,
            callerTargets,
            refinementIntentSender,
        )
    }
}

@Module
@InstallIn(ViewModelComponent::class)
interface SelectionChangeCallbackModule {
    @Binds fun bind(impl: SelectionChangeCallbackImpl): SelectionChangeCallback
}

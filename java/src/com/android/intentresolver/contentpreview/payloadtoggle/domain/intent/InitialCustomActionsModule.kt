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

package com.android.intentresolver.contentpreview.payloadtoggle.domain.intent

import android.app.PendingIntent
import android.service.chooser.ChooserAction
import android.util.Log
import com.android.intentresolver.contentpreview.payloadtoggle.data.model.CustomActionModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
object InitialCustomActionsModule {
    @Provides
    fun initialCustomActionModels(
        chooserActions: List<ChooserAction>,
        @CustomAction pendingIntentSender: PendingIntentSender,
    ): List<CustomActionModel> = chooserActions.map { it.toCustomActionModel(pendingIntentSender) }
}

/**
 * Returns a [CustomActionModel] that sends this [ChooserAction]'s
 * [PendingIntent][ChooserAction.getAction].
 */
fun ChooserAction.toCustomActionModel(pendingIntentSender: PendingIntentSender) =
    CustomActionModel(
        label = label,
        icon = icon,
        performAction = {
            try {
                pendingIntentSender.send(action)
            } catch (_: PendingIntent.CanceledException) {
                Log.d(TAG, "Custom action, $label, has been cancelled")
            }
        }
    )

private const val TAG = "CustomShareActions"

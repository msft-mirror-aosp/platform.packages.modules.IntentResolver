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

package com.android.intentresolver.contentpreview.payloadtoggle.domain.model

import android.content.ComponentName
import android.content.Intent
import android.content.IntentSender
import android.service.chooser.ChooserAction
import android.service.chooser.ChooserTarget

/** Sharing session updates provided by the sharing app from the payload change callback */
data class ShareouselUpdate(
    // for all properties, null value means no change
    val customActions: ValueUpdate<List<ChooserAction>> = ValueUpdate.Absent,
    val modifyShareAction: ValueUpdate<ChooserAction?> = ValueUpdate.Absent,
    val alternateIntents: ValueUpdate<List<Intent>> = ValueUpdate.Absent,
    val callerTargets: ValueUpdate<List<ChooserTarget>> = ValueUpdate.Absent,
    val refinementIntentSender: ValueUpdate<IntentSender?> = ValueUpdate.Absent,
    val resultIntentSender: ValueUpdate<IntentSender?> = ValueUpdate.Absent,
    val metadataText: ValueUpdate<CharSequence?> = ValueUpdate.Absent,
    val excludeComponents: ValueUpdate<List<ComponentName>> = ValueUpdate.Absent,
)

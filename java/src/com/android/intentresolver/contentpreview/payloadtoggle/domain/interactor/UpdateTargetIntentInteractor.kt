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
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.PendingSelectionCallbackRepository
import javax.inject.Inject

class UpdateTargetIntentInteractor
@Inject
constructor(
    private val repository: PendingSelectionCallbackRepository,
    private val chooserRequestInteractor: UpdateChooserRequestInteractor,
) {
    /**
     * Updates the target intent for the chooser. This will kick off an asynchronous IPC with the
     * sharing application, so that it can react to the new intent.
     */
    fun updateTargetIntent(targetIntent: Intent) {
        repository.pendingTargetIntent.value = targetIntent
        chooserRequestInteractor.setTargetIntent(targetIntent)
    }
}

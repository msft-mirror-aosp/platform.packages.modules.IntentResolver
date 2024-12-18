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

package com.android.intentresolver.interactive.domain.interactor

import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.PendingSelectionCallbackRepository
import com.android.intentresolver.data.model.ChooserRequest
import com.android.intentresolver.data.repository.ActivityModelRepository
import com.android.intentresolver.data.repository.ChooserRequestRepository
import com.android.intentresolver.interactive.data.repository.InteractiveSessionCallbackRepository
import com.android.intentresolver.interactive.domain.model.ChooserIntentUpdater
import com.android.intentresolver.ui.viewmodel.readChooserRequest
import com.android.intentresolver.validation.Invalid
import com.android.intentresolver.validation.Valid
import com.android.intentresolver.validation.log
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "ChooserSession"

@ViewModelScoped
class InteractiveSessionInteractor
@Inject
constructor(
    activityModelRepo: ActivityModelRepository,
    private val chooserRequestRepository: ChooserRequestRepository,
    private val pendingSelectionCallbackRepo: PendingSelectionCallbackRepository,
    private val interactiveCallbackRepo: InteractiveSessionCallbackRepository,
) {
    private val activityModel = activityModelRepo.value
    private val sessionCallback =
        chooserRequestRepository.initialRequest.interactiveSessionCallback?.let {
            SafeChooserInteractiveSessionCallback(it)
        }
    val isSessionActive = MutableStateFlow(true)

    suspend fun activate() = coroutineScope {
        if (sessionCallback == null || activityModel.isTaskRoot) {
            sessionCallback?.registerChooserController(null)
            return@coroutineScope
        }
        launch {
            val callbackBinder: IBinder = sessionCallback.asBinder()
            if (callbackBinder.isBinderAlive) {
                val deathRecipient = IBinder.DeathRecipient { isSessionActive.value = false }
                callbackBinder.linkToDeath(deathRecipient, 0)
                try {
                    awaitCancellation()
                } finally {
                    runCatching { sessionCallback.asBinder().unlinkToDeath(deathRecipient, 0) }
                }
            } else {
                isSessionActive.value = false
            }
        }
        val chooserIntentUpdater =
            interactiveCallbackRepo.intentUpdater
                ?: ChooserIntentUpdater().also {
                    interactiveCallbackRepo.setChooserIntentUpdater(it)
                    sessionCallback.registerChooserController(it)
                }
        chooserIntentUpdater.chooserIntent.collect { onIntentUpdated(it) }
    }

    fun sendTopDrawerTopOffsetChange(offset: Int) {
        sessionCallback?.onDrawerVerticalOffsetChanged(offset)
    }

    fun endSession() {
        sessionCallback?.registerChooserController(null)
    }

    private fun onIntentUpdated(chooserIntent: Intent?) {
        if (chooserIntent == null) {
            isSessionActive.value = false
            return
        }

        val result =
            readChooserRequest(
                chooserIntent.extras ?: Bundle(),
                activityModel.launchedFromPackage,
                activityModel.referrer,
            )
        when (result) {
            is Valid<ChooserRequest> -> {
                val newRequest = result.value
                pendingSelectionCallbackRepo.pendingTargetIntent.compareAndSet(
                    null,
                    result.value.targetIntent,
                )
                chooserRequestRepository.chooserRequest.update {
                    it.copy(
                        targetIntent = newRequest.targetIntent,
                        targetAction = newRequest.targetAction,
                        isSendActionTarget = newRequest.isSendActionTarget,
                        targetType = newRequest.targetType,
                        filteredComponentNames = newRequest.filteredComponentNames,
                        callerChooserTargets = newRequest.callerChooserTargets,
                        additionalTargets = newRequest.additionalTargets,
                        replacementExtras = newRequest.replacementExtras,
                        initialIntents = newRequest.initialIntents,
                        shareTargetFilter = newRequest.shareTargetFilter,
                        chosenComponentSender = newRequest.chosenComponentSender,
                        refinementIntentSender = newRequest.refinementIntentSender,
                    )
                }
                pendingSelectionCallbackRepo.pendingTargetIntent.compareAndSet(
                    result.value.targetIntent,
                    null,
                )
            }
            is Invalid -> {
                result.errors.forEach { it.log(TAG) }
            }
        }
    }
}

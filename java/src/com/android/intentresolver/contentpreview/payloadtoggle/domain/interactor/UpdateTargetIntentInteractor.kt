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

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.android.intentresolver.contentpreview.payloadtoggle.domain.interactor

import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.PreviewSelectionsRepository
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.TargetIntentRepository
import com.android.intentresolver.contentpreview.payloadtoggle.domain.intent.CustomAction
import com.android.intentresolver.contentpreview.payloadtoggle.domain.intent.PendingIntentSender
import com.android.intentresolver.contentpreview.payloadtoggle.domain.intent.TargetIntentModifier
import com.android.intentresolver.contentpreview.payloadtoggle.domain.intent.toCustomActionModel
import com.android.intentresolver.contentpreview.payloadtoggle.domain.update.SelectionChangeCallback
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch

/** Updates [TargetIntentRepository] in reaction to user selection changes. */
class UpdateTargetIntentInteractor
@Inject
constructor(
    private val intentRepository: TargetIntentRepository,
    @CustomAction private val pendingIntentSender: PendingIntentSender,
    private val selectionCallback: SelectionChangeCallback,
    private val selectionRepo: PreviewSelectionsRepository,
    private val targetIntentModifier: TargetIntentModifier<PreviewModel>,
) {
    /** Listen for events and update state. */
    suspend fun launch(): Unit = coroutineScope {
        launch {
            intentRepository.targetIntent
                .mapLatest { targetIntent ->
                    selectionCallback.onSelectionChanged(targetIntent)?.customActions ?: emptyList()
                }
                .collect { actions ->
                    intentRepository.customActions.value =
                        actions.map { it.toCustomActionModel(pendingIntentSender) }
                }
        }
        launch {
            selectionRepo.selections.collectLatest {
                intentRepository.targetIntent.value = targetIntentModifier.onSelectionChanged(it)
            }
        }
    }
}

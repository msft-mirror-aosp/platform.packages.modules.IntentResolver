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

import android.app.Activity
import android.content.ContentResolver
import android.content.pm.PackageManager
import com.android.intentresolver.contentpreview.payloadtoggle.data.model.CustomActionModel
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.ActivityResultRepository
import com.android.intentresolver.contentpreview.payloadtoggle.domain.model.ActionModel
import com.android.intentresolver.icon.toComposeIcon
import com.android.intentresolver.inject.Background
import com.android.intentresolver.logging.EventLog
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class CustomActionsInteractor
@Inject
constructor(
    private val activityResultRepo: ActivityResultRepository,
    @Background private val bgDispatcher: CoroutineDispatcher,
    private val contentResolver: ContentResolver,
    private val eventLog: EventLog,
    private val packageManager: PackageManager,
    private val chooserRequestInteractor: ChooserRequestInteractor,
) {
    /** List of [ActionModel] that can be presented in Shareousel. */
    val customActions: Flow<List<ActionModel>>
        get() =
            chooserRequestInteractor.customActions
                .map { actions ->
                    actions.map { action ->
                        ActionModel(
                            label = action.label,
                            icon = action.icon.toComposeIcon(packageManager, contentResolver),
                            performAction = { index -> performAction(action, index) },
                        )
                    }
                }
                .flowOn(bgDispatcher)
                .conflate()

    private fun performAction(action: CustomActionModel, index: Int) {
        action.performAction()
        eventLog.logCustomActionSelected(index)
        activityResultRepo.activityResult.value = Activity.RESULT_OK
    }
}

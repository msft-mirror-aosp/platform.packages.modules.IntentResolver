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

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import com.android.intentresolver.contentpreview.payloadtoggle.data.model.CustomActionModel
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.activityResultRepository
import com.android.intentresolver.contentpreview.payloadtoggle.domain.model.ActionModel
import com.android.intentresolver.data.model.ChooserRequest
import com.android.intentresolver.data.repository.ChooserRequestRepository
import com.android.intentresolver.data.repository.chooserRequestRepository
import com.android.intentresolver.icon.BitmapIcon
import com.android.intentresolver.util.comparingElementsUsingTransform
import com.android.intentresolver.util.runKosmosTest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.junit.Test

class CustomActionsInteractorTest {

    @Test
    fun customActions_initialRepoValue() = runKosmosTest {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ALPHA_8)
        val icon = Icon.createWithBitmap(bitmap)
        chooserRequestRepository =
            ChooserRequestRepository(
                initialRequest =
                    ChooserRequest(targetIntent = Intent(), launchedFromPackage = "pkg"),
                initialActions =
                    listOf(
                        CustomActionModel(label = "label1", icon = icon, performAction = {}),
                    ),
            )
        val underTest = customActionsInteractor
        val customActions: StateFlow<List<ActionModel>> =
            underTest.customActions.stateIn(backgroundScope)
        assertThat(customActions.value)
            .comparingElementsUsingTransform("has a label of") { model: ActionModel -> model.label }
            .containsExactly("label1")
            .inOrder()
        assertThat(customActions.value)
            .comparingElementsUsingTransform("has an icon of") { model: ActionModel -> model.icon }
            .containsExactly(BitmapIcon(icon.bitmap))
            .inOrder()
    }

    @Test
    fun customActions_tracksRepoUpdates() = runKosmosTest {
        val underTest = customActionsInteractor

        val customActions: StateFlow<List<ActionModel>> =
            underTest.customActions.stateIn(backgroundScope)
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ALPHA_8)
        val icon = Icon.createWithBitmap(bitmap)
        val chooserActions = listOf(CustomActionModel("label1", icon) {})
        chooserRequestRepository.customActions.value = chooserActions
        runCurrent()

        assertThat(customActions.value)
            .comparingElementsUsingTransform("has a label of") { model: ActionModel -> model.label }
            .containsExactly("label1")
            .inOrder()
        assertThat(customActions.value)
            .comparingElementsUsingTransform("has an icon of") { model: ActionModel -> model.icon }
            .containsExactly(BitmapIcon(icon.bitmap))
            .inOrder()
    }

    @Test
    fun customActions_performAction_sendsPendingIntent() = runKosmosTest {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ALPHA_8)
        val icon = Icon.createWithBitmap(bitmap)
        var actionSent = false
        chooserRequestRepository =
            ChooserRequestRepository(
                initialRequest =
                    ChooserRequest(targetIntent = Intent(), launchedFromPackage = "pkg"),
                initialActions =
                    listOf(
                        CustomActionModel(
                            label = "label1",
                            icon = icon,
                            performAction = { actionSent = true },
                        )
                    ),
            )
        val underTest = customActionsInteractor

        val customActions: StateFlow<List<ActionModel>> =
            underTest.customActions.stateIn(backgroundScope)

        assertThat(customActions.value).hasSize(1)

        customActions.value[0].performAction(123)

        assertThat(actionSent).isTrue()
        assertThat(activityResultRepository.activityResult.value).isEqualTo(Activity.RESULT_OK)
    }
}

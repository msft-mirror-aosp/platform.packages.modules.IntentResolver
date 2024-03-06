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

package com.android.intentresolver.inject

import android.content.Intent
import android.service.chooser.ChooserAction
import androidx.lifecycle.SavedStateHandle
import com.android.intentresolver.v2.ui.model.ActivityModel
import com.android.intentresolver.v2.ui.model.ChooserRequest
import com.android.intentresolver.v2.ui.viewmodel.readChooserRequest
import com.android.intentresolver.v2.validation.Valid
import com.android.intentresolver.v2.validation.ValidationResult
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Qualifier

@Module
@InstallIn(ViewModelComponent::class)
object ActivityModelModule {
    @Provides
    fun provideActivityModel(savedStateHandle: SavedStateHandle): ActivityModel =
        requireNotNull(savedStateHandle[ActivityModel.ACTIVITY_MODEL_KEY]) {
            "ActivityModel missing in SavedStateHandle! (${ActivityModel.ACTIVITY_MODEL_KEY})"
        }

    @Provides
    @ViewModelScoped
    fun provideChooserRequest(
        activityModel: ActivityModel,
        flags: ChooserServiceFlags,
    ): ValidationResult<ChooserRequest> = readChooserRequest(activityModel, flags)

    @Provides
    @TargetIntent
    fun targetIntent(chooserReq: ValidationResult<ChooserRequest>): Intent =
        requireNotNull((chooserReq as? Valid)?.value?.targetIntent) { "no target intent available" }

    @Provides
    fun customActions(chooserReq: ValidationResult<ChooserRequest>): List<ChooserAction> =
        requireNotNull((chooserReq as? Valid)?.value?.chooserActions) {
            "no chooser actions available"
        }
}

@Qualifier @MustBeDocumented @Retention(AnnotationRetention.RUNTIME) annotation class TargetIntent

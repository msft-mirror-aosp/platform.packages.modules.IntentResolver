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
import android.net.Uri
import android.os.Bundle
import android.service.chooser.ChooserAction
import androidx.lifecycle.SavedStateHandle
import com.android.intentresolver.Flags.saveShareouselState
import com.android.intentresolver.data.model.ChooserRequest
import com.android.intentresolver.data.repository.ActivityModelRepository
import com.android.intentresolver.ui.viewmodel.CHOOSER_REQUEST_KEY
import com.android.intentresolver.ui.viewmodel.readChooserRequest
import com.android.intentresolver.util.ownedByCurrentUser
import com.android.intentresolver.validation.Valid
import com.android.intentresolver.validation.ValidationResult
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
    @ChooserIntent
    fun chooserIntent(activityModelRepo: ActivityModelRepository): Intent =
        activityModelRepo.value.intent

    @Provides
    @ViewModelScoped
    fun provideInitialRequest(
        activityModelRepo: ActivityModelRepository,
        savedStateHandle: SavedStateHandle,
        flags: ChooserServiceFlags,
    ): ValidationResult<ChooserRequest> {
        val activityModel = activityModelRepo.value
        val extras = restoreChooserRequestExtras(activityModel.intent.extras, savedStateHandle)
        return readChooserRequest(activityModel, flags, extras)
    }

    @Provides
    fun provideChooserRequest(initialRequest: ValidationResult<ChooserRequest>): ChooserRequest =
        requireNotNull((initialRequest as? Valid)?.value) {
            "initialRequest is Invalid, no chooser request available"
        }

    @Provides
    @TargetIntent
    fun targetIntent(chooserReq: ValidationResult<ChooserRequest>): Intent =
        requireNotNull((chooserReq as? Valid)?.value?.targetIntent) { "no target intent available" }

    @Provides
    fun customActions(chooserReq: ValidationResult<ChooserRequest>): List<ChooserAction> =
        requireNotNull((chooserReq as? Valid)?.value?.chooserActions) {
            "no chooser actions available"
        }

    @Provides
    @ViewModelScoped
    @ContentUris
    fun selectedUris(chooserRequest: ValidationResult<ChooserRequest>): List<Uri> =
        requireNotNull((chooserRequest as? Valid)?.value?.targetIntent?.contentUris?.toList()) {
            "no selected uris available"
        }

    @Provides
    @FocusedItemIndex
    fun focusedItemIndex(chooserReq: ValidationResult<ChooserRequest>): Int =
        requireNotNull((chooserReq as? Valid)?.value?.focusedItemPosition) {
            "no focused item position available"
        }

    @Provides
    @AdditionalContent
    fun additionalContentUri(chooserReq: ValidationResult<ChooserRequest>): Uri =
        requireNotNull((chooserReq as? Valid)?.value?.additionalContentUri) {
            "no additional content uri available"
        }
}

@Qualifier
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
annotation class FocusedItemIndex

@Qualifier
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
annotation class AdditionalContent

@Qualifier @MustBeDocumented @Retention(AnnotationRetention.RUNTIME) annotation class ChooserIntent

@Qualifier @MustBeDocumented @Retention(AnnotationRetention.RUNTIME) annotation class ContentUris

@Qualifier @MustBeDocumented @Retention(AnnotationRetention.RUNTIME) annotation class TargetIntent

private val Intent.contentUris: Sequence<Uri>
    get() = sequence {
        if (Intent.ACTION_SEND == action) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                ?.takeIf { it.ownedByCurrentUser }
                ?.let { yield(it) }
        } else {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)?.forEach { uri ->
                if (uri.ownedByCurrentUser) {
                    yield(uri)
                }
            }
        }
    }

private fun restoreChooserRequestExtras(
    initialExtras: Bundle?,
    savedStateHandle: SavedStateHandle,
): Bundle =
    if (saveShareouselState()) {
        savedStateHandle.get<Bundle>(CHOOSER_REQUEST_KEY)?.let { savedSateBundle ->
            Bundle().apply {
                initialExtras?.let { putAll(it) }
                putAll(savedSateBundle)
            }
        } ?: initialExtras
    } else {
        initialExtras
    } ?: Bundle()

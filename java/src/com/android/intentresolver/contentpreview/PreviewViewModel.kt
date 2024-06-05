/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.intentresolver.contentpreview

import android.app.Application
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.android.intentresolver.R
import com.android.intentresolver.inject.Background
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.plus

/** A view model for the preview logic */
class PreviewViewModel(
    private val contentResolver: ContentResolver,
    // TODO: inject ImageLoader instead
    private val thumbnailSize: Int,
    @Background private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BasePreviewViewModel() {
    private var targetIntent: Intent? = null
    private var additionalContentUri: Uri? = null
    private var isPayloadTogglingEnabled = false

    override val previewDataProvider by lazy {
        val targetIntent = requireNotNull(this.targetIntent) { "Not initialized" }
        PreviewDataProvider(
            viewModelScope + dispatcher,
            targetIntent,
            additionalContentUri,
            contentResolver,
            isPayloadTogglingEnabled,
        )
    }

    override val imageLoader by lazy {
        ImagePreviewImageLoader(
            viewModelScope + dispatcher,
            thumbnailSize,
            contentResolver,
            cacheSize = 16
        )
    }

    // TODO: make the view model injectable and inject these dependencies instead
    @MainThread
    override fun init(
        targetIntent: Intent,
        additionalContentUri: Uri?,
        isPayloadTogglingEnabled: Boolean,
    ) {
        if (this.targetIntent != null) return
        this.targetIntent = targetIntent
        this.additionalContentUri = additionalContentUri
        this.isPayloadTogglingEnabled = isPayloadTogglingEnabled
    }

    companion object {
        val Factory: ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras
                ): T {
                    val application: Application = checkNotNull(extras[APPLICATION_KEY])
                    return PreviewViewModel(
                        application.contentResolver,
                        application.resources.getDimensionPixelSize(
                            R.dimen.chooser_preview_image_max_dimen
                        )
                    )
                        as T
                }
            }
    }
}

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

package com.android.intentresolver.contentpreview.payloadtoggle.shared.model

import android.net.Uri

/** An individual preview presented in Shareousel. */
data class PreviewModel(
    /** Uri for this item; if this preview is selected, this will be shared with the target app. */
    val uri: Uri,
    /** Uri for the preview image. */
    val previewUri: Uri? = uri,
    /** Mimetype for the data [uri] points to. */
    val mimeType: String?,
    val aspectRatio: Float = 1f,
    /**
     * Relative item position in the list that is used to determine items order in the target intent
     */
    val order: Int,
)

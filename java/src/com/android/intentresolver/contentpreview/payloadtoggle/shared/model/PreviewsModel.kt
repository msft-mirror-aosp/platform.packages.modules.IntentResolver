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

/** A dataset of previews for Shareousel. */
data class PreviewsModel(
    /** All available [PreviewModel]s. */
    val previewModels: List<PreviewModel>,
    /** Index into [previewModels] that should be initially displayed to the user. */
    val startIdx: Int,
    /**
     * Signals that more data should be loaded to the left of this dataset. A `null` value indicates
     * that there is no more data to load in that direction.
     */
    val loadMoreLeft: (() -> Unit)?,
    /**
     * Signals that more data should be loaded to the right of this dataset. A `null` value
     * indicates that there is no more data to load in that direction.
     */
    val loadMoreRight: (() -> Unit)?,
    /**
     * Index into [previewModels] where any attempted access less than or equal to it should trigger
     * a window shift left.
     */
    val leftTriggerIndex: Int,
    /**
     * Index into [previewModels] where any attempted access greater than or equal to it should
     * trigger a window shift right.
     */
    val rightTriggerIndex: Int,
)

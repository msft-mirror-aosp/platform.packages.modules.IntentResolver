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

package com.android.intentresolver.contentpreview.payloadtoggle.data.model

import android.graphics.drawable.Icon

/** Data model for a custom action the user can take. */
data class CustomActionModel(
    /** Label presented to the user identifying this action. */
    val label: CharSequence,
    /** Icon presented to the user for this action. */
    val icon: Icon,
    /** When invoked, performs this action. */
    val performAction: () -> Unit,
)

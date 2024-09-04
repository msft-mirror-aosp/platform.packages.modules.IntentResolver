/*
 * Copyright 2024 The Android Open Source Project
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

@file:JvmName("SuspendedMatrixColorFilter")

package com.android.intentresolver.util.graphics

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter

val suspendedColorMatrix by lazy {
    val grayValue = 127f
    val scale = 0.5f // half bright

    val tempBrightnessMatrix =
        ColorMatrix().apply {
            array.let { m ->
                m[0] = scale
                m[6] = scale
                m[12] = scale
                m[4] = grayValue
                m[9] = grayValue
                m[14] = grayValue
            }
        }

    val matrix =
        ColorMatrix().apply {
            setSaturation(0.0f)
            preConcat(tempBrightnessMatrix)
        }
    ColorMatrixColorFilter(matrix)
}

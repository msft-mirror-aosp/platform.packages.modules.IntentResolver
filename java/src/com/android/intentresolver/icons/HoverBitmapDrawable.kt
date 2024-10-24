/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.intentresolver.icons

import android.graphics.Bitmap
import com.android.launcher3.icons.FastBitmapDrawable

/** A [FastBitmapDrawable] extension that provides access to the bitmap. */
class HoverBitmapDrawable(val bitmap: Bitmap) : FastBitmapDrawable(bitmap) {

    override fun newConstantState(): FastBitmapConstantState {
        return HoverBitmapDrawableState(bitmap, iconColor)
    }

    private class HoverBitmapDrawableState(private val bitmap: Bitmap, color: Int) :
        FastBitmapConstantState(bitmap, color) {
        override fun createDrawable(): FastBitmapDrawable {
            return HoverBitmapDrawable(bitmap)
        }
    }

    companion object {
        init {
            setFlagHoverEnabled(true)
        }
    }
}

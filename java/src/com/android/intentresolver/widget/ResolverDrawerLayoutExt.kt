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

@file:JvmName("ResolverDrawerLayoutExt")

package com.android.intentresolver.widget

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup.MarginLayoutParams

fun ResolverDrawerLayout.getVisibleDrawerRect(outRect: Rect) {
    if (!isLaidOut) {
        outRect.set(0, 0, 0, 0)
        return
    }
    val firstChild = firstNonGoneChild()
    val lp = firstChild?.layoutParams as? MarginLayoutParams
    val margin = lp?.topMargin ?: 0
    val top = maxOf(paddingTop, topOffset + margin)
    val leftEdge = paddingLeft
    val rightEdge = width - paddingRight
    val widthAvailable = rightEdge - leftEdge
    val childWidth = firstChild?.width ?: 0
    val left = leftEdge + (widthAvailable - childWidth) / 2
    val right = left + childWidth
    outRect.set(left, top, right, height - paddingBottom)
}

fun ResolverDrawerLayout.firstNonGoneChild(): View? {
    for (i in 0 until childCount) {
        val view = getChildAt(i)
        if (view.visibility != View.GONE) {
            return view
        }
    }
    return null
}

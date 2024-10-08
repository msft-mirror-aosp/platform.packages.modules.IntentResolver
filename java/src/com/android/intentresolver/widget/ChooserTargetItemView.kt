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

package com.android.intentresolver.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout

class ChooserTargetItemView(
    context: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int,
    defStyleRes: Int,
) : LinearLayout(context, attrs, defStyleAttr, defStyleRes) {
    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
    ) : this(context, attrs, defStyleAttr, 0)

    private var iconView: ImageView? = null

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        if (child is ImageView) {
            iconView = child
        }
    }

    override fun onViewRemoved(child: View?) {
        super.onViewRemoved(child)
        if (child === iconView) {
            iconView = null
        }
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        val iconView = iconView ?: return false
        if (!isEnabled) return true
        when (event.action) {
            MotionEvent.ACTION_HOVER_ENTER -> {
                iconView.isHovered = true
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                iconView.isHovered = false
            }
        }
        return true
    }

    override fun onInterceptHoverEvent(event: MotionEvent?) = true
}

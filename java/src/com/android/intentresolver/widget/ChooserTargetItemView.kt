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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.InputDevice.SOURCE_MOUSE
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_HOVER_ENTER
import android.view.MotionEvent.ACTION_HOVER_MOVE
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.android.intentresolver.R

class ChooserTargetItemView(
    context: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int,
    defStyleRes: Int,
) : LinearLayout(context, attrs, defStyleAttr, defStyleRes) {
    private val outlineRadius: Float
    private val outlineWidth: Float
    private val outlinePaint: Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val outlineInnerPaint: Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private var iconView: ImageView? = null

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
    ) : this(context, attrs, defStyleAttr, 0)

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.ChooserTargetItemView)
        val defaultWidth =
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                2f,
                context.resources.displayMetrics,
            )
        outlineRadius =
            a.getDimension(R.styleable.ChooserTargetItemView_focusOutlineCornerRadius, 0f)
        outlineWidth =
            a.getDimension(R.styleable.ChooserTargetItemView_focusOutlineWidth, defaultWidth)

        outlinePaint.strokeWidth = outlineWidth
        outlinePaint.color =
            a.getColor(R.styleable.ChooserTargetItemView_focusOutlineColor, Color.TRANSPARENT)

        outlineInnerPaint.strokeWidth = outlineWidth
        outlineInnerPaint.color =
            a.getColor(R.styleable.ChooserTargetItemView_focusInnerOutlineColor, Color.TRANSPARENT)
        a.recycle()
    }

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
            ACTION_HOVER_ENTER -> {
                iconView.isHovered = true
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                iconView.isHovered = false
            }
        }
        return true
    }

    override fun onInterceptHoverEvent(event: MotionEvent) =
        if (event.isFromSource(SOURCE_MOUSE)) {
            // This is the same logic as in super.onInterceptHoverEvent (ViewGroup) minus the check
            // that the pointer fall on the scroll bar as we need to control the hover state of the
            // icon.
            // We also want to intercept only MOUSE hover events as the TalkBack's Explore by Touch
            // (including single taps) reported as a hover event.
            event.action == ACTION_HOVER_MOVE || event.action == ACTION_HOVER_ENTER
        } else {
            super.onInterceptHoverEvent(event)
        }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (isFocused) {
            drawFocusInnerOutline(canvas)
            drawFocusOutline(canvas)
        }
    }

    private fun drawFocusInnerOutline(canvas: Canvas) {
        val outlineOffset = outlineWidth + outlineWidth / 2
        canvas.drawRoundRect(
            outlineOffset,
            outlineOffset,
            maxOf(0f, width - outlineOffset),
            maxOf(0f, height - outlineOffset),
            outlineRadius - outlineWidth,
            outlineRadius - outlineWidth,
            outlineInnerPaint,
        )
    }

    private fun drawFocusOutline(canvas: Canvas) {
        val outlineOffset = outlineWidth / 2
        canvas.drawRoundRect(
            outlineOffset,
            outlineOffset,
            maxOf(0f, width - outlineOffset),
            maxOf(0f, height - outlineOffset),
            outlineRadius,
            outlineRadius,
            outlinePaint,
        )
    }
}

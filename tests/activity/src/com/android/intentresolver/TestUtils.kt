/*
 * Copyright 2025 The Android Open Source Project
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

@file:JvmName("TestUtils")

package com.android.intentresolver

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri

@JvmOverloads
fun createSendImageIntent(imageThumbnail: Uri?, mimeType: String = "image/png") =
    Intent().apply {
        setAction(Intent.ACTION_SEND)
        putExtra(Intent.EXTRA_STREAM, imageThumbnail)
        setType(mimeType)
        if (imageThumbnail != null) {
            val clipItem = ClipData.Item(imageThumbnail)
            clipData = ClipData("Clip Label", arrayOf<String>(mimeType), clipItem)
        }
    }

fun createBitmap(width: Int, height: Int, bgColor: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint =
        Paint().apply {
            setColor(bgColor)
            style = Paint.Style.FILL
        }
    canvas.drawPaint(paint)

    with(paint) {
        setColor(Color.WHITE)
        isAntiAlias = true
        textSize = 14f
        textAlign = Paint.Align.CENTER
    }
    canvas.drawText("Hi!", (width / 2f), (height / 2f), paint)

    return bitmap
}

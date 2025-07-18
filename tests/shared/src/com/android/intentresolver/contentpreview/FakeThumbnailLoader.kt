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

package com.android.intentresolver.contentpreview

import android.graphics.Bitmap
import android.net.Uri
import android.util.Size

/** Fake implementation of [ThumbnailLoader] for use in testing. */
class FakeThumbnailLoader(private val defaultSize: Size = Size(100, 100)) : ThumbnailLoader {

    val fakeInvoke = mutableMapOf<Uri, suspend (Size) -> Bitmap?>()
    val invokeCalls = mutableListOf<Uri>()
    var unfinishedInvokeCount = 0

    override suspend fun loadThumbnail(uri: Uri): Bitmap? = getBitmap(uri, defaultSize)

    override suspend fun loadThumbnail(uri: Uri, size: Size): Bitmap? = getBitmap(uri, size)

    private suspend fun getBitmap(uri: Uri, size: Size): Bitmap? {
        invokeCalls.add(uri)
        unfinishedInvokeCount++
        val result = fakeInvoke[uri]?.invoke(size)
        unfinishedInvokeCount--
        return result
    }
}

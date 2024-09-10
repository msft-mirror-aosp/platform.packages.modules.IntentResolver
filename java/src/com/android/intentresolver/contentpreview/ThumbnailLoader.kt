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

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import javax.inject.Inject

/** Interface for objects that can attempt load a [Bitmap] from a [Uri]. */
interface ThumbnailLoader : suspend (Uri) -> Bitmap?

/** Default implementation of [ThumbnailLoader]. */
class ThumbnailLoaderImpl
@Inject
constructor(
    private val contentResolver: ContentResolver,
    @ThumbnailSize thumbnailSize: Int,
) : ThumbnailLoader {

    private val size = Size(thumbnailSize, thumbnailSize)

    override suspend fun invoke(uri: Uri): Bitmap =
        contentResolver.loadThumbnail(uri, size, /* signal = */ null)
}

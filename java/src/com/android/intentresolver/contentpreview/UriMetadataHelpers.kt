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

package com.android.intentresolver.contentpreview

import android.content.ContentInterface
import android.database.Cursor
import android.media.MediaMetadata
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL
import android.provider.MediaStore.MediaColumns.HEIGHT
import android.provider.MediaStore.MediaColumns.WIDTH
import android.util.Log
import android.util.Size
import com.android.intentresolver.measurements.runTracing

internal fun ContentInterface.getTypeSafe(uri: Uri): String? =
    runTracing("getType") {
        try {
            getType(uri)
        } catch (e: SecurityException) {
            logProviderPermissionWarning(uri, "mime type")
            null
        } catch (t: Throwable) {
            Log.e(ContentPreviewUi.TAG, "Failed to read metadata, uri: $uri", t)
            null
        }
    }

internal fun ContentInterface.getStreamTypesSafe(uri: Uri): Array<String?> =
    runTracing("getStreamTypes") {
        try {
            getStreamTypes(uri, "*/*") ?: emptyArray()
        } catch (e: SecurityException) {
            logProviderPermissionWarning(uri, "stream types")
            emptyArray<String?>()
        } catch (t: Throwable) {
            Log.e(ContentPreviewUi.TAG, "Failed to read stream types, uri: $uri", t)
            emptyArray<String?>()
        }
    }

internal fun ContentInterface.querySafe(uri: Uri, columns: Array<String>): Cursor? =
    runTracing("query") {
        try {
            query(uri, columns, null, null)
        } catch (e: SecurityException) {
            logProviderPermissionWarning(uri, "metadata")
            null
        } catch (t: Throwable) {
            Log.e(ContentPreviewUi.TAG, "Failed to read metadata, uri: $uri", t)
            null
        }
    }

internal fun Cursor.readSupportsThumbnail(): Boolean =
    runCatching {
            val flagColIdx = columnNames.indexOf(DocumentsContract.Document.COLUMN_FLAGS)
            flagColIdx >= 0 && ((getInt(flagColIdx) and FLAG_SUPPORTS_THUMBNAIL) != 0)
        }
        .getOrDefault(false)

internal fun Cursor.readPreviewUri(): Uri? =
    runCatching { readString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)?.let(Uri::parse) }
        .getOrNull()

fun Cursor.readSize(): Size? {
    val widthIdx = columnNames.indexOf(WIDTH)
    val heightIdx = columnNames.indexOf(HEIGHT)
    return if (widthIdx < 0 || heightIdx < 0 || isNull(widthIdx) || isNull(heightIdx)) {
        null
    } else {
        runCatching {
                val width = getInt(widthIdx)
                val height = getInt(heightIdx)
                if (width >= 0 && height > 0) {
                    Size(width, height)
                } else {
                    null
                }
            }
            .getOrNull()
    }
}

internal fun Cursor.readString(columnName: String): String? =
    runCatching { columnNames.indexOf(columnName).takeIf { it >= 0 }?.let { getString(it) } }
        .getOrNull()

private fun logProviderPermissionWarning(uri: Uri, dataName: String) {
    // The ContentResolver already logs the exception. Log something more informative.
    Log.w(
        ContentPreviewUi.TAG,
        "Could not read $uri $dataName. If a preview is desired, call Intent#setClipData() to" +
            " ensure that the sharesheet is given permission.",
    )
}

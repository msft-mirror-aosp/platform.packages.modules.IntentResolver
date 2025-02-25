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

package com.android.intentresolver.contentpreview.payloadtoggle.domain.cursor

import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.service.chooser.AdditionalContentContract
import com.android.intentresolver.TestContentProvider
import com.android.intentresolver.contentpreview.payloadtoggle.domain.model.CursorRow
import com.android.intentresolver.util.cursor.CursorView
import com.android.intentresolver.util.cursor.viewBy

class FakePayloadToggleCursorResolver : CursorResolver<CursorRow?> {
    private val uris = mutableListOf<Uri>()
    private var startPosition = -1

    fun setUris(count: Int, startPosition: Int, mimeTypes: Map<Int, String> = emptyMap()) {
        uris.clear()
        this.startPosition = startPosition
        for (i in 0 until count) {
            uris.add(
                TestContentProvider.makeItemUri(
                    i.toString(),
                    mimeTypes.getOrDefault(i, DEFAULT_MIME_TYPE),
                )
            )
        }
    }

    override suspend fun getCursor(): CursorView<CursorRow?>? {
        val cursor = MatrixCursor(arrayOf(AdditionalContentContract.Columns.URI))
        for (uri in uris) {
            cursor.addRow(arrayOf(uri.toString()))
        }
        if (startPosition >= 0) {
            var cursorExtras = cursor.extras
            cursorExtras =
                if (cursorExtras == null) {
                    Bundle()
                } else {
                    Bundle(cursorExtras)
                }
            cursorExtras.putInt(AdditionalContentContract.CursorExtraKeys.POSITION, startPosition)
            cursor.extras = cursorExtras
        }
        return cursor.viewBy { CursorRow(Uri.parse(getString(0)), null, position) }
    }

    companion object {
        const val DEFAULT_MIME_TYPE = "image/png"
    }
}

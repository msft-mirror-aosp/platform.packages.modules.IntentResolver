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

import android.database.MatrixCursor
import android.provider.MediaStore.MediaColumns.HEIGHT
import android.provider.MediaStore.MediaColumns.WIDTH
import android.util.Size
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CursorReadSizeTest {
    @Test
    fun missingSizeColumns() {
        val cursor = MatrixCursor(arrayOf("column")).apply { addRow(arrayOf("abc")) }
        cursor.moveToFirst()

        assertThat(cursor.readSize()).isNull()
    }

    @Test
    fun testIncorrectSizeValues() = runTest {
        val cursor =
            MatrixCursor(arrayOf(WIDTH, HEIGHT)).apply {
                addRow(arrayOf(null, null))
                addRow(arrayOf("100", null))
                addRow(arrayOf(null, "100"))
                addRow(arrayOf("-100", "100"))
                addRow(arrayOf("100", "-100"))
                addRow(arrayOf("100", "abc"))
                addRow(arrayOf("abc", "100"))
            }

        var i = 0
        while (cursor.moveToNext()) {
            i++
            assertWithMessage("Row $i").that(cursor.readSize()).isNull()
        }
    }

    @Test
    fun testCorrectSizeValues() = runTest {
        val cursor =
            MatrixCursor(arrayOf(HEIGHT, WIDTH)).apply {
                addRow(arrayOf("100", 0))
                addRow(arrayOf("100", "50"))
            }

        cursor.moveToNext()
        assertThat(cursor.readSize()).isEqualTo(Size(0, 100))

        cursor.moveToNext()
        assertThat(cursor.readSize()).isEqualTo(Size(50, 100))
    }
}

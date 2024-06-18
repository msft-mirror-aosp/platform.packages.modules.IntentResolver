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

package com.android.intentresolver.contentpreview.payloadtoggle.domain.cursor

import android.content.ContentInterface
import android.content.Intent
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore.MediaColumns.HEIGHT
import android.provider.MediaStore.MediaColumns.WIDTH
import android.service.chooser.AdditionalContentContract.Columns.URI
import android.util.Size
import com.android.intentresolver.util.cursor.get
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock

class PayloadToggleCursorResolverTest {
    private val cursorUri = Uri.parse("content://org.pkg.app.extra")
    private val chooserIntent = Intent()

    @Test
    fun missingSizeColumns() = runTest {
        val uri = createUri(1)
        val sourceCursor =
            MatrixCursor(arrayOf(URI)).apply {
                addRow(arrayOf(uri.toString()))
                addRow(
                    arrayOf(
                        cursorUri.buildUpon().appendPath("should-be-ignored.png").build().toString()
                    )
                )
                addRow(arrayOf(null))
            }
        val fakeContentProvider =
            mock<ContentInterface> {
                on { query(eq(cursorUri), any(), any(), any()) } doReturn sourceCursor
            }
        val testSubject =
            PayloadToggleCursorResolver(
                fakeContentProvider,
                cursorUri,
                chooserIntent,
            )

        val cursor = testSubject.getCursor()
        assertThat(cursor).isNotNull()
        assertThat(cursor!!.count).isEqualTo(3)
        cursor[0].let { row ->
            assertThat(row).isNotNull()
            assertThat(row!!.uri).isEqualTo(uri)
            assertThat(row.previewSize).isNull()
        }
        assertThat(cursor[1]).isNull()
        assertThat(cursor[2]).isNull()
    }

    @Test
    fun testCorrectSizeValues() = runTest {
        val uri = createUri(1)
        val sourceCursor =
            MatrixCursor(arrayOf(URI, WIDTH, HEIGHT)).apply {
                addRow(arrayOf(uri.toString(), "100", "50"))
            }
        val fakeContentProvider =
            mock<ContentInterface> {
                on { query(eq(cursorUri), any(), any(), any()) } doReturn sourceCursor
            }
        val testSubject =
            PayloadToggleCursorResolver(
                fakeContentProvider,
                cursorUri,
                chooserIntent,
            )

        val cursor = testSubject.getCursor()
        assertThat(cursor).isNotNull()
        assertThat(cursor!!.count).isEqualTo(1)

        cursor[0].let { row ->
            assertThat(row).isNotNull()
            assertThat(row!!.uri).isEqualTo(uri)
            assertThat(row.previewSize).isEqualTo(Size(100, 50))
        }
    }

    @Test
    fun testRowPositionValues() = runTest {
        val rowCount = 10
        val sourceCursor =
            MatrixCursor(arrayOf(URI)).apply {
                for (i in 1..rowCount) {
                    addRow(arrayOf(createUri(i).toString()))
                }
            }
        val fakeContentProvider =
            mock<ContentInterface> {
                on { query(eq(cursorUri), any(), any(), any()) } doReturn sourceCursor
            }
        val testSubject =
            PayloadToggleCursorResolver(
                fakeContentProvider,
                cursorUri,
                chooserIntent,
            )

        val cursor = testSubject.getCursor()
        assertThat(cursor).isNotNull()
        assertThat(cursor!!.count).isEqualTo(rowCount)
        for (i in 0 until rowCount) {
            cursor[i].let { row ->
                assertWithMessage("Row $i").that(row).isNotNull()
                assertWithMessage("Row $i").that(row!!.position).isEqualTo(i)
            }
        }
    }
}

private fun createUri(id: Int) = Uri.parse("content://org.pkg/app/img-$id.png")

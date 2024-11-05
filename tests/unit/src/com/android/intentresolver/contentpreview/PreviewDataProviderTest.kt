/*
 * Copyright (C) 2023 The Android Open Source Project
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
import android.content.Intent
import android.database.MatrixCursor
import android.media.MediaMetadata
import android.net.Uri
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import android.platform.test.flag.junit.SetFlagsRule
import android.provider.DocumentsContract
import android.provider.Downloads
import android.provider.OpenableColumns
import com.android.intentresolver.Flags.FLAG_INDIVIDUAL_METADATA_TITLE_READ
import com.google.common.truth.Truth.assertThat
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(Parameterized::class)
@OptIn(ExperimentalCoroutinesApi::class)
class PreviewDataProviderTest(flags: FlagsParameterization) {
    private val contentResolver = mock<ContentInterface>()
    private val mimeTypeClassifier = DefaultMimeTypeClassifier
    private val testScope = TestScope(EmptyCoroutineContext + UnconfinedTestDispatcher())
    @get:Rule val setFlagsRule = SetFlagsRule(flags)

    private fun createDataProvider(
        targetIntent: Intent,
        scope: CoroutineScope = testScope,
        additionalContentUri: Uri? = null,
        resolver: ContentInterface = contentResolver,
        typeClassifier: MimeTypeClassifier = mimeTypeClassifier,
    ) = PreviewDataProvider(scope, targetIntent, additionalContentUri, resolver, typeClassifier)

    @Test
    fun test_nonSendIntentAction_resolvesToTextPreviewUiSynchronously() {
        val targetIntent = Intent(Intent.ACTION_VIEW)
        val testSubject = createDataProvider(targetIntent)

        assertThat(testSubject.previewType).isEqualTo(ContentPreviewType.CONTENT_PREVIEW_TEXT)
        verify(contentResolver, never()).getType(any())
    }

    @Test
    fun test_sendSingleTextFileWithoutPreview_resolvesToFilePreviewUi() =
        testScope.runTest {
            val fileName = "notes.txt"
            val uri = Uri.parse("content://org.pkg.app/$fileName")
            val targetIntent =
                Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_STREAM, uri)
                    type = "text/plain"
                }
            whenever(contentResolver.getType(uri)).thenReturn("text/plain")
            val testSubject = createDataProvider(targetIntent)

            assertThat(testSubject.previewType).isEqualTo(ContentPreviewType.CONTENT_PREVIEW_FILE)
            assertThat(testSubject.uriCount).isEqualTo(1)
            assertThat(testSubject.firstFileInfo?.uri).isEqualTo(uri)
            assertThat(testSubject.getFirstFileName()).isEqualTo(fileName)
            verify(contentResolver, times(1)).getType(any())
        }

    @Test
    fun test_sendSingleTextFileWithDisplayNameAndTitle_displayNameTakesPrecedenceOverTitle() =
        testScope.runTest {
            val uri = Uri.parse("content://org.pkg.app/1234")
            val targetIntent =
                Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_STREAM, uri)
                    type = "text/plain"
                }
            whenever(contentResolver.getType(uri)).thenReturn("text/plain")
            val title = "Notes"
            val displayName = "Notes.txt"
            whenever(contentResolver.query(eq(uri), anyOrNull(), anyOrNull(), anyOrNull()))
                .thenReturn(
                    MatrixCursor(arrayOf(Downloads.Impl.COLUMN_TITLE, OpenableColumns.DISPLAY_NAME))
                        .apply { addRow(arrayOf(title, displayName)) }
                )
            contentResolver.setTitle(uri, title)
            contentResolver.setDisplayName(uri, displayName)
            val testSubject = createDataProvider(targetIntent)

            assertThat(testSubject.previewType).isEqualTo(ContentPreviewType.CONTENT_PREVIEW_FILE)
            assertThat(testSubject.getFirstFileName()).isEqualTo(displayName)
        }

    @Test
    fun test_sendIntentWithoutUris_resolvesToTextPreviewUiSynchronously() {
        val targetIntent = Intent(Intent.ACTION_SEND).apply { type = "image/png" }
        val testSubject = createDataProvider(targetIntent)

        assertThat(testSubject.previewType).isEqualTo(ContentPreviewType.CONTENT_PREVIEW_TEXT)
        verify(contentResolver, never()).getType(any())
    }

    @Test
    fun test_sendSingleImage_resolvesToImagePreviewUi() {
        val uri = Uri.parse("content://org.pkg.app/image.png")
        val targetIntent = Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uri) }
        whenever(contentResolver.getType(uri)).thenReturn("image/png")
        val testSubject = createDataProvider(targetIntent)

        assertThat(testSubject.previewType).isEqualTo(ContentPreviewType.CONTENT_PREVIEW_IMAGE)
        assertThat(testSubject.uriCount).isEqualTo(1)
        assertThat(testSubject.firstFileInfo?.uri).isEqualTo(uri)
        assertThat(testSubject.firstFileInfo?.previewUri).isEqualTo(uri)
        verify(contentResolver, times(1)).getType(any())
    }

    @Test
    fun test_sendSingleFile_resolvesToFilePreviewUi() =
        testScope.runTest {
            val fileName = "paper.pdf"
            val uri = Uri.parse("content://org.pkg.app/$fileName")
            val targetIntent =
                Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uri) }
            whenever(contentResolver.getType(uri)).thenReturn("application/pdf")
            val testSubject = createDataProvider(targetIntent)

            assertThat(testSubject.previewType).isEqualTo(ContentPreviewType.CONTENT_PREVIEW_FILE)
            assertThat(testSubject.uriCount).isEqualTo(1)
            assertThat(testSubject.firstFileInfo?.uri).isEqualTo(uri)
            assertThat(testSubject.firstFileInfo?.previewUri).isNull()
            assertThat(testSubject.getFirstFileName()).isEqualTo(fileName)
            verify(contentResolver, times(1)).getType(any())
        }

    @Test
    fun test_sendSingleImageWithFailingGetType_resolvesToFilePreviewUi() =
        testScope.runTest {
            val fileName = "image.png"
            val uri = Uri.parse("content://org.pkg.app/$fileName")
            val targetIntent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                }
            whenever(contentResolver.getType(uri)).thenThrow(SecurityException("test failure"))
            val testSubject = createDataProvider(targetIntent)

            assertThat(testSubject.previewType).isEqualTo(ContentPreviewType.CONTENT_PREVIEW_FILE)
            assertThat(testSubject.uriCount).isEqualTo(1)
            assertThat(testSubject.firstFileInfo?.uri).isEqualTo(uri)
            assertThat(testSubject.firstFileInfo?.previewUri).isNull()
            assertThat(testSubject.getFirstFileName()).isEqualTo(fileName)
            verify(contentResolver, times(1)).getType(any())
        }

    @Test
    fun test_sendSingleFileWithFailingMetadata_resolvesToFilePreviewUi() =
        testScope.runTest {
            val fileName = "manual.pdf"
            val uri = Uri.parse("content://org.pkg.app/$fileName")
            val targetIntent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                }
            whenever(contentResolver.getType(uri)).thenReturn("application/pdf")
            whenever(contentResolver.getStreamTypes(uri, "*/*"))
                .thenThrow(SecurityException("test failure"))
            whenever(contentResolver.query(eq(uri), anyOrNull(), anyOrNull(), anyOrNull()))
                .thenThrow(SecurityException("test failure"))
            val testSubject = createDataProvider(targetIntent)

            assertThat(testSubject.previewType).isEqualTo(ContentPreviewType.CONTENT_PREVIEW_FILE)
            assertThat(testSubject.uriCount).isEqualTo(1)
            assertThat(testSubject.firstFileInfo?.uri).isEqualTo(uri)
            assertThat(testSubject.firstFileInfo?.previewUri).isNull()
            assertThat(testSubject.getFirstFileName()).isEqualTo(fileName)
            verify(contentResolver, times(1)).getType(any())
        }

    @Test
    @EnableFlags(FLAG_INDIVIDUAL_METADATA_TITLE_READ)
    fun test_sendSingleImageWithFailingGetTypeDisjointTitleRead_resolvesToFilePreviewUi() =
        testScope.runTest {
            val uri = Uri.parse("content://org.pkg.app/image.png")
            val targetIntent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                }
            whenever(contentResolver.getType(uri)).thenThrow(SecurityException("test failure"))
            val title = "Image Title"
            contentResolver.setTitle(uri, title)
            val testSubject = createDataProvider(targetIntent)

            assertThat(testSubject.previewType).isEqualTo(ContentPreviewType.CONTENT_PREVIEW_FILE)
            assertThat(testSubject.uriCount).isEqualTo(1)
            assertThat(testSubject.firstFileInfo?.uri).isEqualTo(uri)
            assertThat(testSubject.firstFileInfo?.previewUri).isNull()
            assertThat(testSubject.getFirstFileName()).isEqualTo(title)
            verify(contentResolver, times(1)).getType(any())
        }

    @Test
    fun test_sendSingleFileWithFailingImageMetadata_resolvesToFilePreviewUi() =
        testScope.runTest {
            val fileName = "notes.pdf"
            val uri = Uri.parse("content://org.pkg.app/$fileName")
            val targetIntent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                }
            whenever(contentResolver.getType(uri)).thenReturn("application/pdf")
            whenever(contentResolver.getStreamTypes(uri, "*/*"))
                .thenThrow(SecurityException("test failure"))
            whenever(contentResolver.query(eq(uri), anyOrNull(), anyOrNull(), anyOrNull()))
                .thenThrow(SecurityException("test failure"))
            val testSubject = createDataProvider(targetIntent)

            assertThat(testSubject.previewType).isEqualTo(ContentPreviewType.CONTENT_PREVIEW_FILE)
            assertThat(testSubject.uriCount).isEqualTo(1)
            assertThat(testSubject.firstFileInfo?.uri).isEqualTo(uri)
            assertThat(testSubject.firstFileInfo?.previewUri).isNull()
            assertThat(testSubject.getFirstFileName()).isEqualTo(fileName)
            verify(contentResolver, times(1)).getType(any())
        }

    @Test
    @EnableFlags(FLAG_INDIVIDUAL_METADATA_TITLE_READ)
    fun test_sendSingleFileWithFailingImageMetadataIndividualTitleRead_resolvesToFilePreviewUi() =
        testScope.runTest {
            val uri = Uri.parse("content://org.pkg.app/image.png")
            val targetIntent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                }
            whenever(contentResolver.getStreamTypes(uri, "*/*"))
                .thenThrow(SecurityException("test failure"))
            whenever(contentResolver.query(uri, ICON_METADATA_COLUMNS, null, null))
                .thenThrow(SecurityException("test failure"))
            val displayName = "display name"
            contentResolver.setDisplayName(uri, displayName)
            val testSubject = createDataProvider(targetIntent)

            assertThat(testSubject.previewType).isEqualTo(ContentPreviewType.CONTENT_PREVIEW_FILE)
            assertThat(testSubject.uriCount).isEqualTo(1)
            assertThat(testSubject.firstFileInfo?.uri).isEqualTo(uri)
            assertThat(testSubject.firstFileInfo?.previewUri).isNull()
            assertThat(testSubject.getFirstFileName()).isEqualTo(displayName)
            verify(contentResolver, times(1)).getType(any())
        }

    @Test
    fun test_SingleFileUriWithImageTypeInGetStreamTypes_useImagePreviewUi() {
        val uri = Uri.parse("content://org.pkg.app/paper.pdf")
        val targetIntent = Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uri) }
        whenever(contentResolver.getStreamTypes(uri, "*/*"))
            .thenReturn(arrayOf("application/pdf", "image/png"))
        val testSubject = createDataProvider(targetIntent)

        assertThat(testSubject.previewType).isEqualTo(ContentPreviewType.CONTENT_PREVIEW_IMAGE)
        assertThat(testSubject.uriCount).isEqualTo(1)
        assertThat(testSubject.firstFileInfo?.uri).isEqualTo(uri)
        assertThat(testSubject.firstFileInfo?.previewUri).isEqualTo(uri)
        verify(contentResolver, times(1)).getType(any())
    }

    @Test
    fun test_SingleNonImageUriWithThumbnailFlag_useImagePreviewUi() {
        testMetadataToImagePreview(
            columns = arrayOf(DocumentsContract.Document.COLUMN_FLAGS),
            values =
                arrayOf(
                    DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL or
                        DocumentsContract.Document.FLAG_SUPPORTS_METADATA
                ),
        )
    }

    @Test
    fun test_SingleNonImageUriWithMetadataIconUri_useImagePreviewUi() {
        testMetadataToImagePreview(
            columns = arrayOf(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI),
            values = arrayOf("content://org.pkg.app/test.pdf?thumbnail"),
        )
    }

    private fun testMetadataToImagePreview(columns: Array<String>, values: Array<Any>) {
        val uri = Uri.parse("content://org.pkg.app/test.pdf")
        val targetIntent = Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uri) }
        whenever(contentResolver.getType(uri)).thenReturn("application/pdf")
        val cursor = MatrixCursor(columns).apply { addRow(values) }
        whenever(contentResolver.query(eq(uri), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(cursor)

        val testSubject = createDataProvider(targetIntent)

        assertThat(testSubject.previewType).isEqualTo(ContentPreviewType.CONTENT_PREVIEW_IMAGE)
        assertThat(testSubject.uriCount).isEqualTo(1)
        assertThat(testSubject.firstFileInfo?.uri).isEqualTo(uri)
        assertThat(testSubject.firstFileInfo?.previewUri).isNotNull()
        verify(contentResolver, times(1)).getType(any())
        assertThat(cursor.isClosed).isTrue()
    }

    @Test
    fun test_emptyQueryResult_cursorGetsClosed() {
        val uri = Uri.parse("content://org.pkg.app/test.pdf")
        val targetIntent = Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uri) }
        whenever(contentResolver.getType(uri)).thenReturn("application/pdf")
        val cursor = MatrixCursor(emptyArray())
        whenever(contentResolver.query(eq(uri), anyOrNull(), anyOrNull(), anyOrNull()))
            .thenReturn(cursor)

        val testSubject = createDataProvider(targetIntent)

        assertThat(testSubject.previewType).isEqualTo(ContentPreviewType.CONTENT_PREVIEW_FILE)
        verify(contentResolver, times(1)).query(eq(uri), anyOrNull(), anyOrNull(), anyOrNull())
        assertThat(cursor.isClosed).isTrue()
    }

    @Test
    fun test_multipleImageUri_useImagePreviewUi() {
        val uri1 = Uri.parse("content://org.pkg.app/test.png")
        val uri2 = Uri.parse("content://org.pkg.app/test.jpg")
        val targetIntent =
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                putExtra(
                    Intent.EXTRA_STREAM,
                    ArrayList<Uri>().apply {
                        add(uri1)
                        add(uri2)
                    },
                )
            }
        whenever(contentResolver.getType(uri1)).thenReturn("image/png")
        whenever(contentResolver.getType(uri2)).thenReturn("image/jpeg")
        val testSubject = createDataProvider(targetIntent)

        assertThat(testSubject.previewType).isEqualTo(ContentPreviewType.CONTENT_PREVIEW_IMAGE)
        assertThat(testSubject.uriCount).isEqualTo(2)
        assertThat(testSubject.firstFileInfo?.uri).isEqualTo(uri1)
        assertThat(testSubject.firstFileInfo?.previewUri).isEqualTo(uri1)
        // preview type can be determined by the first URI type
        verify(contentResolver, times(1)).getType(any())
    }

    @Test
    fun test_SomeImageUri_useImagePreviewUi() {
        val uri1 = Uri.parse("content://org.pkg.app/test.png")
        val uri2 = Uri.parse("content://org.pkg.app/test.pdf")
        whenever(contentResolver.getType(uri1)).thenReturn("image/png")
        whenever(contentResolver.getType(uri2)).thenReturn("application/pdf")
        val targetIntent =
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                putExtra(
                    Intent.EXTRA_STREAM,
                    ArrayList<Uri>().apply {
                        add(uri1)
                        add(uri2)
                    },
                )
            }
        val testSubject = createDataProvider(targetIntent)

        assertThat(testSubject.previewType).isEqualTo(ContentPreviewType.CONTENT_PREVIEW_IMAGE)
        assertThat(testSubject.uriCount).isEqualTo(2)
        assertThat(testSubject.firstFileInfo?.uri).isEqualTo(uri1)
        assertThat(testSubject.firstFileInfo?.previewUri).isEqualTo(uri1)
        // preview type can be determined by the first URI type
        verify(contentResolver, times(1)).getType(any())
    }

    @Test
    fun test_someFileUrisWithPreview_useImagePreviewUi() {
        val uri1 = Uri.parse("content://org.pkg.app/test.mp4")
        val uri2 = Uri.parse("content://org.pkg.app/test.pdf")
        val targetIntent =
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                putExtra(
                    Intent.EXTRA_STREAM,
                    ArrayList<Uri>().apply {
                        add(uri1)
                        add(uri2)
                    },
                )
            }
        whenever(contentResolver.getType(uri1)).thenReturn("video/mpeg4")
        whenever(contentResolver.getStreamTypes(uri1, "*/*")).thenReturn(arrayOf("image/png"))
        whenever(contentResolver.getType(uri2)).thenReturn("application/pdf")
        val testSubject = createDataProvider(targetIntent)

        assertThat(testSubject.previewType).isEqualTo(ContentPreviewType.CONTENT_PREVIEW_IMAGE)
        assertThat(testSubject.uriCount).isEqualTo(2)
        assertThat(testSubject.firstFileInfo?.uri).isEqualTo(uri1)
        assertThat(testSubject.firstFileInfo?.previewUri).isEqualTo(uri1)
        verify(contentResolver, times(2)).getType(any())
    }

    @Test
    fun test_allFileUrisWithoutPreview_useFilePreviewUi() =
        testScope.runTest {
            val firstFileName = "test.html"
            val uri1 = Uri.parse("content://org.pkg.app/$firstFileName")
            val uri2 = Uri.parse("content://org.pkg.app/test.pdf")
            val targetIntent =
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    putExtra(
                        Intent.EXTRA_STREAM,
                        ArrayList<Uri>().apply {
                            add(uri1)
                            add(uri2)
                        },
                    )
                }
            whenever(contentResolver.getType(uri1)).thenReturn("text/html")
            whenever(contentResolver.getType(uri2)).thenReturn("application/pdf")
            val testSubject = createDataProvider(targetIntent)

            assertThat(testSubject.previewType).isEqualTo(ContentPreviewType.CONTENT_PREVIEW_FILE)
            assertThat(testSubject.uriCount).isEqualTo(2)
            assertThat(testSubject.firstFileInfo?.uri).isEqualTo(uri1)
            assertThat(testSubject.firstFileInfo?.previewUri).isNull()
            assertThat(testSubject.getFirstFileName()).isEqualTo(firstFileName)
            verify(contentResolver, times(2)).getType(any())
        }

    @Test
    fun test_imagePreviewFileInfoFlow_dataLoadedOnce() =
        testScope.runTest {
            val uri1 = Uri.parse("content://org.pkg.app/test.html")
            val uri2 = Uri.parse("content://org.pkg.app/test.pdf")
            val targetIntent =
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    putExtra(
                        Intent.EXTRA_STREAM,
                        ArrayList<Uri>().apply {
                            add(uri1)
                            add(uri2)
                        },
                    )
                }
            whenever(contentResolver.getType(uri1)).thenReturn("text/html")
            whenever(contentResolver.getType(uri2)).thenReturn("application/pdf")
            whenever(contentResolver.getStreamTypes(uri1, "*/*"))
                .thenReturn(arrayOf("text/html", "image/jpeg"))
            whenever(contentResolver.getStreamTypes(uri2, "*/*"))
                .thenReturn(arrayOf("application/pdf", "image/png"))
            val testSubject = createDataProvider(targetIntent)

            val fileInfoListOne = testSubject.imagePreviewFileInfoFlow.toList()
            val fileInfoListTwo = testSubject.imagePreviewFileInfoFlow.toList()

            assertThat(fileInfoListOne).hasSize(2)
            assertThat(fileInfoListOne).containsAtLeastElementsIn(fileInfoListTwo).inOrder()

            verify(contentResolver, times(1)).getType(uri1)
            verify(contentResolver, times(1)).getStreamTypes(uri1, "*/*")
            verify(contentResolver, times(1)).getType(uri2)
            verify(contentResolver, times(1)).getStreamTypes(uri2, "*/*")
        }

    @Test
    fun sendImageWithAdditionalContentUri_showPayloadTogglingUi() {
        val uri = Uri.parse("content://org.pkg.app/image.png")
        val targetIntent = Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uri) }
        whenever(contentResolver.getType(uri)).thenReturn("image/png")
        val testSubject =
            createDataProvider(
                targetIntent,
                additionalContentUri = Uri.parse("content://org.pkg.app.extracontent"),
            )

        assertThat(testSubject.previewType)
            .isEqualTo(ContentPreviewType.CONTENT_PREVIEW_PAYLOAD_SELECTION)
        assertThat(testSubject.uriCount).isEqualTo(1)
        assertThat(testSubject.firstFileInfo?.uri).isEqualTo(uri)
        assertThat(testSubject.firstFileInfo?.previewUri).isEqualTo(uri)
        verify(contentResolver, times(1)).getType(any())
    }

    @Test
    fun sendItemsWithAdditionalContentUriWithSameAuthority_showImagePreviewUi() {
        val uri = Uri.parse("content://org.pkg.app/image.png")
        val targetIntent = Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uri) }
        whenever(contentResolver.getType(uri)).thenReturn("image/png")
        val testSubject =
            createDataProvider(
                targetIntent,
                additionalContentUri = Uri.parse("content://org.pkg.app/extracontent"),
            )

        assertThat(testSubject.previewType).isEqualTo(ContentPreviewType.CONTENT_PREVIEW_IMAGE)
        assertThat(testSubject.uriCount).isEqualTo(1)
        assertThat(testSubject.firstFileInfo?.uri).isEqualTo(uri)
        assertThat(testSubject.firstFileInfo?.previewUri).isEqualTo(uri)
        verify(contentResolver, times(1)).getType(any())
    }

    @Test
    fun test_nonSendIntentActionWithAdditionalContentUri_resolvesToTextPreviewUiSynchronously() {
        val targetIntent = Intent(Intent.ACTION_VIEW)
        val testSubject =
            createDataProvider(
                targetIntent,
                additionalContentUri = Uri.parse("content://org.pkg.app/extracontent"),
            )

        assertThat(testSubject.previewType).isEqualTo(ContentPreviewType.CONTENT_PREVIEW_TEXT)
        verify(contentResolver, never()).getType(any())
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters(): List<FlagsParameterization> =
            FlagsParameterization.allCombinationsOf(FLAG_INDIVIDUAL_METADATA_TITLE_READ)
    }
}

private fun ContentInterface.setDisplayName(uri: Uri, displayName: String) =
    setMetadata(uri, arrayOf(OpenableColumns.DISPLAY_NAME), arrayOf(displayName))

private fun ContentInterface.setTitle(uri: Uri, title: String) =
    setMetadata(uri, arrayOf(Downloads.Impl.COLUMN_TITLE), arrayOf(title))

private fun ContentInterface.setMetadata(uri: Uri, columns: Array<String>, values: Array<String>) {
    whenever(query(uri, columns, null, null))
        .thenReturn(MatrixCursor(columns).apply { addRow(values) })
}

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

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.intentresolver.R
import com.android.intentresolver.widget.ImagePreviewView.TransitionElementStatusCallback
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

private const val IMAGE_HEADLINE = "Image Headline"
private const val VIDEO_HEADLINE = "Video Headline"
private const val FILES_HEADLINE = "Files Headline"

@RunWith(AndroidJUnit4::class)
class UnifiedContentPreviewUiTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val testScope = TestScope(EmptyCoroutineContext + UnconfinedTestDispatcher())
    private val actionFactory =
        mock<ChooserContentPreviewUi.ActionFactory> {
            on { createCustomActions() } doReturn emptyList()
        }
    private val imageLoader = mock<ImageLoader>()
    private val headlineGenerator =
        mock<HeadlineGenerator> {
            on { getImagesHeadline(any()) } doReturn IMAGE_HEADLINE
            on { getVideosHeadline(any()) } doReturn VIDEO_HEADLINE
            on { getFilesHeadline(any()) } doReturn FILES_HEADLINE
        }
    private val testMetadataText: CharSequence = "Test metadata text"

    private val context
        get() = getInstrumentation().context

    @Test
    fun test_displayImagesWithoutUriMetadataHeader_showImagesHeadline() {
        testLoadingHeadline("image/*", files = null) { headlineRow ->
            verify(headlineGenerator, times(1)).getImagesHeadline(2)
            verifyPreviewHeadline(headlineRow, IMAGE_HEADLINE)
            verifyPreviewMetadata(headlineRow, testMetadataText)
        }
    }

    @Test
    fun test_displayVideosWithoutUriMetadataHeader_showImagesHeadline() {
        testLoadingHeadline("video/*", files = null) { headlineRow ->
            verify(headlineGenerator, times(1)).getVideosHeadline(2)
            verifyPreviewHeadline(headlineRow, VIDEO_HEADLINE)
            verifyPreviewMetadata(headlineRow, testMetadataText)
        }
    }

    @Test
    fun test_displayDocumentsWithoutUriMetadataHeader_showImagesHeadline() {
        testLoadingHeadline("application/pdf", files = null) { headlineRow ->
            verify(headlineGenerator, times(1)).getFilesHeadline(2)
            verifyPreviewHeadline(headlineRow, FILES_HEADLINE)
            verifyPreviewMetadata(headlineRow, testMetadataText)
        }
    }

    @Test
    fun test_displayMixedContentWithoutUriMetadataHeader_showImagesHeadline() {
        testLoadingHeadline("*/*", files = null) { headlineRow ->
            verify(headlineGenerator, times(1)).getFilesHeadline(2)
            verifyPreviewHeadline(headlineRow, FILES_HEADLINE)
            verifyPreviewMetadata(headlineRow, testMetadataText)
        }
    }

    @Test
    fun test_displayImagesWithUriMetadataSetHeader_showImagesHeadline() {
        val uri = Uri.parse("content://pkg.app/image.png")
        val files =
            listOf(
                FileInfo.Builder(uri).withMimeType("image/png").build(),
                FileInfo.Builder(uri).withMimeType("image/jpeg").build(),
            )
        testLoadingHeadline("image/*", files) { headlineRow ->
            verify(headlineGenerator, times(1)).getImagesHeadline(2)
            verifyPreviewHeadline(headlineRow, IMAGE_HEADLINE)
        }
    }

    @Test
    fun test_displayVideosWithUriMetadataSetHeader_showImagesHeadline() {
        val uri = Uri.parse("content://pkg.app/image.png")
        val files =
            listOf(
                FileInfo.Builder(uri).withMimeType("video/mp4").build(),
                FileInfo.Builder(uri).withMimeType("video/mp4").build(),
            )
        testLoadingHeadline("video/*", files) { headlineRow ->
            verify(headlineGenerator, times(1)).getVideosHeadline(2)
            verifyPreviewHeadline(headlineRow, VIDEO_HEADLINE)
        }
    }

    @Test
    fun test_displayImagesAndVideosWithUriMetadataSetHeader_showImagesHeadline() {
        val uri = Uri.parse("content://pkg.app/image.png")
        val files =
            listOf(
                FileInfo.Builder(uri).withMimeType("image/png").build(),
                FileInfo.Builder(uri).withMimeType("video/mp4").build(),
            )
        testLoadingHeadline("*/*", files) { headlineRow ->
            verify(headlineGenerator, times(1)).getFilesHeadline(2)
            verifyPreviewHeadline(headlineRow, FILES_HEADLINE)
        }
    }

    @Test
    fun test_displayDocumentsWithUriMetadataSetHeader_showImagesHeadline() {
        val uri = Uri.parse("content://pkg.app/image.png")
        val files =
            listOf(
                FileInfo.Builder(uri).withMimeType("application/pdf").build(),
                FileInfo.Builder(uri).withMimeType("application/pdf").build(),
            )
        testLoadingHeadline("application/pdf", files) { headlineRow ->
            verify(headlineGenerator, times(1)).getFilesHeadline(2)
            verifyPreviewHeadline(headlineRow, FILES_HEADLINE)
        }
    }

    private fun testLoadingHeadline(
        intentMimeType: String,
        files: List<FileInfo>?,
        verificationBlock: (View?) -> Unit,
    ) {
        testScope.runTest {
            val endMarker = FileInfo.Builder(Uri.EMPTY).build()
            val emptySourceFlow = MutableSharedFlow<FileInfo>(replay = 1)
            val testSubject =
                UnifiedContentPreviewUi(
                    testScope,
                    /*isSingleImage=*/ false,
                    intentMimeType,
                    actionFactory,
                    imageLoader,
                    DefaultMimeTypeClassifier,
                    object : TransitionElementStatusCallback {
                        override fun onTransitionElementReady(name: String) = Unit
                        override fun onAllTransitionElementsReady() = Unit
                    },
                    files?.let { it.asFlow() } ?: emptySourceFlow.takeWhile { it !== endMarker },
                    /*itemCount=*/ 2,
                    headlineGenerator,
                    testMetadataText,
                )
            val layoutInflater = LayoutInflater.from(context)
            val gridLayout =
                layoutInflater.inflate(R.layout.chooser_grid_scrollable_preview, null, false)
                    as ViewGroup
            val headlineRow = gridLayout.requireViewById<View>(R.id.chooser_headline_row_container)

            assertWithMessage("Headline row should not be inflated by default")
                .that(headlineRow.findViewById<View>(R.id.headline))
                .isNull()

            testSubject.display(
                context.resources,
                LayoutInflater.from(context),
                gridLayout,
                headlineRow,
            )
            emptySourceFlow.tryEmit(endMarker)
            verificationBlock(headlineRow)
        }
    }

    private fun verifyTextViewText(
        viewParent: View?,
        @IdRes textViewResId: Int,
        expectedText: CharSequence,
    ) {
        assertThat(viewParent).isNotNull()
        val textView = viewParent?.findViewById<TextView>(textViewResId)
        assertThat(textView).isNotNull()
        assertThat(textView?.text).isEqualTo(expectedText)
    }

    private fun verifyPreviewHeadline(headerViewParent: View?, expectedText: String) {
        verifyTextViewText(headerViewParent, R.id.headline, expectedText)
    }

    private fun verifyPreviewMetadata(headerViewParent: View?, expectedText: CharSequence) {
        verifyTextViewText(headerViewParent, R.id.metadata, expectedText)
    }
}

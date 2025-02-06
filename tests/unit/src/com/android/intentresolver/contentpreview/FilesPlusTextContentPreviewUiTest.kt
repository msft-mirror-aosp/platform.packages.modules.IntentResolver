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
import android.widget.CheckBox
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.intentresolver.R
import com.android.intentresolver.widget.ActionRow
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.util.function.Consumer
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

private const val HEADLINE_IMAGES = "Image Headline"
private const val HEADLINE_VIDEOS = "Video Headline"
private const val HEADLINE_FILES = "Files Headline"
private const val SHARED_TEXT = "Some text to share"

@RunWith(AndroidJUnit4::class)
class FilesPlusTextContentPreviewUiTest {
    private val testScope = TestScope(EmptyCoroutineContext + UnconfinedTestDispatcher())
    private val actionFactory =
        object : ChooserContentPreviewUi.ActionFactory {
            override fun getEditButtonRunnable(): Runnable? = null

            override fun getCopyButtonRunnable(): Runnable? = null

            override fun createCustomActions(): List<ActionRow.Action> = emptyList()

            override fun getModifyShareAction(): ActionRow.Action? = null

            override fun getExcludeSharedTextAction(): Consumer<Boolean> = Consumer<Boolean> {}
        }
    private val imageLoader = mock<ImageLoader>()
    private val headlineGenerator =
        mock<HeadlineGenerator> {
            on { getImagesHeadline(any()) } doReturn HEADLINE_IMAGES
            on { getVideosHeadline(any()) } doReturn HEADLINE_VIDEOS
            on { getFilesHeadline(any()) } doReturn HEADLINE_FILES
        }
    private val testMetadataText: CharSequence = "Test metadata text"

    private val context
        get() = getInstrumentation().context

    @Test
    fun test_displayImagesPlusTextWithoutUriMetadataHeader_showImagesHeadline() {
        val sharedFileCount = 2
        val (previewView, headlineRow) = testLoadingHeadline("image/*", sharedFileCount)

        assertWithMessage("Preview parent should not be null").that(previewView).isNotNull()
        verify(headlineGenerator, times(1)).getImagesHeadline(sharedFileCount)
        verifyPreviewHeadline(headlineRow, HEADLINE_IMAGES)
        verifyPreviewMetadata(headlineRow, testMetadataText)
        verifySharedText(previewView)
    }

    @Test
    fun test_displayVideosPlusTextWithoutUriMetadataHeader_showVideosHeadline() {
        val sharedFileCount = 2
        val (previewView, headlineRow) = testLoadingHeadline("video/*", sharedFileCount)

        verify(headlineGenerator, times(1)).getVideosHeadline(sharedFileCount)
        assertWithMessage("Preview parent should not be null").that(previewView).isNotNull()
        verifyPreviewHeadline(headlineRow, HEADLINE_VIDEOS)
        verifyPreviewMetadata(headlineRow, testMetadataText)
        verifySharedText(previewView)
    }

    @Test
    fun test_displayDocsPlusTextWithoutUriMetadataHeader_showFilesHeadline() {
        val sharedFileCount = 2
        val (previewView, headlineRow) = testLoadingHeadline("application/pdf", sharedFileCount)

        verify(headlineGenerator, times(1)).getFilesHeadline(sharedFileCount)
        assertWithMessage("Preview parent should not be null").that(previewView).isNotNull()
        verifyPreviewHeadline(headlineRow, HEADLINE_FILES)
        verifyPreviewMetadata(headlineRow, testMetadataText)
        verifySharedText(previewView)
    }

    @Test
    fun test_displayMixedContentPlusTextWithoutUriMetadataHeader_showFilesHeadline() {
        val sharedFileCount = 2
        val (previewView, headlineRow) = testLoadingHeadline("*/*", sharedFileCount)

        verify(headlineGenerator, times(1)).getFilesHeadline(sharedFileCount)
        assertWithMessage("Preview parent should not be null").that(previewView).isNotNull()
        verifyPreviewHeadline(headlineRow, HEADLINE_FILES)
        verifyPreviewMetadata(headlineRow, testMetadataText)
        verifySharedText(previewView)
    }

    @Test
    fun test_displayImagesPlusTextWithUriMetadataSetHeader_showImagesHeadline() {
        val loadedFileMetadata = createFileInfosWithMimeTypes("image/png", "image/jpeg")
        val sharedFileCount = loadedFileMetadata.size
        val (previewView, headlineRow) =
            testLoadingHeadline("image/*", sharedFileCount, loadedFileMetadata)

        verify(headlineGenerator, times(1)).getImagesHeadline(sharedFileCount)
        assertWithMessage("Preview parent should not be null").that(previewView).isNotNull()
        verifyPreviewHeadline(headlineRow, HEADLINE_IMAGES)
        verifyPreviewMetadata(headlineRow, testMetadataText)
        verifySharedText(previewView)
    }

    @Test
    fun test_displayVideosPlusTextWithUriMetadataSetHeader_showVideosHeadline() {
        val loadedFileMetadata = createFileInfosWithMimeTypes("video/mp4", "video/mp4")
        val sharedFileCount = loadedFileMetadata.size
        val (previewView, headlineRow) =
            testLoadingHeadline("video/*", sharedFileCount, loadedFileMetadata)

        verify(headlineGenerator, times(1)).getVideosHeadline(sharedFileCount)
        assertWithMessage("Preview parent should not be null").that(previewView).isNotNull()
        verifyPreviewHeadline(headlineRow, HEADLINE_VIDEOS)
        verifyPreviewMetadata(headlineRow, testMetadataText)
        verifySharedText(previewView)
    }

    @Test
    fun test_displayImagesAndVideosPlusTextWithUriMetadataSetHeader_showFilesHeadline() {
        val loadedFileMetadata = createFileInfosWithMimeTypes("image/png", "video/mp4")
        val sharedFileCount = loadedFileMetadata.size
        val (previewView, headlineRow) =
            testLoadingHeadline("*/*", sharedFileCount, loadedFileMetadata)

        verify(headlineGenerator, times(1)).getFilesHeadline(sharedFileCount)
        assertWithMessage("Preview parent should not be null").that(previewView).isNotNull()
        verifyPreviewHeadline(headlineRow, HEADLINE_FILES)
        verifyPreviewMetadata(headlineRow, testMetadataText)
        verifySharedText(previewView)
    }

    @Test
    fun test_displayDocsPlusTextWithUriMetadataSetHeader_showFilesHeadline() {
        val loadedFileMetadata = createFileInfosWithMimeTypes("application/pdf", "application/pdf")
        val sharedFileCount = loadedFileMetadata.size
        val (previewView, headlineRow) =
            testLoadingHeadline("application/pdf", sharedFileCount, loadedFileMetadata)

        verify(headlineGenerator, times(1)).getFilesHeadline(sharedFileCount)
        assertWithMessage("Preview parent should not be null").that(previewView).isNotNull()
        verifyPreviewHeadline(headlineRow, HEADLINE_FILES)
        verifyPreviewMetadata(headlineRow, testMetadataText)
        verifySharedText(previewView)
    }

    @Test
    fun test_uriMetadataIsMoreSpecificThanIntentMimeType_headlineGetsUpdated() {
        val sharedFileCount = 2
        val testSubject =
            FilesPlusTextContentPreviewUi(
                testScope,
                /*isSingleImage=*/ false,
                sharedFileCount,
                SHARED_TEXT,
                /*intentMimeType=*/ "*/*",
                actionFactory,
                imageLoader,
                DefaultMimeTypeClassifier,
                headlineGenerator,
                testMetadataText,
                /* allowTextToggle=*/ false,
            )
        val layoutInflater = LayoutInflater.from(context)
        val gridLayout =
            layoutInflater.inflate(R.layout.chooser_grid_scrollable_preview, null, false)
                as ViewGroup
        val headlineRow = gridLayout.requireViewById<View>(R.id.chooser_headline_row_container)

        testSubject.display(
            context.resources,
            LayoutInflater.from(context),
            gridLayout,
            headlineRow,
        )

        verify(headlineGenerator, times(1)).getFilesHeadline(sharedFileCount)
        verify(headlineGenerator, never()).getImagesHeadline(sharedFileCount)
        verifyPreviewHeadline(headlineRow, HEADLINE_FILES)
        verifyPreviewMetadata(headlineRow, testMetadataText)

        testSubject.updatePreviewMetadata(createFileInfosWithMimeTypes("image/png", "image/jpg"))

        verify(headlineGenerator, times(1)).getFilesHeadline(sharedFileCount)
        verify(headlineGenerator, times(1)).getImagesHeadline(sharedFileCount)
        verifyPreviewHeadline(headlineRow, HEADLINE_IMAGES)
        verifyPreviewMetadata(headlineRow, testMetadataText)
    }

    @Test
    fun test_uriMetadataIsMoreSpecificThanIntentMimeTypeHeader_headlineGetsUpdated() {
        val sharedFileCount = 2
        val testSubject =
            FilesPlusTextContentPreviewUi(
                testScope,
                /*isSingleImage=*/ false,
                sharedFileCount,
                SHARED_TEXT,
                /*intentMimeType=*/ "*/*",
                actionFactory,
                imageLoader,
                DefaultMimeTypeClassifier,
                headlineGenerator,
                testMetadataText,
                /* allowTextToggle=*/ false,
            )
        val layoutInflater = LayoutInflater.from(context)
        val gridLayout =
            layoutInflater.inflate(R.layout.chooser_grid_scrollable_preview, null, false)
                as ViewGroup
        val headlineRow = gridLayout.requireViewById<View>(R.id.chooser_headline_row_container)

        assertWithMessage("Headline should not be inflated by default")
            .that(headlineRow.findViewById<View>(R.id.headline))
            .isNull()
        assertWithMessage("Metadata should not be inflated by default")
            .that(headlineRow.findViewById<View>(R.id.metadata))
            .isNull()

        val previewView =
            testSubject.display(
                context.resources,
                LayoutInflater.from(context),
                gridLayout,
                headlineRow,
            )

        verify(headlineGenerator, times(1)).getFilesHeadline(sharedFileCount)
        verify(headlineGenerator, never()).getImagesHeadline(sharedFileCount)
        assertWithMessage("Preview parent should not be null").that(previewView).isNotNull()
        verifyPreviewHeadline(headlineRow, HEADLINE_FILES)
        verifyPreviewMetadata(headlineRow, testMetadataText)

        testSubject.updatePreviewMetadata(createFileInfosWithMimeTypes("image/png", "image/jpg"))

        verify(headlineGenerator, times(1)).getFilesHeadline(sharedFileCount)
        verify(headlineGenerator, times(1)).getImagesHeadline(sharedFileCount)
        verifyPreviewHeadline(headlineRow, HEADLINE_IMAGES)
        verifyPreviewMetadata(headlineRow, testMetadataText)
    }

    @Test
    fun test_allowToggle() {
        val testSubject =
            FilesPlusTextContentPreviewUi(
                testScope,
                /*isSingleImage=*/ false,
                /* fileCount=*/ 1,
                SHARED_TEXT,
                /*intentMimeType=*/ "*/*",
                actionFactory,
                imageLoader,
                DefaultMimeTypeClassifier,
                headlineGenerator,
                testMetadataText,
                /* allowTextToggle=*/ true,
            )
        val layoutInflater = LayoutInflater.from(context)
        val gridLayout =
            layoutInflater.inflate(R.layout.chooser_grid_scrollable_preview, null, false)
                as ViewGroup
        val headlineRow = gridLayout.requireViewById<View>(R.id.chooser_headline_row_container)

        testSubject.display(
            context.resources,
            LayoutInflater.from(context),
            gridLayout,
            headlineRow,
        )

        val checkbox = headlineRow.requireViewById<CheckBox>(R.id.include_text_action)
        assertThat(checkbox.visibility).isEqualTo(View.VISIBLE)
        assertThat(checkbox.isChecked).isTrue()
    }

    @Test
    fun test_hideTextToggle() {
        val testSubject =
            FilesPlusTextContentPreviewUi(
                testScope,
                /*isSingleImage=*/ false,
                /* fileCount=*/ 1,
                SHARED_TEXT,
                /*intentMimeType=*/ "*/*",
                actionFactory,
                imageLoader,
                DefaultMimeTypeClassifier,
                headlineGenerator,
                testMetadataText,
                /* allowTextToggle=*/ false,
            )
        val layoutInflater = LayoutInflater.from(context)
        val gridLayout =
            layoutInflater.inflate(R.layout.chooser_grid_scrollable_preview, null, false)
                as ViewGroup
        val headlineRow = gridLayout.requireViewById<View>(R.id.chooser_headline_row_container)

        testSubject.display(
            context.resources,
            LayoutInflater.from(context),
            gridLayout,
            headlineRow,
        )

        val checkbox = headlineRow.requireViewById<CheckBox>(R.id.include_text_action)
        assertThat(checkbox.visibility).isNotEqualTo(View.VISIBLE)
    }

    private fun testLoadingHeadline(
        intentMimeType: String,
        sharedFileCount: Int,
        loadedFileMetadata: List<FileInfo>? = null,
    ): Pair<ViewGroup?, View> {
        val testSubject =
            FilesPlusTextContentPreviewUi(
                testScope,
                /*isSingleImage=*/ false,
                sharedFileCount,
                SHARED_TEXT,
                intentMimeType,
                actionFactory,
                imageLoader,
                DefaultMimeTypeClassifier,
                headlineGenerator,
                testMetadataText,
                /* allowTextToggle=*/ false,
            )
        val layoutInflater = LayoutInflater.from(context)
        val gridLayout =
            layoutInflater.inflate(R.layout.chooser_grid_scrollable_preview, null, false)
                as ViewGroup
        val headlineRow = gridLayout.requireViewById<View>(R.id.chooser_headline_row_container)

        assertWithMessage("Headline should not be inflated by default")
            .that(headlineRow.findViewById<View>(R.id.headline))
            .isNull()

        assertWithMessage("Metadata should not be inflated by default")
            .that(headlineRow.findViewById<View>(R.id.metadata))
            .isNull()

        loadedFileMetadata?.let(testSubject::updatePreviewMetadata)
        return testSubject.display(
            context.resources,
            LayoutInflater.from(context),
            gridLayout,
            headlineRow,
        ) to headlineRow
    }

    private fun createFileInfosWithMimeTypes(vararg mimeTypes: String): List<FileInfo> {
        val uri = Uri.parse("content://pkg.app/file")
        return mimeTypes.map { mimeType -> FileInfo.Builder(uri).withMimeType(mimeType).build() }
    }

    private fun verifyTextViewText(
        parentView: View?,
        @IdRes textViewResId: Int,
        expectedText: CharSequence,
    ) {
        assertThat(parentView).isNotNull()
        val textView = parentView?.findViewById<TextView>(textViewResId)
        assertThat(textView).isNotNull()
        assertThat(textView?.text).isEqualTo(expectedText)
    }

    private fun verifyPreviewHeadline(headerViewParent: View?, expectedText: String) {
        verifyTextViewText(headerViewParent, R.id.headline, expectedText)
    }

    private fun verifyPreviewMetadata(headerViewParent: View?, expectedText: CharSequence) {
        verifyTextViewText(headerViewParent, R.id.metadata, expectedText)
    }

    private fun verifySharedText(previewView: ViewGroup?) {
        verifyTextViewText(previewView, R.id.content_preview_text, SHARED_TEXT)
    }
}

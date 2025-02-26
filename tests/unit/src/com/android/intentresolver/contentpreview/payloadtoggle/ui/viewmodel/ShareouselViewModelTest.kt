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

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.android.intentresolver.contentpreview.payloadtoggle.ui.viewmodel

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.net.Uri
import com.android.intentresolver.FakeImageLoader
import com.android.intentresolver.contentpreview.HeadlineGenerator
import com.android.intentresolver.contentpreview.mimetypeClassifier
import com.android.intentresolver.contentpreview.payloadtoggle.data.model.CustomActionModel
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.activityResultRepository
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.cursorPreviewsRepository
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.previewSelectionsRepository
import com.android.intentresolver.contentpreview.payloadtoggle.domain.intent.PendingIntentSender
import com.android.intentresolver.contentpreview.payloadtoggle.domain.intent.TargetIntentModifier
import com.android.intentresolver.contentpreview.payloadtoggle.domain.intent.pendingIntentSender
import com.android.intentresolver.contentpreview.payloadtoggle.domain.intent.targetIntentModifier
import com.android.intentresolver.contentpreview.payloadtoggle.domain.interactor.chooserRequestInteractor
import com.android.intentresolver.contentpreview.payloadtoggle.domain.interactor.customActionsInteractor
import com.android.intentresolver.contentpreview.payloadtoggle.domain.interactor.headlineGenerator
import com.android.intentresolver.contentpreview.payloadtoggle.domain.interactor.payloadToggleImageLoader
import com.android.intentresolver.contentpreview.payloadtoggle.domain.interactor.selectablePreviewsInteractor
import com.android.intentresolver.contentpreview.payloadtoggle.domain.interactor.selectionInteractor
import com.android.intentresolver.contentpreview.payloadtoggle.domain.model.ValueUpdate
import com.android.intentresolver.contentpreview.payloadtoggle.shared.ContentType
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewKey
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewModel
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewsModel
import com.android.intentresolver.data.model.ChooserRequest
import com.android.intentresolver.data.repository.chooserRequestRepository
import com.android.intentresolver.icon.BitmapIcon
import com.android.intentresolver.logging.FakeEventLog
import com.android.intentresolver.logging.eventLog
import com.android.intentresolver.util.KosmosTestScope
import com.android.intentresolver.util.comparingElementsUsingTransform
import com.android.intentresolver.util.runKosmosTest
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import org.junit.Test

class ShareouselViewModelTest {

    private var Kosmos.viewModelScope: CoroutineScope by Fixture()
    private val Kosmos.shareouselViewModel: ShareouselViewModel by Fixture {
        ShareouselViewModelModule.create(
            interactor = selectablePreviewsInteractor,
            imageLoader = payloadToggleImageLoader,
            actionsInteractor = customActionsInteractor,
            headlineGenerator = headlineGenerator,
            chooserRequestInteractor = chooserRequestInteractor,
            mimeTypeClassifier = mimetypeClassifier,
            selectionInteractor = selectionInteractor,
            scope = viewModelScope,
        )
    }
    private val previewHeight = 500

    @Test
    fun headline_images() = runTest {
        assertThat(shareouselViewModel.headline.first()).isEqualTo("FILES: 1")
        previewSelectionsRepository.selections.value =
            listOf(
                    PreviewModel(
                        key = PreviewKey.final(0),
                        uri = Uri.fromParts("scheme", "ssp", "fragment"),
                        mimeType = "image/png",
                        order = 0,
                    ),
                    PreviewModel(
                        key = PreviewKey.final(1),
                        uri = Uri.fromParts("scheme1", "ssp1", "fragment1"),
                        mimeType = "image/jpeg",
                        order = 1,
                    ),
                )
                .associateBy { it.uri }
        runCurrent()
        assertThat(shareouselViewModel.headline.first()).isEqualTo("IMAGES: 2")
    }

    @Test
    fun headline_videos() = runTest {
        previewSelectionsRepository.selections.value =
            listOf(
                    PreviewModel(
                        key = PreviewKey.final(0),
                        uri = Uri.fromParts("scheme", "ssp", "fragment"),
                        mimeType = "video/mpeg",
                        order = 0,
                    ),
                    PreviewModel(
                        key = PreviewKey.final(1),
                        uri = Uri.fromParts("scheme1", "ssp1", "fragment1"),
                        mimeType = "video/mpeg",
                        order = 1,
                    ),
                )
                .associateBy { it.uri }
        runCurrent()
        assertThat(shareouselViewModel.headline.first()).isEqualTo("VIDEOS: 2")
    }

    @Test
    fun headline_mixed() = runTest {
        previewSelectionsRepository.selections.value =
            listOf(
                    PreviewModel(
                        key = PreviewKey.final(0),
                        uri = Uri.fromParts("scheme", "ssp", "fragment"),
                        mimeType = "image/jpeg",
                        order = 0,
                    ),
                    PreviewModel(
                        key = PreviewKey.final(1),
                        uri = Uri.fromParts("scheme1", "ssp1", "fragment1"),
                        mimeType = "video/mpeg",
                        order = 1,
                    ),
                )
                .associateBy { it.uri }
        runCurrent()
        assertThat(shareouselViewModel.headline.first()).isEqualTo("FILES: 2")
    }

    @Test
    fun metadataText() = runTest {
        val request =
            ChooserRequest(
                targetIntent = Intent(),
                launchedFromPackage = "",
                metadataText = "Hello",
            )
        chooserRequestRepository.chooserRequest.value = request

        runCurrent()

        assertThat(shareouselViewModel.metadataText.first()).isEqualTo("Hello")
    }

    @Test
    fun previews() =
        runTest(targetIntentModifier = { Intent() }) {
            cursorPreviewsRepository.previewsModel.value =
                PreviewsModel(
                    previewModels =
                        listOf(
                            PreviewModel(
                                key = PreviewKey.final(0),
                                uri = Uri.fromParts("scheme", "ssp", "fragment"),
                                mimeType = "image/png",
                                order = 0,
                            ),
                            PreviewModel(
                                key = PreviewKey.final(1),
                                uri = Uri.fromParts("scheme1", "ssp1", "fragment1"),
                                mimeType = "video/mpeg",
                                order = 1,
                            ),
                        ),
                    startIdx = 1,
                    loadMoreLeft = null,
                    loadMoreRight = null,
                    leftTriggerIndex = 0,
                    rightTriggerIndex = 1,
                )
            runCurrent()

            assertWithMessage("previewsKeys is null")
                .that(shareouselViewModel.previews.first())
                .isNotNull()
            assertThat(shareouselViewModel.previews.first()!!.previewModels)
                .comparingElementsUsingTransform("has uri of") { it: PreviewModel -> it.uri }
                .containsExactly(
                    Uri.fromParts("scheme", "ssp", "fragment"),
                    Uri.fromParts("scheme1", "ssp1", "fragment1"),
                )
                .inOrder()

            val previewVm =
                shareouselViewModel.preview.invoke(
                    PreviewModel(
                        key = PreviewKey.final(1),
                        uri = Uri.fromParts("scheme1", "ssp1", "fragment1"),
                        mimeType = "video/mpeg",
                        order = 0,
                    ),
                    previewHeight,
                    /* index = */ 1,
                    viewModelScope,
                )

            runCurrent()

            assertWithMessage("preview bitmap is null")
                .that((previewVm.bitmapLoadState.first() as ValueUpdate.Value).value)
                .isNotNull()
            assertThat(previewVm.isSelected.first()).isFalse()
            assertThat(previewVm.contentType).isEqualTo(ContentType.Video)

            previewVm.setSelected(true)

            assertThat(previewSelectionsRepository.selections.value.keys)
                .contains(Uri.fromParts("scheme1", "ssp1", "fragment1"))
        }

    @Test
    fun previews_wontLoad() =
        runTest(targetIntentModifier = { Intent() }) {
            cursorPreviewsRepository.previewsModel.value =
                PreviewsModel(
                    previewModels =
                        listOf(
                            PreviewModel(
                                key = PreviewKey.final(0),
                                uri = Uri.fromParts("scheme", "ssp", "fragment"),
                                mimeType = "image/png",
                                order = 0,
                            ),
                            PreviewModel(
                                key = PreviewKey.final(1),
                                uri = Uri.fromParts("scheme1", "ssp1", "fragment1"),
                                mimeType = "video/mpeg",
                                order = 1,
                            ),
                        ),
                    startIdx = 1,
                    loadMoreLeft = null,
                    loadMoreRight = null,
                    leftTriggerIndex = 0,
                    rightTriggerIndex = 1,
                )
            runCurrent()

            val previewVm =
                shareouselViewModel.preview.invoke(
                    PreviewModel(
                        key = PreviewKey.final(0),
                        uri = Uri.fromParts("scheme", "ssp", "fragment"),
                        mimeType = "video/mpeg",
                        order = 1,
                    ),
                    previewHeight,
                    /* index = */ 1,
                    viewModelScope,
                )

            runCurrent()

            assertWithMessage("preview bitmap is not null")
                .that((previewVm.bitmapLoadState.first() as ValueUpdate.Value).value)
                .isNull()
        }

    @Test
    fun actions() {
        runTest {
            assertThat(shareouselViewModel.actions.first()).isEmpty()

            val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ALPHA_8)
            val icon = Icon.createWithBitmap(bitmap)
            var actionSent = false
            chooserRequestRepository.customActions.value =
                listOf(
                    CustomActionModel(
                        label = "label1",
                        icon = icon,
                        performAction = { actionSent = true },
                    )
                )
            runCurrent()

            assertThat(shareouselViewModel.actions.first())
                .comparingElementsUsingTransform("has a label of") { vm: ActionChipViewModel ->
                    vm.label
                }
                .containsExactly("label1")
                .inOrder()
            assertThat(shareouselViewModel.actions.first())
                .comparingElementsUsingTransform("has an icon of") { vm: ActionChipViewModel ->
                    vm.icon
                }
                .containsExactly(BitmapIcon(icon.bitmap))
                .inOrder()

            shareouselViewModel.actions.first()[0].onClicked()

            assertThat(actionSent).isTrue()
            assertThat(eventLog.customActionSelected)
                .isEqualTo(FakeEventLog.CustomActionSelected(0))
            assertThat(activityResultRepository.activityResult.value).isEqualTo(Activity.RESULT_OK)
        }
    }

    private fun runTest(
        pendingIntentSender: PendingIntentSender = PendingIntentSender {},
        targetIntentModifier: TargetIntentModifier<PreviewModel> = TargetIntentModifier {
            error("unexpected invocation")
        },
        block: suspend KosmosTestScope.() -> Unit,
    ): Unit = runKosmosTest {
        viewModelScope = backgroundScope
        this.pendingIntentSender = pendingIntentSender
        this.targetIntentModifier = targetIntentModifier
        previewSelectionsRepository.selections.value =
            PreviewModel(
                    key = PreviewKey.final(1),
                    uri = Uri.fromParts("scheme", "ssp", "fragment"),
                    mimeType = null,
                    order = 0,
                )
                .let { mapOf(it.uri to it) }
        payloadToggleImageLoader =
            FakeImageLoader(
                initialBitmaps =
                    mapOf(
                        Uri.fromParts("scheme1", "ssp1", "fragment1") to
                            Bitmap.createBitmap(100, 100, Bitmap.Config.ALPHA_8)
                    )
            )
        headlineGenerator =
            object : HeadlineGenerator {
                override fun getImagesHeadline(count: Int): String = "IMAGES: $count"

                override fun getTextHeadline(text: CharSequence): String = error("not supported")

                override fun getAlbumHeadline(): String = error("not supported")

                override fun getImagesWithTextHeadline(text: CharSequence, count: Int): String =
                    error("not supported")

                override fun getVideosWithTextHeadline(text: CharSequence, count: Int): String =
                    error("not supported")

                override fun getFilesWithTextHeadline(text: CharSequence, count: Int): String =
                    error("not supported")

                override fun getVideosHeadline(count: Int): String = "VIDEOS: $count"

                override fun getFilesHeadline(count: Int): String = "FILES: $count"

                override fun getNotItemsSelectedHeadline() = "Select items to share"

                override fun getCopyButtonContentDescription(sharedText: CharSequence): String =
                    "Copy"
            }
        // instantiate the view model, and then runCurrent() so that it is fully hydrated before
        // starting the test
        shareouselViewModel
        runCurrent()
        block()
    }
}

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
            selectionInteractor = selectionInteractor,
            scope = viewModelScope,
        )
    }

    @Test
    fun headline() = runTest {
        assertThat(shareouselViewModel.headline.first()).isEqualTo("IMAGES: 1")
        previewSelectionsRepository.selections.value =
            setOf(
                PreviewModel(
                    Uri.fromParts("scheme", "ssp", "fragment"),
                    null,
                ),
                PreviewModel(
                    Uri.fromParts("scheme1", "ssp1", "fragment1"),
                    null,
                )
            )
        runCurrent()
        assertThat(shareouselViewModel.headline.first()).isEqualTo("IMAGES: 2")
    }

    @Test
    fun metadataText() = runTest {
        val request =
            ChooserRequest(
                targetIntent = Intent(),
                launchedFromPackage = "",
                metadataText = "Hello"
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
                        setOf(
                            PreviewModel(
                                Uri.fromParts("scheme", "ssp", "fragment"),
                                null,
                            ),
                            PreviewModel(
                                Uri.fromParts("scheme1", "ssp1", "fragment1"),
                                null,
                            )
                        ),
                    startIdx = 1,
                    loadMoreLeft = null,
                    loadMoreRight = null,
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
                shareouselViewModel.preview(
                    PreviewModel(Uri.fromParts("scheme1", "ssp1", "fragment1"), null)
                )

            assertWithMessage("preview bitmap is null").that(previewVm.bitmap.first()).isNotNull()
            assertThat(previewVm.isSelected.first()).isFalse()

            previewVm.setSelected(true)

            assertThat(previewSelectionsRepository.selections.value)
                .comparingElementsUsingTransform("has uri of") { model: PreviewModel -> model.uri }
                .contains(Uri.fromParts("scheme1", "ssp1", "fragment1"))
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
            setOf(PreviewModel(Uri.fromParts("scheme", "ssp", "fragment"), null))
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

                override fun getVideosHeadline(count: Int): String = error("not supported")

                override fun getFilesHeadline(count: Int): String = error("not supported")
            }
        // instantiate the view model, and then runCurrent() so that it is fully hydrated before
        // starting the test
        shareouselViewModel
        runCurrent()
        block()
    }
}

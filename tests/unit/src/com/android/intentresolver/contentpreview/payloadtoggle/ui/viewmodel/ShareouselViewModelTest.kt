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
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.net.Uri
import com.android.intentresolver.FakeImageLoader
import com.android.intentresolver.contentpreview.HeadlineGenerator
import com.android.intentresolver.contentpreview.payloadtoggle.data.model.CustomActionModel
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.ActivityResultRepository
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.CursorPreviewsRepository
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.PendingSelectionCallbackRepository
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.PreviewSelectionsRepository
import com.android.intentresolver.contentpreview.payloadtoggle.domain.intent.PendingIntentSender
import com.android.intentresolver.contentpreview.payloadtoggle.domain.intent.TargetIntentModifier
import com.android.intentresolver.contentpreview.payloadtoggle.domain.interactor.ChooserRequestInteractor
import com.android.intentresolver.contentpreview.payloadtoggle.domain.interactor.CustomActionsInteractor
import com.android.intentresolver.contentpreview.payloadtoggle.domain.interactor.SelectablePreviewsInteractor
import com.android.intentresolver.contentpreview.payloadtoggle.domain.interactor.SelectionInteractor
import com.android.intentresolver.contentpreview.payloadtoggle.domain.interactor.UpdateChooserRequestInteractor
import com.android.intentresolver.contentpreview.payloadtoggle.domain.interactor.UpdateTargetIntentInteractor
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewModel
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewsModel
import com.android.intentresolver.icon.BitmapIcon
import com.android.intentresolver.logging.FakeEventLog
import com.android.intentresolver.mock
import com.android.intentresolver.util.comparingElementsUsingTransform
import com.android.intentresolver.v2.data.model.fakeChooserRequest
import com.android.intentresolver.v2.data.repository.ChooserRequestRepository
import com.android.internal.logging.InstanceId
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ShareouselViewModelTest {

    class Dependencies(
        val pendingIntentSender: PendingIntentSender,
        val targetIntentModifier: TargetIntentModifier<PreviewModel>,
    ) {
        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)
        val previewsRepository = CursorPreviewsRepository()
        val selectionRepository =
            PreviewSelectionsRepository().apply {
                selections.value =
                    setOf(PreviewModel(Uri.fromParts("scheme", "ssp", "fragment"), null))
            }
        val activityResultRepository = ActivityResultRepository()
        val contentResolver = mock<ContentResolver> {}
        val packageManager = mock<PackageManager> {}
        val eventLog = FakeEventLog(instanceId = InstanceId.fakeInstanceId(1))
        val chooserRequestRepo =
            ChooserRequestRepository(
                initialRequest = fakeChooserRequest(),
                initialActions = emptyList(),
            )
        val pendingSelectionCallbackRepo = PendingSelectionCallbackRepository()

        val actionsInteractor
            get() =
                CustomActionsInteractor(
                    activityResultRepo = activityResultRepository,
                    bgDispatcher = testDispatcher,
                    contentResolver = contentResolver,
                    eventLog = eventLog,
                    packageManager = packageManager,
                    chooserRequestInteractor = chooserRequestInteractor,
                )

        val selectionInteractor
            get() =
                SelectionInteractor(
                    selectionsRepo = selectionRepository,
                    targetIntentModifier = targetIntentModifier,
                    updateTargetIntentInteractor = updateTargetIntentInteractor,
                )

        val updateTargetIntentInteractor
            get() =
                UpdateTargetIntentInteractor(
                    repository = pendingSelectionCallbackRepo,
                    chooserRequestInteractor = updateChooserRequestInteractor,
                )

        val updateChooserRequestInteractor
            get() =
                UpdateChooserRequestInteractor(
                    repository = chooserRequestRepo,
                    pendingIntentSender = pendingIntentSender,
                )

        val chooserRequestInteractor
            get() = ChooserRequestInteractor(repository = chooserRequestRepo)

        val previewsInteractor
            get() =
                SelectablePreviewsInteractor(
                    previewsRepo = previewsRepository,
                    selectionInteractor = selectionInteractor,
                )

        val underTest =
            ShareouselViewModelModule.create(
                interactor = previewsInteractor,
                imageLoader =
                    FakeImageLoader(
                        initialBitmaps =
                            mapOf(
                                Uri.fromParts("scheme1", "ssp1", "fragment1") to
                                    Bitmap.createBitmap(100, 100, Bitmap.Config.ALPHA_8)
                            )
                    ),
                actionsInteractor = actionsInteractor,
                headlineGenerator =
                    object : HeadlineGenerator {
                        override fun getImagesHeadline(count: Int): String = "IMAGES: $count"

                        override fun getTextHeadline(text: CharSequence): String =
                            error("not supported")

                        override fun getAlbumHeadline(): String = error("not supported")

                        override fun getImagesWithTextHeadline(
                            text: CharSequence,
                            count: Int
                        ): String = error("not supported")

                        override fun getVideosWithTextHeadline(
                            text: CharSequence,
                            count: Int
                        ): String = error("not supported")

                        override fun getFilesWithTextHeadline(
                            text: CharSequence,
                            count: Int
                        ): String = error("not supported")

                        override fun getVideosHeadline(count: Int): String = error("not supported")

                        override fun getFilesHeadline(count: Int): String = error("not supported")
                    },
                selectionInteractor = selectionInteractor,
                scope = testScope.backgroundScope,
            )
    }

    private inline fun runTestWithDeps(
        pendingIntentSender: PendingIntentSender = PendingIntentSender {},
        targetIntentModifier: TargetIntentModifier<PreviewModel> = TargetIntentModifier {
            error("unexpected invocation")
        },
        crossinline block: suspend TestScope.(Dependencies) -> Unit,
    ): Unit =
        Dependencies(pendingIntentSender, targetIntentModifier).run {
            testScope.runTest {
                runCurrent()
                block(this@run)
            }
        }

    @Test
    fun headline() = runTestWithDeps { deps ->
        with(deps) {
            assertThat(underTest.headline.first()).isEqualTo("IMAGES: 1")
            selectionRepository.selections.value =
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
            assertThat(underTest.headline.first()).isEqualTo("IMAGES: 2")
        }
    }

    @Test
    fun previews() =
        runTestWithDeps(targetIntentModifier = { Intent() }) { deps ->
            with(deps) {
                previewsRepository.previewsModel.value =
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
                    .that(underTest.previews.first())
                    .isNotNull()
                assertThat(underTest.previews.first()!!.previewModels)
                    .comparingElementsUsingTransform("has uri of") { it: PreviewModel -> it.uri }
                    .containsExactly(
                        Uri.fromParts("scheme", "ssp", "fragment"),
                        Uri.fromParts("scheme1", "ssp1", "fragment1"),
                    )
                    .inOrder()

                val previewVm =
                    underTest.preview(
                        PreviewModel(Uri.fromParts("scheme1", "ssp1", "fragment1"), null)
                    )

                assertWithMessage("preview bitmap is null")
                    .that(previewVm.bitmap.first())
                    .isNotNull()
                assertThat(previewVm.isSelected.first()).isFalse()

                previewVm.setSelected(true)

                assertThat(selectionRepository.selections.value)
                    .comparingElementsUsingTransform("has uri of") { model: PreviewModel ->
                        model.uri
                    }
                    .contains(Uri.fromParts("scheme1", "ssp1", "fragment1"))
            }
        }

    @Test
    fun actions() {
        runTestWithDeps { deps ->
            with(deps) {
                assertThat(underTest.actions.first()).isEmpty()

                val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ALPHA_8)
                val icon = Icon.createWithBitmap(bitmap)
                var actionSent = false
                chooserRequestRepo.customActions.value =
                    listOf(
                        CustomActionModel(
                            label = "label1",
                            icon = icon,
                            performAction = { actionSent = true },
                        )
                    )
                runCurrent()

                assertThat(underTest.actions.first())
                    .comparingElementsUsingTransform("has a label of") { vm: ActionChipViewModel ->
                        vm.label
                    }
                    .containsExactly("label1")
                    .inOrder()
                assertThat(underTest.actions.first())
                    .comparingElementsUsingTransform("has an icon of") { vm: ActionChipViewModel ->
                        vm.icon
                    }
                    .containsExactly(BitmapIcon(icon.bitmap))
                    .inOrder()

                underTest.actions.first()[0].onClicked()

                assertThat(actionSent).isTrue()
                assertThat(eventLog.customActionSelected)
                    .isEqualTo(FakeEventLog.CustomActionSelected(0))
                assertThat(activityResultRepository.activityResult.value)
                    .isEqualTo(Activity.RESULT_OK)
            }
        }
    }
}

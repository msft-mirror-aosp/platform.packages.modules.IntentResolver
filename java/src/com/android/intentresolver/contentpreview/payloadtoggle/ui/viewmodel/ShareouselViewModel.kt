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
package com.android.intentresolver.contentpreview.payloadtoggle.ui.viewmodel

import com.android.intentresolver.contentpreview.CachingImagePreviewImageLoader
import com.android.intentresolver.contentpreview.HeadlineGenerator
import com.android.intentresolver.contentpreview.ImageLoader
import com.android.intentresolver.contentpreview.MimeTypeClassifier
import com.android.intentresolver.contentpreview.payloadtoggle.domain.cursor.PayloadToggle
import com.android.intentresolver.contentpreview.payloadtoggle.domain.interactor.ChooserRequestInteractor
import com.android.intentresolver.contentpreview.payloadtoggle.domain.interactor.CustomActionsInteractor
import com.android.intentresolver.contentpreview.payloadtoggle.domain.interactor.SelectablePreviewsInteractor
import com.android.intentresolver.contentpreview.payloadtoggle.domain.interactor.SelectionInteractor
import com.android.intentresolver.contentpreview.payloadtoggle.shared.ContentType
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewModel
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewsModel
import com.android.intentresolver.inject.ViewModelOwned
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.zip

/** A dynamic carousel of selectable previews within share sheet. */
data class ShareouselViewModel(
    /** Text displayed at the top of the share sheet when Shareousel is present. */
    val headline: Flow<String>,
    /** App-provided text shown beneath the headline. */
    val metadataText: Flow<CharSequence?>,
    /**
     * Previews which are available for presentation within Shareousel. Use [preview] to create a
     * [ShareouselPreviewViewModel] for a given [PreviewModel].
     */
    val previews: Flow<PreviewsModel?>,
    /** List of action chips presented underneath Shareousel. */
    val actions: Flow<List<ActionChipViewModel>>,
    /** Creates a [ShareouselPreviewViewModel] for a [PreviewModel] present in [previews]. */
    val preview: (key: PreviewModel) -> ShareouselPreviewViewModel,
)

@Module
@InstallIn(ViewModelComponent::class)
interface ShareouselViewModelModule {

    @Binds @PayloadToggle fun imageLoader(imageLoader: CachingImagePreviewImageLoader): ImageLoader

    companion object {
        @Provides
        fun create(
            interactor: SelectablePreviewsInteractor,
            @PayloadToggle imageLoader: ImageLoader,
            actionsInteractor: CustomActionsInteractor,
            headlineGenerator: HeadlineGenerator,
            selectionInteractor: SelectionInteractor,
            chooserRequestInteractor: ChooserRequestInteractor,
            mimeTypeClassifier: MimeTypeClassifier,
            // TODO: remove if possible
            @ViewModelOwned scope: CoroutineScope,
        ): ShareouselViewModel {
            val keySet =
                interactor.previews.stateIn(
                    scope,
                    SharingStarted.Eagerly,
                    initialValue = null,
                )
            return ShareouselViewModel(
                headline =
                    selectionInteractor.aggregateContentType.zip(
                        selectionInteractor.amountSelected
                    ) { contentType, numItems ->
                        when (contentType) {
                            ContentType.Other -> headlineGenerator.getFilesHeadline(numItems)
                            ContentType.Image -> headlineGenerator.getImagesHeadline(numItems)
                            ContentType.Video -> headlineGenerator.getVideosHeadline(numItems)
                        }
                    },
                metadataText = chooserRequestInteractor.metadataText,
                previews = keySet,
                actions =
                    actionsInteractor.customActions.map { actions ->
                        actions.mapIndexedNotNull { i, model ->
                            val icon = model.icon
                            val label = model.label
                            if (icon == null && label.isBlank()) {
                                null
                            } else {
                                ActionChipViewModel(
                                    label = label.toString(),
                                    icon = model.icon,
                                    onClicked = { model.performAction(i) },
                                )
                            }
                        }
                    },
                preview = { key ->
                    keySet.value?.maybeLoad(key)
                    val previewInteractor = interactor.preview(key)
                    val contentType =
                        when {
                            mimeTypeClassifier.isImageType(key.mimeType) -> ContentType.Image
                            mimeTypeClassifier.isVideoType(key.mimeType) -> ContentType.Video
                            else -> ContentType.Other
                        }
                    ShareouselPreviewViewModel(
                        bitmap = flow { emit(key.previewUri?.let { imageLoader(it) }) },
                        contentType = contentType,
                        isSelected = previewInteractor.isSelected,
                        setSelected = previewInteractor::setSelected,
                        aspectRatio = key.aspectRatio,
                    )
                },
            )
        }
    }
}

private fun PreviewsModel.maybeLoad(key: PreviewModel) {
    when (key) {
        previewModels.firstOrNull() -> loadMoreLeft?.invoke()
        previewModels.lastOrNull() -> loadMoreRight?.invoke()
    }
}

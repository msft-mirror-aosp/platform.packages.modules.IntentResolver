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

import android.util.Size
import com.android.intentresolver.Flags
import com.android.intentresolver.Flags.unselectFinalItem
import com.android.intentresolver.contentpreview.CachingImagePreviewImageLoader
import com.android.intentresolver.contentpreview.HeadlineGenerator
import com.android.intentresolver.contentpreview.ImageLoader
import com.android.intentresolver.contentpreview.MimeTypeClassifier
import com.android.intentresolver.contentpreview.PreviewImageLoader
import com.android.intentresolver.contentpreview.payloadtoggle.domain.cursor.PayloadToggle
import com.android.intentresolver.contentpreview.payloadtoggle.domain.interactor.ChooserRequestInteractor
import com.android.intentresolver.contentpreview.payloadtoggle.domain.interactor.CustomActionsInteractor
import com.android.intentresolver.contentpreview.payloadtoggle.domain.interactor.SelectablePreviewsInteractor
import com.android.intentresolver.contentpreview.payloadtoggle.domain.interactor.SelectionInteractor
import com.android.intentresolver.contentpreview.payloadtoggle.domain.model.ValueUpdate
import com.android.intentresolver.contentpreview.payloadtoggle.shared.ContentType
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewModel
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewsModel
import com.android.intentresolver.inject.ViewModelOwned
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import javax.inject.Provider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
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
    /** Indicates whether there are any selected items */
    val hasSelectedItems: Flow<Boolean>,
    /** Creates a [ShareouselPreviewViewModel] for a [PreviewModel] present in [previews]. */
    val preview:
        (
            key: PreviewModel, previewHeight: Int, index: Int?, scope: CoroutineScope
        ) -> ShareouselPreviewViewModel,
)

@Module
@InstallIn(ViewModelComponent::class)
object ShareouselViewModelModule {

    @Provides
    @PayloadToggle
    fun imageLoader(
        cachingImageLoader: Provider<CachingImagePreviewImageLoader>,
        previewImageLoader: Provider<PreviewImageLoader>
    ): ImageLoader =
        if (Flags.previewImageLoader()) {
            previewImageLoader.get()
        } else {
            cachingImageLoader.get()
        }

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
                selectionInteractor.aggregateContentType.zip(selectionInteractor.amountSelected) {
                    contentType,
                    numItems ->
                    if (unselectFinalItem() && numItems == 0) {
                        headlineGenerator.getNotItemsSelectedHeadline()
                    } else {
                        when (contentType) {
                            ContentType.Other -> headlineGenerator.getFilesHeadline(numItems)
                            ContentType.Image -> headlineGenerator.getImagesHeadline(numItems)
                            ContentType.Video -> headlineGenerator.getVideosHeadline(numItems)
                        }
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
            hasSelectedItems =
                selectionInteractor.selections.map { it.isNotEmpty() }.distinctUntilChanged(),
            preview = { key, previewHeight, index, previewScope ->
                keySet.value?.maybeLoad(index)
                val previewInteractor = interactor.preview(key)
                val contentType =
                    when {
                        mimeTypeClassifier.isImageType(key.mimeType) -> ContentType.Image
                        mimeTypeClassifier.isVideoType(key.mimeType) -> ContentType.Video
                        else -> ContentType.Other
                    }
                val initialBitmapValue =
                    key.previewUri?.let {
                        imageLoader.getCachedBitmap(it)?.let { ValueUpdate.Value(it) }
                    } ?: ValueUpdate.Absent
                ShareouselPreviewViewModel(
                    bitmapLoadState =
                        flow {
                                val previewWidth =
                                    if (key.aspectRatio > 0) {
                                            previewHeight.toFloat() / key.aspectRatio
                                        } else {
                                            previewHeight
                                        }
                                        .toInt()
                                emit(
                                    key.previewUri?.let {
                                        ValueUpdate.Value(
                                            imageLoader(it, Size(previewWidth, previewHeight))
                                        )
                                    } ?: ValueUpdate.Absent
                                )
                            }
                            .stateIn(previewScope, SharingStarted.Eagerly, initialBitmapValue),
                    contentType = contentType,
                    isSelected = previewInteractor.isSelected,
                    setSelected = previewInteractor::setSelected,
                    aspectRatio = key.aspectRatio,
                )
            },
        )
    }
}

private fun PreviewsModel.maybeLoad(index: Int?) {
    when {
        index == null -> {}
        index <= leftTriggerIndex -> loadMoreLeft?.invoke()
        index >= rightTriggerIndex -> loadMoreRight?.invoke()
    }
}

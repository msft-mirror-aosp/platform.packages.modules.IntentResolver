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
package com.android.intentresolver.contentpreview.payloadtoggle.ui.composable

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.intentresolver.R
import com.android.intentresolver.contentpreview.payloadtoggle.domain.model.ValueUpdate
import com.android.intentresolver.contentpreview.payloadtoggle.domain.model.getOrDefault
import com.android.intentresolver.contentpreview.payloadtoggle.shared.ContentType
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewsModel
import com.android.intentresolver.contentpreview.payloadtoggle.ui.viewmodel.ShareouselPreviewViewModel
import com.android.intentresolver.contentpreview.payloadtoggle.ui.viewmodel.ShareouselViewModel
import kotlin.math.abs
import kotlinx.coroutines.launch

@Composable
fun Shareousel(viewModel: ShareouselViewModel) {
    val keySet = viewModel.previews.collectAsStateWithLifecycle(null).value
    if (keySet != null) {
        Shareousel(viewModel, keySet)
    } else {
        Spacer(
            Modifier.height(dimensionResource(R.dimen.chooser_preview_image_height_tall) + 64.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer)
        )
    }
}

@Composable
private fun Shareousel(viewModel: ShareouselViewModel, keySet: PreviewsModel) {
    Column(
        modifier =
            Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(vertical = 16.dp),
    ) {
        PreviewCarousel(keySet, viewModel)
        ActionCarousel(viewModel)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PreviewCarousel(
    previews: PreviewsModel,
    viewModel: ShareouselViewModel,
) {
    val centerIdx = previews.startIdx
    val carouselState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = centerIdx,
            prefetchStrategy = remember { ShareouselLazyListPrefetchStrategy() }
        )
    // TODO: start item needs to be centered, check out ScalingLazyColumn impl or see if
    //  HorizontalPager works for our use-case
    LazyRow(
        state = carouselState,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp),
        modifier =
            Modifier.fillMaxWidth()
                .height(dimensionResource(R.dimen.chooser_preview_image_height_tall))
                .systemGestureExclusion()
    ) {
        itemsIndexed(previews.previewModels, key = { _, model -> model.uri }) { index, model ->

            // Index if this is the element in the center of the viewing area, otherwise null
            val previewIndex by remember {
                derivedStateOf {
                    carouselState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.index == index }
                        ?.let {
                            val viewportCenter = carouselState.layoutInfo.viewportEndOffset / 2
                            val halfPreviewWidth = it.size / 2
                            val previewCenter = it.offset + halfPreviewWidth
                            val previewDistanceToViewportCenter =
                                abs(previewCenter - viewportCenter)
                            if (previewDistanceToViewportCenter <= halfPreviewWidth) index else null
                        }
                }
            }

            ShareouselCard(viewModel.preview(model, previewIndex, rememberCoroutineScope()))
        }
    }
}

@Composable
private fun ShareouselCard(viewModel: ShareouselPreviewViewModel) {
    val bitmapLoadState by viewModel.bitmapLoadState.collectAsStateWithLifecycle()
    val selected by viewModel.isSelected.collectAsStateWithLifecycle(initialValue = false)
    val borderColor = MaterialTheme.colorScheme.primary
    val scope = rememberCoroutineScope()
    val contentDescription =
        when (viewModel.contentType) {
            ContentType.Image -> stringResource(R.string.selectable_image)
            ContentType.Video -> stringResource(R.string.selectable_video)
            else -> stringResource(R.string.selectable_item)
        }
    Crossfade(
        targetState = bitmapLoadState,
        modifier =
            Modifier.semantics { this.contentDescription = contentDescription }
                .clip(RoundedCornerShape(size = 12.dp))
                .toggleable(
                    value = selected,
                    onValueChange = { scope.launch { viewModel.setSelected(it) } },
                )
    ) { state ->
        // TODO: max ratio is actually equal to the viewport ratio
        val aspectRatio = viewModel.aspectRatio.coerceIn(MIN_ASPECT_RATIO, MAX_ASPECT_RATIO)
        if (state is ValueUpdate.Value) {
            state.getOrDefault(null).let { bitmap ->
                ShareouselCard(
                    image = {
                        bitmap?.let {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.aspectRatio(aspectRatio),
                            )
                        } ?: PlaceholderBox(aspectRatio)
                    },
                    contentType = viewModel.contentType,
                    selected = selected,
                    modifier =
                        Modifier.thenIf(selected) {
                            Modifier.border(
                                width = 4.dp,
                                color = borderColor,
                                shape = RoundedCornerShape(size = 12.dp),
                            )
                        }
                )
            }
        } else {
            PlaceholderBox(aspectRatio)
        }
    }
}

@Composable
private fun PlaceholderBox(aspectRatio: Float) {
    Box(
        modifier =
            Modifier.fillMaxHeight()
                .aspectRatio(aspectRatio)
                .background(color = MaterialTheme.colorScheme.surfaceContainerHigh)
    )
}

@Composable
private fun ActionCarousel(viewModel: ShareouselViewModel) {
    val actions by viewModel.actions.collectAsStateWithLifecycle(initialValue = emptyList())
    if (actions.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.height(32.dp),
        ) {
            itemsIndexed(actions) { idx, actionViewModel ->
                if (idx == 0) {
                    Spacer(Modifier.width(dimensionResource(R.dimen.chooser_edge_margin_normal)))
                }
                ShareouselAction(
                    label = actionViewModel.label,
                    onClick = { actionViewModel.onClicked() },
                ) {
                    actionViewModel.icon?.let {
                        Image(
                            icon = it,
                            modifier = Modifier.size(16.dp),
                            colorFilter = ColorFilter.tint(LocalContentColor.current)
                        )
                    }
                }
                if (idx == actions.size - 1) {
                    Spacer(Modifier.width(dimensionResource(R.dimen.chooser_edge_margin_normal)))
                }
            }
        }
    }
}

@Composable
private fun ShareouselAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = leadingIcon,
        border = null,
        shape = RoundedCornerShape(1000.dp), // pill shape.
        colors =
            AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                labelColor = MaterialTheme.colorScheme.onSurface,
                leadingIconContentColor = MaterialTheme.colorScheme.onSurface
            ),
        modifier = modifier,
    )
}

inline fun Modifier.thenIf(condition: Boolean, crossinline factory: () -> Modifier): Modifier =
    if (condition) this.then(factory()) else this

private const val MIN_ASPECT_RATIO = 0.4f
private const val MAX_ASPECT_RATIO = 2.5f

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

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.intentresolver.Flags.announceShareouselItemListPosition
import com.android.intentresolver.Flags.shareouselScrollOffscreenSelections
import com.android.intentresolver.Flags.shareouselSelectionShrink
import com.android.intentresolver.Flags.shareouselTapToScrollSupport
import com.android.intentresolver.Flags.unselectFinalItem
import com.android.intentresolver.R
import com.android.intentresolver.contentpreview.payloadtoggle.domain.model.ValueUpdate
import com.android.intentresolver.contentpreview.payloadtoggle.domain.model.getOrDefault
import com.android.intentresolver.contentpreview.payloadtoggle.shared.ContentType
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewModel
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewsModel
import com.android.intentresolver.contentpreview.payloadtoggle.ui.viewmodel.ShareouselPreviewViewModel
import com.android.intentresolver.contentpreview.payloadtoggle.ui.viewmodel.ShareouselViewModel
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
                .padding(vertical = 16.dp)
    ) {
        PreviewCarousel(keySet, viewModel)
        ActionCarousel(viewModel)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PreviewCarousel(previews: PreviewsModel, viewModel: ShareouselViewModel) {
    var measurements by remember { mutableStateOf(PreviewCarouselMeasurements.UNMEASURED) }
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(dimensionResource(R.dimen.chooser_preview_image_height_tall))
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    measurements =
                        if (placeable.height <= 0) {
                            PreviewCarouselMeasurements.UNMEASURED
                        } else {
                            PreviewCarouselMeasurements(placeable, measureScope = this)
                        }
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }
                .systemGestureExclusion()
    ) {
        // Do not compose the list until we have measured values
        if (measurements == PreviewCarouselMeasurements.UNMEASURED) return@Box

        val prefetchStrategy = remember { ShareouselLazyListPrefetchStrategy() }
        val carouselState = remember {
            LazyListState(
                prefetchStrategy = prefetchStrategy,
                firstVisibleItemIndex = previews.startIdx,
                firstVisibleItemScrollOffset =
                    measurements.scrollOffsetToCenter(
                        previewModel = previews.previewModels[previews.startIdx]
                    ),
            )
        }

        PreviewCarouselItems(
            state = carouselState,
            measurements = measurements,
            previews = previews,
            viewModel = viewModel,
        )
    }
}

@Composable
private fun PreviewCarouselItems(
    state: LazyListState,
    measurements: PreviewCarouselMeasurements,
    previews: PreviewsModel,
    viewModel: ShareouselViewModel,
) {
    LazyRow(
        state = state,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding =
            PaddingValues(
                start = measurements.horizontalPaddingDp,
                end = measurements.horizontalPaddingDp,
            ),
        modifier =
            Modifier.fillMaxSize().conditional(shareouselTapToScrollSupport()) {
                tapToScroll(scrollableState = state)
            },
    ) {
        itemsIndexed(
            items = previews.previewModels,
            key = { _, model -> model.key.key to model.key.isFinal },
        ) { index, model ->
            val visibleItem by remember {
                derivedStateOf {
                    state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                }
            }

            // Index if this is the element in the center of the viewing area, otherwise null
            val previewIndex by remember {
                derivedStateOf {
                    visibleItem?.let {
                        val halfPreviewWidth = it.size / 2
                        val previewCenter = it.offset + halfPreviewWidth
                        val previewDistanceToViewportCenter =
                            abs(previewCenter - measurements.viewportCenterPx)
                        if (previewDistanceToViewportCenter <= halfPreviewWidth) {
                            index
                        } else {
                            null
                        }
                    }
                }
            }

            val previewModel =
                viewModel.preview(
                    /* key = */ model,
                    /* previewHeight = */ measurements.viewportHeightPx,
                    /* index = */ previewIndex,
                    /* scope = */ rememberCoroutineScope(),
                )

            if (shareouselScrollOffscreenSelections()) {
                ScrollOffscreenSelectionsEffect(
                    index = index,
                    previewModel = model,
                    isSelected = previewModel.isSelected,
                    state = state,
                    measurements = measurements,
                )
            }

            ShareouselCard(
                viewModel = previewModel,
                aspectRatio = measurements.coerceAspectRatio(previewModel.aspectRatio),
                annotateWithPosition = previews.previewModels.size > 1,
            )
        }
    }
}

@Composable
private fun ScrollOffscreenSelectionsEffect(
    index: Int,
    previewModel: PreviewModel,
    isSelected: Flow<Boolean>,
    state: LazyListState,
    measurements: PreviewCarouselMeasurements,
) {
    LaunchedEffect(index, previewModel.uri) {
        var current: Boolean? = null
        isSelected.collect { selected ->
            when {
                // First update will always be the current state, so we just want to
                // record the state and do nothing else.
                current == null -> current = selected

                // We only want to act when the state changes
                current != selected -> {
                    current = selected
                    with(state.layoutInfo) {
                        visibleItemsInfo
                            .firstOrNull { it.index == index }
                            ?.let { item ->
                                when {
                                    // Item is partially past start of viewport
                                    item.offset < viewportStartOffset ->
                                        measurements.scrollOffsetToStartEdge()
                                    // Item is partially past end of viewport
                                    (item.offset + item.size) > viewportEndOffset ->
                                        measurements.scrollOffsetToEndEdge(previewModel)
                                    // Item is fully within viewport
                                    else -> null
                                }?.let { scrollOffset ->
                                    state.animateScrollToItem(
                                        index = index,
                                        scrollOffset = scrollOffset,
                                    )
                                }
                            }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareouselCard(
    viewModel: ShareouselPreviewViewModel,
    aspectRatio: Float,
    annotateWithPosition: Boolean,
) {
    val bitmapLoadState by viewModel.bitmapLoadState.collectAsStateWithLifecycle()
    val selected by viewModel.isSelected.collectAsStateWithLifecycle(initialValue = false)
    val contentDescription =
        buildContentDescription(annotateWithPosition = annotateWithPosition, viewModel = viewModel)

    Box(
        modifier = Modifier.fillMaxHeight().aspectRatio(aspectRatio),
        contentAlignment = Alignment.Center,
    ) {
        val scope = rememberCoroutineScope()
        Crossfade(
            targetState = bitmapLoadState,
            modifier =
                Modifier.semantics { this.contentDescription = contentDescription }
                    .testTag(viewModel.testTag)
                    .clickable { scope.launch { viewModel.setSelected(!selected) } }
                    .conditional(shareouselSelectionShrink()) {
                        val selectionScale by animateFloatAsState(if (selected) 0.95f else 1f)
                        scale(selectionScale)
                    }
                    .clip(RoundedCornerShape(size = 12.dp)),
        ) { state ->
            if (state is ValueUpdate.Value) {
                ShareouselBitmapCard(
                    bitmap = state.getOrDefault(null),
                    aspectRatio = aspectRatio,
                    contentType = viewModel.contentType,
                    selected = selected,
                )
            } else {
                PlaceholderBox(aspectRatio)
            }
        }
    }
}

@Composable
private fun buildContentDescription(
    annotateWithPosition: Boolean,
    viewModel: ShareouselPreviewViewModel,
): String = buildString {
    if (
        announceShareouselItemListPosition() &&
            annotateWithPosition &&
            viewModel.cursorPosition >= 0
    ) {
        // If item cursor position is not known, do not announce item position.
        // We can have items with an unknown cursor position only when:
        // * when we haven't got the cursor and showing the initially shared items;
        // * when we've got an inconsistent data from the app (some initially shared items
        //   are missing in the cursor);
        append(stringResource(R.string.item_position_label, viewModel.cursorPosition + 1))
        append(", ")
    }
    append(
        when (viewModel.contentType) {
            ContentType.Image -> stringResource(R.string.selectable_image)
            ContentType.Video -> stringResource(R.string.selectable_video)
            else -> stringResource(R.string.selectable_item)
        }
    )
}

@Composable
private fun ShareouselBitmapCard(
    bitmap: Bitmap?,
    aspectRatio: Float,
    contentType: ContentType,
    selected: Boolean,
) {
    ShareouselCard(
        image = {
            if (bitmap == null) {
                PlaceholderBox(aspectRatio)
            } else {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.aspectRatio(aspectRatio),
                )
            }
        },
        contentType = contentType,
        selected = selected,
        modifier =
            Modifier.conditional(selected) {
                border(
                    width = 4.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(size = 12.dp),
                )
            },
    )
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
        val visibilityFlow =
            if (unselectFinalItem()) {
                viewModel.hasSelectedItems
            } else {
                MutableStateFlow(true)
            }
        val visibility by visibilityFlow.collectAsStateWithLifecycle(true)
        val height = 32.dp
        if (visibility) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.height(height),
            ) {
                itemsIndexed(actions) { idx, actionViewModel ->
                    if (idx == 0) {
                        Spacer(
                            Modifier.width(dimensionResource(R.dimen.chooser_edge_margin_normal))
                        )
                    }
                    ShareouselAction(
                        label = actionViewModel.label,
                        onClick = { actionViewModel.onClicked() },
                    ) {
                        actionViewModel.icon?.let {
                            Image(
                                icon = it,
                                modifier = Modifier.size(16.dp),
                                colorFilter = ColorFilter.tint(LocalContentColor.current),
                            )
                        }
                    }
                    if (idx == actions.size - 1) {
                        Spacer(
                            Modifier.width(dimensionResource(R.dimen.chooser_edge_margin_normal))
                        )
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.height(height))
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
                leadingIconContentColor = MaterialTheme.colorScheme.onSurface,
            ),
        modifier = modifier,
    )
}

private data class PreviewCarouselMeasurements(
    val viewportHeightPx: Int,
    val viewportWidthPx: Int,
    val viewportCenterPx: Int = viewportWidthPx / 2,
    val maxAspectRatio: Float,
    val horizontalPaddingPx: Int,
    val horizontalPaddingDp: Dp,
) {
    constructor(
        placeable: Placeable,
        measureScope: MeasureScope,
        horizontalPadding: Float = (placeable.width - (MIN_ASPECT_RATIO * placeable.height)) / 2,
    ) : this(
        viewportHeightPx = placeable.height,
        viewportWidthPx = placeable.width,
        maxAspectRatio =
            with(measureScope) {
                min(
                    (placeable.width - 32.dp.roundToPx()).toFloat() / placeable.height,
                    MAX_ASPECT_RATIO,
                )
            },
        horizontalPaddingPx = horizontalPadding.roundToInt(),
        horizontalPaddingDp = with(measureScope) { horizontalPadding.toDp() },
    )

    fun coerceAspectRatio(ratio: Float): Float = ratio.coerceIn(MIN_ASPECT_RATIO, maxAspectRatio)

    fun scrollOffsetToCenter(previewModel: PreviewModel): Int =
        horizontalPaddingPx + (aspectRatioToWidthPx(previewModel.aspectRatio) / 2) -
            viewportCenterPx

    fun scrollOffsetToStartEdge(): Int = horizontalPaddingPx

    fun scrollOffsetToEndEdge(previewModel: PreviewModel): Int =
        horizontalPaddingPx + aspectRatioToWidthPx(previewModel.aspectRatio) - viewportWidthPx

    private fun aspectRatioToWidthPx(ratio: Float): Int =
        (coerceAspectRatio(ratio) * viewportHeightPx).roundToInt()

    companion object {
        private const val MIN_ASPECT_RATIO = 0.4f
        private const val MAX_ASPECT_RATIO = 2.5f

        val UNMEASURED =
            PreviewCarouselMeasurements(
                viewportHeightPx = 0,
                viewportWidthPx = 0,
                maxAspectRatio = 0f,
                horizontalPaddingPx = 0,
                horizontalPaddingDp = 0.dp,
            )
    }
}

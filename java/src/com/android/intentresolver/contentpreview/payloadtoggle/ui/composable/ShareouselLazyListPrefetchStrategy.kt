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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListPrefetchScope
import androidx.compose.foundation.lazy.LazyListPrefetchStrategy
import androidx.compose.foundation.lazy.layout.LazyLayoutPrefetchState
import androidx.compose.foundation.lazy.layout.NestedPrefetchScope

/** Prefetch strategy to fetch items ahead and behind the current scroll position. */
@OptIn(ExperimentalFoundationApi::class)
class ShareouselLazyListPrefetchStrategy(
    private val lookAhead: Int = 4,
    private val lookBackward: Int = 1
) : LazyListPrefetchStrategy {
    // Map of index -> prefetch handle
    private val prefetchHandles: MutableMap<Int, LazyLayoutPrefetchState.PrefetchHandle> =
        mutableMapOf()

    private var prefetchRange = IntRange.EMPTY

    private enum class ScrollDirection {
        UNKNOWN, // The user hasn't scrolled in either direction yet.
        FORWARD,
        BACKWARD,
    }

    private var scrollDirection: ScrollDirection = ScrollDirection.UNKNOWN

    override fun LazyListPrefetchScope.onScroll(delta: Float, layoutInfo: LazyListLayoutInfo) {
        if (layoutInfo.visibleItemsInfo.isNotEmpty()) {
            scrollDirection = if (delta < 0) ScrollDirection.FORWARD else ScrollDirection.BACKWARD
            updatePrefetchSet(layoutInfo.visibleItemsInfo)
        }

        if (scrollDirection == ScrollDirection.FORWARD) {
            val lastItem = layoutInfo.visibleItemsInfo.last()
            val spacing = layoutInfo.mainAxisItemSpacing
            val distanceToPrefetchItem =
                lastItem.offset + lastItem.size + spacing - layoutInfo.viewportEndOffset
            // if in the next frame we will get the same delta will we reach the item?
            if (distanceToPrefetchItem < -delta) {
                prefetchHandles.get(lastItem.index + 1)?.markAsUrgent()
            }
        } else {
            val firstItem = layoutInfo.visibleItemsInfo.first()
            val distanceToPrefetchItem = layoutInfo.viewportStartOffset - firstItem.offset
            // if in the next frame we will get the same delta will we reach the item?
            if (distanceToPrefetchItem < delta) {
                prefetchHandles.get(firstItem.index - 1)?.markAsUrgent()
            }
        }
    }

    override fun LazyListPrefetchScope.onVisibleItemsUpdated(layoutInfo: LazyListLayoutInfo) {
        if (layoutInfo.visibleItemsInfo.isNotEmpty()) {
            updatePrefetchSet(layoutInfo.visibleItemsInfo)
        }
    }

    override fun NestedPrefetchScope.onNestedPrefetch(firstVisibleItemIndex: Int) {}

    private fun getVisibleRange(visibleItems: List<LazyListItemInfo>) =
        if (visibleItems.isEmpty()) IntRange.EMPTY
        else IntRange(visibleItems.first().index, visibleItems.last().index)

    /** Update prefetchRange based upon the visible item range and scroll direction. */
    private fun updatePrefetchRange(visibleRange: IntRange) {
        prefetchRange =
            when (scrollDirection) {
                // Prefetch in both directions
                ScrollDirection.UNKNOWN ->
                    visibleRange.first - lookAhead / 2..visibleRange.last + lookAhead / 2
                ScrollDirection.FORWARD ->
                    visibleRange.first - lookBackward..visibleRange.last + lookAhead
                ScrollDirection.BACKWARD ->
                    visibleRange.first - lookAhead..visibleRange.last + lookBackward
            }
    }

    private fun LazyListPrefetchScope.updatePrefetchSet(visibleItems: List<LazyListItemInfo>) {
        val visibleRange = getVisibleRange(visibleItems)
        updatePrefetchRange(visibleRange)
        updatePrefetchOperations(visibleRange)
    }

    private fun LazyListPrefetchScope.updatePrefetchOperations(visibleItemsRange: IntRange) {
        // Remove any fetches outside of the prefetch range or inside the visible range
        prefetchHandles
            .filterKeys { it !in prefetchRange || it in visibleItemsRange }
            .forEach {
                it.value.cancel()
                prefetchHandles.remove(it.key)
            }

        // Ensure all non-visible items in the range are being prefetched
        prefetchRange.forEach {
            if (it !in visibleItemsRange && !prefetchHandles.containsKey(it)) {
                prefetchHandles[it] = schedulePrefetch(it)
            }
        }
    }
}

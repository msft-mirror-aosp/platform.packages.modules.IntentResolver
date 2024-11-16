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

package com.android.intentresolver.contentpreview.payloadtoggle.domain.model

/** A window of data loaded from a cursor. */
data class LoadedWindow<K, V>(
    /** The index position of the item that should be displayed initially. */
    val startIndex: Int,
    /** First cursor page index loaded within this window. */
    val firstLoadedPageNum: Int,
    /** Last cursor page index loaded within this window. */
    val lastLoadedPageNum: Int,
    /** Keys of cursor data within this window, grouped by loaded page. */
    val pages: List<Set<K>>,
    /** Merged set of all cursor data within this window. */
    val merged: Map<K, V>,
    /** Is there more data to the left of this window? */
    val hasMoreLeft: Boolean,
    /** Is there more data to the right of this window? */
    val hasMoreRight: Boolean,
)

/** Number of loaded pages stored within this [LoadedWindow]. */
val LoadedWindow<*, *>.numLoadedPages: Int
    get() = (lastLoadedPageNum - firstLoadedPageNum) + 1

/** Inserts [newPage] to the right, and removes the leftmost page from the window. */
fun <K, V> LoadedWindow<K, V>.shiftWindowRight(
    newPage: Map<K, V>,
    hasMore: Boolean,
): LoadedWindow<K, V> =
    LoadedWindow(
        startIndex = startIndex - newPage.size,
        firstLoadedPageNum = firstLoadedPageNum + 1,
        lastLoadedPageNum = lastLoadedPageNum + 1,
        pages = pages.drop(1) + listOf(newPage.keys),
        merged =
            buildMap {
                putAll(merged)
                pages.first().forEach(::remove)
                putAll(newPage)
            },
        hasMoreLeft = true,
        hasMoreRight = hasMore,
    )

/** Inserts [newPage] to the right, increasing the size of the window to accommodate it. */
fun <K, V> LoadedWindow<K, V>.expandWindowRight(
    newPage: Map<K, V>,
    hasMore: Boolean,
): LoadedWindow<K, V> =
    LoadedWindow(
        startIndex = startIndex,
        firstLoadedPageNum = firstLoadedPageNum,
        lastLoadedPageNum = lastLoadedPageNum + 1,
        pages = pages + listOf(newPage.keys),
        merged = merged + newPage,
        hasMoreLeft = hasMoreLeft,
        hasMoreRight = hasMore,
    )

/** Inserts [newPage] to the left, and removes the rightmost page from the window. */
fun <K, V> LoadedWindow<K, V>.shiftWindowLeft(
    newPage: Map<K, V>,
    hasMore: Boolean,
): LoadedWindow<K, V> =
    LoadedWindow(
        startIndex = startIndex + newPage.size,
        firstLoadedPageNum = firstLoadedPageNum - 1,
        lastLoadedPageNum = lastLoadedPageNum - 1,
        pages = listOf(newPage.keys) + pages.dropLast(1),
        merged =
            buildMap {
                putAll(newPage)
                putAll(merged - pages.last())
            },
        hasMoreLeft = hasMore,
        hasMoreRight = true,
    )

/** Inserts [newPage] to the left, increasing the size olf the window to accommodate it. */
fun <K, V> LoadedWindow<K, V>.expandWindowLeft(
    newPage: Map<K, V>,
    hasMore: Boolean,
): LoadedWindow<K, V> =
    LoadedWindow(
        startIndex = startIndex + newPage.size,
        firstLoadedPageNum = firstLoadedPageNum - 1,
        lastLoadedPageNum = lastLoadedPageNum,
        pages = listOf(newPage.keys) + pages,
        merged = newPage + merged,
        hasMoreLeft = hasMore,
        hasMoreRight = hasMoreRight,
    )

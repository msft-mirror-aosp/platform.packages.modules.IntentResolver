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

package com.android.intentresolver.contentpreview.payloadtoggle.domain.interactor

import android.net.Uri
import android.service.chooser.AdditionalContentContract.CursorExtraKeys.POSITION
import android.util.Log
import com.android.intentresolver.contentpreview.UriMetadataReader
import com.android.intentresolver.contentpreview.payloadtoggle.domain.model.CursorRow
import com.android.intentresolver.contentpreview.payloadtoggle.domain.model.LoadDirection
import com.android.intentresolver.contentpreview.payloadtoggle.domain.model.LoadedWindow
import com.android.intentresolver.contentpreview.payloadtoggle.domain.model.expandWindowLeft
import com.android.intentresolver.contentpreview.payloadtoggle.domain.model.expandWindowRight
import com.android.intentresolver.contentpreview.payloadtoggle.domain.model.numLoadedPages
import com.android.intentresolver.contentpreview.payloadtoggle.domain.model.shiftWindowLeft
import com.android.intentresolver.contentpreview.payloadtoggle.domain.model.shiftWindowRight
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewModel
import com.android.intentresolver.inject.FocusedItemIndex
import com.android.intentresolver.util.cursor.CursorView
import com.android.intentresolver.util.cursor.PagedCursor
import com.android.intentresolver.util.cursor.get
import com.android.intentresolver.util.cursor.paged
import com.android.intentresolver.util.mapParallel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Qualifier
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest

private const val TAG = "CursorPreviewsIntr"

/** Queries data from a remote cursor, and caches it locally for presentation in Shareousel. */
class CursorPreviewsInteractor
@Inject
constructor(
    private val interactor: SetCursorPreviewsInteractor,
    private val selectionInteractor: SelectionInteractor,
    @FocusedItemIndex private val focusedItemIdx: Int,
    private val uriMetadataReader: UriMetadataReader,
    @PageSize private val pageSize: Int,
    @MaxLoadedPages private val maxLoadedPages: Int,
) {

    init {
        check(pageSize > 0) { "pageSize must be greater than zero" }
    }

    /** Start reading data from [uriCursor], and listen for requests to load more. */
    suspend fun launch(uriCursor: CursorView<CursorRow?>, initialPreviews: Iterable<PreviewModel>) {
        // Unclaimed values from the initial selection set. Entries will be removed as the cursor is
        // read, and any still present are inserted at the start / end of the cursor when it is
        // reached by the user.
        val unclaimedRecords: MutableUnclaimedMap =
            initialPreviews
                .asSequence()
                .mapIndexed { i, m -> Pair(m.uri, Pair(i, m)) }
                .toMap(ConcurrentHashMap())
        val pagedCursor: PagedCursor<CursorRow?> = uriCursor.paged(pageSize)
        val startPosition = uriCursor.extras?.getInt(POSITION, 0) ?: 0
        val state =
            loadToMaxPages(
                initialState = readInitialState(pagedCursor, startPosition, unclaimedRecords),
                pagedCursor = pagedCursor,
                unclaimedRecords = unclaimedRecords,
            )
        processLoadRequests(state, pagedCursor, unclaimedRecords)
    }

    private suspend fun loadToMaxPages(
        initialState: CursorWindow,
        pagedCursor: PagedCursor<CursorRow?>,
        unclaimedRecords: MutableUnclaimedMap,
    ): CursorWindow {
        var state = initialState
        val startPageNum = state.firstLoadedPageNum
        while ((state.hasMoreLeft || state.hasMoreRight) && state.numLoadedPages < maxLoadedPages) {
            val (leftTriggerIndex, rightTriggerIndex) = state.triggerIndices()
            interactor.setPreviews(
                previews = state.merged.values.toList(),
                startIndex = startPageNum,
                hasMoreLeft = state.hasMoreLeft,
                hasMoreRight = state.hasMoreRight,
                leftTriggerIndex = leftTriggerIndex,
                rightTriggerIndex = rightTriggerIndex,
            )
            val loadedLeft = startPageNum - state.firstLoadedPageNum
            val loadedRight = state.lastLoadedPageNum - startPageNum
            state =
                when {
                    state.hasMoreLeft && loadedLeft < loadedRight ->
                        state.loadMoreLeft(pagedCursor, unclaimedRecords)
                    state.hasMoreRight -> state.loadMoreRight(pagedCursor, unclaimedRecords)
                    else -> state.loadMoreLeft(pagedCursor, unclaimedRecords)
                }
        }
        return state
    }

    /** Loop forever, processing any loading requests from the UI and updating local cache. */
    private suspend fun processLoadRequests(
        initialState: CursorWindow,
        pagedCursor: PagedCursor<CursorRow?>,
        unclaimedRecords: MutableUnclaimedMap,
    ) {
        var state = initialState
        while (true) {
            val (leftTriggerIndex, rightTriggerIndex) = state.triggerIndices()

            // Design note: in order to prevent load requests from the UI when it was displaying a
            // previously-published dataset being accidentally associated with a recently-published
            // one, we generate a new Flow of load requests for each dataset and only listen to
            // those.
            val loadingState: Flow<LoadDirection?> =
                interactor.setPreviews(
                    previews = state.merged.values.toList(),
                    startIndex = 0, // TODO: actually track this as the window changes?
                    hasMoreLeft = state.hasMoreLeft,
                    hasMoreRight = state.hasMoreRight,
                    leftTriggerIndex = leftTriggerIndex,
                    rightTriggerIndex = rightTriggerIndex,
                )
            state = loadingState.handleOneLoadRequest(state, pagedCursor, unclaimedRecords)
        }
    }

    /**
     * Suspends until a single loading request has been handled, returning the new [CursorWindow]
     * with the loaded data incorporated.
     */
    private suspend fun Flow<LoadDirection?>.handleOneLoadRequest(
        state: CursorWindow,
        pagedCursor: PagedCursor<CursorRow?>,
        unclaimedRecords: MutableUnclaimedMap,
    ): CursorWindow =
        mapLatest { loadDirection ->
                loadDirection?.let {
                    when (loadDirection) {
                        LoadDirection.Left -> state.loadMoreLeft(pagedCursor, unclaimedRecords)
                        LoadDirection.Right -> state.loadMoreRight(pagedCursor, unclaimedRecords)
                    }
                }
            }
            .filterNotNull()
            .first()

    /**
     * Returns the initial [CursorWindow], with a single page loaded that contains the given
     * [startPosition].
     */
    private suspend fun readInitialState(
        cursor: PagedCursor<CursorRow?>,
        startPosition: Int,
        unclaimedRecords: MutableUnclaimedMap,
    ): CursorWindow {
        val startPageIdx = startPosition / pageSize
        val hasMoreLeft = startPageIdx > 0
        val hasMoreRight = startPageIdx < cursor.count - 1
        val page: PreviewMap = buildMap {
            if (!hasMoreLeft) {
                // First read the initial page; this might claim some unclaimed Uris
                val page =
                    cursor.getPageRows(startPageIdx)?.toPage(mutableMapOf(), unclaimedRecords)
                // Now that unclaimed Uris are up-to-date, add them first.
                putAllUnclaimedLeft(unclaimedRecords)
                // Then add the loaded page
                page?.let(::putAll)
            } else {
                cursor.getPageRows(startPageIdx)?.toPage(this, unclaimedRecords)
            }
            // Finally, add the remainder of the unclaimed Uris.
            if (!hasMoreRight) {
                putAllUnclaimedRight(unclaimedRecords)
            }
        }
        return CursorWindow(
            firstLoadedPageNum = startPageIdx,
            lastLoadedPageNum = startPageIdx,
            pages = listOf(page.keys),
            merged = page,
            hasMoreLeft = hasMoreLeft,
            hasMoreRight = hasMoreRight,
        )
    }

    private suspend fun CursorWindow.loadMoreRight(
        cursor: PagedCursor<CursorRow?>,
        unclaimedRecords: MutableUnclaimedMap,
    ): CursorWindow {
        val pageNum = lastLoadedPageNum + 1
        val hasMoreRight = pageNum < cursor.count - 1
        val newPage: PreviewMap = buildMap {
            readAndPutPage(this@loadMoreRight, cursor, pageNum, unclaimedRecords)
            if (!hasMoreRight) {
                putAllUnclaimedRight(unclaimedRecords)
            }
        }
        return if (numLoadedPages < maxLoadedPages) {
            expandWindowRight(newPage, hasMoreRight)
        } else {
            shiftWindowRight(newPage, hasMoreRight)
        }
    }

    private suspend fun CursorWindow.loadMoreLeft(
        cursor: PagedCursor<CursorRow?>,
        unclaimedRecords: MutableUnclaimedMap,
    ): CursorWindow {
        val pageNum = firstLoadedPageNum - 1
        val hasMoreLeft = pageNum > 0
        val newPage: PreviewMap = buildMap {
            if (!hasMoreLeft) {
                // First read the page; this might claim some unclaimed Uris
                val page = readPage(this@loadMoreLeft, cursor, pageNum, unclaimedRecords)
                // Now that unclaimed URIs are up-to-date, add them first
                putAllUnclaimedLeft(unclaimedRecords)
                // Then add the loaded page
                putAll(page)
            } else {
                readAndPutPage(this@loadMoreLeft, cursor, pageNum, unclaimedRecords)
            }
        }
        return if (numLoadedPages < maxLoadedPages) {
            expandWindowLeft(newPage, hasMoreLeft)
        } else {
            shiftWindowLeft(newPage, hasMoreLeft)
        }
    }

    private fun CursorWindow.triggerIndices(): Pair<Int, Int> {
        val totalIndices = numLoadedPages * pageSize
        val midIndex = totalIndices / 2
        val halfPage = pageSize / 2
        return max(midIndex - halfPage, 0) to min(midIndex + halfPage, totalIndices - 1)
    }

    private suspend fun readPage(
        state: CursorWindow,
        pagedCursor: PagedCursor<CursorRow?>,
        pageNum: Int,
        unclaimedRecords: MutableUnclaimedMap,
    ): PreviewMap =
        mutableMapOf<Uri, PreviewModel>()
            .readAndPutPage(state, pagedCursor, pageNum, unclaimedRecords)

    private suspend fun <M : MutablePreviewMap> M.readAndPutPage(
        state: CursorWindow,
        pagedCursor: PagedCursor<CursorRow?>,
        pageNum: Int,
        unclaimedRecords: MutableUnclaimedMap,
    ): M =
        pagedCursor
            .getPageRows(pageNum) // TODO: what do we do if the load fails?
            ?.filter { it.uri !in state.merged }
            ?.toPage(this, unclaimedRecords) ?: this

    private suspend fun <M : MutablePreviewMap> Sequence<CursorRow>.toPage(
        destination: M,
        unclaimedRecords: MutableUnclaimedMap,
    ): M =
        // Restrict parallelism so as to not overload the metadata reader; anecdotally, too
        // many parallel queries causes failures.
        mapParallel(parallelism = 4) { row -> createPreviewModel(row, unclaimedRecords) }
            .associateByTo(destination) { it.uri }

    private fun createPreviewModel(
        row: CursorRow,
        unclaimedRecords: MutableUnclaimedMap,
    ): PreviewModel =
        uriMetadataReader
            .getMetadata(row.uri)
            .let { metadata ->
                val size =
                    row.previewSize
                        ?: metadata.previewUri?.let { uriMetadataReader.readPreviewSize(it) }
                PreviewModel(
                    uri = row.uri,
                    previewUri = metadata.previewUri,
                    mimeType = metadata.mimeType,
                    aspectRatio = size.aspectRatioOrDefault(1f),
                    order = row.position,
                )
            }
            .also { updated ->
                if (unclaimedRecords.remove(row.uri) != null) {
                    // unclaimedRecords contains initially shared (and thus selected) items with
                    // unknown
                    // cursor position. Update selection records when any of those items is
                    // encountered
                    // in the cursor to maintain proper selection order should other items also be
                    // selected.
                    selectionInteractor.updateSelection(updated)
                }
            }

    private fun <M : MutablePreviewMap> M.putAllUnclaimedRight(unclaimed: UnclaimedMap): M =
        putAllUnclaimedWhere(unclaimed) { it >= focusedItemIdx }

    private fun <M : MutablePreviewMap> M.putAllUnclaimedLeft(unclaimed: UnclaimedMap): M =
        putAllUnclaimedWhere(unclaimed) { it < focusedItemIdx }
}

private typealias CursorWindow = LoadedWindow<Uri, PreviewModel>

/**
 * Values from the initial selection set that have not yet appeared within the Cursor. These values
 * are appended to the start/end of the cursor dataset, depending on their position relative to the
 * initially focused value.
 */
private typealias UnclaimedMap = Map<Uri, Pair<Int, PreviewModel>>

/** Mutable version of [UnclaimedMap]. */
private typealias MutableUnclaimedMap = MutableMap<Uri, Pair<Int, PreviewModel>>

private typealias MutablePreviewMap = MutableMap<Uri, PreviewModel>

private typealias PreviewMap = Map<Uri, PreviewModel>

private fun <M : MutablePreviewMap> M.putAllUnclaimedWhere(
    unclaimedRecords: UnclaimedMap,
    predicate: (Int) -> Boolean,
): M =
    unclaimedRecords
        .asSequence()
        .filter { predicate(it.value.first) }
        .map { it.key to it.value.second }
        .toMap(this)

private fun PagedCursor<CursorRow?>.getPageRows(pageNum: Int): Sequence<CursorRow>? =
    runCatching { get(pageNum) }
        .onFailure { Log.e(TAG, "Failed to read additional content cursor page #$pageNum", it) }
        .getOrNull()
        ?.asSafeSequence()
        ?.filterNotNull()

private fun <T> Sequence<T>.asSafeSequence(): Sequence<T> {
    return if (this is SafeSequence) this else SafeSequence(this)
}

private class SafeSequence<T>(private val sequence: Sequence<T>) : Sequence<T> {
    override fun iterator(): Iterator<T> =
        sequence.iterator().let { if (it is SafeIterator) it else SafeIterator(it) }
}

private class SafeIterator<T>(private val iterator: Iterator<T>) : Iterator<T> by iterator {
    override fun hasNext(): Boolean {
        return runCatching { iterator.hasNext() }
            .onFailure { Log.e(TAG, "Failed to read cursor", it) }
            .getOrDefault(false)
    }
}

@Qualifier @MustBeDocumented @Retention(AnnotationRetention.RUNTIME) annotation class PageSize

@Qualifier
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
annotation class MaxLoadedPages

@Module
@InstallIn(SingletonComponent::class)
object ShareouselConstants {
    @Provides @PageSize fun pageSize(): Int = 16

    @Provides @MaxLoadedPages fun maxLoadedPages(): Int = 8
}

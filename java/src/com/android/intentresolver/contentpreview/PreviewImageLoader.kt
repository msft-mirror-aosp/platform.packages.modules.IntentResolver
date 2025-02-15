/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.intentresolver.contentpreview

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.util.Size
import androidx.collection.lruCache
import com.android.intentresolver.inject.Background
import com.android.intentresolver.inject.ViewModelOwned
import javax.annotation.concurrent.GuardedBy
import javax.inject.Inject
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val TAG = "ImageLoader"

@Qualifier @MustBeDocumented @Retention(AnnotationRetention.BINARY) annotation class ThumbnailSize

@Qualifier
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
annotation class PreviewCacheSize

@Qualifier
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
annotation class PreviewMaxConcurrency

/**
 * Implements preview image loading for the payload selection UI. Cancels preview loading for items
 * that has been evicted from the cache at the expense of a possible request duplication (deemed
 * unlikely).
 */
class PreviewImageLoader
@Inject
constructor(
    @ViewModelOwned private val scope: CoroutineScope,
    @PreviewCacheSize private val cacheSize: Int,
    @ThumbnailSize private val defaultPreviewSize: Int,
    private val thumbnailLoader: ThumbnailLoader,
    @Background private val bgDispatcher: CoroutineDispatcher,
    @PreviewMaxConcurrency maxSimultaneousRequests: Int = 4,
) : ImageLoader {

    private val contentResolverSemaphore = Semaphore(maxSimultaneousRequests)

    private val lock = Any()
    @GuardedBy("lock") private val runningRequests = hashMapOf<Uri, RequestRecord>()
    @GuardedBy("lock")
    private val cache =
        lruCache<Uri, RequestRecord>(
            maxSize = cacheSize,
            onEntryRemoved = { _, _, oldRec, newRec ->
                if (oldRec !== newRec) {
                    onRecordEvictedFromCache(oldRec)
                }
            },
        )

    override suspend fun invoke(uri: Uri, size: Size, caching: Boolean): Bitmap? =
        loadImageInternal(uri, size, caching)

    override fun prePopulate(uriSizePairs: List<Pair<Uri, Size>>) {
        uriSizePairs.asSequence().take(cacheSize).forEach { uri ->
            scope.launch { loadImageInternal(uri.first, uri.second, caching = true) }
        }
    }

    private suspend fun loadImageInternal(uri: Uri, size: Size, caching: Boolean): Bitmap? {
        return withRequestRecord(uri, caching) { record ->
            val newSize = sanitize(size)
            val newMetric = newSize.metric
            record
                .also {
                    // set the requested size to the max of the new and the previous value; input
                    // will emit if the resulted value is greater than the old one
                    it.input.update { oldSize ->
                        if (oldSize == null || oldSize.metric < newSize.metric) newSize else oldSize
                    }
                }
                .output
                // filter out bitmaps of a lower resolution than that we're requesting
                .filter { it is BitmapLoadingState.Loaded && newMetric <= it.size.metric }
                .firstOrNull()
                ?.let { (it as BitmapLoadingState.Loaded).bitmap }
        }
    }

    private suspend fun withRequestRecord(
        uri: Uri,
        caching: Boolean,
        block: suspend (RequestRecord) -> Bitmap?,
    ): Bitmap? {
        val record = trackRecordRunning(uri, caching)
        return try {
            block(record)
        } finally {
            untrackRecordRunning(uri, record)
        }
    }

    private fun trackRecordRunning(uri: Uri, caching: Boolean): RequestRecord =
        synchronized(lock) {
            runningRequests
                .getOrPut(uri) { cache[uri] ?: createRecord(uri) }
                .also { record ->
                    record.clientCount++
                    if (caching) {
                        cache.put(uri, record)
                    }
                }
        }

    private fun untrackRecordRunning(uri: Uri, record: RequestRecord) {
        synchronized(lock) {
            record.clientCount--
            if (record.clientCount <= 0) {
                runningRequests.remove(uri)
                val result = record.output.value
                if (cache[uri] == null) {
                    record.loadingJob.cancel()
                } else if (result is BitmapLoadingState.Loaded && result.bitmap == null) {
                    cache.remove(uri)
                }
            }
        }
    }

    private fun onRecordEvictedFromCache(record: RequestRecord) {
        synchronized(lock) {
            if (record.clientCount <= 0) {
                record.loadingJob.cancel()
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun createRecord(uri: Uri): RequestRecord {
        // use a StateFlow with sentinel values to avoid using SharedFlow that is deemed dangerous
        val input = MutableStateFlow<Size?>(null)
        val output = MutableStateFlow<BitmapLoadingState>(BitmapLoadingState.Loading)
        val job =
            scope.launch(bgDispatcher) {
                // the image loading pipeline: input -- a desired image size, output -- a bitmap
                input
                    .filterNotNull()
                    .mapLatest { size -> BitmapLoadingState.Loaded(size, loadBitmap(uri, size)) }
                    .collect { output.tryEmit(it) }
            }
        return RequestRecord(input, output, job, clientCount = 0)
    }

    private suspend fun loadBitmap(uri: Uri, size: Size): Bitmap? =
        contentResolverSemaphore.withPermit {
            runCatching { thumbnailLoader.loadThumbnail(uri, size) }
                .onFailure { Log.d(TAG, "failed to load $uri preview", it) }
                .getOrNull()
        }

    private class RequestRecord(
        /** The image loading pipeline input: desired preview size */
        val input: MutableStateFlow<Size?>,
        /** The image loading pipeline output */
        val output: MutableStateFlow<BitmapLoadingState>,
        /** The image loading pipeline job */
        val loadingJob: Job,
        @GuardedBy("lock") var clientCount: Int,
    )

    private sealed interface BitmapLoadingState {
        data object Loading : BitmapLoadingState

        data class Loaded(val size: Size, val bitmap: Bitmap?) : BitmapLoadingState
    }

    private fun sanitize(size: Size?): Size =
        size?.takeIf { it.width > 0 && it.height > 0 }
            ?: Size(defaultPreviewSize, defaultPreviewSize)
}

private val Size.metric
    get() = maxOf(width, height)

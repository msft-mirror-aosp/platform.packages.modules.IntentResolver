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

package com.android.intentresolver.contentpreview

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.util.Size
import androidx.core.util.lruCache
import com.android.intentresolver.inject.Background
import com.android.intentresolver.inject.ViewModelOwned
import javax.inject.Inject
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

@Qualifier
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
annotation class PreviewMaxConcurrency

/**
 * Implementation of [ImageLoader].
 *
 * Allows for cached or uncached loading of images and limits the number of concurrent requests.
 * Requests are automatically cancelled when they are evicted from the cache. If image loading fails
 * or the request is cancelled (e.g. by eviction), the returned [Bitmap] will be null.
 */
class CachingImagePreviewImageLoader
@Inject
constructor(
    @ViewModelOwned private val scope: CoroutineScope,
    @Background private val bgDispatcher: CoroutineDispatcher,
    private val thumbnailLoader: ThumbnailLoader,
    @PreviewCacheSize cacheSize: Int,
    @PreviewMaxConcurrency maxConcurrency: Int,
) : ImageLoader {

    private val semaphore = Semaphore(maxConcurrency)

    private val cache =
        lruCache(
            maxSize = cacheSize,
            create = { uri: Uri -> scope.async { loadUncachedImage(uri) } },
            onEntryRemoved = { evicted: Boolean, _, oldValue: Deferred<Bitmap?>, _ ->
                // If removed due to eviction, cancel the coroutine, otherwise it is the
                // responsibility
                // of the caller of [cache.remove] to cancel the removed entry when done with it.
                if (evicted) {
                    oldValue.cancel()
                }
            }
        )

    override fun prePopulate(uriSizePairs: List<Pair<Uri, Size>>) {
        uriSizePairs.take(cache.maxSize()).map { cache[it.first] }
    }

    override suspend fun invoke(uri: Uri, size: Size, caching: Boolean): Bitmap? {
        return if (caching) {
            loadCachedImage(uri)
        } else {
            loadUncachedImage(uri)
        }
    }

    private suspend fun loadUncachedImage(uri: Uri): Bitmap? =
        withContext(bgDispatcher) {
            runCatching { semaphore.withPermit { thumbnailLoader.loadThumbnail(uri) } }
                .onFailure {
                    ensureActive()
                    Log.d(TAG, "Failed to load preview for $uri", it)
                }
                .getOrNull()
        }

    private suspend fun loadCachedImage(uri: Uri): Bitmap? =
        // [Deferred#await] is called in a [runCatching] block to catch
        // [CancellationExceptions]s so that they don't cancel the calling coroutine/scope.
        runCatching { cache[uri].await() }.getOrNull()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getCachedBitmap(uri: Uri): Bitmap? =
        kotlin.runCatching { cache[uri].getCompleted() }.getOrNull()

    companion object {
        private const val TAG = "CachingImgPrevLoader"
    }
}

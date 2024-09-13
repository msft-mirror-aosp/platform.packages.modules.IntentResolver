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
import android.util.Size
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PreviewImageLoaderTest {
    private val scope = TestScope()

    @Test
    fun test_cachingImageRequest_imageCached() =
        scope.runTest {
            val uri = createUri(0)
            val thumbnailLoader =
                FakeThumbnailLoader().apply {
                    fakeInvoke[uri] = { size -> createBitmap(size.width, size.height) }
                }
            val testSubject =
                PreviewImageLoader(
                    backgroundScope,
                    1,
                    100,
                    thumbnailLoader,
                    StandardTestDispatcher(scope.testScheduler)
                )

            val b1 = testSubject.invoke(uri, Size(200, 100))
            val b2 = testSubject.invoke(uri, Size(200, 100), caching = false)
            assertThat(b1).isEqualTo(b2)
            assertThat(thumbnailLoader.invokeCalls).hasSize(1)
        }

    @Test
    fun test_nonCachingImageRequest_imageNotCached() =
        scope.runTest {
            val uri = createUri(0)
            val thumbnailLoader =
                FakeThumbnailLoader().apply {
                    fakeInvoke[uri] = { size -> createBitmap(size.width, size.height) }
                }
            val testSubject =
                PreviewImageLoader(
                    backgroundScope,
                    1,
                    100,
                    thumbnailLoader,
                    StandardTestDispatcher(scope.testScheduler)
                )

            testSubject.invoke(uri, Size(200, 100), caching = false)
            testSubject.invoke(uri, Size(200, 100), caching = false)
            assertThat(thumbnailLoader.invokeCalls).hasSize(2)
        }

    @Test
    fun test_twoSimultaneousImageRequests_requestsDeduplicated() =
        scope.runTest {
            val uri = createUri(0)
            val loadingStartedDeferred = CompletableDeferred<Unit>()
            val bitmapDeferred = CompletableDeferred<Bitmap>()
            val thumbnailLoader =
                FakeThumbnailLoader().apply {
                    fakeInvoke[uri] = {
                        loadingStartedDeferred.complete(Unit)
                        bitmapDeferred.await()
                    }
                }
            val testSubject =
                PreviewImageLoader(
                    backgroundScope,
                    1,
                    100,
                    thumbnailLoader,
                    StandardTestDispatcher(scope.testScheduler)
                )

            val b1Deferred = async { testSubject.invoke(uri, Size(200, 100), caching = false) }
            loadingStartedDeferred.await()
            val b2Deferred =
                async(start = CoroutineStart.UNDISPATCHED) {
                    testSubject.invoke(uri, Size(200, 100), caching = true)
                }
            bitmapDeferred.complete(createBitmap(200, 200))

            val b1 = b1Deferred.await()
            val b2 = b2Deferred.await()
            assertThat(b1).isEqualTo(b2)
            assertThat(thumbnailLoader.invokeCalls).hasSize(1)
        }

    @Test
    fun test_cachingRequestCancelledAndEvoked_imageLoadingCancelled() =
        scope.runTest {
            val uriOne = createUri(1)
            val uriTwo = createUri(2)
            val loadingStartedDeferred = CompletableDeferred<Unit>()
            val cancelledRequests = mutableSetOf<Uri>()
            val thumbnailLoader =
                FakeThumbnailLoader().apply {
                    fakeInvoke[uriOne] = {
                        loadingStartedDeferred.complete(Unit)
                        try {
                            awaitCancellation()
                        } catch (e: CancellationException) {
                            cancelledRequests.add(uriOne)
                            throw e
                        }
                    }
                    fakeInvoke[uriTwo] = { createBitmap(200, 200) }
                }
            val testSubject =
                PreviewImageLoader(
                    backgroundScope,
                    cacheSize = 1,
                    defaultPreviewSize = 100,
                    thumbnailLoader,
                    StandardTestDispatcher(scope.testScheduler)
                )

            val jobOne = launch { testSubject.invoke(uriOne, Size(200, 100)) }
            loadingStartedDeferred.await()
            jobOne.cancel()
            scope.runCurrent()

            assertThat(cancelledRequests).isEmpty()

            // second URI should evict the first item from the cache
            testSubject.invoke(uriTwo, Size(200, 100))

            assertThat(thumbnailLoader.invokeCalls).hasSize(2)
            assertThat(cancelledRequests).containsExactly(uriOne)
        }

    @Test
    fun test_nonCachingRequestClientCancels_imageLoadingCancelled() =
        scope.runTest {
            val uri = createUri(1)
            val loadingStartedDeferred = CompletableDeferred<Unit>()
            val cancelledRequests = mutableSetOf<Uri>()
            val thumbnailLoader =
                FakeThumbnailLoader().apply {
                    fakeInvoke[uri] = {
                        loadingStartedDeferred.complete(Unit)
                        try {
                            awaitCancellation()
                        } catch (e: CancellationException) {
                            cancelledRequests.add(uri)
                            throw e
                        }
                    }
                }
            val testSubject =
                PreviewImageLoader(
                    backgroundScope,
                    cacheSize = 1,
                    defaultPreviewSize = 100,
                    thumbnailLoader,
                    StandardTestDispatcher(scope.testScheduler)
                )

            val job = launch { testSubject.invoke(uri, Size(200, 100), caching = false) }
            loadingStartedDeferred.await()
            job.cancel()
            scope.runCurrent()

            assertThat(cancelledRequests).containsExactly(uri)
        }

    @Test
    fun test_requestHigherResImage_newImageLoaded() =
        scope.runTest {
            val uri = createUri(0)
            val thumbnailLoader =
                FakeThumbnailLoader().apply {
                    fakeInvoke[uri] = { size -> createBitmap(size.width, size.height) }
                }
            val testSubject =
                PreviewImageLoader(
                    backgroundScope,
                    1,
                    100,
                    thumbnailLoader,
                    StandardTestDispatcher(scope.testScheduler)
                )

            val b1 = testSubject.invoke(uri, Size(100, 100))
            val b2 = testSubject.invoke(uri, Size(200, 200))
            assertThat(b1).isNotNull()
            assertThat(b1!!.width).isEqualTo(100)
            assertThat(b2).isNotNull()
            assertThat(b2!!.width).isEqualTo(200)
            assertThat(thumbnailLoader.invokeCalls).hasSize(2)
        }

    @Test
    fun test_imageLoadingThrowsException_returnsNull() =
        scope.runTest {
            val uri = createUri(0)
            val thumbnailLoader =
                FakeThumbnailLoader().apply {
                    fakeInvoke[uri] = { throw SecurityException("test") }
                }
            val testSubject =
                PreviewImageLoader(
                    backgroundScope,
                    1,
                    100,
                    thumbnailLoader,
                    StandardTestDispatcher(scope.testScheduler)
                )

            val bitmap = testSubject.invoke(uri, Size(100, 100))
            assertThat(bitmap).isNull()
        }

    @Test
    fun test_requestHigherResImage_cancelsLowerResLoading() =
        scope.runTest {
            val uri = createUri(0)
            val cancelledRequestCount = atomic(0)
            val imageLoadingStarted = CompletableDeferred<Unit>()
            val bitmapDeferred = CompletableDeferred<Bitmap>()
            val thumbnailLoader =
                FakeThumbnailLoader().apply {
                    fakeInvoke[uri] = {
                        imageLoadingStarted.complete(Unit)
                        try {
                            bitmapDeferred.await()
                        } catch (e: CancellationException) {
                            cancelledRequestCount.getAndIncrement()
                            throw e
                        }
                    }
                }
            val testSubject =
                PreviewImageLoader(
                    backgroundScope,
                    1,
                    100,
                    thumbnailLoader,
                    StandardTestDispatcher(scope.testScheduler)
                )

            val lowResSize = 100
            val highResSize = 200
            launch(start = CoroutineStart.UNDISPATCHED) {
                testSubject.invoke(uri, Size(lowResSize, lowResSize))
            }
            imageLoadingStarted.await()
            val result = async { testSubject.invoke(uri, Size(highResSize, highResSize)) }
            runCurrent()
            assertThat(cancelledRequestCount.value).isEqualTo(1)

            bitmapDeferred.complete(createBitmap(highResSize, highResSize))
            val bitmap = result.await()
            assertThat(bitmap).isNotNull()
            assertThat(bitmap!!.width).isEqualTo(highResSize)
            assertThat(thumbnailLoader.invokeCalls).hasSize(2)
        }

    @Test
    fun test_requestLowerResImage_cachedHigherResImageReturned() =
        scope.runTest {
            val uri = createUri(0)
            val thumbnailLoader =
                FakeThumbnailLoader().apply {
                    fakeInvoke[uri] = { size -> createBitmap(size.width, size.height) }
                }
            val lowResSize = 100
            val highResSize = 200
            val testSubject =
                PreviewImageLoader(
                    backgroundScope,
                    1,
                    100,
                    thumbnailLoader,
                    StandardTestDispatcher(scope.testScheduler)
                )

            val b1 = testSubject.invoke(uri, Size(highResSize, highResSize))
            val b2 = testSubject.invoke(uri, Size(lowResSize, lowResSize))
            assertThat(b1).isEqualTo(b2)
            assertThat(b2!!.width).isEqualTo(highResSize)
            assertThat(thumbnailLoader.invokeCalls).hasSize(1)
        }

    @Test
    fun test_incorrectSizeRequested_defaultSizeIsUsed() =
        scope.runTest {
            val uri = createUri(0)
            val defaultPreviewSize = 100
            val thumbnailLoader =
                FakeThumbnailLoader().apply {
                    fakeInvoke[uri] = { size -> createBitmap(size.width, size.height) }
                }
            val testSubject =
                PreviewImageLoader(
                    backgroundScope,
                    cacheSize = 1,
                    defaultPreviewSize,
                    thumbnailLoader,
                    StandardTestDispatcher(scope.testScheduler)
                )

            val b1 = testSubject(uri, Size(0, 0))
            assertThat(b1!!.width).isEqualTo(defaultPreviewSize)

            val largerImageSize = 200
            val b2 = testSubject(uri, Size(largerImageSize, largerImageSize))
            assertThat(b2!!.width).isEqualTo(largerImageSize)
        }

    @Test
    fun test_prePopulateImages_cachesImagesUpToTheCacheSize() =
        scope.runTest {
            val previewSize = Size(100, 100)
            val uris = List(2) { createUri(it) }
            val loadingCount = atomic(0)
            val thumbnailLoader =
                FakeThumbnailLoader().apply {
                    for (uri in uris) {
                        fakeInvoke[uri] = { size ->
                            loadingCount.getAndIncrement()
                            createBitmap(size.width, size.height)
                        }
                    }
                }
            val testSubject =
                PreviewImageLoader(
                    backgroundScope,
                    1,
                    100,
                    thumbnailLoader,
                    StandardTestDispatcher(scope.testScheduler)
                )

            testSubject.prePopulate(uris.map { it to previewSize })
            runCurrent()

            assertThat(loadingCount.value).isEqualTo(1)
            assertThat(thumbnailLoader.invokeCalls).containsExactly(uris[0])

            testSubject(uris[0], previewSize)
            runCurrent()

            assertThat(loadingCount.value).isEqualTo(1)
        }

    @Test
    fun test_oldRecordEvictedFromTheCache() =
        scope.runTest {
            val previewSize = Size(100, 100)
            val uriOne = createUri(1)
            val uriTwo = createUri(2)
            val requestsPerUri = HashMap<Uri, AtomicInteger>()
            val thumbnailLoader =
                FakeThumbnailLoader().apply {
                    for (uri in arrayOf(uriOne, uriTwo)) {
                        fakeInvoke[uri] = { size ->
                            requestsPerUri.getOrPut(uri) { AtomicInteger() }.incrementAndGet()
                            createBitmap(size.width, size.height)
                        }
                    }
                }
            val testSubject =
                PreviewImageLoader(
                    backgroundScope,
                    1,
                    100,
                    thumbnailLoader,
                    StandardTestDispatcher(scope.testScheduler)
                )

            testSubject(uriOne, previewSize)
            testSubject(uriTwo, previewSize)
            testSubject(uriTwo, previewSize)
            testSubject(uriOne, previewSize)

            assertThat(requestsPerUri[uriOne]?.get()).isEqualTo(2)
            assertThat(requestsPerUri[uriTwo]?.get()).isEqualTo(1)
        }

    @Test
    fun test_doNotCacheNulls() =
        scope.runTest {
            val previewSize = Size(100, 100)
            val uri = createUri(1)
            val loadingCount = atomic(0)
            val thumbnailLoader =
                FakeThumbnailLoader().apply {
                    fakeInvoke[uri] = {
                        loadingCount.getAndIncrement()
                        null
                    }
                }
            val testSubject =
                PreviewImageLoader(
                    backgroundScope,
                    1,
                    100,
                    thumbnailLoader,
                    StandardTestDispatcher(scope.testScheduler)
                )

            testSubject(uri, previewSize)
            testSubject(uri, previewSize)

            assertThat(loadingCount.value).isEqualTo(2)
        }

    @Test(expected = CancellationException::class)
    fun invoke_onClosedImageLoaderScope_throwsCancellationException() =
        scope.runTest {
            val uri = createUri(1)
            val thumbnailLoader = FakeThumbnailLoader().apply { fakeInvoke[uri] = { null } }
            val imageLoaderScope = CoroutineScope(coroutineContext)
            val testSubject =
                PreviewImageLoader(
                    imageLoaderScope,
                    1,
                    100,
                    thumbnailLoader,
                    StandardTestDispatcher(scope.testScheduler)
                )
            imageLoaderScope.cancel()
            testSubject(uri, Size(200, 200))
        }

    @Test(expected = CancellationException::class)
    fun invoke_imageLoaderScopeClosedMidflight_throwsCancellationException() =
        scope.runTest {
            val uri = createUri(1)
            val loadingStarted = CompletableDeferred<Unit>()
            val bitmapDeferred = CompletableDeferred<Bitmap?>()
            val thumbnailLoader =
                FakeThumbnailLoader().apply {
                    fakeInvoke[uri] = {
                        loadingStarted.complete(Unit)
                        bitmapDeferred.await()
                    }
                }
            val imageLoaderScope = CoroutineScope(coroutineContext)
            val testSubject =
                PreviewImageLoader(
                    imageLoaderScope,
                    1,
                    100,
                    thumbnailLoader,
                    StandardTestDispatcher(scope.testScheduler)
                )

            launch {
                loadingStarted.await()
                imageLoaderScope.cancel()
            }
            testSubject(uri, Size(200, 200))
        }
}

private fun createUri(id: Int) = Uri.parse("content://org.pkg.app/image-$id.png")

private fun createBitmap(width: Int, height: Int) =
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

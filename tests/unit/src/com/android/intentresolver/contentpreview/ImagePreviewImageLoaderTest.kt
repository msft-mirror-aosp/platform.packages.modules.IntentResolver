/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import com.google.common.truth.Truth.assertThat
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ImagePreviewImageLoaderTest {
    private val imageSize = Size(300, 300)
    private val uriOne = Uri.parse("content://org.package.app/image-1.png")
    private val uriTwo = Uri.parse("content://org.package.app/image-2.png")
    private val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    private val contentResolver =
        mock<ContentResolver> { on { loadThumbnail(any(), any(), anyOrNull()) } doReturn bitmap }
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = UnconfinedTestDispatcher(scheduler)
    private val scope = TestScope(dispatcher)
    private val testSubject =
        ImagePreviewImageLoader(
            dispatcher,
            imageSize.width,
            contentResolver,
            cacheSize = 1,
        )
    private val previewSize = Size(500, 500)

    @Test
    fun prePopulate_cachesImagesUpToTheCacheSize() =
        scope.runTest {
            testSubject.prePopulate(listOf(uriOne to previewSize, uriTwo to previewSize))

            verify(contentResolver, times(1)).loadThumbnail(uriOne, imageSize, null)
            verify(contentResolver, never()).loadThumbnail(uriTwo, imageSize, null)

            testSubject(uriOne, previewSize)
            verify(contentResolver, times(1)).loadThumbnail(uriOne, imageSize, null)
        }

    @Test
    fun invoke_returnCachedImageWhenCalledTwice() =
        scope.runTest {
            testSubject(uriOne, previewSize)
            testSubject(uriOne, previewSize)

            verify(contentResolver, times(1)).loadThumbnail(any(), any(), anyOrNull())
        }

    @Test
    fun invoke_whenInstructed_doesNotCache() =
        scope.runTest {
            testSubject(uriOne, previewSize, false)
            testSubject(uriOne, previewSize, false)

            verify(contentResolver, times(2)).loadThumbnail(any(), any(), anyOrNull())
        }

    @Test
    fun invoke_overlappedRequests_Deduplicate() =
        scope.runTest {
            val dispatcher = StandardTestDispatcher(scheduler)
            val testSubject =
                ImagePreviewImageLoader(
                    dispatcher,
                    imageSize.width,
                    contentResolver,
                    cacheSize = 1,
                )
            coroutineScope {
                launch(start = UNDISPATCHED) { testSubject(uriOne, previewSize, false) }
                launch(start = UNDISPATCHED) { testSubject(uriOne, previewSize, false) }
                scheduler.advanceUntilIdle()
            }

            verify(contentResolver, times(1)).loadThumbnail(any(), any(), anyOrNull())
        }

    @Test
    fun invoke_oldRecordsEvictedFromTheCache() =
        scope.runTest {
            testSubject(uriOne, previewSize)
            testSubject(uriTwo, previewSize)
            testSubject(uriTwo, previewSize)
            testSubject(uriOne, previewSize)

            verify(contentResolver, times(2)).loadThumbnail(uriOne, imageSize, null)
            verify(contentResolver, times(1)).loadThumbnail(uriTwo, imageSize, null)
        }

    @Test
    fun invoke_doNotCacheNulls() =
        scope.runTest {
            whenever(contentResolver.loadThumbnail(any(), any(), anyOrNull())).thenReturn(null)
            testSubject(uriOne, previewSize)
            testSubject(uriOne, previewSize)

            verify(contentResolver, times(2)).loadThumbnail(uriOne, imageSize, null)
        }

    @Test(expected = CancellationException::class)
    fun invoke_onClosedImageLoaderScope_throwsCancellationException() =
        scope.runTest {
            val imageLoaderScope = CoroutineScope(coroutineContext)
            val testSubject =
                ImagePreviewImageLoader(
                    imageLoaderScope,
                    imageSize.width,
                    contentResolver,
                    cacheSize = 1,
                )
            imageLoaderScope.cancel()
            testSubject(uriOne, previewSize)
        }

    @Test(expected = CancellationException::class)
    fun invoke_imageLoaderScopeClosedMidflight_throwsCancellationException() =
        scope.runTest {
            val dispatcher = StandardTestDispatcher(scheduler)
            val imageLoaderScope = CoroutineScope(coroutineContext + dispatcher)
            val testSubject =
                ImagePreviewImageLoader(
                    imageLoaderScope,
                    imageSize.width,
                    contentResolver,
                    cacheSize = 1,
                )
            coroutineScope {
                val deferred =
                    async(start = UNDISPATCHED) { testSubject(uriOne, previewSize, false) }
                imageLoaderScope.cancel()
                scheduler.advanceUntilIdle()
                deferred.await()
            }
        }

    @Test
    fun invoke_multipleCallsWithDifferentCacheInstructions_cachingPrevails() =
        scope.runTest {
            val dispatcher = StandardTestDispatcher(scheduler)
            val imageLoaderScope = CoroutineScope(coroutineContext + dispatcher)
            val testSubject =
                ImagePreviewImageLoader(
                    imageLoaderScope,
                    imageSize.width,
                    contentResolver,
                    cacheSize = 1,
                )
            coroutineScope {
                launch(start = UNDISPATCHED) { testSubject(uriOne, previewSize, false) }
                launch(start = UNDISPATCHED) { testSubject(uriOne, previewSize, true) }
                scheduler.advanceUntilIdle()
            }
            testSubject(uriOne, previewSize, true)

            verify(contentResolver, times(1)).loadThumbnail(uriOne, imageSize, null)
        }

    @Test
    fun invoke_semaphoreGuardsContentResolverCalls() =
        scope.runTest {
            val contentResolver =
                mock<ContentResolver> {
                    on { loadThumbnail(any(), any(), anyOrNull()) } doThrow
                        SecurityException("test")
                }
            val acquireCount = AtomicInteger()
            val releaseCount = AtomicInteger()
            val testSemaphore =
                object : Semaphore {
                    override val availablePermits: Int
                        get() = error("Unexpected invocation")

                    override suspend fun acquire() {
                        acquireCount.getAndIncrement()
                    }

                    override fun tryAcquire(): Boolean {
                        error("Unexpected invocation")
                    }

                    override fun release() {
                        releaseCount.getAndIncrement()
                    }
                }

            val testSubject =
                ImagePreviewImageLoader(
                    CoroutineScope(coroutineContext + dispatcher),
                    imageSize.width,
                    contentResolver,
                    cacheSize = 1,
                    testSemaphore,
                )
            testSubject(uriOne, previewSize, false)

            verify(contentResolver, times(1)).loadThumbnail(uriOne, imageSize, null)
            assertThat(acquireCount.get()).isEqualTo(1)
            assertThat(releaseCount.get()).isEqualTo(1)
        }

    @Test
    fun invoke_semaphoreIsReleasedAfterContentResolverFailure() =
        scope.runTest {
            val semaphoreDeferred = CompletableDeferred<Unit>()
            val releaseCount = AtomicInteger()
            val testSemaphore =
                object : Semaphore {
                    override val availablePermits: Int
                        get() = error("Unexpected invocation")

                    override suspend fun acquire() {
                        semaphoreDeferred.await()
                    }

                    override fun tryAcquire(): Boolean {
                        error("Unexpected invocation")
                    }

                    override fun release() {
                        releaseCount.getAndIncrement()
                    }
                }

            val testSubject =
                ImagePreviewImageLoader(
                    CoroutineScope(coroutineContext + dispatcher),
                    imageSize.width,
                    contentResolver,
                    cacheSize = 1,
                    testSemaphore,
                )
            launch(start = UNDISPATCHED) { testSubject(uriOne, previewSize, false) }

            verify(contentResolver, never()).loadThumbnail(any(), any(), anyOrNull())

            semaphoreDeferred.complete(Unit)

            verify(contentResolver, times(1)).loadThumbnail(uriOne, imageSize, null)
            assertThat(releaseCount.get()).isEqualTo(1)
        }

    @Test
    fun invoke_multipleSimultaneousCalls_limitOnNumberOfSimultaneousOutgoingCallsIsRespected() =
        scope.runTest {
            val requestCount = 4
            val thumbnailCallsCdl = CountDownLatch(requestCount)
            val pendingThumbnailCalls = ArrayDeque<CountDownLatch>()
            val contentResolver =
                mock<ContentResolver> {
                    on { loadThumbnail(any(), any(), anyOrNull()) } doAnswer
                        {
                            val latch = CountDownLatch(1)
                            synchronized(pendingThumbnailCalls) {
                                pendingThumbnailCalls.offer(latch)
                            }
                            thumbnailCallsCdl.countDown()
                            assertTrue("Timeout waiting thumbnail calls", latch.await(1, SECONDS))
                            bitmap
                        }
                }
            val name = "LoadImage"
            val maxSimultaneousRequests = 2
            val threadsStartedCdl = CountDownLatch(requestCount)
            val dispatcher = NewThreadDispatcher(name) { threadsStartedCdl.countDown() }
            val testSubject =
                ImagePreviewImageLoader(
                    CoroutineScope(coroutineContext + dispatcher + CoroutineName(name)),
                    imageSize.width,
                    contentResolver,
                    cacheSize = 1,
                    maxSimultaneousRequests,
                )
            coroutineScope {
                repeat(requestCount) {
                    launch {
                        testSubject(Uri.parse("content://org.pkg.app/image-$it.png"), previewSize)
                    }
                }
                yield()
                // wait for all requests to be dispatched
                assertThat(threadsStartedCdl.await(5, SECONDS)).isTrue()

                assertThat(thumbnailCallsCdl.await(100, MILLISECONDS)).isFalse()
                synchronized(pendingThumbnailCalls) {
                    assertThat(pendingThumbnailCalls.size).isEqualTo(maxSimultaneousRequests)
                }

                pendingThumbnailCalls.poll()?.countDown()
                assertThat(thumbnailCallsCdl.await(100, MILLISECONDS)).isFalse()
                synchronized(pendingThumbnailCalls) {
                    assertThat(pendingThumbnailCalls.size).isEqualTo(maxSimultaneousRequests)
                }

                pendingThumbnailCalls.poll()?.countDown()
                assertThat(thumbnailCallsCdl.await(100, MILLISECONDS)).isTrue()
                synchronized(pendingThumbnailCalls) {
                    assertThat(pendingThumbnailCalls.size).isEqualTo(maxSimultaneousRequests)
                }
                for (cdl in pendingThumbnailCalls) {
                    cdl.countDown()
                }
            }
        }
}

private class NewThreadDispatcher(
    private val coroutineName: String,
    private val launchedCallback: () -> Unit
) : CoroutineDispatcher() {
    override fun isDispatchNeeded(context: CoroutineContext): Boolean = true

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        Thread {
                if (coroutineName == context[CoroutineName.Key]?.name) {
                    launchedCallback()
                }
                block.run()
            }
            .start()
    }
}

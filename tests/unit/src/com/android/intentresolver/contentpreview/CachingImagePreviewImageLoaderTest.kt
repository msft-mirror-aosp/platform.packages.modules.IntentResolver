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
import android.util.Size
import com.google.common.truth.Truth.assertThat
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CachingImagePreviewImageLoaderTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val testJobTime = 100.milliseconds
    private val testCacheSize = 4
    private val testMaxConcurrency = 2
    private val testTimeToFillCache =
        testJobTime * ceil((testCacheSize).toFloat() / testMaxConcurrency.toFloat()).roundToInt()
    private val testUris =
        List(5) { Uri.fromParts("TestScheme$it", "TestSsp$it", "TestFragment$it") }
    private val previewSize = Size(500, 500)
    private val testTimeToLoadAllUris =
        testJobTime * ceil((testUris.size).toFloat() / testMaxConcurrency.toFloat()).roundToInt()
    private val testBitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ALPHA_8)
    private val fakeThumbnailLoader =
        FakeThumbnailLoader().apply {
            testUris.forEach {
                fakeInvoke[it] = {
                    delay(testJobTime)
                    testBitmap
                }
            }
        }

    private val imageLoader =
        CachingImagePreviewImageLoader(
            scope = testScope.backgroundScope,
            bgDispatcher = testDispatcher,
            thumbnailLoader = fakeThumbnailLoader,
            cacheSize = testCacheSize,
            maxConcurrency = testMaxConcurrency,
        )

    @Test
    fun loadImage_notCached_callsThumbnailLoader() =
        testScope.runTest {
            // Arrange
            var result: Bitmap? = null

            // Act
            imageLoader.loadImage(testScope, testUris[0], previewSize) { result = it }
            advanceTimeBy(testJobTime)
            runCurrent()

            // Assert
            assertThat(fakeThumbnailLoader.invokeCalls).containsExactly(testUris[0])
            assertThat(result).isSameInstanceAs(testBitmap)
        }

    @Test
    fun loadImage_cached_usesCachedValue() =
        testScope.runTest {
            // Arrange
            imageLoader.loadImage(testScope, testUris[0], previewSize) {}
            advanceTimeBy(testJobTime)
            runCurrent()
            fakeThumbnailLoader.invokeCalls.clear()
            var result: Bitmap? = null

            // Act
            imageLoader.loadImage(testScope, testUris[0], previewSize) { result = it }
            advanceTimeBy(testJobTime)
            runCurrent()

            // Assert
            assertThat(fakeThumbnailLoader.invokeCalls).isEmpty()
            assertThat(result).isSameInstanceAs(testBitmap)
        }

    @Test
    fun loadImage_error_returnsNull() =
        testScope.runTest {
            // Arrange
            fakeThumbnailLoader.fakeInvoke[testUris[0]] = {
                delay(testJobTime)
                throw RuntimeException("Test exception")
            }
            var result: Bitmap? = testBitmap

            // Act
            imageLoader.loadImage(testScope, testUris[0], previewSize) { result = it }
            advanceTimeBy(testJobTime)
            runCurrent()

            // Assert
            assertThat(fakeThumbnailLoader.invokeCalls).containsExactly(testUris[0])
            assertThat(result).isNull()
        }

    @Test
    fun loadImage_uncached_limitsConcurrency() =
        testScope.runTest {
            // Arrange
            val results = mutableListOf<Bitmap?>()
            assertThat(testUris.size).isGreaterThan(testMaxConcurrency)

            // Act
            testUris.take(testMaxConcurrency + 1).forEach { uri ->
                imageLoader.loadImage(testScope, uri, previewSize) { results.add(it) }
            }

            // Assert
            assertThat(results).isEmpty()
            advanceTimeBy(testJobTime)
            runCurrent()
            assertThat(results).hasSize(testMaxConcurrency)
            advanceTimeBy(testJobTime)
            runCurrent()
            assertThat(results).hasSize(testMaxConcurrency + 1)
            assertThat(results)
                .containsExactlyElementsIn(List(testMaxConcurrency + 1) { testBitmap })
        }

    @Test
    fun loadImage_cacheEvicted_cancelsLoadAndReturnsNull() =
        testScope.runTest {
            // Arrange
            val results = MutableList<Bitmap?>(testUris.size) { null }
            assertThat(testUris.size).isGreaterThan(testCacheSize)

            // Act
            imageLoader.loadImage(testScope, testUris[0], previewSize) { results[0] = it }
            runCurrent()
            testUris.indices.drop(1).take(testCacheSize).forEach { i ->
                imageLoader.loadImage(testScope, testUris[i], previewSize) { results[i] = it }
            }
            advanceTimeBy(testTimeToFillCache)
            runCurrent()

            // Assert
            assertThat(fakeThumbnailLoader.invokeCalls).containsExactlyElementsIn(testUris)
            assertThat(results)
                .containsExactlyElementsIn(
                    List(testUris.size) { index -> if (index == 0) null else testBitmap }
                )
                .inOrder()
            assertThat(fakeThumbnailLoader.unfinishedInvokeCount).isEqualTo(1)
        }

    @Test
    fun prePopulate_fillsCache() =
        testScope.runTest {
            // Arrange
            val fullCacheUris = testUris.take(testCacheSize)
            assertThat(fullCacheUris).hasSize(testCacheSize)

            // Act
            imageLoader.prePopulate(fullCacheUris.map { it to previewSize })
            advanceTimeBy(testTimeToFillCache)
            runCurrent()

            // Assert
            assertThat(fakeThumbnailLoader.invokeCalls).containsExactlyElementsIn(fullCacheUris)

            // Act
            fakeThumbnailLoader.invokeCalls.clear()
            imageLoader.prePopulate(fullCacheUris.map { it to previewSize })
            advanceTimeBy(testTimeToFillCache)
            runCurrent()

            // Assert
            assertThat(fakeThumbnailLoader.invokeCalls).isEmpty()
        }

    @Test
    fun prePopulate_greaterThanCacheSize_fillsCacheThenDropsRemaining() =
        testScope.runTest {
            // Arrange
            assertThat(testUris.size).isGreaterThan(testCacheSize)

            // Act
            imageLoader.prePopulate(testUris.map { it to previewSize })
            advanceTimeBy(testTimeToLoadAllUris)
            runCurrent()

            // Assert
            assertThat(fakeThumbnailLoader.invokeCalls)
                .containsExactlyElementsIn(testUris.take(testCacheSize))

            // Act
            fakeThumbnailLoader.invokeCalls.clear()
            imageLoader.prePopulate(testUris.map { it to previewSize })
            advanceTimeBy(testTimeToLoadAllUris)
            runCurrent()

            // Assert
            assertThat(fakeThumbnailLoader.invokeCalls).isEmpty()
        }

    @Test
    fun prePopulate_fewerThatCacheSize_loadsTheGiven() =
        testScope.runTest {
            // Arrange
            val unfilledCacheUris = testUris.take(testMaxConcurrency)
            assertThat(unfilledCacheUris.size).isLessThan(testCacheSize)

            // Act
            imageLoader.prePopulate(unfilledCacheUris.map { it to previewSize })
            advanceTimeBy(testJobTime)
            runCurrent()

            // Assert
            assertThat(fakeThumbnailLoader.invokeCalls).containsExactlyElementsIn(unfilledCacheUris)

            // Act
            fakeThumbnailLoader.invokeCalls.clear()
            imageLoader.prePopulate(unfilledCacheUris.map { it to previewSize })
            advanceTimeBy(testJobTime)
            runCurrent()

            // Assert
            assertThat(fakeThumbnailLoader.invokeCalls).isEmpty()
        }

    @Test
    fun invoke_uncached_alwaysCallsTheThumbnailLoader() =
        testScope.runTest {
            // Arrange

            // Act
            imageLoader.invoke(testUris[0], previewSize, caching = false)
            imageLoader.invoke(testUris[0], previewSize, caching = false)
            advanceTimeBy(testJobTime)
            runCurrent()

            // Assert
            assertThat(fakeThumbnailLoader.invokeCalls).containsExactly(testUris[0], testUris[0])
        }

    @Test
    fun invoke_cached_usesTheCacheWhenPossible() =
        testScope.runTest {
            // Arrange

            // Act
            imageLoader.invoke(testUris[0], previewSize, caching = true)
            imageLoader.invoke(testUris[0], previewSize, caching = true)
            advanceTimeBy(testJobTime)
            runCurrent()

            // Assert
            assertThat(fakeThumbnailLoader.invokeCalls).containsExactly(testUris[0])
        }
}

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

package com.android.intentresolver.util

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.yield

/** Like [Iterable.map] but executes each [block] invocation in a separate coroutine. */
suspend fun <A, B> Iterable<A>.mapParallel(
    parallelism: Int? = null,
    block: suspend (A) -> B,
): List<B> =
    parallelism?.let { permits ->
        withSemaphore(permits = permits) { mapParallel { withPermit { block(it) } } }
    }
        ?: mapParallel(block)

/** Like [Iterable.map] but executes each [block] invocation in a separate coroutine. */
suspend fun <A, B> Sequence<A>.mapParallel(
    parallelism: Int? = null,
    block: suspend (A) -> B,
): List<B> = asIterable().mapParallel(parallelism, block)

private suspend fun <A, B> Iterable<A>.mapParallel(block: suspend (A) -> B): List<B> =
    coroutineScope {
        map {
                async {
                    yield()
                    block(it)
                }
            }
            .awaitAll()
    }

suspend fun <A, B> Iterable<A>.mapParallelIndexed(
    parallelism: Int? = null,
    block: suspend (Int, A) -> B,
): List<B> =
    parallelism?.let { permits ->
        withSemaphore(permits = permits) {
            mapParallelIndexed { idx, item -> withPermit { block(idx, item) } }
        }
    } ?: mapParallelIndexed(block)

private suspend fun <A, B> Iterable<A>.mapParallelIndexed(block: suspend (Int, A) -> B): List<B> =
    coroutineScope {
        mapIndexed { index, item ->
                async {
                    yield()
                    block(index, item)
                }
            }
            .awaitAll()
    }

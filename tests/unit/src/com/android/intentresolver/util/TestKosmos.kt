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

import com.android.intentresolver.backgroundDispatcher
import com.android.systemui.kosmos.Kosmos
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent

fun Kosmos.runTest(
    dispatcher: TestDispatcher = StandardTestDispatcher(),
    block: suspend KosmosTestScope.() -> Unit,
) {
    val kosmos = this
    backgroundDispatcher = dispatcher
    kotlinx.coroutines.test.runTest(dispatcher) { KosmosTestScope(kosmos, this).block() }
}

fun runKosmosTest(
    dispatcher: TestDispatcher = StandardTestDispatcher(),
    block: suspend KosmosTestScope.() -> Unit,
) {
    Kosmos().runTest(dispatcher, block)
}

class KosmosTestScope(
    kosmos: Kosmos,
    private val testScope: TestScope,
) : Kosmos by kosmos {
    val backgroundScope
        get() = testScope.backgroundScope

    @ExperimentalCoroutinesApi fun runCurrent() = testScope.runCurrent()
}

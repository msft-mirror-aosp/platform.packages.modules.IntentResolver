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

package com.android.intentresolver.contentpreview.payloadtoggle.domain.model

/** Represents an either updated value or the absence of it */
sealed interface ValueUpdate<out T> {
    data class Value<T>(val value: T) : ValueUpdate<T>
    data object Absent : ValueUpdate<Nothing>
}

/** Return encapsulated value if this instance represent Value or `default` if Absent */
fun <T> ValueUpdate<T>.getOrDefault(default: T): T =
    when (this) {
        is ValueUpdate.Value -> value
        is ValueUpdate.Absent -> default
    }

/** Executes the `block` with encapsulated value if this instance represents Value */
inline fun <T> ValueUpdate<T>.onValue(block: (T) -> Unit) {
    if (this is ValueUpdate.Value) {
        block(value)
    }
}

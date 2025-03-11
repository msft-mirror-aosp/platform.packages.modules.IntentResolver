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

package com.android.intentresolver.contentpreview.payloadtoggle.shared.model

/** Unique identifier for preview items. */
sealed interface PreviewKey {

    private data class Temp(override val key: Int, override val isFinal: Boolean = false) :
        PreviewKey

    private data class Final(override val key: Int, override val isFinal: Boolean = true) :
        PreviewKey

    /** The identifier, must be unique among like keys types */
    val key: Int
    /** Whether this key is final or temporary. */
    val isFinal: Boolean

    companion object {
        /**
         * Creates a temporary key.
         *
         * This is used for the initial preview items until final keys can be generated, at which
         * point it is replaced with a final key.
         */
        fun temp(key: Int): PreviewKey = Temp(key)

        /**
         * Creates a final key.
         *
         * This is used for all preview items other than the initial preview items.
         */
        fun final(key: Int): PreviewKey = Final(key)
    }
}

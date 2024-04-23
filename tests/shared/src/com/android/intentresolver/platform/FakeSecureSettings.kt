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

package com.android.intentresolver.platform

/**
 * Creates a SecureSettings instance with predefined values:
 *
 *     val settings = fakeSecureSettings {
 *         putString("stringValue", "example")
 *         putInt("intValue", 42)
 *     }
 */
fun fakeSecureSettings(block: FakeSecureSettings.Builder.() -> Unit): SecureSettings {
    return FakeSecureSettings.Builder().apply(block).build()
}

/** An in memory implementation of [SecureSettings]. */
class FakeSecureSettings private constructor(private val map: Map<String, String>) :
    SecureSettings {

    override fun getString(name: String): String? = map[name]
    override fun getInt(name: String): Int? = getString(name)?.toIntOrNull()
    override fun getLong(name: String): Long? = getString(name)?.toLongOrNull()
    override fun getFloat(name: String): Float? = getString(name)?.toFloatOrNull()

    class Builder {
        private val map = mutableMapOf<String, String>()

        fun putString(name: String, value: String) {
            map[name] = value
        }
        fun putInt(name: String, value: Int) {
            map[name] = value.toString()
        }
        fun putLong(name: String, value: Long) {
            map[name] = value.toString()
        }
        fun putFloat(name: String, value: Float) {
            map[name] = value.toString()
        }

        fun build(): SecureSettings {
            return FakeSecureSettings(map.toMap())
        }
    }
}

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

import android.provider.Settings.SettingNotFoundException

/**
 * A component which provides access to values from [android.provider.Settings.Secure].
 *
 * All methods return nullable types instead of throwing [SettingNotFoundException] which yields
 * cleaner, more idiomatic Kotlin code:
 *
 * // apply a default: val foo = settings.getInt(FOO) ?: DEFAULT_FOO
 *
 * // assert if missing: val required = settings.getInt(REQUIRED_VALUE) ?: error("required value
 * missing")
 */
interface SecureSettings {

    fun getString(name: String): String?

    fun getInt(name: String): Int?

    fun getLong(name: String): Long?

    fun getFloat(name: String): Float?
}

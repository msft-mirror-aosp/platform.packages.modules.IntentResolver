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

/** A proxy to Settings.Global */
interface GlobalSettings : SettingsProxy

/** A proxy to Settings.Secure */
interface SecureSettings : SettingsProxy

/** A proxy to Settings.System */
interface SystemSettings : SettingsProxy

/** A generic Settings proxy interface */
sealed interface SettingsProxy {

    /** Returns the String value set for the given settings key, or null if no value exists. */
    fun getStringOrNull(name: String): String?

    /**
     * Writes a new string value for the given settings key.
     *
     * @return true if the value did not previously exist or was modified
     */
    fun putString(name: String, value: String): Boolean

    /**
     * Returns the Int value for the given settings key or null if no value exists or it cannot be
     * interpreted as an Int.
     */
    fun getIntOrNull(name: String): Int? = getStringOrNull(name)?.toIntOrNull()

    /**
     * Writes a new int value for the given settings key.
     *
     * @return true if the value did not previously exist or was modified
     */
    fun putInt(name: String, value: Int): Boolean = putString(name, value.toString())

    /**
     * Returns the Boolean value for the given settings key or null if no value exists or it cannot
     * be interpreted as a Boolean.
     */
    fun getBooleanOrNull(name: String): Boolean? = getIntOrNull(name)?.let { it != 0 }

    /**
     * Writes a new Boolean value for the given settings key.
     *
     * @return true if the value did not previously exist or was modified
     */
    fun putBoolean(name: String, value: Boolean): Boolean = putInt(name, if (value) 1 else 0)

    /**
     * Returns the Long value for the given settings key or null if no value exists or it cannot be
     * interpreted as a Long.
     */
    fun getLongOrNull(name: String): Long? = getStringOrNull(name)?.toLongOrNull()

    /**
     * Writes a new Long value for the given settings key.
     *
     * @return true if the value did not previously exist or was modified
     */
    fun putLong(name: String, value: Long): Boolean = putString(name, value.toString())

    /**
     * Returns the Float value for the given settings key or null if no value exists or it cannot be
     * interpreted as a Float.
     */
    fun getFloatOrNull(name: String): Float? = getStringOrNull(name)?.toFloatOrNull()

    /**
     * Writes a new float value for the given settings key.
     *
     * @return true if the value did not previously exist or was modified
     */
    fun putFloat(name: String, value: Float): Boolean = putString(name, value.toString())
}

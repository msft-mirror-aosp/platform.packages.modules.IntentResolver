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

import android.content.ContentResolver
import android.provider.Settings
import javax.inject.Inject

/**
 * Implements [SecureSettings] backed by Settings.Secure and a ContentResolver.
 *
 * These methods make Binder calls and may block, so use on the Main thread should be avoided.
 */
class PlatformSecureSettings @Inject constructor(private val resolver: ContentResolver) :
    SecureSettings {

    override fun getString(name: String): String? {
        return Settings.Secure.getString(resolver, name)
    }

    override fun getInt(name: String): Int? {
        return runCatching { Settings.Secure.getInt(resolver, name) }.getOrNull()
    }

    override fun getLong(name: String): Long? {
        return runCatching { Settings.Secure.getLong(resolver, name) }.getOrNull()
    }

    override fun getFloat(name: String): Float? {
        return runCatching { Settings.Secure.getFloat(resolver, name) }.getOrNull()
    }
}

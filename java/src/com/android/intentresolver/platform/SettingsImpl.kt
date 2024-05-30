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

object SettingsImpl {
    /** An implementation of GlobalSettings which forwards to [Settings.Global] */
    class Global @Inject constructor(private val contentResolver: ContentResolver) :
        GlobalSettings {
        override fun getStringOrNull(name: String): String? {
            return Settings.Global.getString(contentResolver, name)
        }

        override fun putString(name: String, value: String): Boolean {
            return Settings.Global.putString(contentResolver, name, value)
        }
    }

    /** An implementation of SecureSettings which forwards to [Settings.Secure] */
    class Secure @Inject constructor(private val contentResolver: ContentResolver) :
        SecureSettings {
        override fun getStringOrNull(name: String): String? {
            return Settings.Secure.getString(contentResolver, name)
        }

        override fun putString(name: String, value: String): Boolean {
            return Settings.Secure.putString(contentResolver, name, value)
        }
    }

    /** An implementation of SystemSettings which forwards to [Settings.System] */
    class System @Inject constructor(private val contentResolver: ContentResolver) :
        SystemSettings {
        override fun getStringOrNull(name: String): String? {
            return Settings.System.getString(contentResolver, name)
        }

        override fun putString(name: String, value: String): Boolean {
            return Settings.System.putString(contentResolver, name, value)
        }
    }
}

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

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FakeSettingsTest {

    private val settings: FakeSettings = fakeSettings {
        putInt(intKey, intVal)
        putString(stringKey, stringVal)
        putFloat(floatKey, floatVal)
        putLong(longKey, longVal)
    }

    @Test
    fun testExpectedValues_returned() {
        assertThat(settings.getIntOrNull(intKey)).isEqualTo(intVal)
        assertThat(settings.getStringOrNull(stringKey)).isEqualTo(stringVal)
        assertThat(settings.getFloatOrNull(floatKey)).isEqualTo(floatVal)
        assertThat(settings.getLongOrNull(longKey)).isEqualTo(longVal)
    }

    @Test
    fun testUndefinedValues_returnNull() {
        assertThat(settings.getIntOrNull("unknown")).isNull()
        assertThat(settings.getStringOrNull("unknown")).isNull()
        assertThat(settings.getFloatOrNull("unknown")).isNull()
        assertThat(settings.getLongOrNull("unknown")).isNull()
    }

    /**
     * FakeSecureSettings models the real secure settings by storing values in String form. The
     * value is returned if/when it can be parsed from the string value, otherwise null.
     */
    @Test
    fun testMismatchedTypes() {
        assertThat(settings.getStringOrNull(intKey)).isEqualTo(intVal.toString())
        assertThat(settings.getStringOrNull(floatKey)).isEqualTo(floatVal.toString())
        assertThat(settings.getStringOrNull(longKey)).isEqualTo(longVal.toString())

        assertThat(settings.getIntOrNull(stringKey)).isNull()
        assertThat(settings.getLongOrNull(stringKey)).isNull()
        assertThat(settings.getFloatOrNull(stringKey)).isNull()

        assertThat(settings.getIntOrNull(longKey)).isNull()
        assertThat(settings.getFloatOrNull(longKey)).isWithin(0.00001f).of(Long.MAX_VALUE.toFloat())

        assertThat(settings.getLongOrNull(floatKey)).isNull()
        assertThat(settings.getIntOrNull(floatKey)).isNull()
    }

    companion object Data {
        const val intKey = "int"
        const val intVal = Int.MAX_VALUE

        const val stringKey = "string"
        const val stringVal = "String"

        const val floatKey = "float"
        const val floatVal = Float.MAX_VALUE

        const val longKey = "long"
        const val longVal = Long.MAX_VALUE
    }
}

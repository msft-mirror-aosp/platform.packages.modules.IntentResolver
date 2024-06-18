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

package com.android.intentresolver.validation.types

import com.android.intentresolver.validation.Importance.CRITICAL
import com.android.intentresolver.validation.Importance.WARNING
import com.android.intentresolver.validation.Invalid
import com.android.intentresolver.validation.NoValue
import com.android.intentresolver.validation.Valid
import com.android.intentresolver.validation.ValueIsWrongType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SimpleValueTest {

    /** Test for validation success when the value is present and the correct type. */
    @Test
    fun present() {
        val keyValidator = SimpleValue("key", expected = Double::class)
        val values = mapOf("key" to Math.PI)

        val result = keyValidator.validate(values::get, CRITICAL)

        assertThat(result).isInstanceOf(Valid::class.java)
        result as Valid<Double>
        assertThat(result.value).isEqualTo(Math.PI)
    }

    /** Test for validation success when the value is present and the correct type. */
    @Test
    fun wrongType() {
        val keyValidator = SimpleValue("key", expected = Double::class)
        val values = mapOf("key" to "Apple Pie")

        val result = keyValidator.validate(values::get, CRITICAL)

        assertThat(result).isInstanceOf(Invalid::class.java)
        result as Invalid<Double>
        assertThat(result.errors)
            .containsExactly(
                ValueIsWrongType(
                    "key",
                    importance = CRITICAL,
                    actualType = String::class,
                    allowedTypes = listOf(Double::class)
                )
            )
    }

    /** Test the failure result when the value is missing. */
    @Test
    fun missing() {
        val keyValidator = SimpleValue("key", expected = Double::class)

        val result = keyValidator.validate(source = { null }, CRITICAL)

        assertThat(result).isInstanceOf(Invalid::class.java)
        result as Invalid<Double>

        assertThat(result.errors).containsExactly(NoValue("key", CRITICAL, Double::class))
    }

    /** Test the failure result when the value is missing. */
    @Test
    fun optional() {
        val keyValidator = SimpleValue("key", expected = Double::class)

        val result = keyValidator.validate(source = { null }, WARNING)

        assertThat(result).isInstanceOf(Invalid::class.java)
        result as Invalid<Double>

        // Note: As single optional validation result, the return must be Invalid
        // when there is no value to return, but no errors will be reported because
        // an optional value cannot be "missing".
        assertThat(result.errors).isEmpty()
    }
}

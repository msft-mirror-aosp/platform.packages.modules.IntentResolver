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

package com.android.intentresolver.validation

import com.android.intentresolver.validation.types.value
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import org.junit.Test

class ValidationTest {

    /** Test required values. */
    @Test
    fun required_valuePresent() {
        val result: ValidationResult<String> =
            validateFrom({ 1 }) {
                val required: Int = required(value<Int>("key"))
                "return value: $required"
            }

        assertThat(result).isInstanceOf(Valid::class.java)
        result as Valid<String>

        assertThat(result.value).isEqualTo("return value: 1")
        assertThat(result.warnings).isEmpty()
    }

    /** Test reporting of absent required values. */
    @Test
    fun required_valueAbsent() {
        val result: ValidationResult<String> =
            validateFrom({ null }) {
                required(value<Int>("key"))
                fail("'required' should have thrown an exception")
                "return value"
            }

        assertThat(result).isInstanceOf(Invalid::class.java)
        result as Invalid<String>

        assertThat(result.errors).containsExactly(NoValue("key", Importance.CRITICAL, Int::class))
    }

    /** Test optional values are ignored when absent. */
    @Test
    fun optional_valuePresent() {
        val result: ValidationResult<String> =
            validateFrom({ 1 }) {
                val optional: Int? = optional(value<Int>("key"))
                "return value: $optional"
            }

        assertThat(result).isInstanceOf(Valid::class.java)
        result as Valid<String>

        assertThat(result.value).isEqualTo("return value: 1")
        assertThat(result.warnings).isEmpty()
    }

    /** Test optional values are ignored when absent. */
    @Test
    fun optional_valueAbsent() {
        val result: ValidationResult<String> =
            validateFrom({ null }) {
                val optional: String? = optional(value<String>("key"))
                "return value: $optional"
            }

        assertThat(result).isInstanceOf(Valid::class.java)
        result as Valid<String>

        assertThat(result.value).isEqualTo("return value: null")
        assertThat(result.warnings).isEmpty()
    }

    /** Test reporting of ignored values. */
    @Test
    fun ignored_valuePresent() {
        val result: ValidationResult<String> =
            validateFrom(mapOf("key" to 1)::get) {
                ignored(value<Int>("key"), "no longer supported")
                "result value"
            }

        assertThat(result).isInstanceOf(Valid::class.java)
        result as Valid<String>

        assertThat(result.value).isEqualTo("result value")
        assertThat(result.warnings).containsExactly(IgnoredValue("key", "no longer supported"))
    }

    /** Test reporting of ignored values. */
    @Test
    fun ignored_valueAbsent() {
        val result: ValidationResult<String> =
            validateFrom({ null }) {
                ignored(value<Int>("key"), "ignored when option foo is set")
                "result value"
            }
        assertThat(result).isInstanceOf(Valid::class.java)
        result as Valid<String>

        assertThat(result.value).isEqualTo("result value")
        assertThat(result.warnings).isEmpty()
    }

    /** Test handling of exceptions in the validation function. */
    @Test
    fun thrown_exception() {
        val result: ValidationResult<String> = validateFrom({ null }) { error("something") }

        assertThat(result).isInstanceOf(Invalid::class.java)
        result as Invalid<String>

        val errorType = result.errors.map { it::class }.first()
        assertThat(errorType).isEqualTo(UncaughtException::class)
    }
}

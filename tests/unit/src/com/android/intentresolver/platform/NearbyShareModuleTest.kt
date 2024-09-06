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

import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import android.testing.TestableResources
import androidx.test.platform.app.InstrumentationRegistry
import com.android.intentresolver.R
import com.google.common.truth.Truth8.assertThat
import org.junit.Before
import org.junit.Test

class NearbyShareModuleTest {

    lateinit var context: Context

    /** Create Resources with overridden values. */
    private fun Context.fakeResources(
        config: Configuration? = null,
        block: TestableResources.() -> Unit
    ) =
        TestableResources(resources)
            .apply { config?.let { overrideConfiguration(it) } }
            .apply(block)
            .resources

    @Before
    fun setup() {
        val instr = InstrumentationRegistry.getInstrumentation()
        context = instr.context
    }

    @Test
    fun valueIsAbsent_whenUnset() {
        val secureSettings: SecureSettings = fakeSettings {}
        val resources =
            context.fakeResources { addOverride(R.string.config_defaultNearbySharingComponent, "") }

        val componentName = NearbyShareModule.nearbyShareComponent(resources, secureSettings)
        assertThat(componentName).isEmpty()
    }

    @Test
    fun defaultValue_readFromResources() {
        val secureSettings: SecureSettings = fakeSettings {}
        val resources =
            context.fakeResources {
                addOverride(
                    R.string.config_defaultNearbySharingComponent,
                    "com.example/.ComponentName"
                )
            }

        val nearbyShareComponent = NearbyShareModule.nearbyShareComponent(resources, secureSettings)

        assertThat(nearbyShareComponent)
            .hasValue(ComponentName.unflattenFromString("com.example/.ComponentName"))
    }

    @Test
    fun secureSettings_overridesDefault() {
        val secureSettings: SecureSettings = fakeSettings {
            putString(Settings.Secure.NEARBY_SHARING_COMPONENT, "com.example/.BComponent")
        }
        val resources =
            context.fakeResources {
                addOverride(
                    R.string.config_defaultNearbySharingComponent,
                    "com.example/.AComponent"
                )
            }

        val nearbyShareComponent = NearbyShareModule.nearbyShareComponent(resources, secureSettings)

        assertThat(nearbyShareComponent)
            .hasValue(ComponentName.unflattenFromString("com.example/.BComponent"))
    }
}

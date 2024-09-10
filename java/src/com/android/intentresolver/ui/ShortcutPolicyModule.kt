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

package com.android.intentresolver.ui

import android.content.res.Resources
import android.provider.DeviceConfig
import com.android.intentresolver.R
import com.android.intentresolver.inject.ApplicationOwned
import com.android.internal.config.sysui.SystemUiDeviceConfigFlags
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
annotation class AppShortcutLimit

@Qualifier
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
annotation class EnforceShortcutLimit

@Qualifier
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
annotation class ShortcutRowLimit

@Module
@InstallIn(SingletonComponent::class)
object ShortcutPolicyModule {
    /**
     * Defines the limit for the number of shortcut targets provided for any single app.
     *
     * This value applies to both results from Shortcut-service and app-provided targets on a
     * per-package basis.
     */
    @Provides
    @Singleton
    @AppShortcutLimit
    fun appShortcutLimit(@ApplicationOwned resources: Resources): Int {
        return resources.getInteger(R.integer.config_maxShortcutTargetsPerApp)
    }

    /**
     * Once this value is no longer necessary it should be replaced in tests with simply replacing
     * [AppShortcutLimit]:
     * ```
     *    @BindValue
     *    @AppShortcutLimit
     *    var shortcutLimit = Int.MAX_VALUE
     * ```
     */
    @Provides
    @Singleton
    @EnforceShortcutLimit
    fun applyShortcutLimit(): Boolean {
        return DeviceConfig.getBoolean(
            DeviceConfig.NAMESPACE_SYSTEMUI,
            SystemUiDeviceConfigFlags.APPLY_SHARING_APP_LIMITS_IN_SYSUI,
            true
        )
    }

    /**
     * Defines the limit for the number of shortcuts presented within the direct share row.
     *
     * This value applies to all displayed direct share targets, including those from Shortcut
     * service as well as app-provided targets.
     */
    @Provides
    @Singleton
    @ShortcutRowLimit
    fun shortcutRowLimit(@ApplicationOwned resources: Resources): Int {
        return resources.getInteger(R.integer.config_chooser_max_targets_per_row)
    }
}

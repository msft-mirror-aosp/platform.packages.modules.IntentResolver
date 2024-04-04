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

package com.android.intentresolver.v2

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.android.intentresolver.FeatureFlags
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint(BroadcastReceiver::class)
class ChooserSelector : Hilt_ChooserSelector() {

    @Inject lateinit var featureFlags: FeatureFlags

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(CHOOSER_PACKAGE, CHOOSER_PACKAGE + CHOOSER_CLASS),
                if (featureFlags.modularFramework()) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                },
                /* flags = */ 0,
            )
        }
    }

    companion object {
        private const val CHOOSER_PACKAGE = "com.android.intentresolver"
        private const val CHOOSER_CLASS = ".v2.ChooserActivity"
    }
}

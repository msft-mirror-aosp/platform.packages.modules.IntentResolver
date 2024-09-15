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

package com.android.intentresolver.inject

import android.service.chooser.FeatureFlagsImpl as ChooserServiceFlagsImpl
import com.android.intentresolver.FeatureFlagsImpl as IntentResolverFlagsImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

typealias IntentResolverFlags = com.android.intentresolver.FeatureFlags

typealias FakeIntentResolverFlags = com.android.intentresolver.FakeFeatureFlagsImpl

typealias ChooserServiceFlags = android.service.chooser.FeatureFlags

typealias FakeChooserServiceFlags = android.service.chooser.FakeFeatureFlagsImpl

@Module
@InstallIn(SingletonComponent::class)
object FeatureFlagsModule {

    @Provides fun intentResolverFlags(): IntentResolverFlags = IntentResolverFlagsImpl()

    @Provides fun chooserServiceFlags(): ChooserServiceFlags = ChooserServiceFlagsImpl()
}

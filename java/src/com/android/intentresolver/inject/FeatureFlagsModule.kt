package com.android.intentresolver.inject

import android.service.chooser.FeatureFlagsImpl as ChooserServiceFlagsImpl
import com.android.intentresolver.FeatureFlagsImpl as IntentResolverFlagsImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

typealias IntentResolverFlags = com.android.intentresolver.FeatureFlags

typealias ChooserServiceFlags = android.service.chooser.FeatureFlags

@Module
@InstallIn(SingletonComponent::class)
object FeatureFlagsModule {

    @Provides fun intentResolverFlags(): IntentResolverFlags = IntentResolverFlagsImpl()

    @Provides fun chooserServiceFlags(): ChooserServiceFlags = ChooserServiceFlagsImpl()
}

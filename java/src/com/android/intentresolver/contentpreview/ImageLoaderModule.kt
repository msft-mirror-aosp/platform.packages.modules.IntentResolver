/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.intentresolver.contentpreview

import android.content.res.Resources
import com.android.intentresolver.Flags.previewImageLoader
import com.android.intentresolver.R
import com.android.intentresolver.inject.ApplicationOwned
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import javax.inject.Provider

@Module
@InstallIn(ViewModelComponent::class)
interface ImageLoaderModule {
    @Binds fun thumbnailLoader(thumbnailLoader: ThumbnailLoaderImpl): ThumbnailLoader

    companion object {
        @Provides
        fun imageLoader(
            imagePreviewImageLoader: Provider<ImagePreviewImageLoader>,
            previewImageLoader: Provider<PreviewImageLoader>,
        ): ImageLoader =
            if (previewImageLoader()) {
                previewImageLoader.get()
            } else {
                imagePreviewImageLoader.get()
            }

        @Provides
        @ThumbnailSize
        fun thumbnailSize(@ApplicationOwned resources: Resources): Int =
            resources.getDimensionPixelSize(R.dimen.chooser_preview_image_max_dimen)

        @Provides @PreviewCacheSize fun cacheSize() = 16

        @Provides @PreviewMaxConcurrency fun maxConcurrency() = 4
    }
}

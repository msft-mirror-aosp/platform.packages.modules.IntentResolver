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

package com.android.intentresolver.icons

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.UserHandle
import androidx.collection.LruCache
import com.android.intentresolver.Flags.targetHoverAndKeyboardFocusStates
import com.android.intentresolver.chooser.DisplayResolveInfo
import com.android.intentresolver.chooser.SelectableTargetInfo
import java.util.function.Consumer
import javax.annotation.concurrent.GuardedBy
import javax.inject.Qualifier

@Qualifier @MustBeDocumented @Retention(AnnotationRetention.BINARY) annotation class Caching

private typealias IconCache = LruCache<String, Bitmap>

class CachingTargetDataLoader(
    private val context: Context,
    private val targetDataLoader: TargetDataLoader,
    private val cacheSize: Int = 100,
) : TargetDataLoader {
    @GuardedBy("self") private val perProfileIconCache = HashMap<UserHandle, IconCache>()

    override fun getOrLoadAppTargetIcon(
        info: DisplayResolveInfo,
        userHandle: UserHandle,
        callback: Consumer<Drawable>,
    ): Drawable? {
        val cacheKey = info.toCacheKey()
        return getCachedAppIcon(cacheKey, userHandle)?.toDrawable()
            ?: targetDataLoader.getOrLoadAppTargetIcon(info, userHandle) { drawable ->
                drawable.extractBitmap()?.let { getProfileIconCache(userHandle).put(cacheKey, it) }
                callback.accept(drawable)
            }
    }

    override fun getOrLoadDirectShareIcon(
        info: SelectableTargetInfo,
        userHandle: UserHandle,
        callback: Consumer<Drawable>,
    ): Drawable? {
        val cacheKey = info.toCacheKey()
        return cacheKey?.let { getCachedAppIcon(it, userHandle) }?.toDrawable()
            ?: targetDataLoader.getOrLoadDirectShareIcon(info, userHandle) { drawable ->
                if (cacheKey != null) {
                    drawable.extractBitmap()?.let {
                        getProfileIconCache(userHandle).put(cacheKey, it)
                    }
                }
                callback.accept(drawable)
            }
    }

    override fun loadLabel(info: DisplayResolveInfo, callback: Consumer<LabelInfo>) =
        targetDataLoader.loadLabel(info, callback)

    override fun getOrLoadLabel(info: DisplayResolveInfo) = targetDataLoader.getOrLoadLabel(info)

    private fun getCachedAppIcon(component: String, userHandle: UserHandle): Bitmap? =
        getProfileIconCache(userHandle)[component]

    private fun getProfileIconCache(userHandle: UserHandle): IconCache =
        synchronized(perProfileIconCache) {
            perProfileIconCache.getOrPut(userHandle) { IconCache(cacheSize) }
        }

    private fun DisplayResolveInfo.toCacheKey() =
        ComponentName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name)
            .flattenToString()

    private fun SelectableTargetInfo.toCacheKey(): String? =
        if (chooserTargetIcon != null) {
            // do not cache icons for caller-provided targets
            null
        } else {
            buildString {
                append(chooserTargetComponentName?.flattenToString() ?: "")
                append("|")
                append(directShareShortcutInfo?.id ?: "")
            }
        }

    private fun Bitmap.toDrawable(): Drawable {
        return if (targetHoverAndKeyboardFocusStates()) {
            HoverBitmapDrawable(this)
        } else {
            BitmapDrawable(context.resources, this)
        }
    }

    private fun Drawable.extractBitmap(): Bitmap? {
        return when (this) {
            is BitmapDrawable -> bitmap
            is HoverBitmapDrawable -> bitmap
            else -> null
        }
    }
}

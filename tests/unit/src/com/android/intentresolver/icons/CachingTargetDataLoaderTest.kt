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
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.UserHandle
import com.android.intentresolver.chooser.SelectableTargetInfo
import java.util.function.Consumer
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CachingTargetDataLoaderTest {
    private val userHandle = UserHandle.of(1)

    @Test
    fun doNotCacheCallerProvidedShortcuts() {
        val callerTarget =
            SelectableTargetInfo.newSelectableTargetInfo(
                /* sourceInfo = */ null,
                /* backupResolveInfo = */ null,
                /* resolvedIntent = */ Intent(),
                /* chooserTargetComponentName =*/ ComponentName("package", "Activity"),
                "chooserTargetUninitializedTitle",
                /* chooserTargetIcon =*/ Icon.createWithContentUri("content://package/icon.png"),
                /* chooserTargetIntentExtras =*/ null,
                /* modifiedScore =*/ 1f,
                /* shortcutInfo = */ null,
                /* appTarget = */ null,
                /* referrerFillInIntent = */ Intent(),
            ) as SelectableTargetInfo

        val targetDataLoader =
            mock<TargetDataLoader> {
                on { getOrLoadDirectShareIcon(eq(callerTarget), eq(userHandle), any()) } doReturn
                    null
            }
        val testSubject = CachingTargetDataLoader(targetDataLoader)
        val callback = Consumer<Drawable> {}

        testSubject.getOrLoadDirectShareIcon(callerTarget, userHandle, callback)
        testSubject.getOrLoadDirectShareIcon(callerTarget, userHandle, callback)

        verify(targetDataLoader) {
            2 * { getOrLoadDirectShareIcon(eq(callerTarget), eq(userHandle), any()) }
        }
    }

    @Test
    fun serviceShortcutsAreCached() {
        val context =
            mock<Context> {
                on { userId } doReturn 1
                on { packageName } doReturn "package"
            }
        val targetInfo =
            SelectableTargetInfo.newSelectableTargetInfo(
                /* sourceInfo = */ null,
                /* backupResolveInfo = */ null,
                /* resolvedIntent = */ Intent(),
                /* chooserTargetComponentName =*/ ComponentName("package", "Activity"),
                "chooserTargetUninitializedTitle",
                /* chooserTargetIcon =*/ null,
                /* chooserTargetIntentExtras =*/ null,
                /* modifiedScore =*/ 1f,
                /* shortcutInfo = */ ShortcutInfo.Builder(context, "1").build(),
                /* appTarget = */ null,
                /* referrerFillInIntent = */ Intent(),
            ) as SelectableTargetInfo

        val targetDataLoader = mock<TargetDataLoader>()
        doAnswer {
                val callback = it.arguments[2] as Consumer<Drawable>
                callback.accept(BitmapDrawable(createBitmap()))
                null
            }
            .whenever(targetDataLoader)
            .getOrLoadDirectShareIcon(eq(targetInfo), eq(userHandle), any())
        val testSubject = CachingTargetDataLoader(targetDataLoader)
        val callback = Consumer<Drawable> {}

        testSubject.getOrLoadDirectShareIcon(targetInfo, userHandle, callback)
        testSubject.getOrLoadDirectShareIcon(targetInfo, userHandle, callback)

        verify(targetDataLoader) {
            1 * { getOrLoadDirectShareIcon(eq(targetInfo), eq(userHandle), any()) }
        }
    }
}

private fun createBitmap() = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)

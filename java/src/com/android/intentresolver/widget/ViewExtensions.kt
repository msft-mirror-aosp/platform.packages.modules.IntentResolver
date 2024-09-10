/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.intentresolver.widget

import android.graphics.Rect
import android.util.Log
import android.view.View
import androidx.core.view.OneShotPreDrawListener
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.suspendCancellableCoroutine

internal suspend fun View.waitForPreDraw(): Unit = suspendCancellableCoroutine { continuation ->
    val isResumed = AtomicBoolean(false)
    val callback =
        OneShotPreDrawListener.add(
            this,
            Runnable {
                if (isResumed.compareAndSet(false, true)) {
                    continuation.resumeWith(Result.success(Unit))
                } else {
                    // it's not really expected but in some unknown corner-case let's not crash
                    Log.e(
                        "waitForPreDraw",
                        "An attempt to resume a completed coroutine",
                        Exception()
                    )
                }
            }
        )
    continuation.invokeOnCancellation { callback.removeListener() }
}

internal fun View.isFullyVisible(): Boolean {
    val rect = Rect()
    val isVisible = getLocalVisibleRect(rect)
    return isVisible && rect.width() == width && rect.height() == height
}

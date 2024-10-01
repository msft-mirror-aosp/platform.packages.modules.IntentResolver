/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.intentresolver.interactive.domain.interactor

import android.util.Log
import com.android.intentresolver.IChooserController
import com.android.intentresolver.IChooserInteractiveSessionCallback

private const val TAG = "SessionCallback"

class SafeChooserInteractiveSessionCallback(
    private val delegate: IChooserInteractiveSessionCallback
) : IChooserInteractiveSessionCallback by delegate {

    override fun registerChooserController(updater: IChooserController?) {
        if (!isAlive) return
        runCatching { delegate.registerChooserController(updater) }
            .onFailure { Log.e(TAG, "Failed to invoke registerChooserController", it) }
    }

    override fun onDrawerVerticalOffsetChanged(offset: Int) {
        if (!isAlive) return
        runCatching { delegate.onDrawerVerticalOffsetChanged(offset) }
            .onFailure { Log.e(TAG, "Failed to invoke onDrawerVerticalOffsetChanged", it) }
    }

    private val isAlive: Boolean
        get() = delegate.asBinder().isBinderAlive
}

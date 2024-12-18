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

package com.android.intentresolver.interactive.data.repository

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import com.android.intentresolver.IChooserController
import com.android.intentresolver.interactive.domain.model.ChooserIntentUpdater
import dagger.hilt.android.scopes.ViewModelScoped
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

private const val INTERACTIVE_SESSION_CALLBACK_KEY = "interactive-session-callback"

@ViewModelScoped
class InteractiveSessionCallbackRepository @Inject constructor(savedStateHandle: SavedStateHandle) {
    private val intentUpdaterRef =
        AtomicReference<ChooserIntentUpdater?>(
            savedStateHandle
                .get<Bundle>(INTERACTIVE_SESSION_CALLBACK_KEY)
                ?.let { it.getBinder(INTERACTIVE_SESSION_CALLBACK_KEY) }
                ?.let { binder ->
                    binder.queryLocalInterface(IChooserController.DESCRIPTOR)
                        as? ChooserIntentUpdater
                }
        )

    val intentUpdater: ChooserIntentUpdater?
        get() = intentUpdaterRef.get()

    init {
        savedStateHandle.setSavedStateProvider(INTERACTIVE_SESSION_CALLBACK_KEY) {
            Bundle().apply { putBinder(INTERACTIVE_SESSION_CALLBACK_KEY, intentUpdater) }
        }
    }

    fun setChooserIntentUpdater(intentUpdater: ChooserIntentUpdater) {
        intentUpdaterRef.compareAndSet(null, intentUpdater)
    }
}

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

package com.android.intentresolver.interactive.domain.model

import android.content.Intent
import com.android.intentresolver.IChooserController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter

private val NotSet = Intent()

class ChooserIntentUpdater : IChooserController.Stub() {
    private val updates = MutableStateFlow<Intent?>(NotSet)

    val chooserIntent: Flow<Intent?>
        get() = updates.filter { it !== NotSet }

    override fun updateIntent(chooserIntent: Intent?) {
        updates.value = chooserIntent
    }
}

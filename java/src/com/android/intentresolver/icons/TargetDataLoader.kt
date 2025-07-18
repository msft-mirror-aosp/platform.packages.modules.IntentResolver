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

package com.android.intentresolver.icons

import android.graphics.drawable.Drawable
import android.os.UserHandle
import com.android.intentresolver.chooser.DisplayResolveInfo
import com.android.intentresolver.chooser.SelectableTargetInfo
import java.util.function.Consumer

/** A target data loader contract. Added to support testing. */
interface TargetDataLoader {
    /** Load an app target icon */
    fun getOrLoadAppTargetIcon(
        info: DisplayResolveInfo,
        userHandle: UserHandle,
        callback: Consumer<Drawable>,
    ): Drawable?

    /** Load a shortcut icon */
    fun getOrLoadDirectShareIcon(
        info: SelectableTargetInfo,
        userHandle: UserHandle,
        callback: Consumer<Drawable>,
    ): Drawable?

    /** Load target label */
    fun loadLabel(info: DisplayResolveInfo, callback: Consumer<LabelInfo>)

    /** Loads DisplayResolveInfo's display label synchronously, if needed */
    fun getOrLoadLabel(info: DisplayResolveInfo)
}

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

@file:JvmName("ResolveInfoHelpers")

package com.android.intentresolver

import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo

fun resolveInfoMatch(lhs: ResolveInfo?, rhs: ResolveInfo?): Boolean =
    (lhs === rhs) ||
        ((lhs != null && rhs != null) &&
            activityInfoMatch(lhs.activityInfo, rhs.activityInfo) &&
            // Comparing against resolveInfo.userHandle in case cloned apps are present,
            // as they will have the same activityInfo.
            lhs.userHandle == rhs.userHandle)

private fun activityInfoMatch(lhs: ActivityInfo?, rhs: ActivityInfo?): Boolean =
    (lhs === rhs) ||
        (lhs != null && rhs != null && lhs.name == rhs.name && lhs.packageName == rhs.packageName)

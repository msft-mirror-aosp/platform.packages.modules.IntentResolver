/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.intentresolver.model;

import android.content.Context;
import android.content.pm.ResolveInfo;

import java.text.Collator;
import java.util.Comparator;

/**
 * Sort intents alphabetically based on package name.
 */
public class ResolveInfoAzInfoComparator<T extends ResolveInfo> implements Comparator<T> {
    Collator mCollator;

    public ResolveInfoAzInfoComparator(Context context) {
        mCollator = Collator.getInstance(context.getResources().getConfiguration().locale);
    }

    @Override
    public int compare(ResolveInfo lhsp, ResolveInfo rhsp) {
        if (lhsp == null) {
            return -1;
        } else if (rhsp == null) {
            return 1;
        }
        return mCollator.compare(lhsp.activityInfo.packageName, rhsp.activityInfo.packageName);
    }
}

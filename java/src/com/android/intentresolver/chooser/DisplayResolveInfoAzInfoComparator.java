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

package com.android.intentresolver.chooser;


import android.content.Context;

import java.text.Collator;
import java.util.Comparator;

/**
 * Sort intents alphabetically based on display label.
 */
public class DisplayResolveInfoAzInfoComparator implements Comparator<DisplayResolveInfo> {
    Comparator<DisplayResolveInfo> mComparator;
    public DisplayResolveInfoAzInfoComparator(Context context) {
        Collator collator = Collator
                .getInstance(context.getResources().getConfiguration().locale);
        // Adding two stage comparator, first stage compares using displayLabel, next stage
        //  compares using resolveInfo.userHandle
        mComparator = Comparator.comparing(DisplayResolveInfo::getDisplayLabel, collator)
                .thenComparingInt(target -> target.getResolveInfo().userHandle.getIdentifier());
    }

    @Override
    public int compare(
            DisplayResolveInfo lhsp, DisplayResolveInfo rhsp) {
        return mComparator.compare(lhsp, rhsp);
    }
}

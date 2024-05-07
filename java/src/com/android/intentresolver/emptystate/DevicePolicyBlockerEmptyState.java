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

package com.android.intentresolver.emptystate;

import android.app.admin.DevicePolicyEventLogger;

import androidx.annotation.Nullable;

/**
 * Empty state that gets strings from the device policy manager and tracks events into
 * event logger of the device policy events.
 */
public class DevicePolicyBlockerEmptyState implements EmptyState {
    private final String mTitle;
    private final String mSubtitle;
    private final int mEventId;
    private final String mEventCategory;

    public DevicePolicyBlockerEmptyState(
            String title,
            String subtitle,
            int devicePolicyEventId,
            String devicePolicyEventCategory) {
        mTitle = title;
        mSubtitle = subtitle;
        mEventId = devicePolicyEventId;
        mEventCategory = devicePolicyEventCategory;
    }

    @Nullable
    @Override
    public String getTitle() {
        return mTitle;
    }

    @Nullable
    @Override
    public String getSubtitle() {
        return mSubtitle;
    }

    @Override
    public void onEmptyStateShown() {
        if (mEventId != -1) {
            DevicePolicyEventLogger.createEvent(mEventId)
                    .setStrings(mEventCategory)
                    .write();
        }
    }

    @Override
    public boolean shouldSkipDataRebuild() {
        return true;
    }
}

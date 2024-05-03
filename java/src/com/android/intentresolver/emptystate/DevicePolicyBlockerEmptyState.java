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
import android.app.admin.DevicePolicyManager;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * Empty state that gets strings from the device policy manager and tracks events into
 * event logger of the device policy events.
 */
public class DevicePolicyBlockerEmptyState implements EmptyState {

    @NonNull
    private final Context mContext;
    private final String mDevicePolicyStringTitleId;
    @StringRes
    private final int mDefaultTitleResource;
    private final String mDevicePolicyStringSubtitleId;
    @StringRes
    private final int mDefaultSubtitleResource;
    private final int mEventId;
    @NonNull
    private final String mEventCategory;

    public DevicePolicyBlockerEmptyState(@NonNull Context context,
            String devicePolicyStringTitleId, @StringRes int defaultTitleResource,
            String devicePolicyStringSubtitleId, @StringRes int defaultSubtitleResource,
            int devicePolicyEventId, @NonNull String devicePolicyEventCategory) {
        mContext = context;
        mDevicePolicyStringTitleId = devicePolicyStringTitleId;
        mDefaultTitleResource = defaultTitleResource;
        mDevicePolicyStringSubtitleId = devicePolicyStringSubtitleId;
        mDefaultSubtitleResource = defaultSubtitleResource;
        mEventId = devicePolicyEventId;
        mEventCategory = devicePolicyEventCategory;
    }

    @Nullable
    @Override
    public String getTitle() {
        return mContext.getSystemService(DevicePolicyManager.class).getResources().getString(
                mDevicePolicyStringTitleId,
                () -> mContext.getString(mDefaultTitleResource));
    }

    @Nullable
    @Override
    public String getSubtitle() {
        return mContext.getSystemService(DevicePolicyManager.class).getResources().getString(
                mDevicePolicyStringSubtitleId,
                () -> mContext.getString(mDefaultSubtitleResource));
    }

    @Override
    public void onEmptyStateShown() {
        DevicePolicyEventLogger.createEvent(mEventId)
                .setStrings(mEventCategory)
                .write();
    }

    @Override
    public boolean shouldSkipDataRebuild() {
        return true;
    }
}

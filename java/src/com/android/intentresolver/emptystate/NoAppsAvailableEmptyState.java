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
import android.stats.devicepolicy.nano.DevicePolicyEnums;

import androidx.annotation.NonNull;

public class NoAppsAvailableEmptyState implements EmptyState {

    @NonNull
    private final String mTitle;

    @NonNull
    private final String mMetricsCategory;

    private final boolean mIsPersonalProfile;

    public NoAppsAvailableEmptyState(@NonNull String title, @NonNull String metricsCategory,
            boolean isPersonalProfile) {
        mTitle = title;
        mMetricsCategory = metricsCategory;
        mIsPersonalProfile = isPersonalProfile;
    }

    @NonNull
    @Override
    public String getTitle() {
        return mTitle;
    }

    @Override
    public void onEmptyStateShown() {
        DevicePolicyEventLogger.createEvent(
                        DevicePolicyEnums.RESOLVER_EMPTY_STATE_NO_APPS_RESOLVED)
                .setStrings(mMetricsCategory)
                .setBoolean(/*isPersonalProfile*/ mIsPersonalProfile)
                .write();
    }
}

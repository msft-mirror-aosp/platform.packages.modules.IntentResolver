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
import androidx.annotation.Nullable;

public class WorkProfileOffEmptyState implements EmptyState {

    private final String mTitle;
    private final ClickListener mOnClick;
    private final String mMetricsCategory;

    public WorkProfileOffEmptyState(String title, @NonNull ClickListener onClick,
            @NonNull String metricsCategory) {
        mTitle = title;
        mOnClick = onClick;
        mMetricsCategory = metricsCategory;
    }

    @Nullable
    @Override
    public String getTitle() {
        return mTitle;
    }

    @Nullable
    @Override
    public ClickListener getButtonClickListener() {
        return mOnClick;
    }

    @Override
    public void onEmptyStateShown() {
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.RESOLVER_EMPTY_STATE_WORK_APPS_DISABLED)
                .setStrings(mMetricsCategory)
                .write();
    }
}

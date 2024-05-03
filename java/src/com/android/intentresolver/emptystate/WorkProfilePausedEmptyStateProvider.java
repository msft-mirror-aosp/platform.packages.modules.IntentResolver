/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_WORK_PAUSED_TITLE;

import static java.util.Objects.requireNonNull;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.intentresolver.ProfileAvailability;
import com.android.intentresolver.ProfileHelper;
import com.android.intentresolver.R;
import com.android.intentresolver.ResolverListAdapter;
import com.android.intentresolver.profiles.OnSwitchOnWorkSelectedListener;
import com.android.intentresolver.shared.model.Profile;

/**
 * Chooser/ResolverActivity empty state provider that returns empty state which is shown when
 * work profile is paused and we need to show a button to enable it.
 */
public class WorkProfilePausedEmptyStateProvider implements EmptyStateProvider {

    private final ProfileHelper mProfileHelper;
    private final ProfileAvailability mProfileAvailability;
    private final String mMetricsCategory;
    private final OnSwitchOnWorkSelectedListener mOnSwitchOnWorkSelectedListener;
    private final Context mContext;

    public WorkProfilePausedEmptyStateProvider(@NonNull Context context,
            ProfileHelper profileHelper,
            ProfileAvailability profileAvailability,
            @Nullable OnSwitchOnWorkSelectedListener onSwitchOnWorkSelectedListener,
            @NonNull String metricsCategory) {
        mContext = context;
        mProfileHelper = profileHelper;
        mProfileAvailability = profileAvailability;
        mMetricsCategory = metricsCategory;
        mOnSwitchOnWorkSelectedListener = onSwitchOnWorkSelectedListener;
    }

    @Nullable
    @Override
    public EmptyState getEmptyState(ResolverListAdapter resolverListAdapter) {
        UserHandle userHandle = resolverListAdapter.getUserHandle();
        if (!mProfileHelper.getWorkProfilePresent()) {
            return null;
        }
        Profile workProfile = requireNonNull(mProfileHelper.getWorkProfile());

        // Policy: only show the "Work profile paused" state when:
        // * provided list adapter is from the work profile
        // * the list adapter is not empty
        // * work profile quiet mode is _enabled_ (unavailable)

        if (!userHandle.equals(workProfile.getPrimary().getHandle())
                || resolverListAdapter.getCount() == 0
                || mProfileAvailability.isAvailable(workProfile)) {
            return null;
        }

        String title = mContext.getSystemService(DevicePolicyManager.class)
                .getResources().getString(RESOLVER_WORK_PAUSED_TITLE,
                    () -> mContext.getString(R.string.resolver_turn_on_work_apps));

        return new WorkProfileOffEmptyState(title, /* EmptyState.ClickListener */ (tab) -> {
            tab.showSpinner();
            if (mOnSwitchOnWorkSelectedListener != null) {
                mOnSwitchOnWorkSelectedListener.onSwitchOnWorkSelected();
            }
            mProfileAvailability.requestQuietModeState(workProfile, false);
        }, mMetricsCategory);
    }

}

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


import static com.android.intentresolver.shared.model.Profile.Type.PERSONAL;

import static java.util.Objects.requireNonNull;

import android.os.UserHandle;

import androidx.annotation.NonNull;

import com.android.intentresolver.ProfileAvailability;
import com.android.intentresolver.ProfileHelper;
import com.android.intentresolver.ResolverListAdapter;
import com.android.intentresolver.shared.model.Profile;
import com.android.intentresolver.ui.ProfilePagerResources;

/**
 * Chooser/ResolverActivity empty state provider that returns empty state which is shown when
 * there are no apps available.
 */
public class NoAppsAvailableEmptyStateProvider implements EmptyStateProvider {

    @NonNull private final String mMetricsCategory;
    private final ProfilePagerResources mProfilePagerResources;
    private final ProfileHelper mProfileHelper;
    private final ProfileAvailability mProfileAvailability;

    public NoAppsAvailableEmptyStateProvider(
            ProfileHelper profileHelper,
            ProfileAvailability profileAvailability,
            @NonNull String metricsCategory,
            ProfilePagerResources profilePagerResources) {
        mProfileHelper = profileHelper;
        mProfileAvailability = profileAvailability;
        mMetricsCategory = metricsCategory;
        mProfilePagerResources = profilePagerResources;
    }

    @NonNull
    @Override
    public EmptyState getEmptyState(ResolverListAdapter resolverListAdapter) {
        UserHandle listUserHandle = resolverListAdapter.getUserHandle();
        if (mProfileAvailability.visibleProfileCount() == 1) {
            return new DefaultEmptyState();
        } else {
            Profile.Type profileType =
                    requireNonNull(mProfileHelper.findProfileType(listUserHandle));
            String title = mProfilePagerResources.noAppsMessage(profileType);
            return new NoAppsAvailableEmptyState(
                    title,
                    mMetricsCategory,
                    /* isPersonalProfile= */ profileType == PERSONAL
            );
        }
    }
}

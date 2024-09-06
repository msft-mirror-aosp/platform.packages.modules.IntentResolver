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

import static android.stats.devicepolicy.DevicePolicyEnums.RESOLVER_EMPTY_STATE_NO_SHARING_TO_PERSONAL;
import static android.stats.devicepolicy.DevicePolicyEnums.RESOLVER_EMPTY_STATE_NO_SHARING_TO_WORK;

import static com.android.intentresolver.ChooserActivity.METRICS_CATEGORY_CHOOSER;

import static java.util.Objects.requireNonNull;

import android.content.Intent;

import androidx.annotation.Nullable;

import com.android.intentresolver.ProfileHelper;
import com.android.intentresolver.ResolverListAdapter;
import com.android.intentresolver.data.repository.DevicePolicyResources;
import com.android.intentresolver.shared.model.Profile;

import java.util.List;

/**
 * Empty state provider that informs about a lack of cross profile sharing. It will return
 * an empty state in case there are no intents which can be forwarded to another profile.
 */
public class NoCrossProfileEmptyStateProvider implements EmptyStateProvider {

    private final ProfileHelper mProfileHelper;
    private final DevicePolicyResources mDevicePolicyResources;
    private final boolean mIsShare;
    private final CrossProfileIntentsChecker mCrossProfileIntentsChecker;

    public NoCrossProfileEmptyStateProvider(
            ProfileHelper profileHelper,
            DevicePolicyResources devicePolicyResources,
            CrossProfileIntentsChecker crossProfileIntentsChecker,
            boolean isShare) {
        mProfileHelper = profileHelper;
        mDevicePolicyResources = devicePolicyResources;
        mIsShare = isShare;
        mCrossProfileIntentsChecker = crossProfileIntentsChecker;
    }

    private boolean hasCrossProfileIntents(List<Intent> intents, Profile source, Profile target) {
        if (source.getPrimary().getHandle().equals(target.getPrimary().getHandle())) {
            return true;
        }
        // Note: Use of getPrimary() here also handles delegation of CLONE profile to parent.
        return mCrossProfileIntentsChecker.hasCrossProfileIntents(intents,
                source.getPrimary().getId(), target.getPrimary().getId());
    }

    @Nullable
    @Override
    public EmptyState getEmptyState(ResolverListAdapter adapter) {
        Profile launchedBy = mProfileHelper.getLaunchedAsProfile();
        Profile tabOwner = requireNonNull(mProfileHelper.findProfile(adapter.getUserHandle()));

        // When sharing into or out of Private profile, perform the check using the parent profile
        // instead. (Hard-coded application of CROSS_PROFILE_CONTENT_SHARING_DELEGATE_FROM_PARENT)

        Profile effectiveSource = launchedBy;
        Profile effectiveTarget = tabOwner;

        // Assumption baked into design: "Personal" profile is the parent of all other profiles.
        if (launchedBy.getType() == Profile.Type.PRIVATE) {
            effectiveSource = mProfileHelper.getPersonalProfile();
        }

        if (tabOwner.getType() == Profile.Type.PRIVATE) {
            effectiveTarget = mProfileHelper.getPersonalProfile();
        }

        // Allow access to the tab when there is at least one target permitted to cross profiles.
        if (hasCrossProfileIntents(adapter.getIntents(), effectiveSource, effectiveTarget)) {
            return null;
        }

        switch (tabOwner.getType()) {
            case PERSONAL:
                return new DevicePolicyBlockerEmptyState(
                        mDevicePolicyResources.getCrossProfileBlocked(),
                        mDevicePolicyResources.toPersonalBlockedByPolicyMessage(mIsShare),
                        RESOLVER_EMPTY_STATE_NO_SHARING_TO_PERSONAL,
                        METRICS_CATEGORY_CHOOSER);

            case WORK:
                return new DevicePolicyBlockerEmptyState(
                        mDevicePolicyResources.getCrossProfileBlocked(),
                        mDevicePolicyResources.toWorkBlockedByPolicyMessage(mIsShare),
                        RESOLVER_EMPTY_STATE_NO_SHARING_TO_WORK,
                        METRICS_CATEGORY_CHOOSER);

            case PRIVATE:
                return new DevicePolicyBlockerEmptyState(
                        mDevicePolicyResources.getCrossProfileBlocked(),
                        mDevicePolicyResources.toPrivateBlockedByPolicyMessage(mIsShare),
                        /* Suppress log event. TODO: Define a new metrics event for this? */ -1,
                        METRICS_CATEGORY_CHOOSER);
        }
        return null;
    }
}

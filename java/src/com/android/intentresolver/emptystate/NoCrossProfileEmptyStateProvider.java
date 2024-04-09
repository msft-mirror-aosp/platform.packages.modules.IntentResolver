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

import android.content.Intent;
import android.os.UserHandle;

import androidx.annotation.Nullable;

import com.android.intentresolver.ProfileHelper;
import com.android.intentresolver.ResolverListAdapter;
import com.android.intentresolver.shared.model.Profile;
import com.android.intentresolver.shared.model.User;

import java.util.List;

/**
 * Empty state provider that informs about a lack of cross profile sharing. It will return
 * an empty state in case there are no intents which can be forwarded to another profile.
 */
public class NoCrossProfileEmptyStateProvider implements EmptyStateProvider {

    private final ProfileHelper mProfileHelper;
    private final EmptyState mNoWorkToPersonalEmptyState;
    private final EmptyState mNoPersonalToWorkEmptyState;
    private final CrossProfileIntentsChecker mCrossProfileIntentsChecker;

    public NoCrossProfileEmptyStateProvider(
            ProfileHelper profileHelper,
            EmptyState noWorkToPersonalEmptyState,
            EmptyState noPersonalToWorkEmptyState,
            CrossProfileIntentsChecker crossProfileIntentsChecker) {
        mProfileHelper = profileHelper;
        mNoWorkToPersonalEmptyState = noWorkToPersonalEmptyState;
        mNoPersonalToWorkEmptyState = noPersonalToWorkEmptyState;
        mCrossProfileIntentsChecker = crossProfileIntentsChecker;
    }

    private boolean anyCrossProfileAllowedIntents(ResolverListAdapter selected, UserHandle source) {
        List<Intent> intents = selected.getIntents();
        UserHandle target = selected.getUserHandle();
        return mCrossProfileIntentsChecker.hasCrossProfileIntents(intents,
                source.getIdentifier(), target.getIdentifier());
    }

    @Nullable
    @Override
    public EmptyState getEmptyState(ResolverListAdapter adapter) {
        Profile launchedAsProfile = mProfileHelper.getLaunchedAsProfile();
        User launchedAs = mProfileHelper.getLaunchedAsProfile().getPrimary();
        UserHandle tabOwnerHandle = adapter.getUserHandle();
        boolean launchedAsSameUser = launchedAs.getHandle().equals(tabOwnerHandle);
        Profile.Type tabOwnerType = mProfileHelper.findProfileType(tabOwnerHandle);

        // Not applicable for private profile.
        if (launchedAsProfile.getType() == Profile.Type.PRIVATE
                || tabOwnerType == Profile.Type.PRIVATE) {
            return null;
        }

        // Allow access to the tab when launched by the same user as the tab owner
        // or when there is at least one target which is permitted for cross-profile.
        if (launchedAsSameUser || anyCrossProfileAllowedIntents(adapter,
                /* source = */ launchedAs.getHandle())) {
            return null;
        }

        switch (launchedAsProfile.getType()) {
            case WORK:  return mNoWorkToPersonalEmptyState;
            case PERSONAL: return mNoPersonalToWorkEmptyState;
        }
        return null;
    }

}

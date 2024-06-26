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

package com.android.intentresolver.v2.emptystate;

import static android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_NO_PERSONAL_APPS;
import static android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_NO_WORK_APPS;

import android.app.admin.DevicePolicyEventLogger;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.ResolveInfo;
import android.os.UserHandle;
import android.stats.devicepolicy.nano.DevicePolicyEnums;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.intentresolver.R;
import com.android.intentresolver.ResolvedComponentInfo;
import com.android.intentresolver.ResolverListAdapter;
import com.android.intentresolver.emptystate.EmptyState;
import com.android.intentresolver.emptystate.EmptyStateProvider;

import java.util.List;

/**
 * Chooser/ResolverActivity empty state provider that returns empty state which is shown when
 * there are no apps available.
 */
public class NoAppsAvailableEmptyStateProvider implements EmptyStateProvider {

    @NonNull
    private final Context mContext;
    @Nullable
    private final UserHandle mWorkProfileUserHandle;
    @Nullable
    private final UserHandle mPersonalProfileUserHandle;
    @NonNull
    private final String mMetricsCategory;
    @NonNull
    private final UserHandle mTabOwnerUserHandleForLaunch;

    public NoAppsAvailableEmptyStateProvider(@NonNull Context context,
            @Nullable UserHandle workProfileUserHandle,
            @Nullable UserHandle personalProfileUserHandle, @NonNull String metricsCategory,
            @NonNull UserHandle tabOwnerUserHandleForLaunch) {
        mContext = context;
        mWorkProfileUserHandle = workProfileUserHandle;
        mPersonalProfileUserHandle = personalProfileUserHandle;
        mMetricsCategory = metricsCategory;
        mTabOwnerUserHandleForLaunch = tabOwnerUserHandleForLaunch;
    }

    @Nullable
    @Override
    @SuppressWarnings("ReferenceEquality")
    public EmptyState getEmptyState(ResolverListAdapter resolverListAdapter) {
        UserHandle listUserHandle = resolverListAdapter.getUserHandle();

        if (mWorkProfileUserHandle != null
                && (mTabOwnerUserHandleForLaunch.equals(listUserHandle)
                || !hasAppsInOtherProfile(resolverListAdapter))) {

            String title;
            if (listUserHandle == mPersonalProfileUserHandle) {
                title = mContext.getSystemService(
                        DevicePolicyManager.class).getResources().getString(
                        RESOLVER_NO_PERSONAL_APPS,
                            () -> mContext.getString(R.string.resolver_no_personal_apps_available));
            } else {
                title = mContext.getSystemService(
                        DevicePolicyManager.class).getResources().getString(
                        RESOLVER_NO_WORK_APPS,
                            () -> mContext.getString(R.string.resolver_no_work_apps_available));
            }

            return new NoAppsAvailableEmptyState(
                    title, mMetricsCategory,
                    /* isPersonalProfile= */ listUserHandle == mPersonalProfileUserHandle
            );
        } else if (mWorkProfileUserHandle == null) {
            // Return default empty state without tracking
            return new DefaultEmptyState();
        }

        return null;
    }

    private boolean hasAppsInOtherProfile(ResolverListAdapter adapter) {
        if (mWorkProfileUserHandle == null) {
            return false;
        }
        List<ResolvedComponentInfo> resolversForIntent =
                adapter.getResolversForUser(mTabOwnerUserHandleForLaunch);
        for (ResolvedComponentInfo info : resolversForIntent) {
            ResolveInfo resolveInfo = info.getResolveInfoAt(0);
            if (resolveInfo.targetUserId != UserHandle.USER_CURRENT) {
                return true;
            }
        }
        return false;
    }

    public static class DefaultEmptyState implements EmptyState {
        @Override
        public boolean useDefaultEmptyView() {
            return true;
        }
    }

    public static class NoAppsAvailableEmptyState implements EmptyState {

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
}

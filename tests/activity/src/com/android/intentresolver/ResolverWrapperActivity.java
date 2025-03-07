/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.intentresolver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.annotation.Nullable;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.test.espresso.idling.CountingIdlingResource;

import com.android.intentresolver.chooser.DisplayResolveInfo;
import com.android.intentresolver.chooser.SelectableTargetInfo;
import com.android.intentresolver.chooser.TargetInfo;
import com.android.intentresolver.emptystate.CrossProfileIntentsChecker;
import com.android.intentresolver.icons.LabelInfo;
import com.android.intentresolver.icons.TargetDataLoader;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/*
 * Simple wrapper around chooser activity to be able to initiate it under test
 */
public class ResolverWrapperActivity extends ResolverActivity {
    static final OverrideData sOverrides = new OverrideData();

    private final CountingIdlingResource mLabelIdlingResource =
            new CountingIdlingResource("LoadLabelTask");

    public CountingIdlingResource getLabelIdlingResource() {
        return mLabelIdlingResource;
    }

    @Override
    public ResolverListAdapter createResolverListAdapter(
            Context context,
            List<Intent> payloadIntents,
            Intent[] initialIntents,
            List<ResolveInfo> rList,
            boolean filterLastUsed,
            UserHandle userHandle) {
        return new ResolverListAdapter(
                context,
                payloadIntents,
                initialIntents,
                rList,
                filterLastUsed,
                createListController(userHandle),
                userHandle,
                payloadIntents.get(0),  // TODO: extract upstream
                this,
                userHandle,
                new TargetDataLoaderWrapper(mTargetDataLoader, mLabelIdlingResource));
    }

    @Override
    protected CrossProfileIntentsChecker createCrossProfileIntentsChecker() {
        if (sOverrides.mCrossProfileIntentsChecker != null) {
            return sOverrides.mCrossProfileIntentsChecker;
        }
        return super.createCrossProfileIntentsChecker();
    }

    ResolverListAdapter getAdapter() {
        return mMultiProfilePagerAdapter.getActiveListAdapter();
    }

    ResolverListAdapter getPersonalListAdapter() {
        return mMultiProfilePagerAdapter.getPersonalListAdapter();
    }

    ResolverListAdapter getWorkListAdapter() {
        return mMultiProfilePagerAdapter.getWorkListAdapter();
    }

    @Override
    public boolean isVoiceInteraction() {
        if (sOverrides.isVoiceInteraction != null) {
            return sOverrides.isVoiceInteraction;
        }
        return super.isVoiceInteraction();
    }

    @Override
    public void safelyStartActivityInternal(TargetInfo cti, UserHandle user,
            @Nullable Bundle options) {
        if (sOverrides.onSafelyStartInternalCallback != null
                && sOverrides.onSafelyStartInternalCallback.apply(new Pair<>(cti, user))) {
            return;
        }
        super.safelyStartActivityInternal(cti, user, options);
    }

    @Override
    protected ResolverListController createListController(UserHandle userHandle) {
        if (userHandle == getUser()) {
            return sOverrides.resolverListController;
        }
        return sOverrides.workResolverListController;
    }

    protected UserHandle getCurrentUserHandle() {
        return mMultiProfilePagerAdapter.getCurrentUserHandle();
    }

    @Override
    public void startActivityAsUser(Intent intent, Bundle options, UserHandle user) {
        super.startActivityAsUser(intent, options, user);
    }

    /**
     * We cannot directly mock the activity created since instrumentation creates it.
     * <p>
     * Instead, we use static instances of this object to modify behavior.
     */
    public static class OverrideData {
        @SuppressWarnings("Since15")
        public Function<Pair<TargetInfo, UserHandle>, Boolean> onSafelyStartInternalCallback;
        public ResolverListController resolverListController;
        public ResolverListController workResolverListController;
        public Boolean isVoiceInteraction;
        public boolean hasCrossProfileIntents;
        public CrossProfileIntentsChecker mCrossProfileIntentsChecker;

        public void reset() {
            onSafelyStartInternalCallback = null;
            isVoiceInteraction = null;
            resolverListController = mock(ResolverListController.class);
            workResolverListController = mock(ResolverListController.class);
            hasCrossProfileIntents = true;
            mCrossProfileIntentsChecker = mock(CrossProfileIntentsChecker.class);
            when(mCrossProfileIntentsChecker.hasCrossProfileIntents(any(), anyInt(), anyInt()))
                    .thenAnswer(invocation -> hasCrossProfileIntents);
        }
    }

    private static class TargetDataLoaderWrapper implements TargetDataLoader {
        private final TargetDataLoader mTargetDataLoader;
        private final CountingIdlingResource mLabelIdlingResource;

        private TargetDataLoaderWrapper(
                TargetDataLoader targetDataLoader, CountingIdlingResource labelIdlingResource) {
            mTargetDataLoader = targetDataLoader;
            mLabelIdlingResource = labelIdlingResource;
        }

        @Override
        @Nullable
        public Drawable getOrLoadAppTargetIcon(
                @NonNull DisplayResolveInfo info,
                @NonNull UserHandle userHandle,
                @NonNull Consumer<Drawable> callback) {
            return mTargetDataLoader.getOrLoadAppTargetIcon(info, userHandle, callback);
        }

        @Override
        @Nullable
        public Drawable getOrLoadDirectShareIcon(
                @NonNull SelectableTargetInfo info,
                @NonNull UserHandle userHandle,
                @NonNull Consumer<Drawable> callback) {
            return mTargetDataLoader.getOrLoadDirectShareIcon(info, userHandle, callback);
        }

        @Override
        public void loadLabel(
                @NonNull DisplayResolveInfo info,
                @NonNull Consumer<LabelInfo> callback) {
            mLabelIdlingResource.increment();
            mTargetDataLoader.loadLabel(
                    info,
                    (result) -> {
                        mLabelIdlingResource.decrement();
                        callback.accept(result);
                    });
        }

        @Override
        public void getOrLoadLabel(@NonNull DisplayResolveInfo info) {
            mTargetDataLoader.getOrLoadLabel(info);
        }
    }
}

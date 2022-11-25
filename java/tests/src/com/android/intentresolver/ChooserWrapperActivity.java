/*
 * Copyright (C) 2008 The Android Open Source Project
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

import static org.mockito.Mockito.when;

import android.annotation.Nullable;
import android.app.prediction.AppPredictor;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.UserHandle;
import android.util.Size;

import com.android.intentresolver.AbstractMultiProfilePagerAdapter.CrossProfileIntentsChecker;
import com.android.intentresolver.AbstractMultiProfilePagerAdapter.MyUserIdProvider;
import com.android.intentresolver.AbstractMultiProfilePagerAdapter.QuietModeManager;
import com.android.intentresolver.ResolverListAdapter.ResolveInfoPresentationGetter;
import com.android.intentresolver.chooser.DisplayResolveInfo;
import com.android.intentresolver.chooser.NotSelectableTargetInfo;
import com.android.intentresolver.chooser.TargetInfo;
import com.android.intentresolver.shortcuts.ShortcutLoader;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import java.util.List;
import java.util.function.Consumer;

/**
 * Simple wrapper around chooser activity to be able to initiate it under test. For more
 * information, see {@code com.android.internal.app.ChooserWrapperActivity}.
 */
public class ChooserWrapperActivity
        extends com.android.intentresolver.ChooserActivity implements IChooserWrapper {
    static final ChooserActivityOverrideData sOverrides = ChooserActivityOverrideData.getInstance();
    private UsageStatsManager mUsm;

    // ResolverActivity (the base class of ChooserActivity) inspects the launched-from UID at
    // onCreate and needs to see some non-negative value in the test.
    @Override
    public int getLaunchedFromUid() {
        return 1234;
    }

    @Override
    public ChooserListAdapter createChooserListAdapter(Context context, List<Intent> payloadIntents,
            Intent[] initialIntents, List<ResolveInfo> rList, boolean filterLastUsed,
            ResolverListController resolverListController) {
        PackageManager packageManager =
                sOverrides.packageManager == null ? context.getPackageManager()
                        : sOverrides.packageManager;
        return new ChooserListAdapter(
                context,
                payloadIntents,
                initialIntents,
                rList,
                filterLastUsed,
                resolverListController,
                this,
                packageManager,
                getChooserActivityLogger());
    }

    @Override
    public ChooserListAdapter getAdapter() {
        return mChooserMultiProfilePagerAdapter.getActiveListAdapter();
    }

    @Override
    public ChooserListAdapter getPersonalListAdapter() {
        return ((ChooserGridAdapter) mMultiProfilePagerAdapter.getAdapterForIndex(0))
                .getListAdapter();
    }

    @Override
    public ChooserListAdapter getWorkListAdapter() {
        if (mMultiProfilePagerAdapter.getInactiveListAdapter() == null) {
            return null;
        }
        return ((ChooserGridAdapter) mMultiProfilePagerAdapter.getAdapterForIndex(1))
                .getListAdapter();
    }

    @Override
    public boolean getIsSelected() {
        return mIsSuccessfullySelected;
    }

    @Override
    protected ComponentName getNearbySharingComponent() {
        // an arbitrary pre-installed activity that handles this type of intent
        return ComponentName.unflattenFromString("com.google.android.apps.messaging/"
                + "com.google.android.apps.messaging.ui.conversationlist.ShareIntentActivity");
    }

    @Override
    protected TargetInfo getNearbySharingTarget(Intent originalIntent) {
        return NotSelectableTargetInfo.newEmptyTargetInfo();
    }

    @Override
    public UsageStatsManager getUsageStatsManager() {
        if (mUsm == null) {
            mUsm = getSystemService(UsageStatsManager.class);
        }
        return mUsm;
    }

    @Override
    public boolean isVoiceInteraction() {
        if (sOverrides.isVoiceInteraction != null) {
            return sOverrides.isVoiceInteraction;
        }
        return super.isVoiceInteraction();
    }

    @Override
    protected MyUserIdProvider createMyUserIdProvider() {
        if (sOverrides.mMyUserIdProvider != null) {
            return sOverrides.mMyUserIdProvider;
        }
        return super.createMyUserIdProvider();
    }

    @Override
    protected CrossProfileIntentsChecker createCrossProfileIntentsChecker() {
        if (sOverrides.mCrossProfileIntentsChecker != null) {
            return sOverrides.mCrossProfileIntentsChecker;
        }
        return super.createCrossProfileIntentsChecker();
    }

    @Override
    protected QuietModeManager createQuietModeManager() {
        if (sOverrides.mQuietModeManager != null) {
            return sOverrides.mQuietModeManager;
        }
        return super.createQuietModeManager();
    }

    @Override
    public void safelyStartActivity(com.android.intentresolver.chooser.TargetInfo cti) {
        if (sOverrides.onSafelyStartCallback != null
                && sOverrides.onSafelyStartCallback.apply(cti)) {
            return;
        }
        super.safelyStartActivity(cti);
    }

    @Override
    protected ResolverListController createListController(UserHandle userHandle) {
        if (userHandle == UserHandle.SYSTEM) {
            when(sOverrides.resolverListController.getUserHandle()).thenReturn(UserHandle.SYSTEM);
            return sOverrides.resolverListController;
        }
        when(sOverrides.workResolverListController.getUserHandle()).thenReturn(userHandle);
        return sOverrides.workResolverListController;
    }

    @Override
    public PackageManager getPackageManager() {
        if (sOverrides.createPackageManager != null) {
            return sOverrides.createPackageManager.apply(super.getPackageManager());
        }
        return super.getPackageManager();
    }

    @Override
    public Resources getResources() {
        if (sOverrides.resources != null) {
            return sOverrides.resources;
        }
        return super.getResources();
    }

    @Override
    protected Bitmap loadThumbnail(Uri uri, Size size) {
        if (sOverrides.previewThumbnail != null) {
            return sOverrides.previewThumbnail;
        }
        return super.loadThumbnail(uri, size);
    }

    @Override
    protected boolean isImageType(String mimeType) {
        return sOverrides.isImageType;
    }

    @Override
    protected MetricsLogger getMetricsLogger() {
        return sOverrides.metricsLogger;
    }

    @Override
    public ChooserActivityLogger getChooserActivityLogger() {
        return sOverrides.chooserActivityLogger;
    }

    @Override
    public Cursor queryResolver(ContentResolver resolver, Uri uri) {
        if (sOverrides.resolverCursor != null) {
            return sOverrides.resolverCursor;
        }

        if (sOverrides.resolverForceException) {
            throw new SecurityException("Test exception handling");
        }

        return super.queryResolver(resolver, uri);
    }

    @Override
    protected boolean isWorkProfile() {
        if (sOverrides.alternateProfileSetting != 0) {
            return sOverrides.alternateProfileSetting == MetricsEvent.MANAGED_PROFILE;
        }
        return super.isWorkProfile();
    }

    @Override
    public DisplayResolveInfo createTestDisplayResolveInfo(Intent originalIntent, ResolveInfo pri,
            CharSequence pLabel, CharSequence pInfo, Intent replacementIntent,
            @Nullable ResolveInfoPresentationGetter resolveInfoPresentationGetter) {
        return DisplayResolveInfo.newDisplayResolveInfo(
                originalIntent,
                pri,
                pLabel,
                pInfo,
                replacementIntent,
                resolveInfoPresentationGetter);
    }

    @Override
    protected UserHandle getWorkProfileUserHandle() {
        return sOverrides.workProfileUserHandle;
    }

    @Override
    public UserHandle getCurrentUserHandle() {
        return mMultiProfilePagerAdapter.getCurrentUserHandle();
    }

    @Override
    public Context createContextAsUser(UserHandle user, int flags) {
        // return the current context as a work profile doesn't really exist in these tests
        return getApplicationContext();
    }

    @Override
    protected ShortcutLoader createShortcutLoader(
            Context context,
            AppPredictor appPredictor,
            UserHandle userHandle,
            IntentFilter targetIntentFilter,
            Consumer<ShortcutLoader.Result> callback) {
        ShortcutLoader shortcutLoader =
                sOverrides.shortcutLoaderFactory.invoke(userHandle, callback);
        if (shortcutLoader != null) {
            return shortcutLoader;
        }
        return super.createShortcutLoader(
                context, appPredictor, userHandle, targetIntentFilter, callback);
    }
}

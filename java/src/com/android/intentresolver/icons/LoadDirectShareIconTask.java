/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.intentresolver.icons;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Trace;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.intentresolver.SimpleIconFactory;
import com.android.intentresolver.TargetPresentationGetter;
import com.android.intentresolver.chooser.SelectableTargetInfo;
import com.android.intentresolver.util.UriFilters;

import java.util.function.Consumer;

import javax.inject.Provider;

/**
 * Loads direct share targets icons.
 */
class LoadDirectShareIconTask extends BaseLoadIconTask {
    private static final String TAG = "DirectShareIconTask";
    private final SelectableTargetInfo mTargetInfo;
    private final Provider<SimpleIconFactory> mIconFactoryProvider;

    LoadDirectShareIconTask(
            Context context,
            SelectableTargetInfo targetInfo,
            TargetPresentationGetter.Factory presentationFactory,
            Provider<SimpleIconFactory> iconFactoryProvider,
            Consumer<Bitmap> callback) {
        super(context, presentationFactory, callback);
        mIconFactoryProvider = iconFactoryProvider;
        mTargetInfo = targetInfo;
    }

    @Override
    @Nullable
    protected Bitmap doInBackground(Void... voids) {
        Bitmap iconBitmap = null;
        Trace.beginSection("shortcut-icon");
        try {
            final Icon icon = mTargetInfo.getChooserTargetIcon();
            if (icon == null || UriFilters.hasValidIcon(icon)) {
                iconBitmap = getChooserTargetIconBitmap(
                        mContext,
                        icon,
                        mTargetInfo.getChooserTargetComponentName(),
                        mTargetInfo.getDirectShareShortcutInfo());
            } else {
                Log.e(TAG, "Failed to load shortcut icon for "
                        + mTargetInfo.getChooserTargetComponentName() + "; no access");
            }
        } catch (Exception e) {
            Log.e(
                    TAG,
                    "Failed to load shortcut icon for "
                            + mTargetInfo.getChooserTargetComponentName(),
                    e);
        } finally {
            Trace.endSection();
        }
        return iconBitmap;
    }

    @WorkerThread
    @Nullable
    private Bitmap getChooserTargetIconBitmap(
            Context context,
            @Nullable Icon icon,
            ComponentName targetComponentName,
            @Nullable ShortcutInfo shortcutInfo) {
        Drawable directShareIcon = null;

        // First get the target drawable and associated activity info
        if (icon != null) {
            directShareIcon = icon.loadDrawable(context);
        } else if (shortcutInfo != null) {
            LauncherApps launcherApps = context.getSystemService(LauncherApps.class);
            if (launcherApps != null) {
                directShareIcon = launcherApps.getShortcutIconDrawable(shortcutInfo, 0);
            }
        }

        if (directShareIcon == null) {
            return null;
        }

        ActivityInfo info = null;
        try {
            info = context.getPackageManager().getActivityInfo(targetComponentName, 0);
        } catch (PackageManager.NameNotFoundException error) {
            Log.e(TAG, "Could not find activity associated with ChooserTarget");
        }

        if (info == null) {
            return null;
        }

        // Now fetch app icon and raster with no badging even in work profile
        Bitmap appIcon = mPresentationFactory.makePresentationGetter(info).getIconBitmap(null);

        // Raster target drawable with appIcon as a badge
        Bitmap directShareBadgedIcon;
        try (SimpleIconFactory sif = mIconFactoryProvider.get()) {
            directShareBadgedIcon = sif.createAppBadgedIconBitmap(directShareIcon, appIcon);
        }

        return directShareBadgedIcon;
    }
}

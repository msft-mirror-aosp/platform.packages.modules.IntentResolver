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

import static com.android.internal.util.LatencyTracker.ACTION_LOAD_SHARE_SHEET;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.SharedElementCallback;
import android.app.prediction.AppPredictor;
import android.app.prediction.AppTarget;
import android.app.prediction.AppTargetEvent;
import android.app.prediction.AppTargetId;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.IntentSender.SendIntentException;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.Insets;
import android.graphics.drawable.Drawable;
import android.metrics.LogMaker;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Parcelable;
import android.os.PatternMatcher;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.storage.StorageManager;
import android.provider.DeviceConfig;
import android.provider.DocumentsContract;
import android.provider.Downloads;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.service.chooser.ChooserTarget;
import android.text.TextUtils;
import android.util.HashedStringCache;
import android.util.Log;
import android.util.PluralsMessageFormatter;
import android.util.Size;
import android.util.Slog;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Space;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import com.android.intentresolver.ResolverListAdapter.ViewHolder;
import com.android.intentresolver.chooser.DisplayResolveInfo;
import com.android.intentresolver.chooser.MultiDisplayResolveInfo;
import com.android.intentresolver.chooser.TargetInfo;
import com.android.intentresolver.model.AbstractResolverComparator;
import com.android.intentresolver.model.AppPredictionServiceResolverComparator;
import com.android.intentresolver.model.ResolverRankerServiceResolverComparator;
import com.android.intentresolver.shortcuts.AppPredictorFactory;
import com.android.intentresolver.shortcuts.ShortcutLoader;
import com.android.intentresolver.widget.ResolverDrawerLayout;
import com.android.intentresolver.widget.RoundedRectImageView;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.config.sysui.SystemUiDeviceConfigFlags;
import com.android.internal.content.PackageMonitor;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.util.FrameworkStatsLog;

import com.google.android.collect.Lists;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.URISyntaxException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The Chooser Activity handles intent resolution specifically for sharing intents -
 * for example, as generated by {@see android.content.Intent#createChooser(Intent, CharSequence)}.
 *
 */
public class ChooserActivity extends ResolverActivity implements
        ChooserListAdapter.ChooserListCommunicator {
    private static final String TAG = "ChooserActivity";

    private boolean mShouldDisplayLandscape;

    public ChooserActivity() {
    }
    /**
     * Boolean extra to change the following behavior: Normally, ChooserActivity finishes itself
     * in onStop when launched in a new task. If this extra is set to true, we do not finish
     * ourselves when onStop gets called.
     */
    public static final String EXTRA_PRIVATE_RETAIN_IN_ON_STOP
            = "com.android.internal.app.ChooserActivity.EXTRA_PRIVATE_RETAIN_IN_ON_STOP";


    /**
     * Transition name for the first image preview.
     * To be used for shared element transition into this activity.
     * @hide
     */
    public static final String FIRST_IMAGE_PREVIEW_TRANSITION_NAME = "screenshot_preview_image";

    private static final String PREF_NUM_SHEET_EXPANSIONS = "pref_num_sheet_expansions";

    private static final String CHIP_LABEL_METADATA_KEY = "android.service.chooser.chip_label";
    private static final String CHIP_ICON_METADATA_KEY = "android.service.chooser.chip_icon";

    private static final boolean DEBUG = true;

    public static final String LAUNCH_LOCATION_DIRECT_SHARE = "direct_share";
    private static final String SHORTCUT_TARGET = "shortcut_target";

    private static final String PLURALS_COUNT = "count";
    private static final String PLURALS_FILE_NAME = "file_name";

    private static final String IMAGE_EDITOR_SHARED_ELEMENT = "screenshot_preview_image";

    // TODO: these data structures are for one-time use in shuttling data from where they're
    // populated in `ShortcutToChooserTargetConverter` to where they're consumed in
    // `ShortcutSelectionLogic` which packs the appropriate elements into the final `TargetInfo`.
    // That flow should be refactored so that `ChooserActivity` isn't responsible for holding their
    // intermediate data, and then these members can be removed.
    private final Map<ChooserTarget, AppTarget> mDirectShareAppTargetCache = new HashMap<>();
    private final Map<ChooserTarget, ShortcutInfo> mDirectShareShortcutInfoCache = new HashMap<>();

    public static final int TARGET_TYPE_DEFAULT = 0;
    public static final int TARGET_TYPE_CHOOSER_TARGET = 1;
    public static final int TARGET_TYPE_SHORTCUTS_FROM_SHORTCUT_MANAGER = 2;
    public static final int TARGET_TYPE_SHORTCUTS_FROM_PREDICTION_SERVICE = 3;

    public static final int SELECTION_TYPE_SERVICE = 1;
    public static final int SELECTION_TYPE_APP = 2;
    public static final int SELECTION_TYPE_STANDARD = 3;
    public static final int SELECTION_TYPE_COPY = 4;
    public static final int SELECTION_TYPE_NEARBY = 5;
    public static final int SELECTION_TYPE_EDIT = 6;

    private static final int SCROLL_STATUS_IDLE = 0;
    private static final int SCROLL_STATUS_SCROLLING_VERTICAL = 1;
    private static final int SCROLL_STATUS_SCROLLING_HORIZONTAL = 2;

    // statsd logger wrapper
    protected ChooserActivityLogger mChooserActivityLogger;

    @IntDef(flag = false, prefix = { "TARGET_TYPE_" }, value = {
            TARGET_TYPE_DEFAULT,
            TARGET_TYPE_CHOOSER_TARGET,
            TARGET_TYPE_SHORTCUTS_FROM_SHORTCUT_MANAGER,
            TARGET_TYPE_SHORTCUTS_FROM_PREDICTION_SERVICE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ShareTargetType {}

    /**
     * The transition time between placeholders for direct share to a message
     * indicating that non are available.
     */
    private static final int NO_DIRECT_SHARE_ANIM_IN_MILLIS = 200;

    private static final float DIRECT_SHARE_EXPANSION_RATE = 0.78f;

    private static final int DEFAULT_SALT_EXPIRATION_DAYS = 7;
    private final int mMaxHashSaltDays = DeviceConfig.getInt(DeviceConfig.NAMESPACE_SYSTEMUI,
            SystemUiDeviceConfigFlags.HASH_SALT_MAX_DAYS,
            DEFAULT_SALT_EXPIRATION_DAYS);

    private static final int URI_PERMISSION_INTENT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;

    private Bundle mReplacementExtras;
    private IntentSender mChosenComponentSender;
    private IntentSender mRefinementIntentSender;
    private RefinementResultReceiver mRefinementResultReceiver;
    private ChooserTarget[] mCallerChooserTargets;
    private ArrayList<ComponentName> mFilteredComponentNames;

    private Intent mReferrerFillInIntent;

    private long mChooserShownTime;
    protected boolean mIsSuccessfullySelected;

    private int mCurrAvailableWidth = 0;
    private Insets mLastAppliedInsets = null;
    private int mLastNumberOfChildren = -1;
    private int mMaxTargetsPerRow = 1;

    private static final int MAX_LOG_RANK_POSITION = 12;

    private static final int MAX_EXTRA_INITIAL_INTENTS = 2;
    private static final int MAX_EXTRA_CHOOSER_TARGETS = 2;

    private SharedPreferences mPinnedSharedPrefs;
    private static final String PINNED_SHARED_PREFS_NAME = "chooser_pin_settings";

    @Retention(SOURCE)
    @IntDef({CONTENT_PREVIEW_FILE, CONTENT_PREVIEW_IMAGE, CONTENT_PREVIEW_TEXT})
    private @interface ContentPreviewType {
    }

    // Starting at 1 since 0 is considered "undefined" for some of the database transformations
    // of tron logs.
    protected static final int CONTENT_PREVIEW_IMAGE = 1;
    protected static final int CONTENT_PREVIEW_FILE = 2;
    protected static final int CONTENT_PREVIEW_TEXT = 3;
    protected MetricsLogger mMetricsLogger;

    private final ExecutorService mBackgroundThreadPoolExecutor = Executors.newFixedThreadPool(5);

    @Nullable
    private ChooserContentPreviewCoordinator mPreviewCoord;

    private int mScrollStatus = SCROLL_STATUS_IDLE;

    @VisibleForTesting
    protected ChooserMultiProfilePagerAdapter mChooserMultiProfilePagerAdapter;
    private final EnterTransitionAnimationDelegate mEnterTransitionAnimationDelegate =
            new EnterTransitionAnimationDelegate();

    private boolean mRemoveSharedElements = false;

    private View mContentView = null;

    private final SparseArray<ProfileRecord> mProfileRecords = new SparseArray<>();

    /**
     * Delegate to handle background resource loads that are dependencies of content previews.
     *
     * TODO: move to an inner class of the (to-be-created) new component for content previews.
     */
    public interface ContentPreviewCoordinator {
        /**
         * Request that an image be loaded in the background and set into a view.
         *
         * @param viewProvider A delegate that will be called exactly once upon completion of the
         * load, from the UI thread, to provide the {@link RoundedRectImageView} that should be
         * populated with the result (if the load was successful) or hidden (if the load failed). If
         * this returns null, the load is discarded as a failure.
         * @param imageUri The {@link Uri} of the image to load.
         * @param extraImages The "extra image count" to set on the {@link RoundedRectImageView}
         * if the image loads successfully.
         *
         * TODO: it looks like clients are probably capable of passing the view directly, but the
         * deferred computation here is a closer match to the legacy model for now.
         */
        void loadUriIntoView(
                Callable<RoundedRectImageView> viewProvider, Uri imageUri, int extraImages);
    }

    private void setupPreDrawForSharedElementTransition(View v) {
        v.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                v.getViewTreeObserver().removeOnPreDrawListener(this);

                if (!mRemoveSharedElements && isActivityTransitionRunning()) {
                    // Disable the window animations as it interferes with the transition animation.
                    getWindow().setWindowAnimations(0);
                }
                mEnterTransitionAnimationDelegate.markImagePreviewReady();
                return true;
            }
        });
    }

    private void hideContentPreview() {
        mRemoveSharedElements = true;
        mEnterTransitionAnimationDelegate.markImagePreviewReady();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        final long intentReceivedTime = System.currentTimeMillis();
        mLatencyTracker.onActionStart(ACTION_LOAD_SHARE_SHEET);

        getChooserActivityLogger().logSharesheetTriggered();

        mIsSuccessfullySelected = false;
        Intent intent = getIntent();
        Parcelable targetParcelable = intent.getParcelableExtra(Intent.EXTRA_INTENT);
        if (targetParcelable instanceof Uri) {
            try {
                targetParcelable = Intent.parseUri(targetParcelable.toString(),
                        Intent.URI_INTENT_SCHEME);
            } catch (URISyntaxException ex) {
                // doesn't parse as an intent; let the next test fail and error out
            }
        }

        if (!(targetParcelable instanceof Intent)) {
            Log.w("ChooserActivity", "Target is not an intent: " + targetParcelable);
            finish();
            super.onCreate(null);
            return;
        }
        final Intent target = (Intent) targetParcelable;
        modifyTargetIntent(target);
        Parcelable[] targetsParcelable
                = intent.getParcelableArrayExtra(Intent.EXTRA_ALTERNATE_INTENTS);
        if (targetsParcelable != null) {
            Intent[] additionalTargets = new Intent[targetsParcelable.length];
            for (int i = 0; i < targetsParcelable.length; i++) {
                if (!(targetsParcelable[i] instanceof Intent)) {
                    Log.w(TAG, "EXTRA_ALTERNATE_INTENTS array entry #" + i
                            + " is not an Intent: " + targetsParcelable[i]);
                    finish();
                    super.onCreate(null);
                    return;
                }
                final Intent additionalTarget = (Intent) targetsParcelable[i];
                additionalTargets[i] = additionalTarget;
                modifyTargetIntent(additionalTarget);
            }
            setAdditionalTargets(additionalTargets);
        }

        mReplacementExtras = intent.getBundleExtra(Intent.EXTRA_REPLACEMENT_EXTRAS);

        // Do not allow the title to be changed when sharing content
        CharSequence title = null;
        if (!isSendAction(target)) {
            title = intent.getCharSequenceExtra(Intent.EXTRA_TITLE);
        } else {
            Log.w(TAG, "Ignoring intent's EXTRA_TITLE, deprecated in P. You may wish to set a"
                    + " preview title by using EXTRA_TITLE property of the wrapped"
                    + " EXTRA_INTENT.");
        }

        int defaultTitleRes = 0;
        if (title == null) {
            defaultTitleRes = com.android.internal.R.string.chooseActivity;
        }

        Parcelable[] pa = intent.getParcelableArrayExtra(Intent.EXTRA_INITIAL_INTENTS);
        Intent[] initialIntents = null;
        if (pa != null) {
            int count = Math.min(pa.length, MAX_EXTRA_INITIAL_INTENTS);
            initialIntents = new Intent[count];
            for (int i = 0; i < count; i++) {
                if (!(pa[i] instanceof Intent)) {
                    Log.w(TAG, "Initial intent #" + i + " not an Intent: " + pa[i]);
                    finish();
                    super.onCreate(null);
                    return;
                }
                final Intent in = (Intent) pa[i];
                modifyTargetIntent(in);
                initialIntents[i] = in;
            }
        }

        mReferrerFillInIntent = new Intent().putExtra(Intent.EXTRA_REFERRER, getReferrer());

        mChosenComponentSender = intent.getParcelableExtra(
                Intent.EXTRA_CHOSEN_COMPONENT_INTENT_SENDER);
        mRefinementIntentSender = intent.getParcelableExtra(
                Intent.EXTRA_CHOOSER_REFINEMENT_INTENT_SENDER);
        setSafeForwardingMode(true);

        mPinnedSharedPrefs = getPinnedSharedPrefs(this);

        mFilteredComponentNames = new ArrayList<>();
        try {
            ComponentName[] exclodedComponents = intent.getParcelableArrayExtra(
                    Intent.EXTRA_EXCLUDE_COMPONENTS,
                    ComponentName.class);
            if (exclodedComponents != null) {
                Collections.addAll(mFilteredComponentNames, exclodedComponents);
            }
        } catch (ClassCastException e) {
            Log.e(TAG, "Excluded components must be of type ComponentName[]", e);
        }

        // Exclude Nearby from main list if chip is present, to avoid duplication
        ComponentName nearby = getNearbySharingComponent();
        if (nearby != null) {
            mFilteredComponentNames.add(nearby);
        }

        pa = intent.getParcelableArrayExtra(Intent.EXTRA_CHOOSER_TARGETS);
        if (pa != null) {
            int count = Math.min(pa.length, MAX_EXTRA_CHOOSER_TARGETS);
            ChooserTarget[] targets = new ChooserTarget[count];
            for (int i = 0; i < count; i++) {
                if (!(pa[i] instanceof ChooserTarget)) {
                    Log.w(TAG, "Chooser target #" + i + " not a ChooserTarget: " + pa[i]);
                    targets = null;
                    break;
                }
                targets[i] = (ChooserTarget) pa[i];
            }
            mCallerChooserTargets = targets;
        }

        mMaxTargetsPerRow = getResources().getInteger(R.integer.config_chooser_max_targets_per_row);
        mShouldDisplayLandscape =
                shouldDisplayLandscape(getResources().getConfiguration().orientation);
        setRetainInOnStop(intent.getBooleanExtra(EXTRA_PRIVATE_RETAIN_IN_ON_STOP, false));
        IntentFilter targetIntentFilter = getTargetIntentFilter(target);
        createProfileRecords(
                new AppPredictorFactory(
                        getApplicationContext(),
                        target.getStringExtra(Intent.EXTRA_TEXT),
                        targetIntentFilter),
                targetIntentFilter);

        mPreviewCoord = new ChooserContentPreviewCoordinator(
                mBackgroundThreadPoolExecutor,
                this,
                this::hideContentPreview,
                this::setupPreDrawForSharedElementTransition);

        super.onCreate(savedInstanceState, target, title, defaultTitleRes, initialIntents,
                null, false);

        mChooserShownTime = System.currentTimeMillis();
        final long systemCost = mChooserShownTime - intentReceivedTime;

        getMetricsLogger().write(new LogMaker(MetricsEvent.ACTION_ACTIVITY_CHOOSER_SHOWN)
                .setSubtype(isWorkProfile() ? MetricsEvent.MANAGED_PROFILE :
                        MetricsEvent.PARENT_PROFILE)
                .addTaggedData(MetricsEvent.FIELD_SHARESHEET_MIMETYPE, target.getType())
                .addTaggedData(MetricsEvent.FIELD_TIME_TO_APP_TARGETS, systemCost));

        if (mResolverDrawerLayout != null) {
            mResolverDrawerLayout.addOnLayoutChangeListener(this::handleLayoutChange);

            // expand/shrink direct share 4 -> 8 viewgroup
            if (isSendAction(target)) {
                mResolverDrawerLayout.setOnScrollChangeListener(this::handleScroll);
            }

            mResolverDrawerLayout.setOnCollapsedChangedListener(
                    new ResolverDrawerLayout.OnCollapsedChangedListener() {

                        // Only consider one expansion per activity creation
                        private boolean mWrittenOnce = false;

                        @Override
                        public void onCollapsedChanged(boolean isCollapsed) {
                            if (!isCollapsed && !mWrittenOnce) {
                                incrementNumSheetExpansions();
                                mWrittenOnce = true;
                            }
                            getChooserActivityLogger()
                                    .logSharesheetExpansionChanged(isCollapsed);
                        }
                    });
        }

        if (DEBUG) {
            Log.d(TAG, "System Time Cost is " + systemCost);
        }

        getChooserActivityLogger().logShareStarted(
                FrameworkStatsLog.SHARESHEET_STARTED,
                getReferrerPackageName(),
                target.getType(),
                mCallerChooserTargets == null ? 0 : mCallerChooserTargets.length,
                initialIntents == null ? 0 : initialIntents.length,
                isWorkProfile(),
                findPreferredContentPreview(getTargetIntent(), getContentResolver()),
                target.getAction()
        );

        setEnterSharedElementCallback(new SharedElementCallback() {
            @Override
            public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
                if (mRemoveSharedElements) {
                    names.remove(FIRST_IMAGE_PREVIEW_TRANSITION_NAME);
                    sharedElements.remove(FIRST_IMAGE_PREVIEW_TRANSITION_NAME);
                }
                super.onMapSharedElements(names, sharedElements);
                mRemoveSharedElements = false;
            }
        });
        mEnterTransitionAnimationDelegate.postponeTransition();
    }

    @Override
    protected int appliedThemeResId() {
        return R.style.Theme_DeviceDefault_Chooser;
    }

    private void createProfileRecords(
            AppPredictorFactory factory, IntentFilter targetIntentFilter) {
        UserHandle mainUserHandle = getPersonalProfileUserHandle();
        createProfileRecord(mainUserHandle, targetIntentFilter, factory);

        UserHandle workUserHandle = getWorkProfileUserHandle();
        if (workUserHandle != null) {
            createProfileRecord(workUserHandle, targetIntentFilter, factory);
        }
    }

    private void createProfileRecord(
            UserHandle userHandle, IntentFilter targetIntentFilter, AppPredictorFactory factory) {
        AppPredictor appPredictor = factory.create(userHandle);
        ShortcutLoader shortcutLoader = ActivityManager.isLowRamDeviceStatic()
                    ? null
                    : createShortcutLoader(
                            getApplicationContext(),
                            appPredictor,
                            userHandle,
                            targetIntentFilter,
                            shortcutsResult -> onShortcutsLoaded(userHandle, shortcutsResult));
        mProfileRecords.put(
                userHandle.getIdentifier(),
                new ProfileRecord(appPredictor, shortcutLoader));
    }

    @Nullable
    private ProfileRecord getProfileRecord(UserHandle userHandle) {
        return mProfileRecords.get(userHandle.getIdentifier(), null);
    }

    @VisibleForTesting
    protected ShortcutLoader createShortcutLoader(
            Context context,
            AppPredictor appPredictor,
            UserHandle userHandle,
            IntentFilter targetIntentFilter,
            Consumer<ShortcutLoader.Result> callback) {
        return new ShortcutLoader(
                context,
                appPredictor,
                userHandle,
                targetIntentFilter,
                callback);
    }

    static SharedPreferences getPinnedSharedPrefs(Context context) {
        // The code below is because in the android:ui process, no one can hear you scream.
        // The package info in the context isn't initialized in the way it is for normal apps,
        // so the standard, name-based context.getSharedPreferences doesn't work. Instead, we
        // build the path manually below using the same policy that appears in ContextImpl.
        // This fails silently under the hood if there's a problem, so if we find ourselves in
        // the case where we don't have access to credential encrypted storage we just won't
        // have our pinned target info.
        final File prefsFile = new File(new File(
                Environment.getDataUserCePackageDirectory(StorageManager.UUID_PRIVATE_INTERNAL,
                        context.getUserId(), context.getPackageName()),
                "shared_prefs"),
                PINNED_SHARED_PREFS_NAME + ".xml");
        return context.getSharedPreferences(prefsFile, MODE_PRIVATE);
    }

    @Override
    protected AbstractMultiProfilePagerAdapter createMultiProfilePagerAdapter(
            Intent[] initialIntents,
            List<ResolveInfo> rList,
            boolean filterLastUsed) {
        if (shouldShowTabs()) {
            mChooserMultiProfilePagerAdapter = createChooserMultiProfilePagerAdapterForTwoProfiles(
                    initialIntents, rList, filterLastUsed);
        } else {
            mChooserMultiProfilePagerAdapter = createChooserMultiProfilePagerAdapterForOneProfile(
                    initialIntents, rList, filterLastUsed);
        }
        return mChooserMultiProfilePagerAdapter;
    }

    private ChooserMultiProfilePagerAdapter createChooserMultiProfilePagerAdapterForOneProfile(
            Intent[] initialIntents,
            List<ResolveInfo> rList,
            boolean filterLastUsed) {
        ChooserGridAdapter adapter = createChooserGridAdapter(
                /* context */ this,
                /* payloadIntents */ mIntents,
                initialIntents,
                rList,
                filterLastUsed,
                /* userHandle */ UserHandle.of(UserHandle.myUserId()));
        return new ChooserMultiProfilePagerAdapter(
                /* context */ this,
                adapter,
                getPersonalProfileUserHandle(),
                /* workProfileUserHandle= */ null,
                isSendAction(getTargetIntent()), mMaxTargetsPerRow);
    }

    private ChooserMultiProfilePagerAdapter createChooserMultiProfilePagerAdapterForTwoProfiles(
            Intent[] initialIntents,
            List<ResolveInfo> rList,
            boolean filterLastUsed) {
        int selectedProfile = findSelectedProfile();
        ChooserGridAdapter personalAdapter = createChooserGridAdapter(
                /* context */ this,
                /* payloadIntents */ mIntents,
                selectedProfile == PROFILE_PERSONAL ? initialIntents : null,
                rList,
                filterLastUsed,
                /* userHandle */ getPersonalProfileUserHandle());
        ChooserGridAdapter workAdapter = createChooserGridAdapter(
                /* context */ this,
                /* payloadIntents */ mIntents,
                selectedProfile == PROFILE_WORK ? initialIntents : null,
                rList,
                filterLastUsed,
                /* userHandle */ getWorkProfileUserHandle());
        return new ChooserMultiProfilePagerAdapter(
                /* context */ this,
                personalAdapter,
                workAdapter,
                selectedProfile,
                getPersonalProfileUserHandle(),
                getWorkProfileUserHandle(),
                isSendAction(getTargetIntent()), mMaxTargetsPerRow);
    }

    private int findSelectedProfile() {
        int selectedProfile = getSelectedProfileExtra();
        if (selectedProfile == -1) {
            selectedProfile = getProfileForUser(getUser());
        }
        return selectedProfile;
    }

    @Override
    protected boolean postRebuildList(boolean rebuildCompleted) {
        updateStickyContentPreview();
        if (shouldShowStickyContentPreview()
                || mChooserMultiProfilePagerAdapter
                        .getCurrentRootAdapter().getSystemRowCount() != 0) {
            logActionShareWithPreview();
        }
        return postRebuildListInternal(rebuildCompleted);
    }

    /**
     * Check if the profile currently used is a work profile.
     * @return true if it is work profile, false if it is parent profile (or no work profile is
     * set up)
     */
    protected boolean isWorkProfile() {
        return getSystemService(UserManager.class)
                .getUserInfo(UserHandle.myUserId()).isManagedProfile();
    }

    @Override
    protected PackageMonitor createPackageMonitor(ResolverListAdapter listAdapter) {
        return new PackageMonitor() {
            @Override
            public void onSomePackagesChanged() {
                handlePackagesChanged(listAdapter);
            }
        };
    }

    /**
     * Update UI to reflect changes in data.
     */
    public void handlePackagesChanged() {
        handlePackagesChanged(/* listAdapter */ null);
    }

    /**
     * Update UI to reflect changes in data.
     * <p>If {@code listAdapter} is {@code null}, both profile list adapters are updated if
     * available.
     */
    private void handlePackagesChanged(@Nullable ResolverListAdapter listAdapter) {
        // Refresh pinned items
        mPinnedSharedPrefs = getPinnedSharedPrefs(this);
        if (listAdapter == null) {
            mChooserMultiProfilePagerAdapter.getActiveListAdapter().handlePackagesChanged();
            if (mChooserMultiProfilePagerAdapter.getCount() > 1) {
                mChooserMultiProfilePagerAdapter.getInactiveListAdapter().handlePackagesChanged();
            }
        } else {
            listAdapter.handlePackagesChanged();
        }
        updateProfileViewButton();
    }

    private void onCopyButtonClicked(View v) {
        Intent targetIntent = getTargetIntent();
        if (targetIntent == null) {
            finish();
        } else {
            final String action = targetIntent.getAction();

            ClipData clipData = null;
            if (Intent.ACTION_SEND.equals(action)) {
                String extraText = targetIntent.getStringExtra(Intent.EXTRA_TEXT);
                Uri extraStream = targetIntent.getParcelableExtra(Intent.EXTRA_STREAM);

                if (extraText != null) {
                    clipData = ClipData.newPlainText(null, extraText);
                } else if (extraStream != null) {
                    clipData = ClipData.newUri(getContentResolver(), null, extraStream);
                } else {
                    Log.w(TAG, "No data available to copy to clipboard");
                    return;
                }
            } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
                final ArrayList<Uri> streams = targetIntent.getParcelableArrayListExtra(
                        Intent.EXTRA_STREAM);
                clipData = ClipData.newUri(getContentResolver(), null, streams.get(0));
                for (int i = 1; i < streams.size(); i++) {
                    clipData.addItem(getContentResolver(), new ClipData.Item(streams.get(i)));
                }
            } else {
                // expected to only be visible with ACTION_SEND or ACTION_SEND_MULTIPLE
                // so warn about unexpected action
                Log.w(TAG, "Action (" + action + ") not supported for copying to clipboard");
                return;
            }

            ClipboardManager clipboardManager = (ClipboardManager) getSystemService(
                    Context.CLIPBOARD_SERVICE);
            clipboardManager.setPrimaryClipAsPackage(clipData, getReferrerPackageName());

            // Log share completion via copy
            LogMaker targetLogMaker = new LogMaker(
                    MetricsEvent.ACTION_ACTIVITY_CHOOSER_PICKED_SYSTEM_TARGET).setSubtype(1);
            getMetricsLogger().write(targetLogMaker);
            getChooserActivityLogger().logShareTargetSelected(
                    SELECTION_TYPE_COPY,
                    "",
                    -1,
                    false);

            setResult(RESULT_OK);
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: " + getComponentName().flattenToShortString());
        maybeCancelFinishAnimation();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        ViewPager viewPager = findViewById(com.android.internal.R.id.profile_pager);
        if (viewPager.isLayoutRtl()) {
            mMultiProfilePagerAdapter.setupViewPager(viewPager);
        }

        mShouldDisplayLandscape = shouldDisplayLandscape(newConfig.orientation);
        mMaxTargetsPerRow = getResources().getInteger(R.integer.config_chooser_max_targets_per_row);
        mChooserMultiProfilePagerAdapter.setMaxTargetsPerRow(mMaxTargetsPerRow);
        adjustPreviewWidth(newConfig.orientation, null);
        updateStickyContentPreview();
        updateTabPadding();
    }

    private boolean shouldDisplayLandscape(int orientation) {
        // Sharesheet fixes the # of items per row and therefore can not correctly lay out
        // when in the restricted size of multi-window mode. In the future, would be nice
        // to use minimum dp size requirements instead
        return orientation == Configuration.ORIENTATION_LANDSCAPE && !isInMultiWindowMode();
    }

    private void adjustPreviewWidth(int orientation, View parent) {
        int width = -1;
        if (mShouldDisplayLandscape) {
            width = getResources().getDimensionPixelSize(R.dimen.chooser_preview_width);
        }

        parent = parent == null ? getWindow().getDecorView() : parent;

        updateLayoutWidth(com.android.internal.R.id.content_preview_text_layout, width, parent);
        updateLayoutWidth(com.android.internal.R.id.content_preview_title_layout, width, parent);
        updateLayoutWidth(com.android.internal.R.id.content_preview_file_layout, width, parent);
    }

    private void updateTabPadding() {
        if (shouldShowTabs()) {
            View tabs = findViewById(com.android.internal.R.id.tabs);
            float iconSize = getResources().getDimension(R.dimen.chooser_icon_size);
            // The entire width consists of icons or padding. Divide the item padding in half to get
            // paddingHorizontal.
            float padding = (tabs.getWidth() - mMaxTargetsPerRow * iconSize)
                    / mMaxTargetsPerRow / 2;
            // Subtract the margin the buttons already have.
            padding -= getResources().getDimension(R.dimen.resolver_profile_tab_margin);
            tabs.setPadding((int) padding, 0, (int) padding, 0);
        }
    }

    private void updateLayoutWidth(int layoutResourceId, int width, View parent) {
        View view = parent.findViewById(layoutResourceId);
        if (view != null && view.getLayoutParams() != null) {
            LayoutParams params = view.getLayoutParams();
            params.width = width;
            view.setLayoutParams(params);
        }
    }

    /**
     * Create a view that will be shown in the content preview area
     * @param parent reference to the parent container where the view should be attached to
     * @return content preview view
     */
    protected ViewGroup createContentPreviewView(
            ViewGroup parent, ContentPreviewCoordinator previewCoord) {
        Intent targetIntent = getTargetIntent();
        int previewType = findPreferredContentPreview(targetIntent, getContentResolver());
        return displayContentPreview(
                previewType, targetIntent, getLayoutInflater(), parent, previewCoord);
    }

    @VisibleForTesting
    protected ComponentName getNearbySharingComponent() {
        String nearbyComponent = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.NEARBY_SHARING_COMPONENT);
        if (TextUtils.isEmpty(nearbyComponent)) {
            nearbyComponent = getString(R.string.config_defaultNearbySharingComponent);
        }
        if (TextUtils.isEmpty(nearbyComponent)) {
            return null;
        }
        return ComponentName.unflattenFromString(nearbyComponent);
    }

    @VisibleForTesting
    protected @Nullable ComponentName getEditSharingComponent() {
        String editorPackage = getApplicationContext().getString(R.string.config_systemImageEditor);
        if (editorPackage == null || TextUtils.isEmpty(editorPackage)) {
            return null;
        }
        return ComponentName.unflattenFromString(editorPackage);
    }

    @VisibleForTesting
    protected TargetInfo getEditSharingTarget(Intent originalIntent) {
        final ComponentName cn = getEditSharingComponent();

        final Intent resolveIntent = new Intent(originalIntent);
        // Retain only URI permission grant flags if present. Other flags may prevent the scene
        // transition animation from running (i.e FLAG_ACTIVITY_NO_ANIMATION,
        // FLAG_ACTIVITY_NEW_TASK, FLAG_ACTIVITY_NEW_DOCUMENT) but also not needed.
        resolveIntent.setFlags(originalIntent.getFlags() & URI_PERMISSION_INTENT_FLAGS);
        resolveIntent.setComponent(cn);
        resolveIntent.setAction(Intent.ACTION_EDIT);
        String originalAction = originalIntent.getAction();
        if (Intent.ACTION_SEND.equals(originalAction)) {
            if (resolveIntent.getData() == null) {
                Uri uri = resolveIntent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (uri != null) {
                    String mimeType = getContentResolver().getType(uri);
                    resolveIntent.setDataAndType(uri, mimeType);
                }
            }
        } else {
            Log.e(TAG, originalAction + " is not supported.");
            return null;
        }
        final ResolveInfo ri = getPackageManager().resolveActivity(
                resolveIntent, PackageManager.GET_META_DATA);
        if (ri == null || ri.activityInfo == null) {
            Log.e(TAG, "Device-specified image edit component (" + cn
                    + ") not available");
            return null;
        }

        final DisplayResolveInfo dri = DisplayResolveInfo.newDisplayResolveInfo(
                originalIntent,
                ri,
                getString(com.android.internal.R.string.screenshot_edit),
                "",
                resolveIntent,
                null);
        dri.setDisplayIcon(getDrawable(com.android.internal.R.drawable.ic_screenshot_edit));
        return dri;
    }

    @VisibleForTesting
    protected TargetInfo getNearbySharingTarget(Intent originalIntent) {
        final ComponentName cn = getNearbySharingComponent();
        if (cn == null) return null;

        final Intent resolveIntent = new Intent(originalIntent);
        resolveIntent.setComponent(cn);
        final ResolveInfo ri = getPackageManager().resolveActivity(
                resolveIntent, PackageManager.GET_META_DATA);
        if (ri == null || ri.activityInfo == null) {
            Log.e(TAG, "Device-specified nearby sharing component (" + cn
                    + ") not available");
            return null;
        }

        // Allow the nearby sharing component to provide a more appropriate icon and label
        // for the chip.
        CharSequence name = null;
        Drawable icon = null;
        final Bundle metaData = ri.activityInfo.metaData;
        if (metaData != null) {
            try {
                final Resources pkgRes = getPackageManager().getResourcesForActivity(cn);
                final int nameResId = metaData.getInt(CHIP_LABEL_METADATA_KEY);
                name = pkgRes.getString(nameResId);
                final int resId = metaData.getInt(CHIP_ICON_METADATA_KEY);
                icon = pkgRes.getDrawable(resId);
            } catch (Resources.NotFoundException ex) {
            } catch (NameNotFoundException ex) {
            }
        }
        if (TextUtils.isEmpty(name)) {
            name = ri.loadLabel(getPackageManager());
        }
        if (icon == null) {
            icon = ri.loadIcon(getPackageManager());
        }

        final DisplayResolveInfo dri = DisplayResolveInfo.newDisplayResolveInfo(
                originalIntent, ri, name, "", resolveIntent, null);
        dri.setDisplayIcon(icon);
        return dri;
    }

    private Button createActionButton(Drawable icon, CharSequence title, View.OnClickListener r) {
        Button b = (Button) LayoutInflater.from(this).inflate(R.layout.chooser_action_button, null);
        if (icon != null) {
            final int size = getResources()
                    .getDimensionPixelSize(R.dimen.chooser_action_button_icon_size);
            icon.setBounds(0, 0, size, size);
            b.setCompoundDrawablesRelative(icon, null, null, null);
        }
        b.setText(title);
        b.setOnClickListener(r);
        return b;
    }

    private Button createCopyButton() {
        final Button b = createActionButton(
                getDrawable(com.android.internal.R.drawable.ic_menu_copy_material),
                getString(com.android.internal.R.string.copy), this::onCopyButtonClicked);
        b.setId(com.android.internal.R.id.chooser_copy_button);
        return b;
    }

    private @Nullable Button createNearbyButton(Intent originalIntent) {
        final TargetInfo ti = getNearbySharingTarget(originalIntent);
        if (ti == null) return null;

        final Button b = createActionButton(
                ti.getDisplayIcon(),
                ti.getDisplayLabel(),
                (View unused) -> {
                    // Log share completion via nearby
                    getChooserActivityLogger().logShareTargetSelected(
                            SELECTION_TYPE_NEARBY,
                            "",
                            -1,
                            false);
                    // Action bar is user-independent, always start as primary
                    safelyStartActivityAsUser(ti, getPersonalProfileUserHandle());
                    finish();
                }
        );
        b.setId(com.android.internal.R.id.chooser_nearby_button);
        return b;
    }

    private @Nullable Button createEditButton(Intent originalIntent) {
        final TargetInfo ti = getEditSharingTarget(originalIntent);
        if (ti == null) return null;

        final Button b = createActionButton(
                ti.getDisplayIcon(),
                ti.getDisplayLabel(),
                (View unused) -> {
                    // Log share completion via edit
                    getChooserActivityLogger().logShareTargetSelected(
                            SELECTION_TYPE_EDIT,
                            "",
                            -1,
                            false);
                    View firstImgView = getFirstVisibleImgPreviewView();
                    // Action bar is user-independent, always start as primary
                    if (firstImgView == null) {
                        safelyStartActivityAsUser(ti, getPersonalProfileUserHandle());
                        finish();
                    } else {
                        ActivityOptions options = ActivityOptions.makeSceneTransitionAnimation(
                                this, firstImgView, IMAGE_EDITOR_SHARED_ELEMENT);
                        safelyStartActivityAsUser(
                                ti, getPersonalProfileUserHandle(), options.toBundle());
                        startFinishAnimation();
                    }
                }
        );
        b.setId(com.android.internal.R.id.chooser_edit_button);
        return b;
    }

    @Nullable
    private View getFirstVisibleImgPreviewView() {
        View firstImage = findViewById(com.android.internal.R.id.content_preview_image_1_large);
        return firstImage != null && firstImage.isVisibleToUser() ? firstImage : null;
    }

    private void addActionButton(ViewGroup parent, Button b) {
        if (b == null) return;
        final ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                        LayoutParams.WRAP_CONTENT,
                        LayoutParams.WRAP_CONTENT
                );
        final int gap = getResources().getDimensionPixelSize(R.dimen.resolver_icon_margin) / 2;
        lp.setMarginsRelative(gap, 0, gap, 0);
        parent.addView(b, lp);
    }

    private ViewGroup displayContentPreview(
            @ContentPreviewType int previewType,
            Intent targetIntent,
            LayoutInflater layoutInflater,
            ViewGroup parent,
            ContentPreviewCoordinator previewCoord) {
        ViewGroup layout = null;

        switch (previewType) {
            case CONTENT_PREVIEW_TEXT:
                layout = displayTextContentPreview(
                        targetIntent, layoutInflater, parent, previewCoord);
                break;
            case CONTENT_PREVIEW_IMAGE:
                layout = displayImageContentPreview(
                        targetIntent, layoutInflater, parent, previewCoord);
                break;
            case CONTENT_PREVIEW_FILE:
                layout = displayFileContentPreview(
                        targetIntent, layoutInflater, parent, previewCoord);
                break;
            default:
                Log.e(TAG, "Unexpected content preview type: " + previewType);
        }

        if (layout != null) {
            adjustPreviewWidth(getResources().getConfiguration().orientation, layout);
        }
        if (previewType != CONTENT_PREVIEW_IMAGE) {
            mEnterTransitionAnimationDelegate.markImagePreviewReady();
        }

        return layout;
    }

    private ViewGroup displayTextContentPreview(
            Intent targetIntent,
            LayoutInflater layoutInflater,
            ViewGroup parent,
            ContentPreviewCoordinator previewCoord) {
        ViewGroup contentPreviewLayout = (ViewGroup) layoutInflater.inflate(
                R.layout.chooser_grid_preview_text, parent, false);

        final ViewGroup actionRow =
                (ViewGroup) contentPreviewLayout.findViewById(com.android.internal.R.id.chooser_action_row);
        addActionButton(actionRow, createCopyButton());
        addActionButton(actionRow, createNearbyButton(targetIntent));

        CharSequence sharingText = targetIntent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (sharingText == null) {
            contentPreviewLayout.findViewById(com.android.internal.R.id.content_preview_text_layout).setVisibility(
                    View.GONE);
        } else {
            TextView textView = contentPreviewLayout.findViewById(com.android.internal.R.id.content_preview_text);
            textView.setText(sharingText);
        }

        String previewTitle = targetIntent.getStringExtra(Intent.EXTRA_TITLE);
        if (TextUtils.isEmpty(previewTitle)) {
            contentPreviewLayout.findViewById(com.android.internal.R.id.content_preview_title_layout).setVisibility(
                    View.GONE);
        } else {
            TextView previewTitleView = contentPreviewLayout.findViewById(
                    com.android.internal.R.id.content_preview_title);
            previewTitleView.setText(previewTitle);

            ClipData previewData = targetIntent.getClipData();
            Uri previewThumbnail = null;
            if (previewData != null) {
                if (previewData.getItemCount() > 0) {
                    ClipData.Item previewDataItem = previewData.getItemAt(0);
                    previewThumbnail = previewDataItem.getUri();
                }
            }

            ImageView previewThumbnailView = contentPreviewLayout.findViewById(
                    com.android.internal.R.id.content_preview_thumbnail);
            if (previewThumbnail == null) {
                previewThumbnailView.setVisibility(View.GONE);
            } else {
                previewCoord.loadUriIntoView(
                        () -> contentPreviewLayout.findViewById(
                                com.android.internal.R.id.content_preview_thumbnail),
                        previewThumbnail,
                        0);
            }
        }

        return contentPreviewLayout;
    }

    private ViewGroup displayImageContentPreview(
            Intent targetIntent,
            LayoutInflater layoutInflater,
            ViewGroup parent,
            ContentPreviewCoordinator previewCoord) {
        ViewGroup contentPreviewLayout = (ViewGroup) layoutInflater.inflate(
                R.layout.chooser_grid_preview_image, parent, false);
        ViewGroup imagePreview = contentPreviewLayout.findViewById(com.android.internal.R.id.content_preview_image_area);

        final ViewGroup actionRow =
                (ViewGroup) contentPreviewLayout.findViewById(com.android.internal.R.id.chooser_action_row);
        //TODO: addActionButton(actionRow, createCopyButton());
        addActionButton(actionRow, createNearbyButton(targetIntent));
        addActionButton(actionRow, createEditButton(targetIntent));

        String action = targetIntent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            Uri uri = targetIntent.getParcelableExtra(Intent.EXTRA_STREAM);
            imagePreview.findViewById(com.android.internal.R.id.content_preview_image_1_large)
                    .setTransitionName(ChooserActivity.FIRST_IMAGE_PREVIEW_TRANSITION_NAME);
            previewCoord.loadUriIntoView(
                    () -> contentPreviewLayout.findViewById(
                            com.android.internal.R.id.content_preview_image_1_large),
                    uri,
                    0);
        } else {
            ContentResolver resolver = getContentResolver();

            List<Uri> uris = targetIntent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            List<Uri> imageUris = new ArrayList<>();
            for (Uri uri : uris) {
                if (isImageType(resolver.getType(uri))) {
                    imageUris.add(uri);
                }
            }

            if (imageUris.size() == 0) {
                Log.i(TAG, "Attempted to display image preview area with zero"
                        + " available images detected in EXTRA_STREAM list");
                imagePreview.setVisibility(View.GONE);
                return contentPreviewLayout;
            }

            imagePreview.findViewById(com.android.internal.R.id.content_preview_image_1_large)
                    .setTransitionName(ChooserActivity.FIRST_IMAGE_PREVIEW_TRANSITION_NAME);
            previewCoord.loadUriIntoView(
                    () -> contentPreviewLayout.findViewById(
                            com.android.internal.R.id.content_preview_image_1_large),
                    imageUris.get(0),
                    0);

            if (imageUris.size() == 2) {
                previewCoord.loadUriIntoView(
                        () -> contentPreviewLayout.findViewById(
                                com.android.internal.R.id.content_preview_image_2_large),
                        imageUris.get(1),
                        0);
            } else if (imageUris.size() > 2) {
                previewCoord.loadUriIntoView(
                        () -> contentPreviewLayout.findViewById(
                                com.android.internal.R.id.content_preview_image_2_small),
                        imageUris.get(1),
                        0);
                previewCoord.loadUriIntoView(
                        () -> contentPreviewLayout.findViewById(
                                com.android.internal.R.id.content_preview_image_3_small),
                        imageUris.get(2),
                        imageUris.size() - 3);
            }
        }

        return contentPreviewLayout;
    }

    private static class FileInfo {
        public final String name;
        public final boolean hasThumbnail;

        FileInfo(String name, boolean hasThumbnail) {
            this.name = name;
            this.hasThumbnail = hasThumbnail;
        }
    }

    /**
     * Wrapping the ContentResolver call to expose for easier mocking,
     * and to avoid mocking Android core classes.
     */
    @VisibleForTesting
    public Cursor queryResolver(ContentResolver resolver, Uri uri) {
        return resolver.query(uri, null, null, null, null);
    }

    private FileInfo extractFileInfo(Uri uri, ContentResolver resolver) {
        String fileName = null;
        boolean hasThumbnail = false;

        try (Cursor cursor = queryResolver(resolver, uri)) {
            if (cursor != null && cursor.getCount() > 0) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int titleIndex = cursor.getColumnIndex(Downloads.Impl.COLUMN_TITLE);
                int flagsIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS);

                cursor.moveToFirst();
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex);
                } else if (titleIndex != -1) {
                    fileName = cursor.getString(titleIndex);
                }

                if (flagsIndex != -1) {
                    hasThumbnail = (cursor.getInt(flagsIndex)
                            & DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL) != 0;
                }
            }
        } catch (SecurityException | NullPointerException e) {
            logContentPreviewWarning(uri);
        }

        if (TextUtils.isEmpty(fileName)) {
            fileName = uri.getPath();
            int index = fileName.lastIndexOf('/');
            if (index != -1) {
                fileName = fileName.substring(index + 1);
            }
        }

        return new FileInfo(fileName, hasThumbnail);
    }

    private void logContentPreviewWarning(Uri uri) {
        // The ContentResolver already logs the exception. Log something more informative.
        Log.w(TAG, "Could not load (" + uri.toString() + ") thumbnail/name for preview. If "
                + "desired, consider using Intent#createChooser to launch the ChooserActivity, "
                + "and set your Intent's clipData and flags in accordance with that method's "
                + "documentation");
    }

    private ViewGroup displayFileContentPreview(
            Intent targetIntent,
            LayoutInflater layoutInflater,
            ViewGroup parent,
            ContentPreviewCoordinator previewCoord) {
        ViewGroup contentPreviewLayout = (ViewGroup) layoutInflater.inflate(
                R.layout.chooser_grid_preview_file, parent, false);

        final ViewGroup actionRow =
                (ViewGroup) contentPreviewLayout.findViewById(com.android.internal.R.id.chooser_action_row);
        //TODO(b/120417119): addActionButton(actionRow, createCopyButton());
        addActionButton(actionRow, createNearbyButton(targetIntent));

        String action = targetIntent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            Uri uri = targetIntent.getParcelableExtra(Intent.EXTRA_STREAM);
            loadFileUriIntoView(uri, contentPreviewLayout, previewCoord);
        } else {
            List<Uri> uris = targetIntent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            int uriCount = uris.size();

            if (uriCount == 0) {
                contentPreviewLayout.setVisibility(View.GONE);
                Log.i(TAG,
                        "Appears to be no uris available in EXTRA_STREAM, removing "
                                + "preview area");
                return contentPreviewLayout;
            } else if (uriCount == 1) {
                loadFileUriIntoView(uris.get(0), contentPreviewLayout, previewCoord);
            } else {
                FileInfo fileInfo = extractFileInfo(uris.get(0), getContentResolver());
                int remUriCount = uriCount - 1;
                Map<String, Object> arguments = new HashMap<>();
                arguments.put(PLURALS_COUNT, remUriCount);
                arguments.put(PLURALS_FILE_NAME, fileInfo.name);
                String fileName = PluralsMessageFormatter.format(
                        getResources(),
                        arguments,
                        R.string.file_count);

                TextView fileNameView = contentPreviewLayout.findViewById(
                        com.android.internal.R.id.content_preview_filename);
                fileNameView.setText(fileName);

                View thumbnailView = contentPreviewLayout.findViewById(
                        com.android.internal.R.id.content_preview_file_thumbnail);
                thumbnailView.setVisibility(View.GONE);

                ImageView fileIconView = contentPreviewLayout.findViewById(
                        com.android.internal.R.id.content_preview_file_icon);
                fileIconView.setVisibility(View.VISIBLE);
                fileIconView.setImageResource(R.drawable.ic_file_copy);
            }
        }

        return contentPreviewLayout;
    }

    private void loadFileUriIntoView(
            final Uri uri, final View parent, final ContentPreviewCoordinator previewCoord) {
        FileInfo fileInfo = extractFileInfo(uri, getContentResolver());

        TextView fileNameView = parent.findViewById(com.android.internal.R.id.content_preview_filename);
        fileNameView.setText(fileInfo.name);

        if (fileInfo.hasThumbnail) {
            previewCoord.loadUriIntoView(
                    () -> parent.findViewById(
                            com.android.internal.R.id.content_preview_file_thumbnail),
                    uri,
                    0);
        } else {
            View thumbnailView = parent.findViewById(com.android.internal.R.id.content_preview_file_thumbnail);
            thumbnailView.setVisibility(View.GONE);

            ImageView fileIconView = parent.findViewById(com.android.internal.R.id.content_preview_file_icon);
            fileIconView.setVisibility(View.VISIBLE);
            fileIconView.setImageResource(R.drawable.chooser_file_generic);
        }
    }

    @VisibleForTesting
    protected boolean isImageType(String mimeType) {
        return mimeType != null && mimeType.startsWith("image/");
    }

    @ContentPreviewType
    private int findPreferredContentPreview(Uri uri, ContentResolver resolver) {
        if (uri == null) {
            return CONTENT_PREVIEW_TEXT;
        }

        String mimeType = resolver.getType(uri);
        return isImageType(mimeType) ? CONTENT_PREVIEW_IMAGE : CONTENT_PREVIEW_FILE;
    }

    /**
     * In {@link android.content.Intent#getType}, the app may specify a very general
     * mime-type that broadly covers all data being shared, such as {@literal *}/*
     * when sending an image and text. We therefore should inspect each item for the
     * the preferred type, in order of IMAGE, FILE, TEXT.
     */
    @ContentPreviewType
    private int findPreferredContentPreview(Intent targetIntent, ContentResolver resolver) {
        String action = targetIntent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            Uri uri = targetIntent.getParcelableExtra(Intent.EXTRA_STREAM);
            return findPreferredContentPreview(uri, resolver);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            List<Uri> uris = targetIntent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (uris == null || uris.isEmpty()) {
                return CONTENT_PREVIEW_TEXT;
            }

            for (Uri uri : uris) {
                // Defaulting to file preview when there are mixed image/file types is
                // preferable, as it shows the user the correct number of items being shared
                if (findPreferredContentPreview(uri, resolver) == CONTENT_PREVIEW_FILE) {
                    return CONTENT_PREVIEW_FILE;
                }
            }

            return CONTENT_PREVIEW_IMAGE;
        }

        return CONTENT_PREVIEW_TEXT;
    }

    private int getNumSheetExpansions() {
        return getPreferences(Context.MODE_PRIVATE).getInt(PREF_NUM_SHEET_EXPANSIONS, 0);
    }

    private void incrementNumSheetExpansions() {
        getPreferences(Context.MODE_PRIVATE).edit().putInt(PREF_NUM_SHEET_EXPANSIONS,
                getNumSheetExpansions() + 1).apply();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (maybeCancelFinishAnimation()) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (isFinishing()) {
            mLatencyTracker.onActionCancel(ACTION_LOAD_SHARE_SHEET);
        }

        if (mRefinementResultReceiver != null) {
            mRefinementResultReceiver.destroy();
            mRefinementResultReceiver = null;
        }

        mBackgroundThreadPoolExecutor.shutdownNow();

        destroyProfileRecords();
    }

    private void destroyProfileRecords() {
        for (int i = 0; i < mProfileRecords.size(); ++i) {
            mProfileRecords.valueAt(i).destroy();
        }
        mProfileRecords.clear();
    }

    @Override // ResolverListCommunicator
    public Intent getReplacementIntent(ActivityInfo aInfo, Intent defIntent) {
        Intent result = defIntent;
        if (mReplacementExtras != null) {
            final Bundle replExtras = mReplacementExtras.getBundle(aInfo.packageName);
            if (replExtras != null) {
                result = new Intent(defIntent);
                result.putExtras(replExtras);
            }
        }
        if (aInfo.name.equals(IntentForwarderActivity.FORWARD_INTENT_TO_PARENT)
                || aInfo.name.equals(IntentForwarderActivity.FORWARD_INTENT_TO_MANAGED_PROFILE)) {
            result = Intent.createChooser(result,
                    getIntent().getCharSequenceExtra(Intent.EXTRA_TITLE));

            // Don't auto-launch single intents if the intent is being forwarded. This is done
            // because automatically launching a resolving application as a response to the user
            // action of switching accounts is pretty unexpected.
            result.putExtra(Intent.EXTRA_AUTO_LAUNCH_SINGLE_CHOICE, false);
        }
        return result;
    }

    @Override
    public void onActivityStarted(TargetInfo cti) {
        if (mChosenComponentSender != null) {
            final ComponentName target = cti.getResolvedComponentName();
            if (target != null) {
                final Intent fillIn = new Intent().putExtra(Intent.EXTRA_CHOSEN_COMPONENT, target);
                try {
                    mChosenComponentSender.sendIntent(this, Activity.RESULT_OK, fillIn, null, null);
                } catch (IntentSender.SendIntentException e) {
                    Slog.e(TAG, "Unable to launch supplied IntentSender to report "
                            + "the chosen component: " + e);
                }
            }
        }
    }

    @Override
    public void addUseDifferentAppLabelIfNecessary(ResolverListAdapter adapter) {
        if (mCallerChooserTargets != null && mCallerChooserTargets.length > 0) {
            mChooserMultiProfilePagerAdapter.getActiveListAdapter().addServiceResults(
                    /* origTarget */ null,
                    Lists.newArrayList(mCallerChooserTargets),
                    TARGET_TYPE_DEFAULT,
                    /* directShareShortcutInfoCache */ Collections.emptyMap(),
                    /* directShareAppTargetCache */ Collections.emptyMap());
        }
    }

    @Override
    public int getLayoutResource() {
        return R.layout.chooser_grid;
    }

    @Override // ResolverListCommunicator
    public boolean shouldGetActivityMetadata() {
        return true;
    }

    @Override
    public boolean shouldAutoLaunchSingleChoice(TargetInfo target) {
        // Note that this is only safe because the Intent handled by the ChooserActivity is
        // guaranteed to contain no extras unknown to the local ClassLoader. That is why this
        // method can not be replaced in the ResolverActivity whole hog.
        if (!super.shouldAutoLaunchSingleChoice(target)) {
            return false;
        }

        return getIntent().getBooleanExtra(Intent.EXTRA_AUTO_LAUNCH_SINGLE_CHOICE, true);
    }

    private void showTargetDetails(TargetInfo targetInfo) {
        if (targetInfo == null) return;

        List<DisplayResolveInfo> targetList = targetInfo.getAllDisplayTargets();
        if (targetList.isEmpty()) {
            Log.e(TAG, "No displayable data to show target details");
            return;
        }

        // TODO: implement these type-conditioned behaviors polymorphically, and consider moving
        // the logic into `ChooserTargetActionsDialogFragment.show()`.
        boolean isShortcutPinned = targetInfo.isSelectableTargetInfo() && targetInfo.isPinned();
        IntentFilter intentFilter =
                targetInfo.isSelectableTargetInfo() ? getTargetIntentFilter() : null;
        String shortcutTitle = targetInfo.isSelectableTargetInfo()
                ? targetInfo.getDisplayLabel().toString() : null;
        String shortcutIdKey = targetInfo.getDirectShareShortcutId();

        ChooserTargetActionsDialogFragment.show(
                getSupportFragmentManager(),
                targetList,
                mChooserMultiProfilePagerAdapter.getCurrentUserHandle(),
                shortcutIdKey,
                shortcutTitle,
                isShortcutPinned,
                intentFilter);
    }

    private void modifyTargetIntent(Intent in) {
        if (isSendAction(in)) {
            in.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT |
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        }
    }

    @Override
    protected boolean onTargetSelected(TargetInfo target, boolean alwaysCheck) {
        if (mRefinementIntentSender != null) {
            final Intent fillIn = new Intent();
            final List<Intent> sourceIntents = target.getAllSourceIntents();
            if (!sourceIntents.isEmpty()) {
                fillIn.putExtra(Intent.EXTRA_INTENT, sourceIntents.get(0));
                if (sourceIntents.size() > 1) {
                    final Intent[] alts = new Intent[sourceIntents.size() - 1];
                    for (int i = 1, N = sourceIntents.size(); i < N; i++) {
                        alts[i - 1] = sourceIntents.get(i);
                    }
                    fillIn.putExtra(Intent.EXTRA_ALTERNATE_INTENTS, alts);
                }
                if (mRefinementResultReceiver != null) {
                    mRefinementResultReceiver.destroy();
                }
                mRefinementResultReceiver = new RefinementResultReceiver(this, target, null);
                fillIn.putExtra(Intent.EXTRA_RESULT_RECEIVER,
                        mRefinementResultReceiver);
                try {
                    mRefinementIntentSender.sendIntent(this, 0, fillIn, null, null);
                    return false;
                } catch (SendIntentException e) {
                    Log.e(TAG, "Refinement IntentSender failed to send", e);
                }
            }
        }
        updateModelAndChooserCounts(target);
        return super.onTargetSelected(target, alwaysCheck);
    }

    @Override
    public void startSelected(int which, boolean always, boolean filtered) {
        ChooserListAdapter currentListAdapter =
                mChooserMultiProfilePagerAdapter.getActiveListAdapter();
        TargetInfo targetInfo = currentListAdapter
                .targetInfoForPosition(which, filtered);
        if (targetInfo != null && targetInfo.isNotSelectableTargetInfo()) {
            return;
        }

        final long selectionCost = System.currentTimeMillis() - mChooserShownTime;

        if (targetInfo.isMultiDisplayResolveInfo()) {
            MultiDisplayResolveInfo mti = (MultiDisplayResolveInfo) targetInfo;
            if (!mti.hasSelected()) {
                ChooserStackedAppDialogFragment.show(
                        getSupportFragmentManager(),
                        mti,
                        which,
                        mChooserMultiProfilePagerAdapter.getCurrentUserHandle());
                return;
            }
        }

        super.startSelected(which, always, filtered);

        if (currentListAdapter.getCount() > 0) {
            // Log the index of which type of target the user picked.
            // Lower values mean the ranking was better.
            int cat = 0;
            int value = which;
            int directTargetAlsoRanked = -1;
            int numCallerProvided = 0;
            HashedStringCache.HashResult directTargetHashed = null;
            switch (currentListAdapter.getPositionTargetType(which)) {
                case ChooserListAdapter.TARGET_SERVICE:
                    cat = MetricsEvent.ACTION_ACTIVITY_CHOOSER_PICKED_SERVICE_TARGET;
                    directTargetHashed = targetInfo.getHashedTargetIdForMetrics(this);
                    directTargetAlsoRanked = getRankedPosition(targetInfo);

                    if (mCallerChooserTargets != null) {
                        numCallerProvided = mCallerChooserTargets.length;
                    }
                    getChooserActivityLogger().logShareTargetSelected(
                            SELECTION_TYPE_SERVICE,
                            targetInfo.getResolveInfo().activityInfo.processName,
                            value,
                            targetInfo.isPinned()
                    );
                    break;
                case ChooserListAdapter.TARGET_CALLER:
                case ChooserListAdapter.TARGET_STANDARD:
                    cat = MetricsEvent.ACTION_ACTIVITY_CHOOSER_PICKED_APP_TARGET;
                    value -= currentListAdapter.getSurfacedTargetInfo().size();
                    numCallerProvided = currentListAdapter.getCallerTargetCount();
                    getChooserActivityLogger().logShareTargetSelected(
                            SELECTION_TYPE_APP,
                            targetInfo.getResolveInfo().activityInfo.processName,
                            value,
                            targetInfo.isPinned()
                    );
                    break;
                case ChooserListAdapter.TARGET_STANDARD_AZ:
                    // A-Z targets are unranked standard targets; we use -1 to mark that they
                    // are from the alphabetical pool.
                    value = -1;
                    cat = MetricsEvent.ACTION_ACTIVITY_CHOOSER_PICKED_STANDARD_TARGET;
                    getChooserActivityLogger().logShareTargetSelected(
                            SELECTION_TYPE_STANDARD,
                            targetInfo.getResolveInfo().activityInfo.processName,
                            value,
                            false
                    );
                    break;
            }

            if (cat != 0) {
                LogMaker targetLogMaker = new LogMaker(cat).setSubtype(value);
                if (directTargetHashed != null) {
                    targetLogMaker.addTaggedData(
                            MetricsEvent.FIELD_HASHED_TARGET_NAME, directTargetHashed.hashedString);
                    targetLogMaker.addTaggedData(
                                    MetricsEvent.FIELD_HASHED_TARGET_SALT_GEN,
                                    directTargetHashed.saltGeneration);
                    targetLogMaker.addTaggedData(MetricsEvent.FIELD_RANKED_POSITION,
                                    directTargetAlsoRanked);
                }
                targetLogMaker.addTaggedData(MetricsEvent.FIELD_IS_CATEGORY_USED,
                        numCallerProvided);
                getMetricsLogger().write(targetLogMaker);
            }

            if (mIsSuccessfullySelected) {
                if (DEBUG) {
                    Log.d(TAG, "User Selection Time Cost is " + selectionCost);
                    Log.d(TAG, "position of selected app/service/caller is " +
                            Integer.toString(value));
                }
                MetricsLogger.histogram(null, "user_selection_cost_for_smart_sharing",
                        (int) selectionCost);
                MetricsLogger.histogram(null, "app_position_for_smart_sharing", value);
            }
        }
    }

    private int getRankedPosition(TargetInfo targetInfo) {
        String targetPackageName =
                targetInfo.getChooserTargetComponentName().getPackageName();
        ChooserListAdapter currentListAdapter =
                mChooserMultiProfilePagerAdapter.getActiveListAdapter();
        int maxRankedResults = Math.min(currentListAdapter.mDisplayList.size(),
                MAX_LOG_RANK_POSITION);

        for (int i = 0; i < maxRankedResults; i++) {
            if (currentListAdapter.mDisplayList.get(i)
                    .getResolveInfo().activityInfo.packageName.equals(targetPackageName)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected boolean shouldAddFooterView() {
        // To accommodate for window insets
        return true;
    }

    @Override
    protected void applyFooterView(int height) {
        int count = mChooserMultiProfilePagerAdapter.getItemCount();

        for (int i = 0; i < count; i++) {
            mChooserMultiProfilePagerAdapter.getAdapterForIndex(i).setFooterHeight(height);
        }
    }

    private IntentFilter getTargetIntentFilter() {
        return getTargetIntentFilter(getTargetIntent());
    }

    private IntentFilter getTargetIntentFilter(final Intent intent) {
        try {
            String dataString = intent.getDataString();
            if (intent.getType() == null) {
                if (!TextUtils.isEmpty(dataString)) {
                    return new IntentFilter(intent.getAction(), dataString);
                }
                Log.e(TAG, "Failed to get target intent filter: intent data and type are null");
                return null;
            }
            IntentFilter intentFilter = new IntentFilter(intent.getAction(), intent.getType());
            List<Uri> contentUris = new ArrayList<>();
            if (Intent.ACTION_SEND.equals(intent.getAction())) {
                Uri uri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (uri != null) {
                    contentUris.add(uri);
                }
            } else {
                List<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                if (uris != null) {
                    contentUris.addAll(uris);
                }
            }
            for (Uri uri : contentUris) {
                intentFilter.addDataScheme(uri.getScheme());
                intentFilter.addDataAuthority(uri.getAuthority(), null);
                intentFilter.addDataPath(uri.getPath(), PatternMatcher.PATTERN_LITERAL);
            }
            return intentFilter;
        } catch (Exception e) {
            Log.e(TAG, "Failed to get target intent filter", e);
            return null;
        }
    }

    private void logDirectShareTargetReceived(int logCategory, UserHandle forUser) {
        ProfileRecord profileRecord = getProfileRecord(forUser);
        if (profileRecord == null) {
            return;
        }

        final int apiLatency =
                (int) (SystemClock.elapsedRealtime() - profileRecord.loadingStartTime);
        getMetricsLogger().write(new LogMaker(logCategory).setSubtype(apiLatency));
    }

    void updateModelAndChooserCounts(TargetInfo info) {
        if (info != null && info.isMultiDisplayResolveInfo()) {
            info = ((MultiDisplayResolveInfo) info).getSelectedTarget();
        }
        if (info != null) {
            sendClickToAppPredictor(info);
            final ResolveInfo ri = info.getResolveInfo();
            Intent targetIntent = getTargetIntent();
            if (ri != null && ri.activityInfo != null && targetIntent != null) {
                ChooserListAdapter currentListAdapter =
                        mChooserMultiProfilePagerAdapter.getActiveListAdapter();
                if (currentListAdapter != null) {
                    sendImpressionToAppPredictor(info, currentListAdapter);
                    currentListAdapter.updateModel(info.getResolvedComponentName());
                    currentListAdapter.updateChooserCounts(ri.activityInfo.packageName,
                            targetIntent.getAction());
                }
                if (DEBUG) {
                    Log.d(TAG, "ResolveInfo Package is " + ri.activityInfo.packageName);
                    Log.d(TAG, "Action to be updated is " + targetIntent.getAction());
                }
            } else if (DEBUG) {
                Log.d(TAG, "Can not log Chooser Counts of null ResolveInfo");
            }
        }
        mIsSuccessfullySelected = true;
    }

    private void sendImpressionToAppPredictor(TargetInfo targetInfo, ChooserListAdapter adapter) {
        // Send DS target impression info to AppPredictor, only when user chooses app share.
        if (targetInfo.isChooserTargetInfo()) {
            return;
        }

        AppPredictor directShareAppPredictor = getAppPredictor(
                mChooserMultiProfilePagerAdapter.getCurrentUserHandle());
        if (directShareAppPredictor == null) {
            return;
        }
        List<TargetInfo> surfacedTargetInfo = adapter.getSurfacedTargetInfo();
        List<AppTargetId> targetIds = new ArrayList<>();
        for (TargetInfo chooserTargetInfo : surfacedTargetInfo) {
            ShortcutInfo shortcutInfo = chooserTargetInfo.getDirectShareShortcutInfo();
            if (shortcutInfo != null) {
                ComponentName componentName =
                        chooserTargetInfo.getChooserTargetComponentName();
                targetIds.add(new AppTargetId(
                        String.format(
                                "%s/%s/%s",
                                shortcutInfo.getId(),
                                componentName.flattenToString(),
                                SHORTCUT_TARGET)));
            }
        }
        directShareAppPredictor.notifyLaunchLocationShown(LAUNCH_LOCATION_DIRECT_SHARE, targetIds);
    }

    private void sendClickToAppPredictor(TargetInfo targetInfo) {
        if (!targetInfo.isChooserTargetInfo()) {
            return;
        }

        AppPredictor directShareAppPredictor = getAppPredictor(
                mChooserMultiProfilePagerAdapter.getCurrentUserHandle());
        if (directShareAppPredictor == null) {
            return;
        }
        AppTarget appTarget = targetInfo.getDirectShareAppTarget();
        if (appTarget != null) {
            // This is a direct share click that was provided by the APS
            directShareAppPredictor.notifyAppTargetEvent(
                    new AppTargetEvent.Builder(appTarget, AppTargetEvent.ACTION_LAUNCH)
                        .setLaunchLocation(LAUNCH_LOCATION_DIRECT_SHARE)
                        .build());
        }
    }

    @Nullable
    private AppPredictor getAppPredictor(UserHandle userHandle) {
        ProfileRecord record = getProfileRecord(userHandle);
        return (record == null) ? null : record.appPredictor;
    }

    void onRefinementResult(TargetInfo selectedTarget, Intent matchingIntent) {
        if (mRefinementResultReceiver != null) {
            mRefinementResultReceiver.destroy();
            mRefinementResultReceiver = null;
        }
        if (selectedTarget == null) {
            Log.e(TAG, "Refinement result intent did not match any known targets; canceling");
        } else if (!checkTargetSourceIntent(selectedTarget, matchingIntent)) {
            Log.e(TAG, "onRefinementResult: Selected target " + selectedTarget
                    + " cannot match refined source intent " + matchingIntent);
        } else {
            TargetInfo clonedTarget = selectedTarget.cloneFilledIn(matchingIntent, 0);
            if (super.onTargetSelected(clonedTarget, false)) {
                updateModelAndChooserCounts(clonedTarget);
                finish();
                return;
            }
        }
        onRefinementCanceled();
    }

    void onRefinementCanceled() {
        if (mRefinementResultReceiver != null) {
            mRefinementResultReceiver.destroy();
            mRefinementResultReceiver = null;
        }
        finish();
    }

    boolean checkTargetSourceIntent(TargetInfo target, Intent matchingIntent) {
        final List<Intent> targetIntents = target.getAllSourceIntents();
        for (int i = 0, N = targetIntents.size(); i < N; i++) {
            final Intent targetIntent = targetIntents.get(i);
            if (targetIntent.filterEquals(matchingIntent)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sort intents alphabetically based on display label.
     */
    static class AzInfoComparator implements Comparator<DisplayResolveInfo> {
        Collator mCollator;
        AzInfoComparator(Context context) {
            mCollator = Collator.getInstance(context.getResources().getConfiguration().locale);
        }

        @Override
        public int compare(
                DisplayResolveInfo lhsp, DisplayResolveInfo rhsp) {
            return mCollator.compare(lhsp.getDisplayLabel(), rhsp.getDisplayLabel());
        }
    }

    protected MetricsLogger getMetricsLogger() {
        if (mMetricsLogger == null) {
            mMetricsLogger = new MetricsLogger();
        }
        return mMetricsLogger;
    }

    protected ChooserActivityLogger getChooserActivityLogger() {
        if (mChooserActivityLogger == null) {
            mChooserActivityLogger = new ChooserActivityLogger();
        }
        return mChooserActivityLogger;
    }

    public class ChooserListController extends ResolverListController {
        public ChooserListController(Context context,
                PackageManager pm,
                Intent targetIntent,
                String referrerPackageName,
                int launchedFromUid,
                UserHandle userId,
                AbstractResolverComparator resolverComparator) {
            super(context, pm, targetIntent, referrerPackageName, launchedFromUid, userId,
                    resolverComparator);
        }

        @Override
        boolean isComponentFiltered(ComponentName name) {
            return mFilteredComponentNames != null && mFilteredComponentNames.contains(name);
        }

        @Override
        public boolean isComponentPinned(ComponentName name) {
            return mPinnedSharedPrefs.getBoolean(name.flattenToString(), false);
        }
    }

    @VisibleForTesting
    public ChooserGridAdapter createChooserGridAdapter(Context context,
            List<Intent> payloadIntents, Intent[] initialIntents, List<ResolveInfo> rList,
            boolean filterLastUsed, UserHandle userHandle) {
        ChooserListAdapter chooserListAdapter = createChooserListAdapter(context, payloadIntents,
                initialIntents, rList, filterLastUsed,
                createListController(userHandle));
        return new ChooserGridAdapter(chooserListAdapter);
    }

    @VisibleForTesting
    public ChooserListAdapter createChooserListAdapter(Context context,
            List<Intent> payloadIntents, Intent[] initialIntents, List<ResolveInfo> rList,
            boolean filterLastUsed, ResolverListController resolverListController) {
        return new ChooserListAdapter(
                context,
                payloadIntents,
                initialIntents,
                rList,
                filterLastUsed,
                resolverListController,
                this,
                context.getPackageManager(),
                getChooserActivityLogger());
    }

    @VisibleForTesting
    protected ResolverListController createListController(UserHandle userHandle) {
        AppPredictor appPredictor = getAppPredictor(userHandle);
        AbstractResolverComparator resolverComparator;
        if (appPredictor != null) {
            resolverComparator = new AppPredictionServiceResolverComparator(this, getTargetIntent(),
                    getReferrerPackageName(), appPredictor, userHandle, getChooserActivityLogger());
        } else {
            resolverComparator =
                    new ResolverRankerServiceResolverComparator(this, getTargetIntent(),
                        getReferrerPackageName(), null, getChooserActivityLogger());
        }

        return new ChooserListController(
                this,
                mPm,
                getTargetIntent(),
                getReferrerPackageName(),
                mLaunchedFromUid,
                userHandle,
                resolverComparator);
    }

    @VisibleForTesting
    protected Bitmap loadThumbnail(Uri uri, Size size) {
        if (uri == null || size == null) {
            return null;
        }

        try {
            return getContentResolver().loadThumbnail(uri, size, null);
        } catch (IOException | NullPointerException | SecurityException ex) {
            logContentPreviewWarning(uri);
        }
        return null;
    }

    private void handleScroll(View view, int x, int y, int oldx, int oldy) {
        if (mChooserMultiProfilePagerAdapter.getCurrentRootAdapter() != null) {
            mChooserMultiProfilePagerAdapter.getCurrentRootAdapter().handleScroll(view, y, oldy);
        }
    }

    /*
     * Need to dynamically adjust how many icons can fit per row before we add them,
     * which also means setting the correct offset to initially show the content
     * preview area + 2 rows of targets
     */
    private void handleLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
            int oldTop, int oldRight, int oldBottom) {
        if (mChooserMultiProfilePagerAdapter == null) {
            return;
        }
        RecyclerView recyclerView = mChooserMultiProfilePagerAdapter.getActiveAdapterView();
        ChooserGridAdapter gridAdapter = mChooserMultiProfilePagerAdapter.getCurrentRootAdapter();
        // Skip height calculation if recycler view was scrolled to prevent it inaccurately
        // calculating the height, as the logic below does not account for the scrolled offset.
        if (gridAdapter == null || recyclerView == null
                || recyclerView.computeVerticalScrollOffset() != 0) {
            return;
        }

        final int availableWidth = right - left - v.getPaddingLeft() - v.getPaddingRight();
        boolean isLayoutUpdated =
                gridAdapter.calculateChooserTargetWidth(availableWidth)
                || recyclerView.getAdapter() == null
                || availableWidth != mCurrAvailableWidth;

        boolean insetsChanged = !Objects.equals(mLastAppliedInsets, mSystemWindowInsets);

        if (isLayoutUpdated
                || insetsChanged
                || mLastNumberOfChildren != recyclerView.getChildCount()) {
            mCurrAvailableWidth = availableWidth;
            if (isLayoutUpdated) {
                // It is very important we call setAdapter from here. Otherwise in some cases
                // the resolver list doesn't get populated, such as b/150922090, b/150918223
                // and b/150936654
                recyclerView.setAdapter(gridAdapter);
                ((GridLayoutManager) recyclerView.getLayoutManager()).setSpanCount(
                        mMaxTargetsPerRow);

                updateTabPadding();
            }

            UserHandle currentUserHandle = mChooserMultiProfilePagerAdapter.getCurrentUserHandle();
            int currentProfile = getProfileForUser(currentUserHandle);
            int initialProfile = findSelectedProfile();
            if (currentProfile != initialProfile) {
                return;
            }

            if (mLastNumberOfChildren == recyclerView.getChildCount() && !insetsChanged) {
                return;
            }

            getMainThreadHandler().post(() -> {
                if (mResolverDrawerLayout == null || gridAdapter == null) {
                    return;
                }
                int offset = calculateDrawerOffset(top, bottom, recyclerView, gridAdapter);
                mResolverDrawerLayout.setCollapsibleHeightReserved(offset);
                mEnterTransitionAnimationDelegate.markOffsetCalculated();
                mLastAppliedInsets = mSystemWindowInsets;
            });
        }
    }

    private int calculateDrawerOffset(
            int top, int bottom, RecyclerView recyclerView, ChooserGridAdapter gridAdapter) {

        final int bottomInset = mSystemWindowInsets != null
                ? mSystemWindowInsets.bottom : 0;
        int offset = bottomInset;
        int rowsToShow = gridAdapter.getSystemRowCount()
                + gridAdapter.getProfileRowCount()
                + gridAdapter.getServiceTargetRowCount()
                + gridAdapter.getCallerAndRankedTargetRowCount();

        // then this is most likely not a SEND_* action, so check
        // the app target count
        if (rowsToShow == 0) {
            rowsToShow = gridAdapter.getRowCount();
        }

        // still zero? then use a default height and leave, which
        // can happen when there are no targets to show
        if (rowsToShow == 0 && !shouldShowStickyContentPreview()) {
            offset += getResources().getDimensionPixelSize(
                    R.dimen.chooser_max_collapsed_height);
            return offset;
        }

        View stickyContentPreview = findViewById(com.android.internal.R.id.content_preview_container);
        if (shouldShowStickyContentPreview() && isStickyContentPreviewShowing()) {
            offset += stickyContentPreview.getHeight();
        }

        if (shouldShowTabs()) {
            offset += findViewById(com.android.internal.R.id.tabs).getHeight();
        }

        if (recyclerView.getVisibility() == View.VISIBLE) {
            int directShareHeight = 0;
            rowsToShow = Math.min(4, rowsToShow);
            boolean shouldShowExtraRow = shouldShowExtraRow(rowsToShow);
            mLastNumberOfChildren = recyclerView.getChildCount();
            for (int i = 0, childCount = recyclerView.getChildCount();
                    i < childCount && rowsToShow > 0; i++) {
                View child = recyclerView.getChildAt(i);
                if (((GridLayoutManager.LayoutParams)
                        child.getLayoutParams()).getSpanIndex() != 0) {
                    continue;
                }
                int height = child.getHeight();
                offset += height;
                if (shouldShowExtraRow) {
                    offset += height;
                }

                if (gridAdapter.getTargetType(
                        recyclerView.getChildAdapterPosition(child))
                        == ChooserListAdapter.TARGET_SERVICE) {
                    directShareHeight = height;
                }
                rowsToShow--;
            }

            boolean isExpandable = getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_PORTRAIT && !isInMultiWindowMode();
            if (directShareHeight != 0 && shouldShowContentPreview()
                    && isExpandable) {
                // make sure to leave room for direct share 4->8 expansion
                int requiredExpansionHeight =
                        (int) (directShareHeight / DIRECT_SHARE_EXPANSION_RATE);
                int topInset = mSystemWindowInsets != null ? mSystemWindowInsets.top : 0;
                int minHeight = bottom - top - mResolverDrawerLayout.getAlwaysShowHeight()
                        - requiredExpansionHeight - topInset - bottomInset;

                offset = Math.min(offset, minHeight);
            }
        } else {
            ViewGroup currentEmptyStateView = getActiveEmptyStateView();
            if (currentEmptyStateView.getVisibility() == View.VISIBLE) {
                offset += currentEmptyStateView.getHeight();
            }
        }

        return Math.min(offset, bottom - top);
    }

    /**
     * If we have a tabbed view and are showing 1 row in the current profile and an empty
     * state screen in the other profile, to prevent cropping of the empty state screen we show
     * a second row in the current profile.
     */
    private boolean shouldShowExtraRow(int rowsToShow) {
        return shouldShowTabs()
                && rowsToShow == 1
                && mChooserMultiProfilePagerAdapter.shouldShowEmptyStateScreen(
                        mChooserMultiProfilePagerAdapter.getInactiveListAdapter());
    }

    /**
     * Returns {@link #PROFILE_PERSONAL}, {@link #PROFILE_WORK}, or -1 if the given user handle
     * does not match either the personal or work user handle.
     **/
    private int getProfileForUser(UserHandle currentUserHandle) {
        if (currentUserHandle.equals(getPersonalProfileUserHandle())) {
            return PROFILE_PERSONAL;
        } else if (currentUserHandle.equals(getWorkProfileUserHandle())) {
            return PROFILE_WORK;
        }
        Log.e(TAG, "User " + currentUserHandle + " does not belong to a personal or work profile.");
        return -1;
    }

    private ViewGroup getActiveEmptyStateView() {
        int currentPage = mChooserMultiProfilePagerAdapter.getCurrentPage();
        return mChooserMultiProfilePagerAdapter.getItem(currentPage).getEmptyStateView();
    }

    @Override // ResolverListCommunicator
    public void onHandlePackagesChanged(ResolverListAdapter listAdapter) {
        mChooserMultiProfilePagerAdapter.getActiveListAdapter().notifyDataSetChanged();
        super.onHandlePackagesChanged(listAdapter);
    }

    @Override // SelectableTargetInfoCommunicator
    public Intent getReferrerFillInIntent() {
        return mReferrerFillInIntent;
    }

    @Override // ChooserListCommunicator
    public int getMaxRankedTargets() {
        return mMaxTargetsPerRow;
    }

    @Override
    public void onListRebuilt(ResolverListAdapter listAdapter, boolean rebuildComplete) {
        setupScrollListener();
        maybeSetupGlobalLayoutListener();

        ChooserListAdapter chooserListAdapter = (ChooserListAdapter) listAdapter;
        if (chooserListAdapter.getUserHandle()
                .equals(mChooserMultiProfilePagerAdapter.getCurrentUserHandle())) {
            mChooserMultiProfilePagerAdapter.getActiveAdapterView()
                    .setAdapter(mChooserMultiProfilePagerAdapter.getCurrentRootAdapter());
            mChooserMultiProfilePagerAdapter
                    .setupListAdapter(mChooserMultiProfilePagerAdapter.getCurrentPage());
        }

        if (chooserListAdapter.mDisplayList == null
                || chooserListAdapter.mDisplayList.isEmpty()) {
            chooserListAdapter.notifyDataSetChanged();
        } else {
            chooserListAdapter.updateAlphabeticalList();
        }

        if (rebuildComplete) {
            getChooserActivityLogger().logSharesheetAppLoadComplete();
            maybeQueryAdditionalPostProcessingTargets(chooserListAdapter);
            mLatencyTracker.onActionEnd(ACTION_LOAD_SHARE_SHEET);
        }
    }

    private void maybeQueryAdditionalPostProcessingTargets(ChooserListAdapter chooserListAdapter) {
        UserHandle userHandle = chooserListAdapter.getUserHandle();
        ProfileRecord record = getProfileRecord(userHandle);
        if (record == null) {
            return;
        }
        if (record.shortcutLoader == null) {
            return;
        }
        record.loadingStartTime = SystemClock.elapsedRealtime();
        record.shortcutLoader.queryShortcuts(chooserListAdapter.getDisplayResolveInfos());
    }

    @MainThread
    private void onShortcutsLoaded(
            UserHandle userHandle, ShortcutLoader.Result shortcutsResult) {
        if (DEBUG) {
            Log.d(TAG, "onShortcutsLoaded for user: " + userHandle);
        }
        mDirectShareShortcutInfoCache.putAll(shortcutsResult.directShareShortcutInfoCache);
        mDirectShareAppTargetCache.putAll(shortcutsResult.directShareAppTargetCache);
        ChooserListAdapter adapter =
                mChooserMultiProfilePagerAdapter.getListAdapterForUserHandle(userHandle);
        if (adapter != null) {
            for (ShortcutLoader.ShortcutResultInfo resultInfo : shortcutsResult.shortcutsByApp) {
                adapter.addServiceResults(
                        resultInfo.appTarget,
                        resultInfo.shortcuts,
                        shortcutsResult.isFromAppPredictor
                                ? TARGET_TYPE_SHORTCUTS_FROM_PREDICTION_SERVICE
                                : TARGET_TYPE_SHORTCUTS_FROM_SHORTCUT_MANAGER,
                        mDirectShareShortcutInfoCache,
                        mDirectShareAppTargetCache);
            }
            adapter.completeServiceTargetLoading();
        }

        logDirectShareTargetReceived(
                MetricsEvent.ACTION_DIRECT_SHARE_TARGETS_LOADED_SHORTCUT_MANAGER,
                userHandle);

        sendVoiceChoicesIfNeeded();
        getChooserActivityLogger().logSharesheetDirectLoadComplete();
    }

    private void setupScrollListener() {
        if (mResolverDrawerLayout == null) {
            return;
        }
        int elevatedViewResId = shouldShowTabs() ? com.android.internal.R.id.tabs : com.android.internal.R.id.chooser_header;
        final View elevatedView = mResolverDrawerLayout.findViewById(elevatedViewResId);
        final float defaultElevation = elevatedView.getElevation();
        final float chooserHeaderScrollElevation =
                getResources().getDimensionPixelSize(R.dimen.chooser_header_scroll_elevation);
        mChooserMultiProfilePagerAdapter.getActiveAdapterView().addOnScrollListener(
                new RecyclerView.OnScrollListener() {
                    public void onScrollStateChanged(RecyclerView view, int scrollState) {
                        if (scrollState == RecyclerView.SCROLL_STATE_IDLE) {
                            if (mScrollStatus == SCROLL_STATUS_SCROLLING_VERTICAL) {
                                mScrollStatus = SCROLL_STATUS_IDLE;
                                setHorizontalScrollingEnabled(true);
                            }
                        } else if (scrollState == RecyclerView.SCROLL_STATE_DRAGGING) {
                            if (mScrollStatus == SCROLL_STATUS_IDLE) {
                                mScrollStatus = SCROLL_STATUS_SCROLLING_VERTICAL;
                                setHorizontalScrollingEnabled(false);
                            }
                        }
                    }

                    public void onScrolled(RecyclerView view, int dx, int dy) {
                        if (view.getChildCount() > 0) {
                            View child = view.getLayoutManager().findViewByPosition(0);
                            if (child == null || child.getTop() < 0) {
                                elevatedView.setElevation(chooserHeaderScrollElevation);
                                return;
                            }
                        }

                        elevatedView.setElevation(defaultElevation);
                    }
                });
    }

    private void maybeSetupGlobalLayoutListener() {
        if (shouldShowTabs()) {
            return;
        }
        final View recyclerView = mChooserMultiProfilePagerAdapter.getActiveAdapterView();
        recyclerView.getViewTreeObserver()
                .addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        // Fixes an issue were the accessibility border disappears on list creation.
                        recyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        final TextView titleView = findViewById(com.android.internal.R.id.title);
                        if (titleView != null) {
                            titleView.setFocusable(true);
                            titleView.setFocusableInTouchMode(true);
                            titleView.requestFocus();
                            titleView.requestAccessibilityFocus();
                        }
                    }
                });
    }

    @Override // ChooserListCommunicator
    public boolean isSendAction(Intent targetIntent) {
        if (targetIntent == null) {
            return false;
        }

        String action = targetIntent.getAction();
        if (action == null) {
            return false;
        }

        if (Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            return true;
        }

        return false;
    }

    /**
     * The sticky content preview is shown only when we have a tabbed view. It's shown above
     * the tabs so it is not part of the scrollable list. If we are not in tabbed view,
     * we instead show the content preview as a regular list item.
     */
    private boolean shouldShowStickyContentPreview() {
        return shouldShowStickyContentPreviewNoOrientationCheck()
                && !getResources().getBoolean(R.bool.resolver_landscape_phone);
    }

    private boolean shouldShowStickyContentPreviewNoOrientationCheck() {
        return shouldShowTabs()
                && mMultiProfilePagerAdapter.getListAdapterForUserHandle(
                UserHandle.of(UserHandle.myUserId())).getCount() > 0
                && shouldShowContentPreview();
    }

    /**
     * @return true if we want to show the content preview area
     */
    protected boolean shouldShowContentPreview() {
        return isSendAction(getTargetIntent());
    }

    private void updateStickyContentPreview() {
        if (shouldShowStickyContentPreviewNoOrientationCheck()) {
            // The sticky content preview is only shown when we show the work and personal tabs.
            // We don't show it in landscape as otherwise there is no room for scrolling.
            // If the sticky content preview will be shown at some point with orientation change,
            // then always preload it to avoid subsequent resizing of the share sheet.
            ViewGroup contentPreviewContainer = findViewById(com.android.internal.R.id.content_preview_container);
            if (contentPreviewContainer.getChildCount() == 0) {
                ViewGroup contentPreviewView =
                        createContentPreviewView(contentPreviewContainer, mPreviewCoord);
                contentPreviewContainer.addView(contentPreviewView);
            }
        }
        if (shouldShowStickyContentPreview()) {
            showStickyContentPreview();
        } else {
            hideStickyContentPreview();
        }
    }

    private void showStickyContentPreview() {
        if (isStickyContentPreviewShowing()) {
            return;
        }
        ViewGroup contentPreviewContainer = findViewById(com.android.internal.R.id.content_preview_container);
        contentPreviewContainer.setVisibility(View.VISIBLE);
    }

    private boolean isStickyContentPreviewShowing() {
        ViewGroup contentPreviewContainer = findViewById(com.android.internal.R.id.content_preview_container);
        return contentPreviewContainer.getVisibility() == View.VISIBLE;
    }

    private void hideStickyContentPreview() {
        if (!isStickyContentPreviewShowing()) {
            return;
        }
        ViewGroup contentPreviewContainer = findViewById(com.android.internal.R.id.content_preview_container);
        contentPreviewContainer.setVisibility(View.GONE);
    }

    private void logActionShareWithPreview() {
        Intent targetIntent = getTargetIntent();
        int previewType = findPreferredContentPreview(targetIntent, getContentResolver());
        getMetricsLogger().write(new LogMaker(MetricsEvent.ACTION_SHARE_WITH_PREVIEW)
                .setSubtype(previewType));
    }

    private void startFinishAnimation() {
        View rootView = findRootView();
        if (rootView != null) {
            rootView.startAnimation(new FinishAnimation(this, rootView));
        }
    }

    private boolean maybeCancelFinishAnimation() {
        View rootView = findRootView();
        Animation animation = (rootView == null) ? null : rootView.getAnimation();
        if (animation instanceof FinishAnimation) {
            boolean hasEnded = animation.hasEnded();
            animation.cancel();
            rootView.clearAnimation();
            return !hasEnded;
        }
        return false;
    }

    private View findRootView() {
        if (mContentView == null) {
            mContentView = findViewById(android.R.id.content);
        }
        return mContentView;
    }

    abstract static class ViewHolderBase extends RecyclerView.ViewHolder {
        private int mViewType;

        ViewHolderBase(View itemView, int viewType) {
            super(itemView);
            this.mViewType = viewType;
        }

        int getViewType() {
            return mViewType;
        }
    }

    /**
     * Used to bind types of individual item including
     * {@link ChooserGridAdapter#VIEW_TYPE_NORMAL},
     * {@link ChooserGridAdapter#VIEW_TYPE_CONTENT_PREVIEW},
     * {@link ChooserGridAdapter#VIEW_TYPE_PROFILE},
     * and {@link ChooserGridAdapter#VIEW_TYPE_AZ_LABEL}.
     */
    final class ItemViewHolder extends ViewHolderBase {
        ResolverListAdapter.ViewHolder mWrappedViewHolder;
        int mListPosition = ChooserListAdapter.NO_POSITION;

        ItemViewHolder(View itemView, boolean isClickable, int viewType) {
            super(itemView, viewType);
            mWrappedViewHolder = new ResolverListAdapter.ViewHolder(itemView);
            if (isClickable) {
                itemView.setOnClickListener(v -> startSelected(mListPosition,
                        false/* always */, true/* filterd */));

                itemView.setOnLongClickListener(v -> {
                    final TargetInfo ti = mChooserMultiProfilePagerAdapter.getActiveListAdapter()
                            .targetInfoForPosition(mListPosition, /* filtered */ true);

                    // This should always be the case for ItemViewHolder, check for validity
                    if (ti.isDisplayResolveInfo()) {
                        showTargetDetails(ti);
                    }
                    return true;
                });
            }
        }
    }

    /**
     * Add a footer to the list, to support scrolling behavior below the navbar.
     */
    static final class FooterViewHolder extends ViewHolderBase {
        FooterViewHolder(View itemView, int viewType) {
            super(itemView, viewType);
        }
    }

    /**
     * Intentionally override the {@link ResolverActivity} implementation as we only need that
     * implementation for the intent resolver case.
     */
    @Override
    public void onButtonClick(View v) {}

    /**
     * Intentionally override the {@link ResolverActivity} implementation as we only need that
     * implementation for the intent resolver case.
     */
    @Override
    protected void resetButtonBar() {}

    @Override
    protected String getMetricsCategory() {
        return METRICS_CATEGORY_CHOOSER;
    }

    @Override
    protected void onProfileTabSelected() {
        ChooserGridAdapter currentRootAdapter =
                mChooserMultiProfilePagerAdapter.getCurrentRootAdapter();
        currentRootAdapter.updateDirectShareExpansion();
        // This fixes an edge case where after performing a variety of gestures, vertical scrolling
        // ends up disabled. That's because at some point the old tab's vertical scrolling is
        // disabled and the new tab's is enabled. For context, see b/159997845
        setVerticalScrollEnabled(true);
        if (mResolverDrawerLayout != null) {
            mResolverDrawerLayout.scrollNestedScrollableChildBackToTop();
        }
    }

    @Override
    protected WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
        if (shouldShowTabs()) {
            mChooserMultiProfilePagerAdapter
                    .setEmptyStateBottomOffset(insets.getSystemWindowInsetBottom());
            mChooserMultiProfilePagerAdapter.setupContainerPadding(
                    getActiveEmptyStateView().findViewById(com.android.internal.R.id.resolver_empty_state_container));
        }

        WindowInsets result = super.onApplyWindowInsets(v, insets);
        if (mResolverDrawerLayout != null) {
            mResolverDrawerLayout.requestLayout();
        }
        return result;
    }

    private void setHorizontalScrollingEnabled(boolean enabled) {
        ResolverViewPager viewPager = findViewById(com.android.internal.R.id.profile_pager);
        viewPager.setSwipingEnabled(enabled);
    }

    private void setVerticalScrollEnabled(boolean enabled) {
        ChooserGridLayoutManager layoutManager =
                (ChooserGridLayoutManager) mChooserMultiProfilePagerAdapter.getActiveAdapterView()
                        .getLayoutManager();
        layoutManager.setVerticalScrollEnabled(enabled);
    }

    @Override
    void onHorizontalSwipeStateChanged(int state) {
        if (state == ViewPager.SCROLL_STATE_DRAGGING) {
            if (mScrollStatus == SCROLL_STATUS_IDLE) {
                mScrollStatus = SCROLL_STATUS_SCROLLING_HORIZONTAL;
                setVerticalScrollEnabled(false);
            }
        } else if (state == ViewPager.SCROLL_STATE_IDLE) {
            if (mScrollStatus == SCROLL_STATUS_SCROLLING_HORIZONTAL) {
                mScrollStatus = SCROLL_STATUS_IDLE;
                setVerticalScrollEnabled(true);
            }
        }
    }

    /**
     * Adapter for all types of items and targets in ShareSheet.
     * Note that ranked sections like Direct Share - while appearing grid-like - are handled on the
     * row level by this adapter but not on the item level. Individual targets within the row are
     * handled by {@link ChooserListAdapter}
     */
    @VisibleForTesting
    public final class ChooserGridAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final ChooserListAdapter mChooserListAdapter;
        private final LayoutInflater mLayoutInflater;
        private final boolean mShowAzLabelIfPoss;

        private DirectShareViewHolder mDirectShareViewHolder;
        private int mChooserTargetWidth = 0;

        private int mFooterHeight = 0;

        private static final int VIEW_TYPE_DIRECT_SHARE = 0;
        private static final int VIEW_TYPE_NORMAL = 1;
        private static final int VIEW_TYPE_CONTENT_PREVIEW = 2;
        private static final int VIEW_TYPE_PROFILE = 3;
        private static final int VIEW_TYPE_AZ_LABEL = 4;
        private static final int VIEW_TYPE_CALLER_AND_RANK = 5;
        private static final int VIEW_TYPE_FOOTER = 6;

        private static final int NUM_EXPANSIONS_TO_HIDE_AZ_LABEL = 20;

        ChooserGridAdapter(ChooserListAdapter wrappedAdapter) {
            super();
            mChooserListAdapter = wrappedAdapter;
            mLayoutInflater = LayoutInflater.from(ChooserActivity.this);

            mShowAzLabelIfPoss = getNumSheetExpansions() < NUM_EXPANSIONS_TO_HIDE_AZ_LABEL;

            wrappedAdapter.registerDataSetObserver(new DataSetObserver() {
                @Override
                public void onChanged() {
                    super.onChanged();
                    notifyDataSetChanged();
                }

                @Override
                public void onInvalidated() {
                    super.onInvalidated();
                    notifyDataSetChanged();
                }
            });
        }

        public void setFooterHeight(int height) {
            mFooterHeight = height;
        }

        /**
         * Calculate the chooser target width to maximize space per item
         *
         * @param width The new row width to use for recalculation
         * @return true if the view width has changed
         */
        public boolean calculateChooserTargetWidth(int width) {
            if (width == 0) {
                return false;
            }

            // Limit width to the maximum width of the chooser activity
            int maxWidth = getResources().getDimensionPixelSize(R.dimen.chooser_width);
            width = Math.min(maxWidth, width);

            int newWidth = width / mMaxTargetsPerRow;
            if (newWidth != mChooserTargetWidth) {
                mChooserTargetWidth = newWidth;
                return true;
            }

            return false;
        }

        public int getRowCount() {
            return (int) (
                    getSystemRowCount()
                            + getProfileRowCount()
                            + getServiceTargetRowCount()
                            + getCallerAndRankedTargetRowCount()
                            + getAzLabelRowCount()
                            + Math.ceil(
                            (float) mChooserListAdapter.getAlphaTargetCount()
                                    / mMaxTargetsPerRow)
            );
        }

        /**
         * Whether the "system" row of targets is displayed.
         * This area includes the content preview (if present) and action row.
         */
        public int getSystemRowCount() {
            // For the tabbed case we show the sticky content preview above the tabs,
            // please refer to shouldShowStickyContentPreview
            if (shouldShowTabs()) {
                return 0;
            }

            if (!shouldShowContentPreview()) {
                return 0;
            }

            if (mChooserListAdapter == null || mChooserListAdapter.getCount() == 0) {
                return 0;
            }

            return 1;
        }

        public int getProfileRowCount() {
            if (shouldShowTabs()) {
                return 0;
            }
            return mChooserListAdapter.getOtherProfile() == null ? 0 : 1;
        }

        public int getFooterRowCount() {
            return 1;
        }

        public int getCallerAndRankedTargetRowCount() {
            return (int) Math.ceil(
                    ((float) mChooserListAdapter.getCallerTargetCount()
                            + mChooserListAdapter.getRankedTargetCount()) / mMaxTargetsPerRow);
        }

        // There can be at most one row in the listview, that is internally
        // a ViewGroup with 2 rows
        public int getServiceTargetRowCount() {
            if (shouldShowContentPreview()
                    && !ActivityManager.isLowRamDeviceStatic()) {
                return 1;
            }
            return 0;
        }

        public int getAzLabelRowCount() {
            // Only show a label if the a-z list is showing
            return (mShowAzLabelIfPoss && mChooserListAdapter.getAlphaTargetCount() > 0) ? 1 : 0;
        }

        @Override
        public int getItemCount() {
            return (int) (
                    getSystemRowCount()
                            + getProfileRowCount()
                            + getServiceTargetRowCount()
                            + getCallerAndRankedTargetRowCount()
                            + getAzLabelRowCount()
                            + mChooserListAdapter.getAlphaTargetCount()
                            + getFooterRowCount()
            );
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            switch (viewType) {
                case VIEW_TYPE_CONTENT_PREVIEW:
                    return new ItemViewHolder(
                            createContentPreviewView(parent, mPreviewCoord),
                            false,
                            viewType);
                case VIEW_TYPE_PROFILE:
                    return new ItemViewHolder(createProfileView(parent), false, viewType);
                case VIEW_TYPE_AZ_LABEL:
                    return new ItemViewHolder(createAzLabelView(parent), false, viewType);
                case VIEW_TYPE_NORMAL:
                    return new ItemViewHolder(
                            mChooserListAdapter.createView(parent), true, viewType);
                case VIEW_TYPE_DIRECT_SHARE:
                case VIEW_TYPE_CALLER_AND_RANK:
                    return createItemGroupViewHolder(viewType, parent);
                case VIEW_TYPE_FOOTER:
                    Space sp = new Space(parent.getContext());
                    sp.setLayoutParams(new RecyclerView.LayoutParams(
                            LayoutParams.MATCH_PARENT, mFooterHeight));
                    return new FooterViewHolder(sp, viewType);
                default:
                    // Since we catch all possible viewTypes above, no chance this is being called.
                    return null;
            }
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            int viewType = ((ViewHolderBase) holder).getViewType();
            switch (viewType) {
                case VIEW_TYPE_DIRECT_SHARE:
                case VIEW_TYPE_CALLER_AND_RANK:
                    bindItemGroupViewHolder(position, (ItemGroupViewHolder) holder);
                    break;
                case VIEW_TYPE_NORMAL:
                    bindItemViewHolder(position, (ItemViewHolder) holder);
                    break;
                default:
            }
        }

        @Override
        public int getItemViewType(int position) {
            int count;

            int countSum = (count = getSystemRowCount());
            if (count > 0 && position < countSum) return VIEW_TYPE_CONTENT_PREVIEW;

            countSum += (count = getProfileRowCount());
            if (count > 0 && position < countSum) return VIEW_TYPE_PROFILE;

            countSum += (count = getServiceTargetRowCount());
            if (count > 0 && position < countSum) return VIEW_TYPE_DIRECT_SHARE;

            countSum += (count = getCallerAndRankedTargetRowCount());
            if (count > 0 && position < countSum) return VIEW_TYPE_CALLER_AND_RANK;

            countSum += (count = getAzLabelRowCount());
            if (count > 0 && position < countSum) return VIEW_TYPE_AZ_LABEL;

            if (position == getItemCount() - 1) return VIEW_TYPE_FOOTER;

            return VIEW_TYPE_NORMAL;
        }

        public int getTargetType(int position) {
            return mChooserListAdapter.getPositionTargetType(getListPosition(position));
        }

        private View createProfileView(ViewGroup parent) {
            View profileRow = mLayoutInflater.inflate(R.layout.chooser_profile_row, parent, false);
            mProfileView = profileRow.findViewById(com.android.internal.R.id.profile_button);
            mProfileView.setOnClickListener(ChooserActivity.this::onProfileClick);
            updateProfileViewButton();
            return profileRow;
        }

        private View createAzLabelView(ViewGroup parent) {
            return mLayoutInflater.inflate(R.layout.chooser_az_label_row, parent, false);
        }

        private ItemGroupViewHolder loadViewsIntoGroup(ItemGroupViewHolder holder) {
            final int spec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            final int exactSpec = MeasureSpec.makeMeasureSpec(mChooserTargetWidth,
                    MeasureSpec.EXACTLY);
            int columnCount = holder.getColumnCount();

            final boolean isDirectShare = holder instanceof DirectShareViewHolder;

            for (int i = 0; i < columnCount; i++) {
                final View v = mChooserListAdapter.createView(holder.getRowByIndex(i));
                final int column = i;
                v.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startSelected(holder.getItemIndex(column), false, true);
                    }
                });

                // Show menu for both direct share and app share targets after long click.
                v.setOnLongClickListener(v1 -> {
                    TargetInfo ti = mChooserListAdapter.targetInfoForPosition(
                            holder.getItemIndex(column), true);
                    showTargetDetails(ti);
                    return true;
                });

                holder.addView(i, v);

                // Force Direct Share to be 2 lines and auto-wrap to second line via hoz scroll =
                // false. TextView#setHorizontallyScrolling must be reset after #setLines. Must be
                // done before measuring.
                if (isDirectShare) {
                    final ViewHolder vh = (ViewHolder) v.getTag();
                    vh.text.setLines(2);
                    vh.text.setHorizontallyScrolling(false);
                    vh.text2.setVisibility(View.GONE);
                }

                // Force height to be a given so we don't have visual disruption during scaling.
                v.measure(exactSpec, spec);
                setViewBounds(v, v.getMeasuredWidth(), v.getMeasuredHeight());
            }

            final ViewGroup viewGroup = holder.getViewGroup();

            // Pre-measure and fix height so we can scale later.
            holder.measure();
            setViewBounds(viewGroup, LayoutParams.MATCH_PARENT, holder.getMeasuredRowHeight());

            if (isDirectShare) {
                DirectShareViewHolder dsvh = (DirectShareViewHolder) holder;
                setViewBounds(dsvh.getRow(0), LayoutParams.MATCH_PARENT, dsvh.getMinRowHeight());
                setViewBounds(dsvh.getRow(1), LayoutParams.MATCH_PARENT, dsvh.getMinRowHeight());
            }

            viewGroup.setTag(holder);
            return holder;
        }

        private void setViewBounds(View view, int widthPx, int heightPx) {
            LayoutParams lp = view.getLayoutParams();
            if (lp == null) {
                lp = new LayoutParams(widthPx, heightPx);
                view.setLayoutParams(lp);
            } else {
                lp.height = heightPx;
                lp.width = widthPx;
            }
        }

        ItemGroupViewHolder createItemGroupViewHolder(int viewType, ViewGroup parent) {
            if (viewType == VIEW_TYPE_DIRECT_SHARE) {
                ViewGroup parentGroup = (ViewGroup) mLayoutInflater.inflate(
                        R.layout.chooser_row_direct_share, parent, false);
                ViewGroup row1 = (ViewGroup) mLayoutInflater.inflate(R.layout.chooser_row,
                        parentGroup, false);
                ViewGroup row2 = (ViewGroup) mLayoutInflater.inflate(R.layout.chooser_row,
                        parentGroup, false);
                parentGroup.addView(row1);
                parentGroup.addView(row2);

                mDirectShareViewHolder = new DirectShareViewHolder(parentGroup,
                        Lists.newArrayList(row1, row2), mMaxTargetsPerRow, viewType,
                        mChooserMultiProfilePagerAdapter::getActiveListAdapter);
                loadViewsIntoGroup(mDirectShareViewHolder);

                return mDirectShareViewHolder;
            } else {
                ViewGroup row = (ViewGroup) mLayoutInflater.inflate(R.layout.chooser_row, parent,
                        false);
                ItemGroupViewHolder holder =
                        new SingleRowViewHolder(row, mMaxTargetsPerRow, viewType);
                loadViewsIntoGroup(holder);

                return holder;
            }
        }

        /**
         * Need to merge CALLER + ranked STANDARD into a single row and prevent a separator from
         * showing on top of the AZ list if the AZ label is visible. All other types are placed into
         * their own row as determined by their target type, and dividers are added in the list to
         * separate each type.
         */
        int getRowType(int rowPosition) {
            // Merge caller and ranked standard into a single row
            int positionType = mChooserListAdapter.getPositionTargetType(rowPosition);
            if (positionType == ChooserListAdapter.TARGET_CALLER) {
                return ChooserListAdapter.TARGET_STANDARD;
            }

            // If an the A-Z label is shown, prevent a separator from appearing by making the A-Z
            // row type the same as the suggestion row type
            if (getAzLabelRowCount() > 0 && positionType == ChooserListAdapter.TARGET_STANDARD_AZ) {
                return ChooserListAdapter.TARGET_STANDARD;
            }

            return positionType;
        }

        void bindItemViewHolder(int position, ItemViewHolder holder) {
            View v = holder.itemView;
            int listPosition = getListPosition(position);
            holder.mListPosition = listPosition;
            mChooserListAdapter.bindView(listPosition, v);
        }

        void bindItemGroupViewHolder(int position, ItemGroupViewHolder holder) {
            final ViewGroup viewGroup = (ViewGroup) holder.itemView;
            int start = getListPosition(position);
            int startType = getRowType(start);

            int columnCount = holder.getColumnCount();
            int end = start + columnCount - 1;
            while (getRowType(end) != startType && end >= start) {
                end--;
            }

            if (end == start && mChooserListAdapter.getItem(start).isEmptyTargetInfo()) {
                final TextView textView = viewGroup.findViewById(com.android.internal.R.id.chooser_row_text_option);

                if (textView.getVisibility() != View.VISIBLE) {
                    textView.setAlpha(0.0f);
                    textView.setVisibility(View.VISIBLE);
                    textView.setText(R.string.chooser_no_direct_share_targets);

                    ValueAnimator fadeAnim = ObjectAnimator.ofFloat(textView, "alpha", 0.0f, 1.0f);
                    fadeAnim.setInterpolator(new DecelerateInterpolator(1.0f));

                    float translationInPx = getResources().getDimensionPixelSize(
                            R.dimen.chooser_row_text_option_translate);
                    textView.setTranslationY(translationInPx);
                    ValueAnimator translateAnim = ObjectAnimator.ofFloat(textView, "translationY",
                            0.0f);
                    translateAnim.setInterpolator(new DecelerateInterpolator(1.0f));

                    AnimatorSet animSet = new AnimatorSet();
                    animSet.setDuration(NO_DIRECT_SHARE_ANIM_IN_MILLIS);
                    animSet.setStartDelay(NO_DIRECT_SHARE_ANIM_IN_MILLIS);
                    animSet.playTogether(fadeAnim, translateAnim);
                    animSet.start();
                }
            }

            for (int i = 0; i < columnCount; i++) {
                final View v = holder.getView(i);

                if (start + i <= end) {
                    holder.setViewVisibility(i, View.VISIBLE);
                    holder.setItemIndex(i, start + i);
                    mChooserListAdapter.bindView(holder.getItemIndex(i), v);
                } else {
                    holder.setViewVisibility(i, View.INVISIBLE);
                }
            }
        }

        int getListPosition(int position) {
            position -= getSystemRowCount() + getProfileRowCount();

            final int serviceCount = mChooserListAdapter.getServiceTargetCount();
            final int serviceRows = (int) Math.ceil((float) serviceCount / getMaxRankedTargets());
            if (position < serviceRows) {
                return position * mMaxTargetsPerRow;
            }

            position -= serviceRows;

            final int callerAndRankedCount = mChooserListAdapter.getCallerTargetCount()
                                                 + mChooserListAdapter.getRankedTargetCount();
            final int callerAndRankedRows = getCallerAndRankedTargetRowCount();
            if (position < callerAndRankedRows) {
                return serviceCount + position * mMaxTargetsPerRow;
            }

            position -= getAzLabelRowCount() + callerAndRankedRows;

            return callerAndRankedCount + serviceCount + position;
        }

        public void handleScroll(View v, int y, int oldy) {
            boolean canExpandDirectShare = canExpandDirectShare();
            if (mDirectShareViewHolder != null && canExpandDirectShare) {
                mDirectShareViewHolder.handleScroll(
                        mChooserMultiProfilePagerAdapter.getActiveAdapterView(), y, oldy,
                        mMaxTargetsPerRow);
            }
        }

        /**
         * Only expand direct share area if there is a minimum number of targets.
         */
        private boolean canExpandDirectShare() {
            // Do not enable until we have confirmed more apps are using sharing shortcuts
            // Check git history for enablement logic
            return false;
        }

        public ChooserListAdapter getListAdapter() {
            return mChooserListAdapter;
        }

        boolean shouldCellSpan(int position) {
            return getItemViewType(position) == VIEW_TYPE_NORMAL;
        }

        void updateDirectShareExpansion() {
            if (mDirectShareViewHolder == null || !canExpandDirectShare()) {
                return;
            }
            RecyclerView activeAdapterView =
                    mChooserMultiProfilePagerAdapter.getActiveAdapterView();
            if (mResolverDrawerLayout.isCollapsed()) {
                mDirectShareViewHolder.collapse(activeAdapterView);
            } else {
                mDirectShareViewHolder.expand(activeAdapterView);
            }
        }
    }

    /**
     * Used to bind types for group of items including:
     * {@link ChooserGridAdapter#VIEW_TYPE_DIRECT_SHARE},
     * and {@link ChooserGridAdapter#VIEW_TYPE_CALLER_AND_RANK}.
     */
    abstract static class ItemGroupViewHolder extends ViewHolderBase {
        protected int mMeasuredRowHeight;
        private int[] mItemIndices;
        protected final View[] mCells;
        private final int mColumnCount;

        ItemGroupViewHolder(int cellCount, View itemView, int viewType) {
            super(itemView, viewType);
            this.mCells = new View[cellCount];
            this.mItemIndices = new int[cellCount];
            this.mColumnCount = cellCount;
        }

        abstract ViewGroup addView(int index, View v);

        abstract ViewGroup getViewGroup();

        abstract ViewGroup getRowByIndex(int index);

        abstract ViewGroup getRow(int rowNumber);

        abstract void setViewVisibility(int i, int visibility);

        public int getColumnCount() {
            return mColumnCount;
        }

        public void measure() {
            final int spec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            getViewGroup().measure(spec, spec);
            mMeasuredRowHeight = getViewGroup().getMeasuredHeight();
        }

        public int getMeasuredRowHeight() {
            return mMeasuredRowHeight;
        }

        public void setItemIndex(int itemIndex, int listIndex) {
            mItemIndices[itemIndex] = listIndex;
        }

        public int getItemIndex(int itemIndex) {
            return mItemIndices[itemIndex];
        }

        public View getView(int index) {
            return mCells[index];
        }
    }

    static class SingleRowViewHolder extends ItemGroupViewHolder {
        private final ViewGroup mRow;

        SingleRowViewHolder(ViewGroup row, int cellCount, int viewType) {
            super(cellCount, row, viewType);

            this.mRow = row;
        }

        public ViewGroup getViewGroup() {
            return mRow;
        }

        public ViewGroup getRowByIndex(int index) {
            return mRow;
        }

        public ViewGroup getRow(int rowNumber) {
            if (rowNumber == 0) return mRow;
            return null;
        }

        public ViewGroup addView(int index, View v) {
            mRow.addView(v);
            mCells[index] = v;

            return mRow;
        }

        public void setViewVisibility(int i, int visibility) {
            getView(i).setVisibility(visibility);
        }
    }

    static class DirectShareViewHolder extends ItemGroupViewHolder {
        private final ViewGroup mParent;
        private final List<ViewGroup> mRows;
        private int mCellCountPerRow;

        private boolean mHideDirectShareExpansion = false;
        private int mDirectShareMinHeight = 0;
        private int mDirectShareCurrHeight = 0;
        private int mDirectShareMaxHeight = 0;

        private final boolean[] mCellVisibility;

        private final Supplier<ChooserListAdapter> mListAdapterSupplier;

        DirectShareViewHolder(ViewGroup parent, List<ViewGroup> rows, int cellCountPerRow,
                int viewType, Supplier<ChooserListAdapter> listAdapterSupplier) {
            super(rows.size() * cellCountPerRow, parent, viewType);

            this.mParent = parent;
            this.mRows = rows;
            this.mCellCountPerRow = cellCountPerRow;
            this.mCellVisibility = new boolean[rows.size() * cellCountPerRow];
            Arrays.fill(mCellVisibility, true);
            this.mListAdapterSupplier = listAdapterSupplier;
        }

        public ViewGroup addView(int index, View v) {
            ViewGroup row = getRowByIndex(index);
            row.addView(v);
            mCells[index] = v;

            return row;
        }

        public ViewGroup getViewGroup() {
            return mParent;
        }

        public ViewGroup getRowByIndex(int index) {
            return mRows.get(index / mCellCountPerRow);
        }

        public ViewGroup getRow(int rowNumber) {
            return mRows.get(rowNumber);
        }

        public void measure() {
            final int spec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
            getRow(0).measure(spec, spec);
            getRow(1).measure(spec, spec);

            mDirectShareMinHeight = getRow(0).getMeasuredHeight();
            mDirectShareCurrHeight = mDirectShareCurrHeight > 0
                    ? mDirectShareCurrHeight : mDirectShareMinHeight;
            mDirectShareMaxHeight = 2 * mDirectShareMinHeight;
        }

        public int getMeasuredRowHeight() {
            return mDirectShareCurrHeight;
        }

        public int getMinRowHeight() {
            return mDirectShareMinHeight;
        }

        public void setViewVisibility(int i, int visibility) {
            final View v = getView(i);
            if (visibility == View.VISIBLE) {
                mCellVisibility[i] = true;
                v.setVisibility(visibility);
                v.setAlpha(1.0f);
            } else if (visibility == View.INVISIBLE && mCellVisibility[i]) {
                mCellVisibility[i] = false;

                ValueAnimator fadeAnim = ObjectAnimator.ofFloat(v, "alpha", 1.0f, 0f);
                fadeAnim.setDuration(NO_DIRECT_SHARE_ANIM_IN_MILLIS);
                fadeAnim.setInterpolator(new AccelerateInterpolator(1.0f));
                fadeAnim.addListener(new AnimatorListenerAdapter() {
                    public void onAnimationEnd(Animator animation) {
                        v.setVisibility(View.INVISIBLE);
                    }
                });
                fadeAnim.start();
            }
        }

        public void handleScroll(RecyclerView view, int y, int oldy, int maxTargetsPerRow) {
            // only exit early if fully collapsed, otherwise onListRebuilt() with shifting
            // targets can lock us into an expanded mode
            boolean notExpanded = mDirectShareCurrHeight == mDirectShareMinHeight;
            if (notExpanded) {
                if (mHideDirectShareExpansion) {
                    return;
                }

                // only expand if we have more than maxTargetsPerRow, and delay that decision
                // until they start to scroll
                ChooserListAdapter adapter = mListAdapterSupplier.get();
                int validTargets = adapter.getSelectableServiceTargetCount();
                if (validTargets <= maxTargetsPerRow) {
                    mHideDirectShareExpansion = true;
                    return;
                }
            }

            int yDiff = (int) ((oldy - y) * DIRECT_SHARE_EXPANSION_RATE);

            int prevHeight = mDirectShareCurrHeight;
            int newHeight = Math.min(prevHeight + yDiff, mDirectShareMaxHeight);
            newHeight = Math.max(newHeight, mDirectShareMinHeight);
            yDiff = newHeight - prevHeight;

            updateDirectShareRowHeight(view, yDiff, newHeight);
        }

        void expand(RecyclerView view) {
            updateDirectShareRowHeight(view, mDirectShareMaxHeight - mDirectShareCurrHeight,
                    mDirectShareMaxHeight);
        }

        void collapse(RecyclerView view) {
            updateDirectShareRowHeight(view, mDirectShareMinHeight - mDirectShareCurrHeight,
                    mDirectShareMinHeight);
        }

        private void updateDirectShareRowHeight(RecyclerView view, int yDiff, int newHeight) {
            if (view == null || view.getChildCount() == 0 || yDiff == 0) {
                return;
            }

            // locate the item to expand, and offset the rows below that one
            boolean foundExpansion = false;
            for (int i = 0; i < view.getChildCount(); i++) {
                View child = view.getChildAt(i);

                if (foundExpansion) {
                    child.offsetTopAndBottom(yDiff);
                } else {
                    if (child.getTag() != null && child.getTag() instanceof DirectShareViewHolder) {
                        int widthSpec = MeasureSpec.makeMeasureSpec(child.getWidth(),
                                MeasureSpec.EXACTLY);
                        int heightSpec = MeasureSpec.makeMeasureSpec(newHeight,
                                MeasureSpec.EXACTLY);
                        child.measure(widthSpec, heightSpec);
                        child.getLayoutParams().height = child.getMeasuredHeight();
                        child.layout(child.getLeft(), child.getTop(), child.getRight(),
                                child.getTop() + child.getMeasuredHeight());

                        foundExpansion = true;
                    }
                }
            }

            if (foundExpansion) {
                mDirectShareCurrHeight = newHeight;
            }
        }
    }

    static class ChooserTargetRankingInfo {
        public final List<AppTarget> scores;
        public final UserHandle userHandle;

        ChooserTargetRankingInfo(List<AppTarget> chooserTargetScores,
                UserHandle userHandle) {
            this.scores = chooserTargetScores;
            this.userHandle = userHandle;
        }
    }

    static class RefinementResultReceiver extends ResultReceiver {
        private ChooserActivity mChooserActivity;
        private TargetInfo mSelectedTarget;

        public RefinementResultReceiver(ChooserActivity host, TargetInfo target,
                Handler handler) {
            super(handler);
            mChooserActivity = host;
            mSelectedTarget = target;
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            if (mChooserActivity == null) {
                Log.e(TAG, "Destroyed RefinementResultReceiver received a result");
                return;
            }
            if (resultData == null) {
                Log.e(TAG, "RefinementResultReceiver received null resultData");
                return;
            }

            switch (resultCode) {
                case RESULT_CANCELED:
                    mChooserActivity.onRefinementCanceled();
                    break;
                case RESULT_OK:
                    Parcelable intentParcelable = resultData.getParcelable(Intent.EXTRA_INTENT);
                    if (intentParcelable instanceof Intent) {
                        mChooserActivity.onRefinementResult(mSelectedTarget,
                                (Intent) intentParcelable);
                    } else {
                        Log.e(TAG, "RefinementResultReceiver received RESULT_OK but no Intent"
                                + " in resultData with key Intent.EXTRA_INTENT");
                    }
                    break;
                default:
                    Log.w(TAG, "Unknown result code " + resultCode
                            + " sent to RefinementResultReceiver");
                    break;
            }
        }

        public void destroy() {
            mChooserActivity = null;
            mSelectedTarget = null;
        }
    }

    /**
     * A helper class to track app's readiness for the scene transition animation.
     * The app is ready when both the image is laid out and the drawer offset is calculated.
     */
    private class EnterTransitionAnimationDelegate implements View.OnLayoutChangeListener {
        private boolean mPreviewReady = false;
        private boolean mOffsetCalculated = false;

        void postponeTransition() {
            postponeEnterTransition();
        }

        void markImagePreviewReady() {
            if (!mPreviewReady) {
                mPreviewReady = true;
                maybeStartListenForLayout();
            }
        }

        void markOffsetCalculated() {
            if (!mOffsetCalculated) {
                mOffsetCalculated = true;
                maybeStartListenForLayout();
            }
        }

        private void maybeStartListenForLayout() {
            if (mPreviewReady && mOffsetCalculated && mResolverDrawerLayout != null) {
                if (mResolverDrawerLayout.isInLayout()) {
                    startPostponedEnterTransition();
                } else {
                    mResolverDrawerLayout.addOnLayoutChangeListener(this);
                    mResolverDrawerLayout.requestLayout();
                }
            }
        }

        @Override
        public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
                int oldTop, int oldRight, int oldBottom) {
            v.removeOnLayoutChangeListener(this);
            startPostponedEnterTransition();
        }
    }

    /**
     * Used in combination with the scene transition when launching the image editor
     */
    private static class FinishAnimation extends AlphaAnimation implements
            Animation.AnimationListener {
        @Nullable
        private Activity mActivity;
        @Nullable
        private View mRootView;
        private final float mFromAlpha;

        FinishAnimation(@NonNull Activity activity, @NonNull View rootView) {
            super(rootView.getAlpha(), 0.0f);
            mActivity = activity;
            mRootView = rootView;
            mFromAlpha = rootView.getAlpha();
            setInterpolator(new LinearInterpolator());
            long duration = activity.getWindow().getTransitionBackgroundFadeDuration();
            setDuration(duration);
            // The scene transition animation looks better when it's not overlapped with this
            // fade-out animation thus the delay.
            // It is most likely that the image editor will cause this activity to stop and this
            // animation will be cancelled in the background without running (i.e. we'll animate
            // only when this activity remains partially visible after the image editor launch).
            setStartOffset(duration);
            super.setAnimationListener(this);
        }

        @Override
        public void setAnimationListener(AnimationListener listener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void cancel() {
            if (mRootView != null) {
                mRootView.setAlpha(mFromAlpha);
            }
            cleanup();
            super.cancel();
        }

        @Override
        public void onAnimationStart(Animation animation) {
        }

        @Override
        public void onAnimationEnd(Animation animation) {
            Activity activity = mActivity;
            cleanup();
            if (activity != null) {
                activity.finish();
            }
        }

        @Override
        public void onAnimationRepeat(Animation animation) {
        }

        private void cleanup() {
            mActivity = null;
            mRootView = null;
        }
    }

    @Override
    protected void maybeLogProfileChange() {
        getChooserActivityLogger().logSharesheetProfileChanged();
    }

    private static class ProfileRecord {
        /** The {@link AppPredictor} for this profile, if any. */
        @Nullable
        public final AppPredictor appPredictor;
        /**
         * null if we should not load shortcuts.
         */
        @Nullable
        public final ShortcutLoader shortcutLoader;
        public long loadingStartTime;

        private ProfileRecord(
                @Nullable AppPredictor appPredictor,
                @Nullable ShortcutLoader shortcutLoader) {
            this.appPredictor = appPredictor;
            this.shortcutLoader = shortcutLoader;
        }

        public void destroy() {
            if (shortcutLoader != null) {
                shortcutLoader.destroy();
            }
            if (appPredictor != null) {
                appPredictor.destroy();
            }
        }
    }
}

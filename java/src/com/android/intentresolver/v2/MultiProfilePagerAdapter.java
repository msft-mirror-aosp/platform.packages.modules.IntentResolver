/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.intentresolver.v2;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.os.Trace;
import android.os.UserHandle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TabHost;
import android.widget.TextView;

import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.android.intentresolver.ResolverListAdapter;
import com.android.intentresolver.emptystate.EmptyState;
import com.android.intentresolver.emptystate.EmptyStateProvider;
import com.android.intentresolver.v2.emptystate.EmptyStateUiHelper;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableList;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Skeletal {@link PagerAdapter} implementation for a UI with per-profile tabs (as in Sharesheet).
 * <p>
 * TODO: attempt to further restrict visibility/improve encapsulation in the methods we expose.
 * <p>
 * TODO: deprecate and audit/fix usages of any methods that refer to the "active" or "inactive"
 * <p>
 * adapters; these were marked {@link VisibleForTesting} and their usage seems like an accident
 * waiting to happen since clients seem to make assumptions about which adapter will be "active" in
 * a particular context, and more explicit APIs would make sure those were valid.
 * <p>
 * TODO: consider renaming legacy methods (e.g. why do we know it's a "list", not just a "page"?)
 * <p>
 * TODO: this is part of an in-progress refactor to merge with `GenericMultiProfilePagerAdapter`.
 * As originally noted there, we've reduced explicit references to the `ResolverListAdapter` base
 * type and may be able to drop the type constraint.
 *
 * @param <PageViewT> the type of the widget that represents the contents of a page in this adapter
 * @param <SinglePageAdapterT> the type of a "root" adapter class to be instantiated and included in
 * the per-profile records.
 * @param <ListAdapterT> the concrete type of a {@link ResolverListAdapter} implementation to
 * control the contents of a given per-profile list. This is provided for convenience, since it must
 * be possible to get the list adapter from the page adapter via our
 * <code>mListAdapterExtractor</code>.
 */
class MultiProfilePagerAdapter<
        PageViewT extends ViewGroup,
        SinglePageAdapterT,
        ListAdapterT extends ResolverListAdapter> extends PagerAdapter {

    /**
     * Delegate to set up a given adapter and page view to be used together.
     * @param <PageViewT> (as in {@link MultiProfilePagerAdapter}).
     * @param <SinglePageAdapterT> (as in {@link MultiProfilePagerAdapter}).
     */
    public interface AdapterBinder<PageViewT, SinglePageAdapterT> {
        /**
         * The given {@code view} will be associated with the given {@code adapter}. Do any work
         * necessary to configure them compatibly, introduce them to each other, etc.
         */
        void bind(PageViewT view, SinglePageAdapterT adapter);
    }

    public static final int PROFILE_PERSONAL = 0;
    public static final int PROFILE_WORK = 1;

    @IntDef({PROFILE_PERSONAL, PROFILE_WORK})
    public @interface Profile {}

    private final Function<SinglePageAdapterT, ListAdapterT> mListAdapterExtractor;
    private final AdapterBinder<PageViewT, SinglePageAdapterT> mAdapterBinder;
    private final Supplier<ViewGroup> mPageViewInflater;

    private final ImmutableList<ProfileDescriptor<PageViewT, SinglePageAdapterT>> mItems;

    private final EmptyStateProvider mEmptyStateProvider;
    private final UserHandle mWorkProfileUserHandle;
    private final UserHandle mCloneProfileUserHandle;
    private final Supplier<Boolean> mWorkProfileQuietModeChecker;  // True when work is quiet.

    private final Set<Integer> mLoadedPages;
    private int mCurrentPage;
    private OnProfileSelectedListener mOnProfileSelectedListener;

    protected MultiProfilePagerAdapter(
            Function<SinglePageAdapterT, ListAdapterT> listAdapterExtractor,
            AdapterBinder<PageViewT, SinglePageAdapterT> adapterBinder,
            ImmutableList<SinglePageAdapterT> adapters,
            EmptyStateProvider emptyStateProvider,
            Supplier<Boolean> workProfileQuietModeChecker,
            @Profile int defaultProfile,
            UserHandle workProfileUserHandle,
            UserHandle cloneProfileUserHandle,
            Supplier<ViewGroup> pageViewInflater,
            Supplier<Optional<Integer>> containerBottomPaddingOverrideSupplier) {
        mCurrentPage = defaultProfile;
        mLoadedPages = new HashSet<>();
        mWorkProfileUserHandle = workProfileUserHandle;
        mCloneProfileUserHandle = cloneProfileUserHandle;
        mEmptyStateProvider = emptyStateProvider;
        mWorkProfileQuietModeChecker = workProfileQuietModeChecker;

        mListAdapterExtractor = listAdapterExtractor;
        mAdapterBinder = adapterBinder;
        mPageViewInflater = pageViewInflater;

        ImmutableList.Builder<ProfileDescriptor<PageViewT, SinglePageAdapterT>> items =
                new ImmutableList.Builder<>();
        // TODO: for now this only builds in personal and work tabs; any other provided `adapters`
        // are ignored. Historically this class wouldn't have behaved correctly for any more than
        // those two tabs, so this is more explicit about our current support. Upcoming changes will
        // generalize to support more tabs.
        for (SinglePageAdapterT pageAdapter : adapters) {
            ListAdapterT listAdapter = mListAdapterExtractor.apply(pageAdapter);
            if (listAdapter.getUserHandle().equals(workProfileUserHandle)) {
                items.add(
                        createProfileDescriptor(
                                PROFILE_WORK, pageAdapter, containerBottomPaddingOverrideSupplier));
            } else {
                // TODO: it shouldn't be possible to add multiple "personal" descriptors. For now
                // we're just trusting our clients to provide valid data. We should avoid making
                // inferences from the adapter's user handle, and instead have the pager-adapter
                // receive all the necessary configuration data (in some format that ensures
                // uniqueness of the adapters assigned to a given profile).
                items.add(
                        createProfileDescriptor(
                                PROFILE_PERSONAL,
                                pageAdapter,
                                containerBottomPaddingOverrideSupplier));
            }
        }
        mItems = items.build();
    }

    private ProfileDescriptor<PageViewT, SinglePageAdapterT> createProfileDescriptor(
            @Profile int profile,
            SinglePageAdapterT adapter,
            Supplier<Optional<Integer>> containerBottomPaddingOverrideSupplier) {
        return new ProfileDescriptor<>(
                profile, mPageViewInflater.get(), adapter, containerBottomPaddingOverrideSupplier);
    }

    private @Profile int getProfileForPageNumber(int position) {
        if (hasAdapterForIndex(position)) {
            return mItems.get(position).mProfile;
        }
        return -1;
    }

    public int getPageNumberForProfile(@Profile int profile) {
        for (int i = 0; i < mItems.size(); ++i) {
            if (profile == mItems.get(i).mProfile) {
                return i;
            }
        }
        return -1;
    }

    private void updateActiveTabStyle(TabHost tabHost) {
        int currentTab = tabHost.getCurrentTab();

        for (int pageNumber = 0; pageNumber < getItemCount(); ++pageNumber) {
            // TODO: can we avoid this downcast by pushing our knowledge of the intended view type
            // somewhere else?
            TextView tabText = (TextView) tabHost.getTabWidget().getChildAt(pageNumber);
            tabText.setSelected(currentTab == pageNumber);
        }
    }

    public void setupProfileTabs(
            LayoutInflater layoutInflater,
            TabHost tabHost,
            ViewPager viewPager,
            int tabButtonLayoutResId,
            int tabPageContentViewId,
            String personalTabLabel,
            String personalTabAccessibilityLabel,
            String personalTabTag,
            String workTabLabel,
            String workTabAccessibilityLabel,
            String workTabTag,
            Runnable onTabChangeListener,
            MultiProfilePagerAdapter.OnProfileSelectedListener clientOnProfileSelectedListener) {
        tabHost.setup();
        viewPager.setSaveEnabled(false);

        Button personalButton = (Button) layoutInflater.inflate(
                tabButtonLayoutResId, tabHost.getTabWidget(), false);
        personalButton.setText(personalTabLabel);
        personalButton.setContentDescription(personalTabAccessibilityLabel);

        TabHost.TabSpec personalTabSpec = tabHost.newTabSpec(personalTabTag)
                .setContent(tabPageContentViewId)
                .setIndicator(personalButton);
        tabHost.addTab(personalTabSpec);

        Button workButton = (Button) layoutInflater.inflate(
                tabButtonLayoutResId, tabHost.getTabWidget(), false);
        workButton.setText(workTabLabel);
        workButton.setContentDescription(workTabAccessibilityLabel);

        TabHost.TabSpec workTabSpec = tabHost.newTabSpec(workTabTag)
                .setContent(tabPageContentViewId)
                .setIndicator(workButton);
        tabHost.addTab(workTabSpec);

        tabHost.getTabWidget().setVisibility(View.VISIBLE);

        updateActiveTabStyle(tabHost);

        tabHost.setOnTabChangedListener(tabId -> {
            updateActiveTabStyle(tabHost);
            // TODO: update for 3+ tabs.
            if (personalTabTag.equals(tabId)) {
                viewPager.setCurrentItem(0);
            } else {
                viewPager.setCurrentItem(1);
            }
            onTabChangeListener.run();
        });

        viewPager.setVisibility(View.VISIBLE);
        tabHost.setCurrentTab(getCurrentPage());
        mOnProfileSelectedListener =
                new MultiProfilePagerAdapter.OnProfileSelectedListener() {
                    @Override
                    public void onProfilePageSelected(@Profile int profileId, int pageNumber) {
                        tabHost.setCurrentTab(pageNumber);
                        clientOnProfileSelectedListener.onProfilePageSelected(
                                profileId, pageNumber);
                    }

                    @Override
                    public void onProfilePageStateChanged(int state) {
                        clientOnProfileSelectedListener.onProfilePageStateChanged(state);
                    }
                };
    }

    /**
     * Sets this instance of this class as {@link ViewPager}'s {@link PagerAdapter} and sets
     * an {@link ViewPager.OnPageChangeListener} where it keeps track of the currently displayed
     * page and rebuilds the list.
     */
    public void setupViewPager(ViewPager viewPager) {
        viewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                mCurrentPage = position;
                if (!mLoadedPages.contains(position)) {
                    rebuildActiveTab(true);
                    mLoadedPages.add(position);
                }
                if (mOnProfileSelectedListener != null) {
                    mOnProfileSelectedListener.onProfilePageSelected(
                            getProfileForPageNumber(position), position);
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                if (mOnProfileSelectedListener != null) {
                    mOnProfileSelectedListener.onProfilePageStateChanged(state);
                }
            }
        });
        viewPager.setAdapter(this);
        viewPager.setCurrentItem(mCurrentPage);
        mLoadedPages.add(mCurrentPage);
    }

    public void clearInactiveProfileCache() {
        forEachInactivePage(pageNumber -> mLoadedPages.remove(pageNumber));
    }

    @Override
    public final ViewGroup instantiateItem(ViewGroup container, int position) {
        setupListAdapter(position);
        final ProfileDescriptor<PageViewT, SinglePageAdapterT> descriptor = getItem(position);
        container.addView(descriptor.mRootView);
        return descriptor.mRootView;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object view) {
        container.removeView((View) view);
    }

    @Override
    public int getCount() {
        return getItemCount();
    }

    public int getCurrentPage() {
        return mCurrentPage;
    }

    public final @Profile int getActiveProfile() {
        return getProfileForPageNumber(getCurrentPage());
    }

    @VisibleForTesting
    public UserHandle getCurrentUserHandle() {
        return getActiveListAdapter().getUserHandle();
    }

    @Override
    public boolean isViewFromObject(View view, Object object) {
        return view == object;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return null;
    }

    public UserHandle getCloneUserHandle() {
        return mCloneProfileUserHandle;
    }

    /**
     * Returns the {@link ProfileDescriptor} relevant to the given <code>pageIndex</code>.
     * <ul>
     * <li>For a device with only one user, <code>pageIndex</code> value of
     * <code>0</code> would return the personal profile {@link ProfileDescriptor}.</li>
     * <li>For a device with a work profile, <code>pageIndex</code> value of <code>0</code> would
     * return the personal profile {@link ProfileDescriptor}, and <code>pageIndex</code> value of
     * <code>1</code> would return the work profile {@link ProfileDescriptor}.</li>
     * </ul>
     */
    private ProfileDescriptor<PageViewT, SinglePageAdapterT> getItem(int pageIndex) {
        return mItems.get(pageIndex);
    }

    private ViewGroup getEmptyStateView(int pageIndex) {
        return getItem(pageIndex).getEmptyStateView();
    }

    public ViewGroup getActiveEmptyStateView() {
        return getEmptyStateView(getCurrentPage());
    }

    /**
     * Returns the number of {@link ProfileDescriptor} objects.
     * <p>For a normal consumer device with only one user returns <code>1</code>.
     * <p>For a device with a work profile returns <code>2</code>.
     */
    public final int getItemCount() {
        return mItems.size();
    }

    public final PageViewT getListViewForIndex(int index) {
        return getItem(index).mView;
    }

    /**
     * Returns the adapter of the list view for the relevant page specified by
     * <code>pageIndex</code>.
     * <p>This method is meant to be implemented with an implementation-specific return type
     * depending on the adapter type.
     */
    @VisibleForTesting
    public final SinglePageAdapterT getAdapterForIndex(int index) {
        return getItem(index).mAdapter;
    }

    /**
     * Performs view-related initialization procedures for the adapter specified
     * by <code>pageIndex</code>.
     */
    public final void setupListAdapter(int pageIndex) {
        mAdapterBinder.bind(getListViewForIndex(pageIndex), getAdapterForIndex(pageIndex));
    }

    /**
     * Returns the {@link ListAdapterT} instance of the profile that represents
     * <code>userHandle</code>. If there is no such adapter for the specified
     * <code>userHandle</code>, returns {@code null}.
     * <p>For example, if there is a work profile on the device with user id 10, calling this method
     * with <code>UserHandle.of(10)</code> returns the work profile {@link ListAdapterT}.
     */
    @Nullable
    public final ListAdapterT getListAdapterForUserHandle(UserHandle userHandle) {
        if (getPersonalListAdapter().getUserHandle().equals(userHandle)
                || userHandle.equals(getCloneUserHandle())) {
            return getPersonalListAdapter();
        } else if ((getWorkListAdapter() != null)
                && getWorkListAdapter().getUserHandle().equals(userHandle)) {
            return getWorkListAdapter();
        }
        return null;
    }

    private ListAdapterT getListAdapterForPageNumber(int pageNumber) {
        return mListAdapterExtractor.apply(getAdapterForIndex(pageNumber));
    }

    /**
     * Returns the {@link ListAdapterT} instance of the profile that is currently visible
     * to the user.
     * <p>For example, if the user is viewing the work tab in the share sheet, this method returns
     * the work profile {@link ListAdapterT}.
     */
    @VisibleForTesting
    public final ListAdapterT getActiveListAdapter() {
        return getListAdapterForPageNumber(getCurrentPage());
    }

    public final ListAdapterT getPersonalListAdapter() {
        return getListAdapterForPageNumber(getPageNumberForProfile(PROFILE_PERSONAL));
    }

    /** @return whether our tab data contains a page for the specified {@code profile} ID. */
    public final boolean hasPageForProfile(@Profile int profile) {
        return hasAdapterForIndex(getPageNumberForProfile(profile));
    }

    @Nullable
    public final ListAdapterT getWorkListAdapter() {
        if (!hasPageForProfile(PROFILE_WORK)) {
            return null;
        }
        return getListAdapterForPageNumber(getPageNumberForProfile(PROFILE_WORK));
    }

    public final SinglePageAdapterT getCurrentRootAdapter() {
        return getAdapterForIndex(getCurrentPage());
    }

    public final PageViewT getActiveAdapterView() {
        return getListViewForIndex(getCurrentPage());
    }

    private boolean anyAdapterHasItems() {
        for (int i = 0; i < mItems.size(); ++i) {
            ListAdapterT listAdapter = mListAdapterExtractor.apply(getAdapterForIndex(i));
            if (listAdapter.getCount() > 0) {
                return true;
            }
        }
        return false;
    }

    public void refreshPackagesInAllTabs() {
        // TODO: it's unclear if this legacy logic really requires the active tab to be rebuilt
        // first, or if we could just iterate over the tabs in arbitrary order.
        getActiveListAdapter().handlePackagesChanged();
        forEachInactivePage(page -> getListAdapterForPageNumber(page).handlePackagesChanged());
    }

    /**
     * Notify that there has been a package change which could potentially modify the set of targets
     * that should be shown in the specified {@code listAdapter}. This <em>may</em> result in
     * "rebuilding" the target list for that adapter.
     *
     * @param listAdapter an adapter that may need to be updated after the package-change event.
     * @param waitingToEnableWorkProfile whether we've turned on the work profile, but haven't yet
     * seen an {@code ACTION_USER_UNLOCKED} broadcast. In this case we skip the rebuild of any
     * work-profile adapter because we wouldn't expect meaningful results -- but another rebuild
     * will be prompted when we eventually get the broadcast.
     *
     * @return whether we're able to proceed with a Sharesheet session after processing this
     * package-change event. If false, we were able to rebuild the targets but determined that there
     * aren't any we could present in the UI without the app looking broken, so we should just quit.
     */
    public boolean onHandlePackagesChanged(
            ListAdapterT listAdapter, boolean waitingToEnableWorkProfile) {
        if (listAdapter == getActiveListAdapter()) {
            if (listAdapter.getUserHandle().equals(mWorkProfileUserHandle)
                    && waitingToEnableWorkProfile) {
                // We have just turned on the work profile and entered the passcode to start it,
                // now we are waiting to receive the ACTION_USER_UNLOCKED broadcast. There is no
                // point in reloading the list now, since the work profile user is still turning on.
                return true;
            }

            boolean listRebuilt = rebuildActiveTab(true);
            if (listRebuilt) {
                listAdapter.notifyDataSetChanged();
            }

            // TODO: shouldn't we check that the inactive tabs are built before declaring that we
            // have to quit for lack of items?
            return anyAdapterHasItems();
        } else {
            clearInactiveProfileCache();
            return true;
        }
    }

    /**
     * Fully-rebuild the active tab and, if specified, partially-rebuild any other inactive tabs.
     */
    public boolean rebuildTabs(boolean includePartialRebuildOfInactiveTabs) {
        // TODO: we may be able to determine `includePartialRebuildOfInactiveTabs` ourselves as
        // a function of our own instance state. OTOH the purpose of this "partial rebuild" is to
        // be able to evaluate the intermediate state of one particular profile tab (i.e. work
        // profile) that may not generalize well when we have other "inactive tabs." I.e., either we
        // rebuild *all* the inactive tabs just to evaluate some auto-launch conditions that only
        // depend on personal and/or work tabs, or we have to explicitly specify the ones we care
        // about. It's not the pager-adapter's business to know "which ones we care about," so maybe
        // they should be rebuilt lazily when-and-if it comes up (e.g. during the evaluation of
        // autolaunch conditions).
        boolean rebuildCompleted = rebuildActiveTab(true) || getActiveListAdapter().isTabLoaded();
        if (includePartialRebuildOfInactiveTabs) {
            // Per legacy logic, avoid short-circuiting (TODO: why? possibly so that we *start*
            // loading the inactive tabs even if we're still waiting on the active tab to finish?).
            boolean completedRebuildingInactiveTabs = rebuildInactiveTabs(false);
            rebuildCompleted = rebuildCompleted && completedRebuildingInactiveTabs;
        }
        return rebuildCompleted;
    }

    /**
     * Rebuilds the tab that is currently visible to the user.
     * <p>Returns {@code true} if rebuild has completed.
     */
    public final boolean rebuildActiveTab(boolean doPostProcessing) {
        Trace.beginSection("MultiProfilePagerAdapter#rebuildActiveTab");
        boolean result = rebuildTab(getActiveListAdapter(), doPostProcessing);
        Trace.endSection();
        return result;
    }

    /**
     * Rebuilds any tabs that are not currently visible to the user.
     * <p>Returns {@code true} if rebuild has completed in all inactive tabs.
     */
    private boolean rebuildInactiveTabs(boolean doPostProcessing) {
        Trace.beginSection("MultiProfilePagerAdapter#rebuildInactiveTab");
        AtomicBoolean allRebuildsComplete = new AtomicBoolean(true);
        forEachInactivePage(pageNumber -> {
            // Evaluate the rebuild for every inactive page, even if we've already seen some adapter
            // return an "incomplete" status (i.e., even if `allRebuildsComplete` is already false)
            // and so we already know we'll end up returning false for the batch.
            // TODO: any particular reason the per-page legacy logic was set up in this order, or
            // could we possibly short-circuit the rebuild if the tab is already "loaded"?
            ListAdapterT inactiveAdapter = getListAdapterForPageNumber(pageNumber);
            boolean rebuildInactivePageCompleted =
                    rebuildTab(inactiveAdapter, doPostProcessing) || inactiveAdapter.isTabLoaded();
            if (!rebuildInactivePageCompleted) {
                allRebuildsComplete.set(false);
            }
        });
        Trace.endSection();
        return allRebuildsComplete.get();
    }

    private int userHandleToPageIndex(UserHandle userHandle) {
        if (userHandle.equals(getPersonalListAdapter().getUserHandle())) {
            return getPageNumberForProfile(PROFILE_PERSONAL);
        } else {
            return getPageNumberForProfile(PROFILE_WORK);
        }
    }

    protected void forEachPage(Consumer<Integer> pageNumberHandler) {
        for (int pageNumber = 0; pageNumber < getItemCount(); ++pageNumber) {
            pageNumberHandler.accept(pageNumber);
        }
    }

    protected void forEachInactivePage(Consumer<Integer> inactivePageNumberHandler) {
        forEachPage(pageNumber -> {
            if (pageNumber != getCurrentPage()) {
                inactivePageNumberHandler.accept(pageNumber);
            }
        });
    }

    protected boolean rebuildTab(ListAdapterT activeListAdapter, boolean doPostProcessing) {
        if (shouldSkipRebuild(activeListAdapter)) {
            activeListAdapter.postListReadyRunnable(doPostProcessing, /* rebuildCompleted */ true);
            return false;
        }
        return activeListAdapter.rebuildList(doPostProcessing);
    }

    private boolean shouldSkipRebuild(ListAdapterT activeListAdapter) {
        EmptyState emptyState = mEmptyStateProvider.getEmptyState(activeListAdapter);
        return emptyState != null && emptyState.shouldSkipDataRebuild();
    }

    private boolean hasAdapterForIndex(int pageIndex) {
        return (pageIndex >= 0) && (pageIndex < getCount());
    }

    /**
     * The empty state screens are shown according to their priority:
     * <ol>
     * <li>(highest priority) cross-profile disabled by policy (handled in
     * {@link #rebuildTab(ListAdapterT, boolean)})</li>
     * <li>no apps available</li>
     * <li>(least priority) work is off</li>
     * </ol>
     *
     * The intention is to prevent the user from having to turn
     * the work profile on if there will not be any apps resolved
     * anyway.
     *
     * TODO: move this comment to the place where we configure our composite provider.
     */
    public void showEmptyResolverListEmptyState(ListAdapterT listAdapter) {
        final EmptyState emptyState = mEmptyStateProvider.getEmptyState(listAdapter);

        if (emptyState == null) {
            return;
        }

        emptyState.onEmptyStateShown();

        View.OnClickListener clickListener = null;

        if (emptyState.getButtonClickListener() != null) {
            clickListener = v -> emptyState.getButtonClickListener().onClick(() -> {
                ProfileDescriptor<PageViewT, SinglePageAdapterT> descriptor = getItem(
                        userHandleToPageIndex(listAdapter.getUserHandle()));
                descriptor.mEmptyStateUi.showSpinner();
            });
        }

        showEmptyState(listAdapter, emptyState, clickListener);
    }

    /**
     * Class to get user id of the current process
     */
    public static class MyUserIdProvider {
        /**
         * @return user id of the current process
         */
        public int getMyUserId() {
            return UserHandle.myUserId();
        }
    }

    private void showEmptyState(
            ListAdapterT activeListAdapter,
            EmptyState emptyState,
            View.OnClickListener buttonOnClick) {
        ProfileDescriptor<PageViewT, SinglePageAdapterT> descriptor = getItem(
                userHandleToPageIndex(activeListAdapter.getUserHandle()));
        descriptor.mEmptyStateUi.showEmptyState(emptyState, buttonOnClick);
        activeListAdapter.markTabLoaded();
    }

    /**
     * Sets up the padding of the view containing the empty state screens for the current adapter
     * view.
     */
    protected final void setupContainerPadding() {
        getItem(getCurrentPage()).setupContainerPadding();
    }

    public void showListView(ListAdapterT activeListAdapter) {
        ProfileDescriptor<PageViewT, SinglePageAdapterT> descriptor = getItem(
                userHandleToPageIndex(activeListAdapter.getUserHandle()));
        descriptor.mEmptyStateUi.hide();
    }

    /**
     * @return whether any "inactive" tab's adapter would show an empty-state screen in our current
     * application state.
     */
    public final boolean shouldShowEmptyStateScreenInAnyInactiveAdapter() {
        AtomicBoolean anyEmpty = new AtomicBoolean(false);
        // TODO: The "inactive" condition is legacy logic. Could we simplify and ask "any"?
        forEachInactivePage(pageNumber -> {
            if (shouldShowEmptyStateScreen(getListAdapterForPageNumber(pageNumber))) {
                anyEmpty.set(true);
            }
        });
        return anyEmpty.get();
    }

    public boolean shouldShowEmptyStateScreen(ListAdapterT listAdapter) {
        int count = listAdapter.getUnfilteredCount();
        return (count == 0 && listAdapter.getPlaceholderCount() == 0)
                || (listAdapter.getUserHandle().equals(mWorkProfileUserHandle)
                    && mWorkProfileQuietModeChecker.get());
    }

    // TODO: `ChooserActivity` also has a per-profile record type. Maybe the "multi-profile pager"
    // should be the owner of all per-profile data (especially now that the API is generic)?
    private static class ProfileDescriptor<PageViewT, SinglePageAdapterT> {
        final @Profile int mProfile;

        final ViewGroup mRootView;
        final EmptyStateUiHelper mEmptyStateUi;

        // TODO: post-refactoring, we may not need to retain these ivars directly (since they may
        // be encapsulated within the `EmptyStateUiHelper`?).
        private final ViewGroup mEmptyStateView;

        private final SinglePageAdapterT mAdapter;
        private final PageViewT mView;

        ProfileDescriptor(
                @Profile int forProfile,
                ViewGroup rootView,
                SinglePageAdapterT adapter,
                Supplier<Optional<Integer>> containerBottomPaddingOverrideSupplier) {
            mProfile = forProfile;
            mRootView = rootView;
            mAdapter = adapter;
            mEmptyStateView = rootView.findViewById(com.android.internal.R.id.resolver_empty_state);
            mView = (PageViewT) rootView.findViewById(com.android.internal.R.id.resolver_list);
            mEmptyStateUi = new EmptyStateUiHelper(
                    rootView,
                    com.android.internal.R.id.resolver_list,
                    containerBottomPaddingOverrideSupplier);
        }

        protected ViewGroup getEmptyStateView() {
            return mEmptyStateView;
        }

        private void setupContainerPadding() {
            mEmptyStateUi.setupContainerPadding();
        }
    }

    /** Listener interface for changes between the per-profile UI tabs. */
    public interface OnProfileSelectedListener {
        /**
         * Callback for when the user changes the active tab.
         * <p>This callback is only called when the intent resolver or share sheet shows
         * more than one profile.
         * @param profileId the ID of the newly-selected profile, e.g. {@link #PROFILE_PERSONAL}
         * if the personal profile tab was selected or {@link #PROFILE_WORK} if the work profile tab
         * was selected.
         */
        void onProfilePageSelected(@Profile int profileId, int pageNumber);


        /**
         * Callback for when the scroll state changes. Useful for discovering when the user begins
         * dragging, when the pager is automatically settling to the current page, or when it is
         * fully stopped/idle.
         * @param state {@link ViewPager#SCROLL_STATE_IDLE}, {@link ViewPager#SCROLL_STATE_DRAGGING}
         *              or {@link ViewPager#SCROLL_STATE_SETTLING}
         * @see ViewPager.OnPageChangeListener#onPageScrollStateChanged
         */
        void onProfilePageStateChanged(int state);
    }

    /**
     * Listener for when the user switches on the work profile from the work tab.
     */
    public interface OnSwitchOnWorkSelectedListener {
        /**
         * Callback for when the user switches on the work profile from the work tab.
         */
        void onSwitchOnWorkSelected();
    }
}
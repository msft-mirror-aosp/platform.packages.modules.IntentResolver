/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.intentresolver.profiles;

import android.content.Context;
import android.os.UserHandle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.PagerAdapter;

import com.android.intentresolver.ChooserListAdapter;
import com.android.intentresolver.ChooserRecyclerViewAccessibilityDelegate;
import com.android.intentresolver.R;
import com.android.intentresolver.emptystate.EmptyStateProvider;
import com.android.intentresolver.grid.ChooserGridAdapter;
import com.android.intentresolver.measurements.Tracer;

import com.google.common.collect.ImmutableList;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * A {@link PagerAdapter} which describes the work and personal profile share sheet screens.
 */
public class ChooserMultiProfilePagerAdapter extends MultiProfilePagerAdapter<
        RecyclerView, ChooserGridAdapter, ChooserListAdapter> {
    private static final int SINGLE_CELL_SPAN_SIZE = 1;

    private final ChooserProfileAdapterBinder mAdapterBinder;
    private final BottomPaddingOverrideSupplier mBottomPaddingOverrideSupplier;

    public ChooserMultiProfilePagerAdapter(
            Context context,
            ImmutableList<TabConfig<ChooserGridAdapter>> tabs,
            EmptyStateProvider emptyStateProvider,
            Supplier<Boolean> workProfileQuietModeChecker,
            @ProfileType int defaultProfile,
            UserHandle workProfileUserHandle,
            UserHandle cloneProfileUserHandle,
            int maxTargetsPerRow) {
        this(
                context,
                new ChooserProfileAdapterBinder(maxTargetsPerRow),
                tabs,
                emptyStateProvider,
                workProfileQuietModeChecker,
                defaultProfile,
                workProfileUserHandle,
                cloneProfileUserHandle,
                new BottomPaddingOverrideSupplier(context));
    }

    private ChooserMultiProfilePagerAdapter(
            Context context,
            ChooserProfileAdapterBinder adapterBinder,
            ImmutableList<TabConfig<ChooserGridAdapter>> tabs,
            EmptyStateProvider emptyStateProvider,
            Supplier<Boolean> workProfileQuietModeChecker,
            @ProfileType int defaultProfile,
            UserHandle workProfileUserHandle,
            UserHandle cloneProfileUserHandle,
            BottomPaddingOverrideSupplier bottomPaddingOverrideSupplier) {
        super(
                gridAdapter -> gridAdapter.getListAdapter(),
                adapterBinder,
                tabs,
                emptyStateProvider,
                workProfileQuietModeChecker,
                defaultProfile,
                workProfileUserHandle,
                cloneProfileUserHandle,
                () -> makeProfileView(context),
                bottomPaddingOverrideSupplier);
        mAdapterBinder = adapterBinder;
        mBottomPaddingOverrideSupplier = bottomPaddingOverrideSupplier;
    }

    public void setMaxTargetsPerRow(int maxTargetsPerRow) {
        mAdapterBinder.setMaxTargetsPerRow(maxTargetsPerRow);
    }

    public void setEmptyStateBottomOffset(int bottomOffset) {
        mBottomPaddingOverrideSupplier.setEmptyStateBottomOffset(bottomOffset);
        setupContainerPadding();
    }

    /**
     * Notify adapter about the drawer's collapse state. This will affect the app divider's
     * visibility.
     */
    public void setIsCollapsed(boolean isCollapsed) {
        for (int i = 0, size = getItemCount(); i < size; i++) {
            getPageAdapterForIndex(i).setAzLabelVisibility(!isCollapsed);
        }
    }

    /**
     * Set enabled status for all targets in all profiles.
     */
    public void setTargetsEnabled(boolean isEnabled) {
        for (int i = 0, size = getItemCount(); i < size; i++) {
            getPageAdapterForIndex(i).getListAdapter().setTargetsEnabled(isEnabled);
        }
    }

    private static ViewGroup makeProfileView(Context context) {
        LayoutInflater inflater = LayoutInflater.from(context);
        ViewGroup rootView =
                (ViewGroup) inflater.inflate(R.layout.chooser_list_per_profile_wrap, null, false);
        RecyclerView recyclerView = rootView.findViewById(com.android.internal.R.id.resolver_list);
        recyclerView.setAccessibilityDelegateCompat(
                new ChooserRecyclerViewAccessibilityDelegate(recyclerView));
        return rootView;
    }

    @Override
    public boolean onHandlePackagesChanged(
            ChooserListAdapter listAdapter, boolean waitingToEnableWorkProfile) {
        // TODO: why do we need to do the extra `notifyDataSetChanged()` in (only) the Chooser case?
        getActiveListAdapter().notifyDataSetChanged();
        return super.onHandlePackagesChanged(listAdapter, waitingToEnableWorkProfile);
    }

    @Override
    protected final boolean rebuildTab(ChooserListAdapter listAdapter, boolean doPostProcessing) {
        if (doPostProcessing) {
            Tracer.INSTANCE.beginAppTargetLoadingSection(listAdapter.getUserHandle());
        }
        return super.rebuildTab(listAdapter, doPostProcessing);
    }

    /** Apply the specified {@code height} as the footer in each tab's adapter. */
    public void setFooterHeightInEveryAdapter(int height) {
        for (int i = 0; i < getItemCount(); ++i) {
            getPageAdapterForIndex(i).setFooterHeight(height);
        }
    }

    /** Cleanup system resources */
    public void destroy() {
        for (int i = 0, count = getItemCount(); i < count; i++) {
            ChooserGridAdapter adapter = getPageAdapterForIndex(i);
            if (adapter != null) {
                adapter.getListAdapter().onDestroy();
            }
        }
    }

    private static class BottomPaddingOverrideSupplier implements Supplier<Optional<Integer>> {
        private final Context mContext;
        private int mBottomOffset;

        BottomPaddingOverrideSupplier(Context context) {
            mContext = context;
        }

        public void setEmptyStateBottomOffset(int bottomOffset) {
            mBottomOffset = bottomOffset;
        }

        @Override
        public Optional<Integer> get() {
            int initialBottomPadding = mContext.getResources().getDimensionPixelSize(
                    R.dimen.resolver_empty_state_container_padding_bottom);
            return Optional.of(initialBottomPadding + mBottomOffset);
        }
    }

    private static class ChooserProfileAdapterBinder implements
            AdapterBinder<RecyclerView, ChooserGridAdapter> {
        private int mMaxTargetsPerRow;

        ChooserProfileAdapterBinder(int maxTargetsPerRow) {
            mMaxTargetsPerRow = maxTargetsPerRow;
        }

        public void setMaxTargetsPerRow(int maxTargetsPerRow) {
            mMaxTargetsPerRow = maxTargetsPerRow;
        }

        @Override
        public void bind(
                RecyclerView recyclerView, ChooserGridAdapter chooserGridAdapter) {
            GridLayoutManager glm = (GridLayoutManager) recyclerView.getLayoutManager();
            glm.setSpanCount(mMaxTargetsPerRow);
            glm.setSpanSizeLookup(
                    new GridLayoutManager.SpanSizeLookup() {
                        @Override
                        public int getSpanSize(int position) {
                            return chooserGridAdapter.shouldCellSpan(position)
                                    ? SINGLE_CELL_SPAN_SIZE
                                    : glm.getSpanCount();
                        }
                    });
        }
    }
}

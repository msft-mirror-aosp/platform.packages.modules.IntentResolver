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

package com.android.intentresolver;

import static android.testing.PollingCheck.waitFor;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.swipeUp;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isSelected;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.android.intentresolver.ChooserActivityWorkProfileTest.TestCase.ExpectedBlocker.NO_BLOCKER;
import static com.android.intentresolver.ChooserActivityWorkProfileTest.TestCase.ExpectedBlocker.PERSONAL_PROFILE_ACCESS_BLOCKER;
import static com.android.intentresolver.ChooserActivityWorkProfileTest.TestCase.ExpectedBlocker.PERSONAL_PROFILE_SHARE_BLOCKER;
import static com.android.intentresolver.ChooserActivityWorkProfileTest.TestCase.ExpectedBlocker.WORK_PROFILE_ACCESS_BLOCKER;
import static com.android.intentresolver.ChooserActivityWorkProfileTest.TestCase.ExpectedBlocker.WORK_PROFILE_SHARE_BLOCKER;
import static com.android.intentresolver.ChooserActivityWorkProfileTest.TestCase.Tab.PERSONAL;
import static com.android.intentresolver.ChooserActivityWorkProfileTest.TestCase.Tab.WORK;
import static com.android.intentresolver.ChooserWrapperActivity.sOverrides;

import static org.hamcrest.CoreMatchers.not;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.companion.DeviceFilter;
import android.content.Intent;
import android.os.UserHandle;

import androidx.test.espresso.NoMatchingViewException;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.intentresolver.ChooserActivityWorkProfileTest.TestCase.Tab;
import com.android.intentresolver.data.repository.FakeUserRepository;
import com.android.intentresolver.data.repository.UserRepository;
import com.android.intentresolver.data.repository.UserRepositoryModule;
import com.android.intentresolver.inject.ApplicationUser;
import com.android.intentresolver.inject.ProfileParent;
import com.android.intentresolver.shared.model.User;

import dagger.hilt.android.testing.BindValue;
import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import dagger.hilt.android.testing.UninstallModules;

import junit.framework.AssertionFailedError;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@DeviceFilter.MediumType
@RunWith(Parameterized.class)
@HiltAndroidTest
@UninstallModules(UserRepositoryModule.class)
public class ChooserActivityWorkProfileTest {

    private static final UserHandle PERSONAL_USER_HANDLE = InstrumentationRegistry
            .getInstrumentation().getTargetContext().getUser();
    private static final UserHandle WORK_USER_HANDLE = UserHandle.of(10);

    @Rule(order = 0)
    public HiltAndroidRule mHiltAndroidRule = new HiltAndroidRule(this);

    @Rule(order = 1)
    public ActivityTestRule<ChooserWrapperActivity> mActivityRule =
            new ActivityTestRule<>(ChooserWrapperActivity.class, false,
                    false);

    @BindValue
    @ApplicationUser
    public final UserHandle mApplicationUser;

    @BindValue
    @ProfileParent
    public final UserHandle mProfileParent;

    /** For setup of test state, a mutable reference of mUserRepository  */
    private final FakeUserRepository mFakeUserRepo = new FakeUserRepository(
            List.of(new User(PERSONAL_USER_HANDLE.getIdentifier(), User.Role.PERSONAL)));

    @BindValue
    public final UserRepository mUserRepository;

    private final TestCase mTestCase;

    public ChooserActivityWorkProfileTest(TestCase testCase) {
        mTestCase = testCase;
        mApplicationUser = mTestCase.getMyUserHandle();
        mProfileParent = PERSONAL_USER_HANDLE;
        mUserRepository = new FakeUserRepository(List.of(
                new User(PERSONAL_USER_HANDLE.getIdentifier(), User.Role.PERSONAL),
                new User(WORK_USER_HANDLE.getIdentifier(), User.Role.WORK)));
    }

    @Before
    public void cleanOverrideData() {
        // TODO: use the other form of `adoptShellPermissionIdentity()` where we explicitly list the
        // permissions we require (which we'll read from the manifest at runtime).
        InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity();

        sOverrides.reset();
    }

    @Test
    public void testBlocker() {
        setUpPersonalAndWorkComponentInfos();
        sOverrides.hasCrossProfileIntents = mTestCase.hasCrossProfileIntents();

        launchActivity(mTestCase.getIsSendAction());
        switchToTab(mTestCase.getTab());

        switch (mTestCase.getExpectedBlocker()) {
            case NO_BLOCKER:
                assertNoBlockerDisplayed();
                break;
            case PERSONAL_PROFILE_SHARE_BLOCKER:
                assertCantSharePersonalAppsBlockerDisplayed();
                break;
            case WORK_PROFILE_SHARE_BLOCKER:
                assertCantShareWorkAppsBlockerDisplayed();
                break;
            case PERSONAL_PROFILE_ACCESS_BLOCKER:
                assertCantAccessPersonalAppsBlockerDisplayed();
                break;
            case WORK_PROFILE_ACCESS_BLOCKER:
                assertCantAccessWorkAppsBlockerDisplayed();
                break;
        }
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection tests() {
        return Arrays.asList(
                new TestCase(
                        /* isSendAction= */ true,
                        /* hasCrossProfileIntents= */ true,
                        /* myUserHandle= */ WORK_USER_HANDLE,
                        /* tab= */ WORK,
                        /* expectedBlocker= */ NO_BLOCKER
                ),
                new TestCase(
                        /* isSendAction= */ true,
                        /* hasCrossProfileIntents= */ false,
                        /* myUserHandle= */ WORK_USER_HANDLE,
                        /* tab= */ WORK,
                        /* expectedBlocker= */ NO_BLOCKER
                ),
                new TestCase(
                        /* isSendAction= */ true,
                        /* hasCrossProfileIntents= */ true,
                        /* myUserHandle= */ PERSONAL_USER_HANDLE,
                        /* tab= */ WORK,
                        /* expectedBlocker= */ NO_BLOCKER
                ),
                new TestCase(
                        /* isSendAction= */ true,
                        /* hasCrossProfileIntents= */ false,
                        /* myUserHandle= */ PERSONAL_USER_HANDLE,
                        /* tab= */ WORK,
                        /* expectedBlocker= */ WORK_PROFILE_SHARE_BLOCKER
                ),
                new TestCase(
                        /* isSendAction= */ true,
                        /* hasCrossProfileIntents= */ true,
                        /* myUserHandle= */ WORK_USER_HANDLE,
                        /* tab= */ PERSONAL,
                        /* expectedBlocker= */ NO_BLOCKER
                ),
                new TestCase(
                        /* isSendAction= */ true,
                        /* hasCrossProfileIntents= */ false,
                        /* myUserHandle= */ WORK_USER_HANDLE,
                        /* tab= */ PERSONAL,
                        /* expectedBlocker= */ PERSONAL_PROFILE_SHARE_BLOCKER
                ),
                new TestCase(
                        /* isSendAction= */ true,
                        /* hasCrossProfileIntents= */ true,
                        /* myUserHandle= */ PERSONAL_USER_HANDLE,
                        /* tab= */ PERSONAL,
                        /* expectedBlocker= */ NO_BLOCKER
                ),
                new TestCase(
                        /* isSendAction= */ true,
                        /* hasCrossProfileIntents= */ false,
                        /* myUserHandle= */ PERSONAL_USER_HANDLE,
                        /* tab= */ PERSONAL,
                        /* expectedBlocker= */ NO_BLOCKER
                ),
                new TestCase(
                        /* isSendAction= */ false,
                        /* hasCrossProfileIntents= */ true,
                        /* myUserHandle= */ WORK_USER_HANDLE,
                        /* tab= */ WORK,
                        /* expectedBlocker= */ NO_BLOCKER
                ),
                new TestCase(
                        /* isSendAction= */ false,
                        /* hasCrossProfileIntents= */ false,
                        /* myUserHandle= */ WORK_USER_HANDLE,
                        /* tab= */ WORK,
                        /* expectedBlocker= */ NO_BLOCKER
                ),
                new TestCase(
                        /* isSendAction= */ false,
                        /* hasCrossProfileIntents= */ true,
                        /* myUserHandle= */ PERSONAL_USER_HANDLE,
                        /* tab= */ WORK,
                        /* expectedBlocker= */ NO_BLOCKER
                ),
                new TestCase(
                        /* isSendAction= */ false,
                        /* hasCrossProfileIntents= */ false,
                        /* myUserHandle= */ PERSONAL_USER_HANDLE,
                        /* tab= */ WORK,
                        /* expectedBlocker= */ WORK_PROFILE_ACCESS_BLOCKER
                ),
                new TestCase(
                        /* isSendAction= */ false,
                        /* hasCrossProfileIntents= */ true,
                        /* myUserHandle= */ WORK_USER_HANDLE,
                        /* tab= */ PERSONAL,
                        /* expectedBlocker= */ NO_BLOCKER
                ),
                new TestCase(
                        /* isSendAction= */ false,
                        /* hasCrossProfileIntents= */ false,
                        /* myUserHandle= */ WORK_USER_HANDLE,
                        /* tab= */ PERSONAL,
                        /* expectedBlocker= */ PERSONAL_PROFILE_ACCESS_BLOCKER
                ),
                new TestCase(
                        /* isSendAction= */ false,
                        /* hasCrossProfileIntents= */ true,
                        /* myUserHandle= */ PERSONAL_USER_HANDLE,
                        /* tab= */ PERSONAL,
                        /* expectedBlocker= */ NO_BLOCKER
                ),
                new TestCase(
                        /* isSendAction= */ false,
                        /* hasCrossProfileIntents= */ false,
                        /* myUserHandle= */ PERSONAL_USER_HANDLE,
                        /* tab= */ PERSONAL,
                        /* expectedBlocker= */ NO_BLOCKER
                )
        );
    }

    private List<ResolvedComponentInfo> createResolvedComponentsForTestWithOtherProfile(
            int numberOfResults, int userId, UserHandle resolvedForUser) {
        List<ResolvedComponentInfo> infoList = new ArrayList<>(numberOfResults);
        for (int i = 0; i < numberOfResults; i++) {
            infoList.add(
                    ResolverDataProvider
                            .createResolvedComponentInfoWithOtherId(i, userId, resolvedForUser));
        }
        return infoList;
    }

    private List<ResolvedComponentInfo> createResolvedComponentsForTest(int numberOfResults,
            UserHandle resolvedForUser) {
        List<ResolvedComponentInfo> infoList = new ArrayList<>(numberOfResults);
        for (int i = 0; i < numberOfResults; i++) {
            infoList.add(ResolverDataProvider.createResolvedComponentInfo(i, resolvedForUser));
        }
        return infoList;
    }

    private void setUpPersonalAndWorkComponentInfos() {
        int workProfileTargets = 4;
        List<ResolvedComponentInfo> personalResolvedComponentInfos =
                createResolvedComponentsForTestWithOtherProfile(3,
                        /* userId */ WORK_USER_HANDLE.getIdentifier(), PERSONAL_USER_HANDLE);
        List<ResolvedComponentInfo> workResolvedComponentInfos =
                createResolvedComponentsForTest(workProfileTargets, WORK_USER_HANDLE);
        setupResolverControllers(personalResolvedComponentInfos, workResolvedComponentInfos);
    }

    private void setupResolverControllers(
            List<ResolvedComponentInfo> personalResolvedComponentInfos,
            List<ResolvedComponentInfo> workResolvedComponentInfos) {
        when(sOverrides.resolverListController.getResolversForIntentAsUser(
                Mockito.anyBoolean(),
                Mockito.anyBoolean(),
                Mockito.anyBoolean(),
                Mockito.isA(List.class),
                eq(UserHandle.SYSTEM)))
                        .thenReturn(new ArrayList<>(personalResolvedComponentInfos));
        when(sOverrides.workResolverListController.getResolversForIntentAsUser(
                Mockito.anyBoolean(),
                Mockito.anyBoolean(),
                Mockito.anyBoolean(),
                Mockito.isA(List.class),
                eq(UserHandle.SYSTEM)))
                        .thenReturn(new ArrayList<>(personalResolvedComponentInfos));
        when(sOverrides.workResolverListController.getResolversForIntentAsUser(
                Mockito.anyBoolean(),
                Mockito.anyBoolean(),
                Mockito.anyBoolean(),
                Mockito.isA(List.class),
                eq(WORK_USER_HANDLE)))
                        .thenReturn(new ArrayList<>(workResolvedComponentInfos));
    }

    private void waitForIdle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private void assertCantAccessWorkAppsBlockerDisplayed() {
        onView(withText(R.string.resolver_cross_profile_blocked))
                .check(matches(isDisplayed()));
        onView(withText(R.string.resolver_cant_access_work_apps_explanation))
                .check(matches(isDisplayed()));
    }

    private void assertCantAccessPersonalAppsBlockerDisplayed() {
        onView(withText(R.string.resolver_cross_profile_blocked))
                .check(matches(isDisplayed()));
        onView(withText(R.string.resolver_cant_access_personal_apps_explanation))
                .check(matches(isDisplayed()));
    }

    private void assertCantShareWorkAppsBlockerDisplayed() {
        onView(withText(R.string.resolver_cross_profile_blocked))
                .check(matches(isDisplayed()));
        onView(withText(R.string.resolver_cant_share_with_work_apps_explanation))
                .check(matches(isDisplayed()));
    }

    private void assertCantSharePersonalAppsBlockerDisplayed() {
        onView(withText(R.string.resolver_cross_profile_blocked))
                .check(matches(isDisplayed()));
        onView(withText(R.string.resolver_cant_share_with_personal_apps_explanation))
                .check(matches(isDisplayed()));
    }

    private void assertNoBlockerDisplayed() {
        try {
            onView(withText(R.string.resolver_cross_profile_blocked))
                    .check(matches(not(isDisplayed())));
        } catch (NoMatchingViewException ignored) {
        }
    }

    private void switchToTab(Tab tab) {
        final int stringId = tab == Tab.WORK ? R.string.resolver_work_tab
                : R.string.resolver_personal_tab;

        waitFor(() -> {
            onView(withText(stringId)).perform(click());
            waitForIdle();

            try {
                onView(withText(stringId)).check(matches(isSelected()));
                return true;
            } catch (AssertionFailedError e) {
                return false;
            }
        });

        onView(withId(com.android.internal.R.id.contentPanel))
                .perform(swipeUp());
        waitForIdle();
    }

    private Intent createTextIntent(boolean isSendAction) {
        Intent sendIntent = new Intent();
        if (isSendAction) {
            sendIntent.setAction(Intent.ACTION_SEND);
        }
        sendIntent.putExtra(Intent.EXTRA_TEXT, "testing intent sending");
        sendIntent.setType("text/plain");
        return sendIntent;
    }

    private void launchActivity(boolean isSendAction) {
        Intent sendIntent = createTextIntent(isSendAction);
        mActivityRule.launchActivity(Intent.createChooser(sendIntent, "Test"));
        waitForIdle();
    }

    public static class TestCase {
        private final boolean mIsSendAction;
        private final boolean mHasCrossProfileIntents;
        private final UserHandle mMyUserHandle;
        private final Tab mTab;
        private final ExpectedBlocker mExpectedBlocker;

        public enum ExpectedBlocker {
            NO_BLOCKER,
            PERSONAL_PROFILE_SHARE_BLOCKER,
            WORK_PROFILE_SHARE_BLOCKER,
            PERSONAL_PROFILE_ACCESS_BLOCKER,
            WORK_PROFILE_ACCESS_BLOCKER
        }

        public enum Tab {
            WORK,
            PERSONAL
        }

        public TestCase(boolean isSendAction, boolean hasCrossProfileIntents,
                UserHandle myUserHandle, Tab tab, ExpectedBlocker expectedBlocker) {
            mIsSendAction = isSendAction;
            mHasCrossProfileIntents = hasCrossProfileIntents;
            mMyUserHandle = myUserHandle;
            mTab = tab;
            mExpectedBlocker = expectedBlocker;
        }

        public boolean getIsSendAction() {
            return mIsSendAction;
        }

        public boolean hasCrossProfileIntents() {
            return mHasCrossProfileIntents;
        }

        public UserHandle getMyUserHandle() {
            return mMyUserHandle;
        }

        public Tab getTab() {
            return mTab;
        }

        public ExpectedBlocker getExpectedBlocker() {
            return mExpectedBlocker;
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder("test");

            if (mTab == WORK) {
                result.append("WorkTab_");
            } else {
                result.append("PersonalTab_");
            }

            if (mIsSendAction) {
                result.append("sendAction_");
            } else {
                result.append("notSendAction_");
            }

            if (mHasCrossProfileIntents) {
                result.append("hasCrossProfileIntents_");
            } else {
                result.append("doesNotHaveCrossProfileIntents_");
            }

            if (mMyUserHandle.equals(PERSONAL_USER_HANDLE)) {
                result.append("myUserIsPersonal_");
            } else {
                result.append("myUserIsWork_");
            }

            if (mExpectedBlocker == ExpectedBlocker.NO_BLOCKER) {
                result.append("thenNoBlocker");
            } else if (mExpectedBlocker == PERSONAL_PROFILE_ACCESS_BLOCKER) {
                result.append("thenAccessBlockerOnPersonalProfile");
            } else if (mExpectedBlocker == PERSONAL_PROFILE_SHARE_BLOCKER) {
                result.append("thenShareBlockerOnPersonalProfile");
            } else if (mExpectedBlocker == WORK_PROFILE_ACCESS_BLOCKER) {
                result.append("thenAccessBlockerOnWorkProfile");
            } else if (mExpectedBlocker == WORK_PROFILE_SHARE_BLOCKER) {
                result.append("thenShareBlockerOnWorkProfile");
            }

            return result.toString();
        }
    }
}

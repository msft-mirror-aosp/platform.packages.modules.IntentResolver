/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.intentresolver

import android.content.ClipData
import android.content.ClipDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.net.Uri
import android.os.UserHandle
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.provider.DeviceConfig
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.AndroidComposeUiTestEnvironment
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.android.intentresolver.TestContentProvider.Companion.makeItemUri
import com.android.intentresolver.chooser.TargetInfo
import com.android.intentresolver.contentpreview.ImageLoader
import com.android.intentresolver.contentpreview.ImageLoaderModule
import com.android.intentresolver.contentpreview.payloadtoggle.domain.cursor.CursorResolver
import com.android.intentresolver.contentpreview.payloadtoggle.domain.cursor.FakePayloadToggleCursorResolver
import com.android.intentresolver.contentpreview.payloadtoggle.domain.cursor.FakePayloadToggleCursorResolver.Companion.DEFAULT_MIME_TYPE
import com.android.intentresolver.contentpreview.payloadtoggle.domain.cursor.PayloadToggle
import com.android.intentresolver.contentpreview.payloadtoggle.domain.cursor.PayloadToggleCursorResolver
import com.android.intentresolver.contentpreview.payloadtoggle.domain.model.CursorRow
import com.android.intentresolver.contentpreview.payloadtoggle.domain.update.FakeSelectionChangeCallback
import com.android.intentresolver.contentpreview.payloadtoggle.domain.update.SelectionChangeCallback
import com.android.intentresolver.contentpreview.payloadtoggle.domain.update.SelectionChangeCallbackModule
import com.android.intentresolver.data.repository.FakeUserRepository
import com.android.intentresolver.data.repository.UserRepository
import com.android.intentresolver.data.repository.UserRepositoryModule
import com.android.intentresolver.inject.ApplicationUser
import com.android.intentresolver.inject.PackageManagerModule
import com.android.intentresolver.inject.ProfileParent
import com.android.intentresolver.platform.AppPredictionAvailable
import com.android.intentresolver.platform.AppPredictionModule
import com.android.intentresolver.platform.ImageEditor
import com.android.intentresolver.platform.ImageEditorModule
import com.android.intentresolver.shared.model.User
import com.android.intentresolver.tests.R
import com.android.internal.config.sysui.SystemUiDeviceConfigFlags
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import java.util.Optional
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Function
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.hamcrest.Matchers.allOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.stub

private const val TEST_TARGET_CATEGORY = "com.android.intentresolver.tests.TEST_RECEIVER_CATEGORY"
private const val PACKAGE = "com.android.intentresolver.tests"
private const val IMAGE_ACTIVITY = "com.android.intentresolver.tests.ImageReceiverActivity"
private const val VIDEO_ACTIVITY = "com.android.intentresolver.tests.VideoReceiverActivity"
private const val ALL_MEDIA_ACTIVITY = "com.android.intentresolver.tests.AllMediaReceiverActivity"
private const val IMAGE_ACTIVITY_LABEL = "ImageActivity"
private const val VIDEO_ACTIVITY_LABEL = "VideoActivity"
private const val ALL_MEDIA_ACTIVITY_LABEL = "AllMediaActivity"

/**
 * Instrumentation tests for ChooserActivity.
 *
 * Legacy test suite migrated from framework CoreTests.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
@HiltAndroidTest
@UninstallModules(
    AppPredictionModule::class,
    ImageEditorModule::class,
    PackageManagerModule::class,
    ImageLoaderModule::class,
    UserRepositoryModule::class,
    PayloadToggleCursorResolver.Binding::class,
    SelectionChangeCallbackModule::class,
)
class ChooserActivityShareouselTest() {
    @get:Rule(order = 0)
    val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @get:Rule(order = 1) val hiltAndroidRule: HiltAndroidRule = HiltAndroidRule(this)

    @Inject @ApplicationContext lateinit var context: Context

    @BindValue lateinit var packageManager: PackageManager

    private val fakeUserRepo = FakeUserRepository(listOf(PERSONAL_USER))

    @BindValue val userRepository: UserRepository = fakeUserRepo
    @AppPredictionAvailable @BindValue val appPredictionAvailable = false

    private val fakeImageLoader = FakeImageLoader()

    @BindValue val imageLoader: ImageLoader = fakeImageLoader
    @BindValue
    @ImageEditor
    val imageEditor: Optional<ComponentName> =
        Optional.ofNullable(
            ComponentName.unflattenFromString(
                "com.google.android.apps.messaging/.ui.conversationlist.ShareIntentActivity"
            )
        )

    @BindValue @ApplicationUser val applicationUser = PERSONAL_USER_HANDLE

    @BindValue @ProfileParent val profileParent = PERSONAL_USER_HANDLE

    private val fakeCursorResolver = FakePayloadToggleCursorResolver()
    @BindValue
    @PayloadToggle
    val additionalContentCursorResolver: CursorResolver<CursorRow?> = fakeCursorResolver

    @BindValue val selectionChangeCallback: SelectionChangeCallback = FakeSelectionChangeCallback()

    @Before
    fun setUp() {
        // TODO: use the other form of `adoptShellPermissionIdentity()` where we explicitly list the
        // permissions we require (which we'll read from the manifest at runtime).
        InstrumentationRegistry.getInstrumentation().uiAutomation.adoptShellPermissionIdentity()

        cleanOverrideData()

        // Assign @Inject fields
        hiltAndroidRule.inject()

        // Populate @BindValue dependencies using injected values. These fields contribute
        // values to the dependency graph at activity launch time. This allows replacing
        // arbitrary bindings per-test case if needed.
        packageManager = context.packageManager
        with(ChooserActivityOverrideData.getInstance()) {
            personalUserHandle = PERSONAL_USER_HANDLE
            mockListController(resolverListController)
        }
    }

    private fun setDeviceConfigProperty(propertyName: String, value: String) {
        // TODO: consider running with {@link #runWithShellPermissionIdentity()} to more narrowly
        // request WRITE_DEVICE_CONFIG permissions if we get rid of the broad grant we currently
        // configure in {@link #setup()}.
        // TODO: is it really appropriate that this is always set with makeDefault=true?
        val valueWasSet =
            DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_SYSTEMUI,
                propertyName,
                value,
                true, /* makeDefault */
            )
        check(valueWasSet) { "Could not set $propertyName to $value" }
    }

    private fun cleanOverrideData() {
        ChooserActivityOverrideData.getInstance().reset()

        setDeviceConfigProperty(
            SystemUiDeviceConfigFlags.APPLY_SHARING_APP_LIMITS_IN_SYSUI,
            true.toString(),
        )
    }

    @Test
    fun test_shareInitiallySelectedItem_initiallySelectedItemShared() {
        val launchedTargetInfo = AtomicReference<TargetInfo?>()
        with(ChooserActivityOverrideData.getInstance()) {
            onSafelyStartInternalCallback =
                Function<TargetInfo, Boolean> { targetInfo ->
                    launchedTargetInfo.set(targetInfo)
                    true
                }
        }
        val mimeTypes = emptyMap<Int, String>()
        setBitmaps(mimeTypes)
        fakeCursorResolver.setUris(count = 3, startPosition = 1, mimeTypes)
        launchActivityWithComposeTestEnv(makeItemUri("1", DEFAULT_MIME_TYPE), DEFAULT_MIME_TYPE) {
            selectTarget(IMAGE_ACTIVITY_LABEL)
        }

        val launchedTarget = launchedTargetInfo.get()
        assertThat(launchedTarget).isNotNull()
        val launchedIntent = launchedTarget!!.resolvedIntent
        assertThat(launchedIntent.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(launchedIntent.type).isEqualTo(DEFAULT_MIME_TYPE)
        assertThat(launchedIntent.component).isEqualTo(ComponentName(PACKAGE, IMAGE_ACTIVITY))
    }

    @Test
    fun test_changeSelectedItem_newlySelectedItemShared() {
        val launchedTargetInfo = AtomicReference<TargetInfo?>()
        with(ChooserActivityOverrideData.getInstance()) {
            onSafelyStartInternalCallback =
                Function<TargetInfo, Boolean> { targetInfo ->
                    launchedTargetInfo.set(targetInfo)
                    true
                }
        }
        val videoMimeType = "video/mp4"
        val mimeTypes = mapOf(1 to videoMimeType)
        setBitmaps(mimeTypes)
        fakeCursorResolver.setUris(count = 3, startPosition = 0, mimeTypes)
        launchActivityWithComposeTestEnv(makeItemUri("0", DEFAULT_MIME_TYPE), DEFAULT_MIME_TYPE) {
            scrollToPosition(0)
            tapOnItem(makeItemUri("0", DEFAULT_MIME_TYPE))
            scrollToPosition(1)
            tapOnItem(makeItemUri("1", videoMimeType))
            selectTarget(VIDEO_ACTIVITY_LABEL)
        }

        val launchedTarget = launchedTargetInfo.get()
        assertThat(launchedTarget).isNotNull()
        val launchedIntent = launchedTarget!!.resolvedIntent
        assertThat(launchedIntent.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(launchedIntent.type).isEqualTo(videoMimeType)
        assertThat(launchedIntent.component).isEqualTo(ComponentName(PACKAGE, VIDEO_ACTIVITY))
    }

    @Test
    fun test_selectAllItems_allItemsShared() {
        val launchedTargetInfo = AtomicReference<TargetInfo?>()
        with(ChooserActivityOverrideData.getInstance()) {
            onSafelyStartInternalCallback =
                Function<TargetInfo, Boolean> { targetInfo ->
                    launchedTargetInfo.set(targetInfo)
                    true
                }
        }
        val videoMimeType = "video/mp4"
        val mimeTypes = mapOf(1 to videoMimeType)
        setBitmaps(mimeTypes)
        fakeCursorResolver.setUris(3, 0, mimeTypes)
        launchActivityWithComposeTestEnv(makeItemUri("0", DEFAULT_MIME_TYPE), DEFAULT_MIME_TYPE) {
            scrollToPosition(1)
            tapOnItem(makeItemUri("1", videoMimeType))
            scrollToPosition(2)
            tapOnItem(makeItemUri("2", DEFAULT_MIME_TYPE))
            selectTarget(ALL_MEDIA_ACTIVITY_LABEL)
        }

        val launchedTarget = launchedTargetInfo.get()
        assertThat(launchedTarget).isNotNull()
        val launchedIntent = launchedTarget!!.resolvedIntent
        assertThat(launchedIntent.action).isEqualTo(Intent.ACTION_SEND_MULTIPLE)
        assertThat(launchedIntent.type).isEqualTo("*/*")
        assertThat(launchedIntent.component).isEqualTo(ComponentName(PACKAGE, ALL_MEDIA_ACTIVITY))
    }

    private fun setBitmaps(mimeTypes: Map<Int, String>) {
        arrayOf(Color.RED, Color.GREEN, Color.BLUE).forEachIndexed { i, color ->
            fakeImageLoader.setBitmap(
                makeItemUri(i.toString(), mimeTypes.getOrDefault(i, DEFAULT_MIME_TYPE)),
                createBitmap(100, 100, color),
            )
        }
    }

    private fun launchActivityWithComposeTestEnv(
        initialItem: Uri,
        mimeType: String,
        block: AndroidComposeUiTest<ChooserWrapperActivity>.() -> Unit,
    ) {
        val sendIntent =
            Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, initialItem)
                addCategory(TEST_TARGET_CATEGORY)
                type = mimeType
                clipData = ClipData("test", arrayOf(mimeType), ClipData.Item(initialItem))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

        val chooserIntent =
            Intent.createChooser(sendIntent, null).apply {
                component =
                    ComponentName(
                        "com.android.intentresolver.tests",
                        "com.android.intentresolver.ChooserWrapperActivity",
                    )
                putExtra(
                    Intent.EXTRA_CHOOSER_ADDITIONAL_CONTENT_URI,
                    Uri.parse("content://com.android.intentresolver.test.additional"),
                )
                putExtra(Intent.EXTRA_AUTO_LAUNCH_SINGLE_CHOICE, false)
                putExtra(Intent.EXTRA_CHOOSER_FOCUSED_ITEM_POSITION, 0)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        val activityRef = AtomicReference<ChooserWrapperActivity?>()
        val composeTestEnv = AndroidComposeUiTestEnvironment {
            requireNotNull(activityRef.get()) { "Activity was not launched" }
        }
        var scenario: ActivityScenario<ChooserWrapperActivity?>? = null
        try {
            composeTestEnv.runTest {
                this@runTest.mainClock.autoAdvance = true
                scenario = ActivityScenario.launch<ChooserWrapperActivity>(chooserIntent)
                scenario.onActivity { activityRef.set(it) }
                waitForIdle()
                block()
            }
        } finally {
            scenario?.close()
        }
    }

    private fun AndroidComposeUiTest<ChooserWrapperActivity>.tapOnItem(uri: Uri) {
        onNodeWithTag(uri.toString()).performClick()
        waitForIdle()
    }

    private fun AndroidComposeUiTest<ChooserWrapperActivity>.scrollToPosition(position: Int) {
        onNode(hasScrollToIndexAction()).performScrollToIndex(position)
        waitForIdle()
    }

    private fun AndroidComposeUiTest<ChooserWrapperActivity>.selectTarget(name: String) {
        onView(
                allOf(
                    withId(R.id.item),
                    ViewMatchers.hasDescendant(withText(name)),
                    ViewMatchers.isEnabled(),
                )
            )
            .perform(click())
        waitForIdle()
    }

    private fun mockListController(resolverListController: ResolverListController) {
        resolverListController.stub {
            on {
                getResolversForIntentAsUser(anyBoolean(), anyBoolean(), anyBoolean(), any(), any())
            } doAnswer
                { invocation ->
                    fakeTargetResolutionLogic(invocation.getArgument<List<Intent>>(3))
                }
        }
    }

    private fun fakeTargetResolutionLogic(intentList: List<Intent>): List<ResolvedComponentInfo> {
        require(intentList.size == 1) { "Expected a single intent" }
        val intent = intentList[0]
        require(
            intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE
        ) {
            "Expected send intent"
        }
        val mimeType = requireNotNull(intent.type) { "Expected intent with type" }
        val (activity, label) =
            when {
                ClipDescription.compareMimeTypes(mimeType, "image/*") ->
                    IMAGE_ACTIVITY to IMAGE_ACTIVITY_LABEL
                ClipDescription.compareMimeTypes(mimeType, "video/*") ->
                    VIDEO_ACTIVITY to VIDEO_ACTIVITY_LABEL
                else -> ALL_MEDIA_ACTIVITY to ALL_MEDIA_ACTIVITY_LABEL
            }
        val componentName = ComponentName(PACKAGE, activity)
        return listOf(
            ResolvedComponentInfo(
                componentName,
                intent,
                ResolveInfo().apply {
                    activityInfo = ResolverDataProvider.createActivityInfo(componentName)
                    targetUserId = UserHandle.USER_CURRENT
                    userHandle = PERSONAL_USER_HANDLE
                    nonLocalizedLabel = label
                },
            )
        )
    }

    companion object {
        private val PERSONAL_USER_HANDLE: UserHandle =
            InstrumentationRegistry.getInstrumentation().targetContext.getUser()

        private val PERSONAL_USER = User(PERSONAL_USER_HANDLE.identifier, User.Role.PERSONAL)
    }
}

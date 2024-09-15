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

package com.android.intentresolver

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.RECEIVER_EXPORTED
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Resources
import android.graphics.drawable.Icon
import android.service.chooser.ChooserAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.intentresolver.logging.EventLog
import com.android.intentresolver.ui.ShareResultSender
import com.android.intentresolver.ui.model.ShareAction
import com.google.common.truth.Truth.assertThat
import java.util.Optional
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class ChooserActionFactoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().context

    private val logger = mock<EventLog>()
    private val actionLabel = "Action label"
    private val testAction = "com.android.intentresolver.testaction"
    private val countdown = CountDownLatch(1)
    private val testReceiver: BroadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // Just doing at most a single countdown per test.
                countdown.countDown()
            }
        }
    private val resultConsumer =
        object : Consumer<Int> {
            var latestReturn = Integer.MIN_VALUE

            override fun accept(resultCode: Int) {
                latestReturn = resultCode
            }
        }
    private val featureFlags =
        FakeFeatureFlagsImpl().apply { setFlag(Flags.FLAG_FIX_PARTIAL_IMAGE_EDIT_TRANSITION, true) }

    @Before
    fun setup() {
        context.registerReceiver(testReceiver, IntentFilter(testAction), RECEIVER_EXPORTED)
    }

    @After
    fun teardown() {
        context.unregisterReceiver(testReceiver)
    }

    @Test
    fun testCreateCustomActions() {
        val factory = createFactory()

        val customActions = factory.createCustomActions()

        assertThat(customActions.size).isEqualTo(1)
        assertThat(customActions[0].label).isEqualTo(actionLabel)

        // click it
        customActions[0].onClicked.run()

        verify(logger).logCustomActionSelected(eq(0))
        assertEquals(Activity.RESULT_OK, resultConsumer.latestReturn)
        // Verify the pending intent has been called
        assertTrue("Timed out waiting for broadcast", countdown.await(2500, TimeUnit.MILLISECONDS))
    }

    @Test
    fun nonSendAction_noCopyRunnable() {
        val targetIntent =
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                putExtra(Intent.EXTRA_TEXT, "Text to show")
            }

        val testSubject =
            ChooserActionFactory(
                /* context = */ context,
                /* targetIntent = */ targetIntent,
                /* referrerPackageName = */ null,
                /* chooserActions = */ emptyList(),
                /* imageEditor = */ Optional.empty(),
                /* log = */ logger,
                /* onUpdateSharedTextIsExcluded = */ {},
                /* firstVisibleImageQuery = */ { null },
                /* activityStarter = */ mock(),
                /* shareResultSender = */ null,
                /* finishCallback = */ {},
                /* clipboardManager = */ mock(),
                /* featureFlags = */ featureFlags,
            )
        assertThat(testSubject.copyButtonRunnable).isNull()
    }

    @Test
    fun sendActionNoText_noCopyRunnable() {
        val targetIntent = Intent(Intent.ACTION_SEND)
        val testSubject =
            ChooserActionFactory(
                /* context = */ context,
                /* targetIntent = */ targetIntent,
                /* referrerPackageName = */ "com.example",
                /* chooserActions = */ emptyList(),
                /* imageEditor = */ Optional.empty(),
                /* log = */ logger,
                /* onUpdateSharedTextIsExcluded = */ {},
                /* firstVisibleImageQuery = */ { null },
                /* activityStarter = */ mock(),
                /* shareResultSender = */ null,
                /* finishCallback = */ {},
                /* clipboardManager = */ mock(),
                /* featureFlags = */ featureFlags,
            )
        assertThat(testSubject.copyButtonRunnable).isNull()
    }

    @Test
    fun sendActionWithTextCopyRunnable() {
        val targetIntent = Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_TEXT, "Text") }
        val resultSender = mock<ShareResultSender>()
        val testSubject =
            ChooserActionFactory(
                /* context = */ context,
                /* targetIntent = */ targetIntent,
                /* referrerPackageName = */ "com.example",
                /* chooserActions = */ emptyList(),
                /* imageEditor = */ Optional.empty(),
                /* log = */ logger,
                /* onUpdateSharedTextIsExcluded = */ {},
                /* firstVisibleImageQuery = */ { null },
                /* activityStarter = */ mock(),
                /* shareResultSender = */ resultSender,
                /* finishCallback = */ {},
                /* clipboardManager = */ mock(),
                /* featureFlags = */ featureFlags,
            )
        assertThat(testSubject.copyButtonRunnable).isNotNull()

        testSubject.copyButtonRunnable?.run()

        verify(resultSender) { 1 * { onActionSelected(ShareAction.SYSTEM_COPY) } }
    }

    private fun createFactory(): ChooserActionFactory {
        val testPendingIntent =
            PendingIntent.getBroadcast(context, 0, Intent(testAction), PendingIntent.FLAG_IMMUTABLE)
        val targetIntent = Intent()
        val action =
            ChooserAction.Builder(
                    Icon.createWithResource("", Resources.ID_NULL),
                    actionLabel,
                    testPendingIntent
                )
                .build()
        return ChooserActionFactory(
            /* context = */ context,
            /* targetIntent = */ targetIntent,
            /* referrerPackageName = */ "com.example",
            /* chooserActions = */ listOf(action),
            /* imageEditor = */ Optional.empty(),
            /* log = */ logger,
            /* onUpdateSharedTextIsExcluded = */ {},
            /* firstVisibleImageQuery = */ { null },
            /* activityStarter = */ mock(),
            /* shareResultSender = */ null,
            /* finishCallback = */ resultConsumer,
            /* clipboardManager = */ mock(),
            /* featureFlags = */ featureFlags,
        )
    }
}

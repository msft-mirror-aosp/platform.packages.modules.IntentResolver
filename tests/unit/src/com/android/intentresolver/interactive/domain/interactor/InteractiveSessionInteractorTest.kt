/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.intentresolver.interactive.domain.interactor

import android.content.ComponentName
import android.content.Intent
import android.content.Intent.ACTION_QUICK_VIEW
import android.content.Intent.ACTION_RUN
import android.content.Intent.ACTION_SEND
import android.content.Intent.ACTION_VIEW
import android.content.Intent.EXTRA_ALTERNATE_INTENTS
import android.content.Intent.EXTRA_CHOOSER_REFINEMENT_INTENT_SENDER
import android.content.Intent.EXTRA_CHOOSER_RESULT_INTENT_SENDER
import android.content.Intent.EXTRA_CHOOSER_TARGETS
import android.content.Intent.EXTRA_EXCLUDE_COMPONENTS
import android.content.Intent.EXTRA_INITIAL_INTENTS
import android.content.Intent.EXTRA_REPLACEMENT_EXTRAS
import android.content.IntentSender
import android.os.Binder
import android.os.IBinder
import android.os.IBinder.DeathRecipient
import android.os.IInterface
import android.os.Parcel
import android.os.ResultReceiver
import android.os.ShellCallback
import android.service.chooser.ChooserTarget
import androidx.core.os.bundleOf
import androidx.lifecycle.SavedStateHandle
import com.android.intentresolver.IChooserController
import com.android.intentresolver.IChooserInteractiveSessionCallback
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.PendingSelectionCallbackRepository
import com.android.intentresolver.data.model.ChooserRequest
import com.android.intentresolver.data.repository.ActivityModelRepository
import com.android.intentresolver.data.repository.ChooserRequestRepository
import com.android.intentresolver.interactive.data.repository.InteractiveSessionCallbackRepository
import com.android.intentresolver.shared.model.ActivityModel
import com.google.common.truth.Correspondence
import com.google.common.truth.Truth.assertThat
import java.io.FileDescriptor
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test

class InteractiveSessionInteractorTest {
    private val activityModelRepo =
        ActivityModelRepository().apply {
            initialize {
                ActivityModel(
                    intent = Intent(),
                    launchedFromUid = 12345,
                    launchedFromPackage = "org.client.package",
                    referrer = null,
                    isTaskRoot = false,
                )
            }
        }
    private val interactiveSessionCallback = FakeChooserInteractiveSessionCallback()
    private val pendingSelectionCallbackRepo = PendingSelectionCallbackRepository()
    private val savedStateHandle = SavedStateHandle()
    private val interactiveCallbackRepo = InteractiveSessionCallbackRepository(savedStateHandle)

    @Test
    fun testChooserLaunchedInNewTask_sessionClosed() = runTest {
        val activityModelRepo =
            ActivityModelRepository().apply {
                initialize {
                    ActivityModel(
                        intent = Intent(),
                        launchedFromUid = 12345,
                        launchedFromPackage = "org.client.package",
                        referrer = null,
                        isTaskRoot = true,
                    )
                }
            }
        val chooserRequestRepository =
            ChooserRequestRepository(
                initialRequest =
                    ChooserRequest(
                        targetIntent = Intent(ACTION_SEND),
                        interactiveSessionCallback = interactiveSessionCallback,
                        launchedFromPackage = activityModelRepo.value.launchedFromPackage,
                    ),
                initialActions = emptyList(),
            )
        val testSubject =
            InteractiveSessionInteractor(
                activityModelRepo = activityModelRepo,
                chooserRequestRepository = chooserRequestRepository,
                pendingSelectionCallbackRepo,
                interactiveCallbackRepo,
            )

        testSubject.activate()

        assertThat(interactiveSessionCallback.registeredIntentUpdaters).containsExactly(null)
    }

    @Test
    fun testDeadBinder_sessionEnd() = runTest {
        interactiveSessionCallback.isAlive = false
        val chooserRequestRepository =
            ChooserRequestRepository(
                initialRequest =
                    ChooserRequest(
                        targetIntent = Intent(ACTION_SEND),
                        interactiveSessionCallback = interactiveSessionCallback,
                        launchedFromPackage = activityModelRepo.value.launchedFromPackage,
                    ),
                initialActions = emptyList(),
            )
        val testSubject =
            InteractiveSessionInteractor(
                activityModelRepo = activityModelRepo,
                chooserRequestRepository = chooserRequestRepository,
                pendingSelectionCallbackRepo,
                interactiveCallbackRepo,
            )

        backgroundScope.launch { testSubject.activate() }
        this.testScheduler.runCurrent()

        assertThat(testSubject.isSessionActive.value).isFalse()
    }

    @Test
    fun testBinderDies_sessionEnd() = runTest {
        val chooserRequestRepository =
            ChooserRequestRepository(
                initialRequest =
                    ChooserRequest(
                        targetIntent = Intent(ACTION_SEND),
                        interactiveSessionCallback = interactiveSessionCallback,
                        launchedFromPackage = activityModelRepo.value.launchedFromPackage,
                    ),
                initialActions = emptyList(),
            )
        val testSubject =
            InteractiveSessionInteractor(
                activityModelRepo = activityModelRepo,
                chooserRequestRepository = chooserRequestRepository,
                pendingSelectionCallbackRepo,
                interactiveCallbackRepo,
            )

        backgroundScope.launch { testSubject.activate() }
        this.testScheduler.runCurrent()

        assertThat(testSubject.isSessionActive.value).isTrue()
        assertThat(interactiveSessionCallback.linkedDeathRecipients).hasSize(1)

        interactiveSessionCallback.linkedDeathRecipients[0].binderDied()

        assertThat(testSubject.isSessionActive.value).isFalse()
    }

    @Test
    fun testScopeCancelled_unsubscribeFromBinder() = runTest {
        val chooserRequestRepository =
            ChooserRequestRepository(
                initialRequest =
                    ChooserRequest(
                        targetIntent = Intent(ACTION_SEND),
                        interactiveSessionCallback = interactiveSessionCallback,
                        launchedFromPackage = activityModelRepo.value.launchedFromPackage,
                    ),
                initialActions = emptyList(),
            )
        val testSubject =
            InteractiveSessionInteractor(
                activityModelRepo = activityModelRepo,
                chooserRequestRepository = chooserRequestRepository,
                pendingSelectionCallbackRepo,
                interactiveCallbackRepo,
            )

        val job = backgroundScope.launch { testSubject.activate() }
        testScheduler.runCurrent()

        assertThat(interactiveSessionCallback.linkedDeathRecipients).hasSize(1)
        assertThat(interactiveSessionCallback.unlinkedDeathRecipients).hasSize(0)

        job.cancel()
        testScheduler.runCurrent()

        assertThat(interactiveSessionCallback.unlinkedDeathRecipients).hasSize(1)
    }

    @Test
    fun endSession_intentUpdaterCallbackReset() = runTest {
        val chooserRequestRepository =
            ChooserRequestRepository(
                initialRequest =
                    ChooserRequest(
                        targetIntent = Intent(ACTION_SEND),
                        interactiveSessionCallback = interactiveSessionCallback,
                        launchedFromPackage = activityModelRepo.value.launchedFromPackage,
                    ),
                initialActions = emptyList(),
            )
        val testSubject =
            InteractiveSessionInteractor(
                activityModelRepo = activityModelRepo,
                chooserRequestRepository = chooserRequestRepository,
                pendingSelectionCallbackRepo,
                interactiveCallbackRepo,
            )

        backgroundScope.launch { testSubject.activate() }
        testScheduler.runCurrent()

        assertThat(interactiveSessionCallback.registeredIntentUpdaters).hasSize(1)

        testSubject.endSession()

        assertThat(interactiveSessionCallback.registeredIntentUpdaters).hasSize(2)
        assertThat(interactiveSessionCallback.registeredIntentUpdaters[1]).isNull()
    }

    @Test
    fun nullChooserIntentReceived_sessionEnds() = runTest {
        val chooserRequestRepository =
            ChooserRequestRepository(
                initialRequest =
                    ChooserRequest(
                        targetIntent = Intent(ACTION_SEND),
                        interactiveSessionCallback = interactiveSessionCallback,
                        launchedFromPackage = activityModelRepo.value.launchedFromPackage,
                    ),
                initialActions = emptyList(),
            )
        val testSubject =
            InteractiveSessionInteractor(
                activityModelRepo = activityModelRepo,
                chooserRequestRepository = chooserRequestRepository,
                pendingSelectionCallbackRepo,
                interactiveCallbackRepo,
            )

        backgroundScope.launch { testSubject.activate() }
        testScheduler.runCurrent()

        assertThat(interactiveSessionCallback.registeredIntentUpdaters).hasSize(1)
        interactiveSessionCallback.registeredIntentUpdaters[0]!!.updateIntent(null)
        testScheduler.runCurrent()

        assertThat(testSubject.isSessionActive.value).isFalse()
    }

    @Test
    fun invalidChooserIntentReceived_intentIgnored() = runTest {
        val chooserRequestRepository =
            ChooserRequestRepository(
                initialRequest =
                    ChooserRequest(
                        targetIntent = Intent(ACTION_SEND),
                        interactiveSessionCallback = interactiveSessionCallback,
                        launchedFromPackage = activityModelRepo.value.launchedFromPackage,
                    ),
                initialActions = emptyList(),
            )
        val testSubject =
            InteractiveSessionInteractor(
                activityModelRepo = activityModelRepo,
                chooserRequestRepository = chooserRequestRepository,
                pendingSelectionCallbackRepo,
                interactiveCallbackRepo,
            )

        backgroundScope.launch { testSubject.activate() }
        testScheduler.runCurrent()

        assertThat(interactiveSessionCallback.registeredIntentUpdaters).hasSize(1)
        interactiveSessionCallback.registeredIntentUpdaters[0]!!.updateIntent(Intent())
        testScheduler.runCurrent()

        assertThat(testSubject.isSessionActive.value).isTrue()
        assertThat(chooserRequestRepository.chooserRequest.value)
            .isEqualTo(chooserRequestRepository.initialRequest)
    }

    @Test
    fun validChooserIntentReceived_chooserRequestUpdated() = runTest {
        val chooserRequestRepository =
            ChooserRequestRepository(
                initialRequest =
                    ChooserRequest(
                        targetIntent = Intent(ACTION_SEND),
                        interactiveSessionCallback = interactiveSessionCallback,
                        launchedFromPackage = activityModelRepo.value.launchedFromPackage,
                    ),
                initialActions = emptyList(),
            )
        val testSubject =
            InteractiveSessionInteractor(
                activityModelRepo = activityModelRepo,
                chooserRequestRepository = chooserRequestRepository,
                pendingSelectionCallbackRepo,
                interactiveCallbackRepo,
            )

        backgroundScope.launch { testSubject.activate() }
        testScheduler.runCurrent()

        assertThat(interactiveSessionCallback.registeredIntentUpdaters).hasSize(1)
        val newTargetIntent = Intent(ACTION_VIEW).apply { type = "image/png" }
        val newFilteredComponents = arrayOf(ComponentName.unflattenFromString("com.app/.MainA"))
        val newCallerTargets =
            arrayOf(
                ChooserTarget(
                    "A",
                    null,
                    0.5f,
                    ComponentName.unflattenFromString("org.pkg/.Activity"),
                    null,
                )
            )
        val newAdditionalIntents = arrayOf(Intent(ACTION_RUN))
        val newReplacementExtras = bundleOf("ONE" to 1, "TWO" to 2)
        val newInitialIntents = arrayOf(Intent(ACTION_QUICK_VIEW))
        val newResultSender = IntentSender(Binder())
        val newRefinementSender = IntentSender(Binder())
        interactiveSessionCallback.registeredIntentUpdaters[0]!!.updateIntent(
            Intent.createChooser(newTargetIntent, "").apply {
                putExtra(EXTRA_EXCLUDE_COMPONENTS, newFilteredComponents)
                putExtra(EXTRA_CHOOSER_TARGETS, newCallerTargets)
                putExtra(EXTRA_ALTERNATE_INTENTS, newAdditionalIntents)
                putExtra(EXTRA_REPLACEMENT_EXTRAS, newReplacementExtras)
                putExtra(EXTRA_INITIAL_INTENTS, newInitialIntents)
                putExtra(EXTRA_CHOOSER_RESULT_INTENT_SENDER, newResultSender)
                putExtra(EXTRA_CHOOSER_REFINEMENT_INTENT_SENDER, newRefinementSender)
            }
        )
        testScheduler.runCurrent()

        assertThat(testSubject.isSessionActive.value).isTrue()
        val updatedRequest = chooserRequestRepository.chooserRequest.value
        assertThat(updatedRequest.targetAction).isEqualTo(newTargetIntent.action)
        assertThat(updatedRequest.targetType).isEqualTo(newTargetIntent.type)
        assertThat(updatedRequest.filteredComponentNames).containsExactly(newFilteredComponents[0])
        assertThat(updatedRequest.callerChooserTargets).containsExactly(newCallerTargets[0])
        assertThat(updatedRequest.additionalTargets)
            .comparingElementsUsing<Intent, String>(
                Correspondence.transforming({ it.action }, "action")
            )
            .containsExactly(newAdditionalIntents[0].action)
        assertThat(updatedRequest.replacementExtras!!.keySet())
            .containsExactlyElementsIn(newReplacementExtras.keySet())
        assertThat(updatedRequest.initialIntents)
            .comparingElementsUsing<Intent, String>(
                Correspondence.transforming({ it.action }, "action")
            )
            .containsExactly(newInitialIntents[0].action)
        assertThat(updatedRequest.chosenComponentSender).isEqualTo(newResultSender)
        assertThat(updatedRequest.refinementIntentSender).isEqualTo(newRefinementSender)
    }
}

private class FakeChooserInteractiveSessionCallback :
    IChooserInteractiveSessionCallback, IBinder, IInterface {
    var isAlive = true
    val registeredIntentUpdaters = ArrayList<IChooserController?>()
    val linkedDeathRecipients = ArrayList<DeathRecipient>()
    val unlinkedDeathRecipients = ArrayList<DeathRecipient>()

    override fun registerChooserController(intentUpdater: IChooserController?) {
        registeredIntentUpdaters.add(intentUpdater)
    }

    override fun onDrawerVerticalOffsetChanged(offset: Int) {}

    override fun asBinder() = this

    override fun getInterfaceDescriptor() = ""

    override fun pingBinder() = true

    override fun isBinderAlive() = isAlive

    override fun queryLocalInterface(descriptor: String): IInterface =
        this@FakeChooserInteractiveSessionCallback

    override fun dump(fd: FileDescriptor, args: Array<out String>?) = Unit

    override fun dumpAsync(fd: FileDescriptor, args: Array<out String>?) = Unit

    override fun shellCommand(
        `in`: FileDescriptor?,
        out: FileDescriptor?,
        err: FileDescriptor?,
        args: Array<out String>,
        shellCallback: ShellCallback?,
        resultReceiver: ResultReceiver,
    ) = Unit

    override fun transact(code: Int, data: Parcel, reply: Parcel?, flags: Int) = true

    override fun linkToDeath(recipient: DeathRecipient, flags: Int) {
        linkedDeathRecipients.add(recipient)
    }

    override fun unlinkToDeath(recipient: DeathRecipient, flags: Int): Boolean {
        unlinkedDeathRecipients.add(recipient)
        return true
    }
}

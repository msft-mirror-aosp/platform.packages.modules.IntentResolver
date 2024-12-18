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

package com.android.intentresolver

import android.app.Activity
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.intentresolver.Flags.unselectFinalItem
import com.android.intentresolver.annotation.JavaInterop
import com.android.intentresolver.contentpreview.ContentPreviewType.CONTENT_PREVIEW_PAYLOAD_SELECTION
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.ActivityResultRepository
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.PendingSelectionCallbackRepository
import com.android.intentresolver.data.model.ChooserRequest
import com.android.intentresolver.platform.GlobalSettings
import com.android.intentresolver.ui.viewmodel.ChooserViewModel
import com.android.intentresolver.validation.Invalid
import com.android.intentresolver.validation.Valid
import com.android.intentresolver.validation.log
import dagger.hilt.android.scopes.ActivityScoped
import java.util.function.Consumer
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG: String = "ChooserHelper"

/**
 * __Purpose__
 *
 * Cleanup aid. Provides a pathway to cleaner code.
 *
 * __Incoming References__
 *
 * ChooserHelper must not expose any properties or functions directly back to ChooserActivity. If a
 * value or operation is required by ChooserActivity, then it must be added to ChooserInitializer
 * (or a new interface as appropriate) with ChooserActivity supplying a callback to receive it at
 * the appropriate point. This enforces unidirectional control flow.
 *
 * __Outgoing References__
 *
 * _ChooserActivity_
 *
 * This class must only reference it's host as Activity/ComponentActivity; no down-cast to
 * [ChooserActivity]. Other components should be created here or supplied via Injection, and not
 * referenced directly within ChooserActivity. This prevents circular dependencies from forming. If
 * necessary, during cleanup the dependency can be supplied back to ChooserActivity as described
 * above in 'Incoming References', see [ChooserInitializer].
 *
 * _Elsewhere_
 *
 * Where possible, Singleton and ActivityScoped dependencies should be injected here instead of
 * referenced from an existing location. If not available for injection, the value should be
 * constructed here, then provided to where it is needed.
 */
@ActivityScoped
@JavaInterop
class ChooserHelper
@Inject
constructor(
    hostActivity: Activity,
    private val activityResultRepo: ActivityResultRepository,
    private val pendingSelectionCallbackRepo: PendingSelectionCallbackRepository,
    private val globalSettings: GlobalSettings,
) : DefaultLifecycleObserver {
    // This is guaranteed by Hilt, since only a ComponentActivity is injectable.
    private val activity: ComponentActivity = hostActivity as ComponentActivity
    private val viewModel by activity.viewModels<ChooserViewModel>()

    // TODO: provide the following through an init object passed into [setInitialize]
    private lateinit var activityInitializer: Runnable
    /** Invoked when there are updates to ChooserRequest */
    var onChooserRequestChanged: Consumer<ChooserRequest> = Consumer {}
    /** Invoked when there are a new change to payload selection */
    var onPendingSelection: Runnable = Runnable {}
    var onHasSelections: Consumer<Boolean> = Consumer {}

    init {
        activity.lifecycle.addObserver(this)
    }

    /**
     * Set the initialization hook for the host activity.
     *
     * This _must_ be called from [ChooserActivity.onCreate].
     */
    fun setInitializer(initializer: Runnable) {
        check(activity.lifecycle.currentState == Lifecycle.State.INITIALIZED) {
            "setInitializer must be called before onCreate returns"
        }
        activityInitializer = initializer
    }

    /** Invoked by Lifecycle, after [ChooserActivity.onCreate] _returns_. */
    override fun onCreate(owner: LifecycleOwner) {
        Log.i(TAG, "CREATE")
        Log.i(TAG, "${viewModel.activityModel}")

        val callerUid: Int = viewModel.activityModel.launchedFromUid
        if (callerUid < 0 || UserHandle.isIsolated(callerUid)) {
            Log.e(TAG, "Can't start a chooser from uid $callerUid")
            activity.finish()
            return
        }

        if (globalSettings.getBooleanOrNull(Settings.Global.SECURE_FRP_MODE) == true) {
            Log.e(TAG, "Sharing disabled due to active FRP lock.")
            activity.finish()
            return
        }

        when (val request = viewModel.initialRequest) {
            is Valid -> initializeActivity(request)
            is Invalid -> reportErrorsAndFinish(request)
        }

        activity.lifecycleScope.launch {
            activity.setResult(activityResultRepo.activityResult.filterNotNull().first())
            activity.finish()
        }

        activity.lifecycleScope.launch {
            val hasPendingIntentFlow =
                pendingSelectionCallbackRepo.pendingTargetIntent
                    .map { it != null }
                    .distinctUntilChanged()
                    .onEach { hasPendingIntent ->
                        if (hasPendingIntent) {
                            onPendingSelection.run()
                        }
                    }
            activity.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val hasSelectionFlow =
                    if (
                        unselectFinalItem() &&
                            viewModel.previewDataProvider.previewType ==
                                CONTENT_PREVIEW_PAYLOAD_SELECTION
                    ) {
                        viewModel.shareouselViewModel.hasSelectedItems.stateIn(scope = this).also {
                            flow ->
                            launch { flow.collect { onHasSelections.accept(it) } }
                        }
                    } else {
                        MutableStateFlow(true).asStateFlow()
                    }
                val requestControlFlow =
                    hasSelectionFlow
                        .combine(hasPendingIntentFlow) { hasSelections, hasPendingIntent ->
                            hasSelections && !hasPendingIntent
                        }
                        .distinctUntilChanged()
                viewModel.request
                    .combine(requestControlFlow) { request, isReady -> request to isReady }
                    // only take ChooserRequest if there are no pending callbacks
                    .filter { it.second }
                    .map { it.first }
                    .distinctUntilChanged(areEquivalent = { old, new -> old === new })
                    .collect { onChooserRequestChanged.accept(it) }
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        Log.i(TAG, "START")
    }

    override fun onResume(owner: LifecycleOwner) {
        Log.i(TAG, "RESUME")
    }

    override fun onPause(owner: LifecycleOwner) {
        Log.i(TAG, "PAUSE")
    }

    override fun onStop(owner: LifecycleOwner) {
        Log.i(TAG, "STOP")
    }

    override fun onDestroy(owner: LifecycleOwner) {
        Log.i(TAG, "DESTROY")
    }

    private fun reportErrorsAndFinish(request: Invalid<ChooserRequest>) {
        request.errors.forEach { it.log(TAG) }
        activity.finish()
    }

    private fun initializeActivity(request: Valid<ChooserRequest>) {
        request.warnings.forEach { it.log(TAG) }
        activityInitializer.run()
    }
}

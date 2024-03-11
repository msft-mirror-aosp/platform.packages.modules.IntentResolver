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

package com.android.intentresolver.v2

import android.app.Activity
import android.os.UserHandle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.ActivityResultRepository
import com.android.intentresolver.inject.Background
import com.android.intentresolver.v2.annotation.JavaInterop
import com.android.intentresolver.v2.domain.interactor.UserInteractor
import com.android.intentresolver.v2.shared.model.Profile
import com.android.intentresolver.v2.ui.model.ChooserRequest
import com.android.intentresolver.v2.ui.viewmodel.ChooserViewModel
import com.android.intentresolver.v2.validation.Invalid
import com.android.intentresolver.v2.validation.Valid
import com.android.intentresolver.v2.validation.log
import dagger.hilt.android.scopes.ActivityScoped
import java.util.function.Consumer
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val TAG: String = "ChooserHelper"

/**
 * Provides initial values to ChooserActivity and completes initialization from onCreate.
 *
 * This information is collected and provided on behalf of ChooserActivity to eliminate the need for
 * suspending functions within remaining synchronous startup code.
 */
@JavaInterop
fun interface ChooserInitializer {
    /** @param initialState the initial state to provide to initialization */
    fun initializeWith(initialState: InitialState)
}

/**
 * A parameter object for Initialize which contains all the values which are required "early", on
 * the main thread and outside of any coroutines. This supports code which expects to be called by
 * the system on the main thread only. (This includes everything originally called from onCreate).
 */
@JavaInterop
data class InitialState(
    val profiles: List<Profile>,
    val availability: Map<Profile, Boolean>,
    val launchedAs: Profile
)

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
    private val userInteractor: UserInteractor,
    private val activityResultRepo: ActivityResultRepository,
    @Background private val background: CoroutineDispatcher,
) : DefaultLifecycleObserver {
    // This is guaranteed by Hilt, since only a ComponentActivity is injectable.
    private val activity: ComponentActivity = hostActivity as ComponentActivity
    private val viewModel by activity.viewModels<ChooserViewModel>()

    private lateinit var activityInitializer: ChooserInitializer

    var onChooserRequestChanged: Consumer<ChooserRequest> = Consumer {}

    init {
        activity.lifecycle.addObserver(this)
    }

    /**
     * Set the initialization hook for the host activity.
     *
     * This _must_ be called from [ChooserActivity.onCreate].
     */
    fun setInitializer(initializer: ChooserInitializer) {
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

        when (val request = viewModel.initialRequest) {
            is Valid -> initializeActivity(request)
            is Invalid -> reportErrorsAndFinish(request)
        }

        activity.lifecycleScope.launch {
            activity.setResult(activityResultRepo.activityResult.filterNotNull().first())
            activity.finish()
        }

        activity.lifecycleScope.launch {
            activity.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.request.collect { onChooserRequestChanged.accept(it) }
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

        val initialState =
            runBlocking(background) {
                val initialProfiles = userInteractor.profiles.first()
                val initialAvailability = userInteractor.availability.first()
                val launchedAsProfile = userInteractor.launchedAsProfile.first()
                InitialState(initialProfiles, initialAvailability, launchedAsProfile)
            }
        activityInitializer.initializeWith(initialState)
    }
}

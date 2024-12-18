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
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.android.intentresolver.annotation.JavaInterop
import com.android.intentresolver.domain.interactor.UserInteractor
import com.android.intentresolver.inject.Background
import com.android.intentresolver.ui.model.ResolverRequest
import com.android.intentresolver.ui.viewmodel.ResolverViewModel
import com.android.intentresolver.validation.Invalid
import com.android.intentresolver.validation.Valid
import com.android.intentresolver.validation.log
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher

private const val TAG: String = "ResolverHelper"

/**
 * __Purpose__
 *
 * Cleanup aid. Provides a pathway to cleaner code.
 *
 * __Incoming References__
 *
 * ResolverHelper must not expose any properties or functions directly back to ResolverActivity. If
 * a value or operation is required by ResolverActivity, then it must be added to
 * ResolverInitializer (or a new interface as appropriate) with ResolverActivity supplying a
 * callback to receive it at the appropriate point. This enforces unidirectional control flow.
 *
 * __Outgoing References__
 *
 * _ResolverActivity_
 *
 * This class must only reference it's host as Activity/ComponentActivity; no down-cast to
 * [ResolverActivity]. Other components should be created here or supplied via Injection, and not
 * referenced directly from the activity. This prevents circular dependencies from forming. If
 * necessary, during cleanup the dependency can be supplied back to ChooserActivity as described
 * above in 'Incoming References', see [ResolverInitializer].
 *
 * _Elsewhere_
 *
 * Where possible, Singleton and ActivityScoped dependencies should be injected here instead of
 * referenced from an existing location. If not available for injection, the value should be
 * constructed here, then provided to where it is needed.
 */
@ActivityScoped
@JavaInterop
class ResolverHelper
@Inject
constructor(
    hostActivity: Activity,
    private val userInteractor: UserInteractor,
    @Background private val background: CoroutineDispatcher,
) : DefaultLifecycleObserver {
    // This is guaranteed by Hilt, since only a ComponentActivity is injectable.
    private val activity: ComponentActivity = hostActivity as ComponentActivity
    private val viewModel by activity.viewModels<ResolverViewModel>()

    private lateinit var activityInitializer: Runnable

    init {
        activity.lifecycle.addObserver(this)
    }

    /**
     * Set the initialization hook for the host activity.
     *
     * This _must_ be called from [ResolverActivity.onCreate].
     */
    fun setInitializer(initializer: Runnable) {
        if (activity.lifecycle.currentState != Lifecycle.State.INITIALIZED) {
            error("setInitializer must be called before onCreate returns")
        }
        activityInitializer = initializer
    }

    /** Invoked by Lifecycle, after Activity.onCreate() _returns_. */
    override fun onCreate(owner: LifecycleOwner) {
        Log.i(TAG, "CREATE")
        Log.i(TAG, "${viewModel.activityModel}")

        val callerUid: Int = viewModel.activityModel.launchedFromUid
        if (callerUid < 0 || UserHandle.isIsolated(callerUid)) {
            Log.e(TAG, "Can't start a resolver from uid $callerUid")
            activity.finish()
            return
        }

        when (val request = viewModel.initialRequest) {
            is Valid -> initializeActivity(request)
            is Invalid -> reportErrorsAndFinish(request)
        }
    }

    private fun reportErrorsAndFinish(request: Invalid<ResolverRequest>) {
        request.errors.forEach { it.log(TAG) }
        activity.finish()
    }

    private fun initializeActivity(request: Valid<ResolverRequest>) {
        Log.d(TAG, "initializeActivity")
        request.warnings.forEach { it.log(TAG) }

        activityInitializer.run()
    }
}

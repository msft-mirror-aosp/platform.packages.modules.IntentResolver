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

import androidx.annotation.MainThread
import com.android.intentresolver.annotation.JavaInterop
import com.android.intentresolver.domain.interactor.UserInteractor
import com.android.intentresolver.shared.model.Profile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Provides availability status for profiles */
@JavaInterop
class ProfileAvailability(
    private val userInteractor: UserInteractor,
    private val scope: CoroutineScope,
    private val background: CoroutineDispatcher,
) {
    /** Used by WorkProfilePausedEmptyStateProvider */
    var waitingToEnableProfile = false
        private set

    /** Set by ChooserActivity to call onWorkProfileStatusUpdated */
    var onProfileStatusChange: Runnable? = null

    private var waitJob: Job? = null

    /** Query current profile availability. An unavailable profile is one which is not active. */
    @MainThread
    fun isAvailable(profile: Profile?): Boolean {
        return runBlocking(background) {
            userInteractor.availability.map { it[profile] == true }.first()
        }
    }

    /**
     * The number of profiles which are visible. All profiles count except for private which is
     * hidden when locked.
     */
    fun visibleProfileCount() =
        runBlocking(background) {
            val availability = userInteractor.availability.first()
            val profiles = userInteractor.profiles.first()
            profiles
                .filter {
                    when (it.type) {
                        Profile.Type.PRIVATE -> availability[it] == true
                        else -> true
                    }
                }
                .size
        }

    /** Used by WorkProfilePausedEmptyStateProvider */
    fun requestQuietModeState(profile: Profile, quietMode: Boolean) {
        val enableProfile = !quietMode

        // Check if the profile is already in the correct state
        if (isAvailable(profile) == enableProfile) {
            return // No-op
        }

        // Support existing code
        if (enableProfile) {
            waitingToEnableProfile = true
            waitJob?.cancel()

            val job =
                scope.launch {
                    // Wait for the profile to become available
                    userInteractor.availability.filter { it[profile] == true }.first()
                }
            job.invokeOnCompletion {
                waitingToEnableProfile = false
                onProfileStatusChange?.run()
            }
            waitJob = job
        }

        // Apply the change
        scope.launch { userInteractor.updateState(profile, enableProfile) }
    }
}

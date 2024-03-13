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

import com.android.intentresolver.v2.annotation.JavaInterop
import com.android.intentresolver.v2.domain.interactor.UserInteractor
import com.android.intentresolver.v2.shared.model.Profile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Provides availability status for profiles */
@JavaInterop
class ProfileAvailability(
    private val scope: CoroutineScope,
    private val userInteractor: UserInteractor,
    initialState: Map<Profile, Boolean>
) {
    private val availability =
        userInteractor.availability.stateIn(scope, SharingStarted.Eagerly, initialState)

    /** Used by WorkProfilePausedEmptyStateProvider */
    var waitingToEnableProfile = false
        private set

    /** Set by ChooserActivity to call onWorkProfileStatusUpdated */
    var onProfileStatusChange: Runnable? = null

    private var waitJob: Job? = null
    /** Query current profile availability. An unavailable profile is one which is not active. */
    fun isAvailable(profile: Profile) = availability.value[profile] ?: false

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
                    availability.filter { it[profile] == true }.first()
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

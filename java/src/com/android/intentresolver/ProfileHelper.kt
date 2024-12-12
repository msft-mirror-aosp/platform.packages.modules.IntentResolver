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

import android.os.UserHandle
import androidx.annotation.MainThread
import com.android.intentresolver.annotation.JavaInterop
import com.android.intentresolver.domain.interactor.UserInteractor
import com.android.intentresolver.shared.model.Profile
import com.android.intentresolver.shared.model.User
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@JavaInterop
@MainThread
class ProfileHelper
@Inject
constructor(interactor: UserInteractor, private val background: CoroutineDispatcher) {
    private val launchedByHandle: UserHandle = interactor.launchedAs

    val launchedAsProfile by lazy {
        runBlocking(background) { interactor.launchedAsProfile.first() }
    }
    val profiles by lazy { runBlocking(background) { interactor.profiles.first() } }

    // Map UserHandle back to a user within launchedByProfile
    private val launchedByUser: User =
        when (launchedByHandle) {
            launchedAsProfile.primary.handle -> launchedAsProfile.primary
            launchedAsProfile.clone?.handle -> requireNotNull(launchedAsProfile.clone)
            else -> error("launchedByUser must be a member of launchedByProfile")
        }
    val launchedAsProfileType: Profile.Type = launchedAsProfile.type

    val personalProfile = profiles.single { it.type == Profile.Type.PERSONAL }
    val workProfile = profiles.singleOrNull { it.type == Profile.Type.WORK }
    val privateProfile = profiles.singleOrNull { it.type == Profile.Type.PRIVATE }

    val personalHandle = personalProfile.primary.handle
    val workHandle = workProfile?.primary?.handle
    val privateHandle = privateProfile?.primary?.handle
    val cloneHandle = personalProfile.clone?.handle

    val isLaunchedAsCloneProfile = launchedByUser == launchedAsProfile.clone

    val cloneUserPresent = personalProfile.clone != null
    val workProfilePresent = workProfile != null
    val privateProfilePresent = privateProfile != null

    // Name retained for ease of review, to be renamed later
    val tabOwnerUserHandleForLaunch =
        if (launchedByUser.role == User.Role.CLONE) {
            // When started by clone user, return the profile owner instead
            launchedAsProfile.primary.handle
        } else {
            // Otherwise the launched user is used
            launchedByUser.handle
        }

    fun findProfile(handle: UserHandle): Profile? {
        return profiles.firstOrNull { it.primary.handle == handle || it.clone?.handle == handle }
    }

    fun findProfileType(handle: UserHandle): Profile.Type? = findProfile(handle)?.type

    // Name retained for ease of review, to be renamed later
    fun getQueryIntentsHandle(handle: UserHandle): UserHandle? {
        return if (isLaunchedAsCloneProfile && handle == personalHandle) {
            cloneHandle
        } else {
            handle
        }
    }
}

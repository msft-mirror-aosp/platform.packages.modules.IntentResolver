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

package com.android.intentresolver.data.repository

import android.content.pm.UserInfo
import com.android.intentresolver.shared.model.User
import com.android.intentresolver.shared.model.User.Role

/** Maps the UserInfo to one of the defined [Roles][User.Role], if possible. */
fun UserInfo.getSupportedUserRole(): Role? =
    when {
        isFull -> Role.PERSONAL
        isManagedProfile -> Role.WORK
        isCloneProfile -> Role.CLONE
        isPrivateProfile -> Role.PRIVATE
        else -> null
    }

/**
 * Creates a [User], based on values from a [UserInfo].
 *
 * ```
 * val users: List<User> =
 *     getEnabledProfiles(user).map(::toUser).filterNotNull()
 * ```
 *
 * @return a [User] if the [UserInfo] matched a supported [Role], otherwise null
 */
fun UserInfo.toUser(): User? {
    return getSupportedUserRole()?.let { role -> User(userHandle.identifier, role) }
}

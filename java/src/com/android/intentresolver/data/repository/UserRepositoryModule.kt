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

import android.content.Context
import android.os.UserHandle
import android.os.UserManager
import com.android.intentresolver.inject.ApplicationUser
import com.android.intentresolver.inject.ProfileParent
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface UserRepositoryModule {
    companion object {
        @Provides
        @Singleton
        @ApplicationUser
        fun applicationUser(@ApplicationContext context: Context): UserHandle = context.user

        @Provides
        @Singleton
        @ProfileParent
        fun profileParent(
            @ApplicationContext context: Context,
            userManager: UserManager
        ): UserHandle {
            return userManager.getProfileParent(context.user) ?: context.user
        }
    }

    @Binds @Singleton fun userRepository(impl: UserRepositoryImpl): UserRepository
}

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

package com.android.intentresolver.v2.data.repository

import android.content.Context
import android.os.UserHandle
import android.util.LruCache
import androidx.core.content.getSystemService
import javax.inject.Inject
import kotlin.reflect.KClass

/**
 * Provides cached instances of a [system service][Context.getSystemService] created with
 * [the context of a specified user][Context.createContextAsUser].
 *
 * System services which have only `@UserHandleAware` APIs operate on the user id available from
 * [Context.getUser], the context used to retrieve the service. This utility helps adapt a per-user
 * API model to work in multi-user manner.
 *
 * Example usage:
 * ```
 * val usageStats = userScopedService<UsageStatsManager>(context)
 *
 * fun getStatsForUser(
 *     user: User,
 *     from: Long,
 *     to: Long
 * ): UsageStats {
 *     return usageStats.forUser(user)
 *        .queryUsageStats(INTERVAL_BEST, from, to)
 * }
 * ```
 */
interface UserScopedService<T> {
    fun forUser(user: UserHandle): T
}

/**
 * Provides cached Context instances each distinct per-User.
 *
 * @see [UserScopedService]
 */
class UserScopedContext @Inject constructor(private val applicationContext: Context) {
    private val contextCacheSizeLimit = 8

    private val instances =
        object : LruCache<UserHandle, Context>(contextCacheSizeLimit) {
            override fun create(key: UserHandle): Context {
                return applicationContext.createContextAsUser(key, 0)
            }
        }

    fun forUser(user: UserHandle): Context {
        synchronized(this) {
            return if (applicationContext.user == user) {
                applicationContext
            } else {
                return instances[user]
            }
        }
    }
}

/** Returns a cache of service instances, distinct by user */
class UserScopedServiceImpl<T : Any>(
    contexts: UserScopedContext,
    serviceType: KClass<T>
): UserScopedService<T> {
    private val instances =
        object : LruCache<UserHandle, T>(8) {
            override fun create(key: UserHandle): T {
                val context = contexts.forUser(key)
                return requireNotNull(context.getSystemService(serviceType.java))
            }
        }

    override fun forUser(user: UserHandle): T {
        synchronized(this) {
            return instances[user]
        }
    }
}

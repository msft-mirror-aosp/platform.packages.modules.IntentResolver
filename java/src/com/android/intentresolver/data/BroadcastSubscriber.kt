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

package com.android.intentresolver.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.UserHandle
import android.util.Log
import com.android.intentresolver.inject.Broadcast
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val TAG = "BroadcastSubscriber"

class BroadcastSubscriber
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @Broadcast private val handler: Handler
) {
    /**
     * Returns a [callbackFlow] that, when collected, registers a broadcast receiver and emits a new
     * value whenever broadcast matching _filter_ is received. The result value will be computed
     * using [transform] and emitted if non-null.
     */
    fun <T> createFlow(
        filter: IntentFilter,
        user: UserHandle,
        transform: (Intent) -> T?,
    ): Flow<T> = callbackFlow {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    transform(intent)?.also { result ->
                        trySend(result).onFailure { Log.e(TAG, "Failed to send $result", it) }
                    }
                        ?: Log.w(TAG, "Ignored broadcast $intent")
                }
            }

        @Suppress("MissingPermission")
        context.registerReceiverAsUser(
            receiver,
            user,
            IntentFilter(filter),
            null,
            handler,
            Context.RECEIVER_NOT_EXPORTED
        )
        awaitClose { context.unregisterReceiver(receiver) }
    }
}

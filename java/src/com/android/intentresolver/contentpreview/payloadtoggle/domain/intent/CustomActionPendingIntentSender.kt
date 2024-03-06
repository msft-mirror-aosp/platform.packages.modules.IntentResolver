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

package com.android.intentresolver.contentpreview.payloadtoggle.domain.intent

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import com.android.intentresolver.R
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Qualifier

/** [PendingIntentSender] for Shareousel custom actions. */
class CustomActionPendingIntentSender
@Inject
constructor(
    @ApplicationContext private val context: Context,
) : PendingIntentSender {
    override fun send(pendingIntent: PendingIntent) {
        pendingIntent.send(
            /* context = */ null,
            /* code = */ 0,
            /* intent = */ null,
            /* onFinished = */ null,
            /* handler = */ null,
            /* requiredPermission = */ null,
            /* options = */ ActivityOptions.makeCustomAnimation(
                    context,
                    R.anim.slide_in_right,
                    R.anim.slide_out_left,
                )
                .toBundle()
        )
    }

    @Module
    @InstallIn(SingletonComponent::class)
    interface Binding {
        @Binds
        @CustomAction
        fun bindSender(sender: CustomActionPendingIntentSender): PendingIntentSender
    }
}

/** [PendingIntentSender] for Shareousel custom actions. */
@Qualifier @MustBeDocumented @Retention(AnnotationRetention.RUNTIME) annotation class CustomAction

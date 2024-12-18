/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.intentresolver.logging

import com.android.internal.logging.InstanceId
import com.android.internal.logging.InstanceIdSequence
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.android.components.ActivityRetainedComponent
import dagger.hilt.android.scopes.ActivityRetainedScoped
import dagger.hilt.testing.TestInstallIn

/** Binds a [FakeEventLog] as [EventLog] in tests. */
@Module
@TestInstallIn(components = [ActivityRetainedComponent::class], replaces = [EventLogModule::class])
interface TestEventLogModule {

    @Binds @ActivityRetainedScoped fun fakeEventLog(impl: FakeEventLog): EventLog

    companion object {
        @Provides
        fun instanceId(sequence: InstanceIdSequence): InstanceId = sequence.newInstanceId()
    }
}

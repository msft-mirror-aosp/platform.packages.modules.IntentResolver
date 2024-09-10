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

package com.android.intentresolver.inject

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.ViewModelLifecycle
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

@Module
@InstallIn(ViewModelComponent::class)
object ViewModelCoroutineScopeModule {
    @Provides
    @ViewModelScoped
    @ViewModelOwned
    fun viewModelScope(@Main dispatcher: CoroutineDispatcher, lifecycle: ViewModelLifecycle) =
        lifecycle.asCoroutineScope(dispatcher)
}

fun ViewModelLifecycle.asCoroutineScope(context: CoroutineContext = EmptyCoroutineContext) =
    CoroutineScope(context).also { addOnClearedListener { it.cancel() } }

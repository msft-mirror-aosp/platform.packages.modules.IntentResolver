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

package com.android.intentresolver.inject

import javax.inject.Qualifier

@Qualifier @MustBeDocumented @Retention(AnnotationRetention.RUNTIME) annotation class ActivityOwned

@Qualifier
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
annotation class ViewModelOwned

@Qualifier
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationOwned

@Qualifier
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationUser

@Qualifier @MustBeDocumented @Retention(AnnotationRetention.RUNTIME) annotation class Broadcast

@Qualifier @MustBeDocumented @Retention(AnnotationRetention.RUNTIME) annotation class ProfileParent

@Qualifier @MustBeDocumented @Retention(AnnotationRetention.RUNTIME) annotation class Background

@Qualifier @MustBeDocumented @Retention(AnnotationRetention.RUNTIME) annotation class Default

@Qualifier @MustBeDocumented @Retention(AnnotationRetention.RUNTIME) annotation class Main

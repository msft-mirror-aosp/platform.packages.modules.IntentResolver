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

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.android.intentresolver.contentpreview.payloadtoggle.domain.interactor

import android.content.ComponentName
import android.content.Intent
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import com.android.intentresolver.Flags.FLAG_SHAREOUSEL_UPDATE_EXCLUDE_COMPONENTS_EXTRA
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.pendingSelectionCallbackRepository
import com.android.intentresolver.contentpreview.payloadtoggle.domain.model.ShareouselUpdate
import com.android.intentresolver.contentpreview.payloadtoggle.domain.model.ValueUpdate
import com.android.intentresolver.contentpreview.payloadtoggle.domain.update.SelectionChangeCallback
import com.android.intentresolver.contentpreview.payloadtoggle.domain.update.selectionChangeCallback
import com.android.intentresolver.data.repository.chooserRequestRepository
import com.android.intentresolver.util.runKosmosTest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test

class UpdateChooserRequestInteractorTest {
    @get:Rule val setFlagsRule = SetFlagsRule()

    @Test
    fun updateTargetIntentWithSelection() = runKosmosTest {
        val selectionCallbackResult = ShareouselUpdate(metadataText = ValueUpdate.Value("update"))
        selectionChangeCallback = SelectionChangeCallback { selectionCallbackResult }

        backgroundScope.launch { processTargetIntentUpdatesInteractor.activate() }

        updateTargetIntentInteractor.updateTargetIntent(Intent())
        runCurrent()

        assertThat(pendingSelectionCallbackRepository.pendingTargetIntent.value).isNull()
        assertThat(chooserRequestRepository.chooserRequest.value.metadataText).isEqualTo("update")
    }

    @Test
    @EnableFlags(FLAG_SHAREOUSEL_UPDATE_EXCLUDE_COMPONENTS_EXTRA)
    fun testSelectionResultWithExcludedComponents_chooserRequestIsUpdated() = runKosmosTest {
        val excludedComponent = ComponentName("org.pkg.app", "Class")
        val selectionCallbackResult =
            ShareouselUpdate(excludeComponents = ValueUpdate.Value(listOf(excludedComponent)))
        selectionChangeCallback = SelectionChangeCallback { selectionCallbackResult }

        backgroundScope.launch { processTargetIntentUpdatesInteractor.activate() }

        updateTargetIntentInteractor.updateTargetIntent(Intent())
        runCurrent()

        assertThat(chooserRequestRepository.chooserRequest.value.filteredComponentNames)
            .containsExactly(excludedComponent)
    }
}

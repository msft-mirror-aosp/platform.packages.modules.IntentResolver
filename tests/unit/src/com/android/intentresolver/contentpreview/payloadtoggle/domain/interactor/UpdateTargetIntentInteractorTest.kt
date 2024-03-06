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

import android.content.Intent
import android.net.Uri
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.PreviewSelectionsRepository
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.TargetIntentRepository
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class UpdateTargetIntentInteractorTest {
    @Test
    fun updateTargetIntentWithSelection() = runTest {
        val initialIntent = Intent()
        val intentRepository = TargetIntentRepository(initialIntent, emptyList())
        val selectionRepository = PreviewSelectionsRepository()
        val underTest =
            UpdateTargetIntentInteractor(
                intentRepository = intentRepository,
                selectionCallback = { intent -> null },
                selectionRepo = selectionRepository,
                targetIntentModifier = { selection ->
                    Intent()
                        .putParcelableArrayListExtra(
                            "selection",
                            selection.mapTo(ArrayList()) { it.uri },
                        )
                },
                pendingIntentSender = {},
            )

        backgroundScope.launch { underTest.launch() }
        runCurrent()

        assertThat(
                intentRepository.targetIntent.value.getParcelableArrayListExtra(
                    "selection",
                    Uri::class.java,
                )
            )
            .isEmpty()

        selectionRepository.selections.value =
            setOf(
                PreviewModel(Uri.fromParts("scheme0", "ssp0", "fragment0"), null),
                PreviewModel(Uri.fromParts("scheme1", "ssp1", "fragment1"), null),
                PreviewModel(Uri.fromParts("scheme2", "ssp2", "fragment2"), null),
            )
        runCurrent()

        assertThat(
                intentRepository.targetIntent.value.getParcelableArrayListExtra(
                    "selection",
                    Uri::class.java,
                )
            )
            .containsExactly(
                Uri.fromParts("scheme0", "ssp0", "fragment0"),
                Uri.fromParts("scheme1", "ssp1", "fragment1"),
                Uri.fromParts("scheme2", "ssp2", "fragment2"),
            )
    }
}

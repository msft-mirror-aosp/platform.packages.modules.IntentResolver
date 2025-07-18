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

package com.android.intentresolver.contentpreview.payloadtoggle.domain.interactor

import android.content.Intent
import android.net.Uri
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import com.android.intentresolver.Flags
import com.android.intentresolver.contentpreview.mimetypeClassifier
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.previewSelectionsRepository
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewKey
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewModel
import com.android.intentresolver.util.runKosmosTest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import org.junit.Rule
import org.junit.Test

class SelectionInteractorTest {
    @get:Rule val flagsRule = SetFlagsRule()

    @Test
    @DisableFlags(Flags.FLAG_UNSELECT_FINAL_ITEM)
    fun singleSelection_removalPrevented() = runKosmosTest {
        val initialPreview =
            PreviewModel(
                key = PreviewKey.final(1),
                uri = Uri.fromParts("scheme", "ssp", "fragment"),
                mimeType = null,
                order = 0,
            )
        previewSelectionsRepository.selections.value = mapOf(initialPreview.uri to initialPreview)

        val underTest =
            SelectionInteractor(
                previewSelectionsRepository,
                { Intent() },
                updateTargetIntentInteractor,
                mimetypeClassifier,
            )

        assertThat(underTest.selections.first()).containsExactly(initialPreview.uri)

        // Shouldn't do anything!
        underTest.unselect(initialPreview)

        assertThat(underTest.selections.first()).containsExactly(initialPreview.uri)
    }

    @Test
    @EnableFlags(Flags.FLAG_UNSELECT_FINAL_ITEM)
    fun singleSelection_itemRemovedNoPendingIntentUpdates() = runKosmosTest {
        val initialPreview =
            PreviewModel(
                key = PreviewKey.final(1),
                uri = Uri.fromParts("scheme", "ssp", "fragment"),
                mimeType = null,
                order = 0,
            )
        previewSelectionsRepository.selections.value = mapOf(initialPreview.uri to initialPreview)

        val underTest =
            SelectionInteractor(
                previewSelectionsRepository,
                { Intent() },
                updateTargetIntentInteractor,
                mimetypeClassifier,
            )

        assertThat(underTest.selections.first()).containsExactly(initialPreview.uri)

        underTest.unselect(initialPreview)

        assertThat(underTest.selections.first()).isEmpty()
        assertThat(previewSelectionsRepository.selections.value).isEmpty()
    }

    @Test
    fun multipleSelections_removalAllowed() = runKosmosTest {
        val first =
            PreviewModel(
                key = PreviewKey.final(1),
                uri = Uri.fromParts("scheme", "ssp", "fragment"),
                mimeType = null,
                order = 0,
            )
        val second =
            PreviewModel(
                key = PreviewKey.final(2),
                uri = Uri.fromParts("scheme2", "ssp2", "fragment2"),
                mimeType = null,
                order = 1,
            )
        previewSelectionsRepository.selections.value = listOf(first, second).associateBy { it.uri }

        val underTest =
            SelectionInteractor(
                previewSelectionsRepository,
                { Intent() },
                updateTargetIntentInteractor,
                mimetypeClassifier,
            )

        underTest.unselect(first)

        assertThat(underTest.selections.first()).containsExactly(second.uri)
    }
}

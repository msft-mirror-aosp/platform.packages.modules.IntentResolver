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
import com.android.intentresolver.contentpreview.mimetypeClassifier
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.previewSelectionsRepository
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewModel
import com.android.intentresolver.util.runKosmosTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SelectionInteractorTest {
    @Test
    fun singleSelection_removalPrevented() = runKosmosTest {
        val initialPreview =
            PreviewModel(uri = Uri.fromParts("scheme", "ssp", "fragment"), mimeType = null)
        previewSelectionsRepository.selections.value = listOf(initialPreview)

        val underTest =
            SelectionInteractor(
                previewSelectionsRepository,
                { Intent() },
                updateTargetIntentInteractor,
                mimetypeClassifier,
            )

        assertThat(underTest.selections.value).containsExactly(initialPreview)

        // Shouldn't do anything!
        underTest.unselect(initialPreview)

        assertThat(underTest.selections.value).containsExactly(initialPreview)
    }

    @Test
    fun multipleSelections_removalAllowed() = runKosmosTest {
        val first = PreviewModel(uri = Uri.fromParts("scheme", "ssp", "fragment"), mimeType = null)
        val second =
            PreviewModel(uri = Uri.fromParts("scheme2", "ssp2", "fragment2"), mimeType = null)
        previewSelectionsRepository.selections.value = listOf(first, second)

        val underTest =
            SelectionInteractor(
                previewSelectionsRepository,
                { Intent() },
                updateTargetIntentInteractor,
                mimetypeClassifier
            )

        underTest.unselect(first)

        assertThat(underTest.selections.value).containsExactly(second)
    }
}

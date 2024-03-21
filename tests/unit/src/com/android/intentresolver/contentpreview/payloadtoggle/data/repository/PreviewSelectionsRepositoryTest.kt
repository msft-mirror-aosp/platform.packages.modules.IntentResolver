/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.intentresolver.contentpreview.payloadtoggle.data.repository

import android.net.Uri
import com.android.intentresolver.contentpreview.payloadtoggle.data.model.SelectionRecordType.Initial
import com.android.intentresolver.contentpreview.payloadtoggle.data.model.SelectionRecordType.Uninitialized
import com.android.intentresolver.contentpreview.payloadtoggle.data.model.SelectionRecordType.Updated
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewModel
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PreviewSelectionsRepositoryTest {

    @Test
    fun testSelectionStatus() {
        val testSubject = PreviewSelectionsRepository()

        assertThat(testSubject.selections.value.type).isEqualTo(Uninitialized)

        testSubject.setSelection(setOf(PreviewModel(Uri.parse("content://pkg/1.png"), "image/png")))

        assertThat(testSubject.selections.value.type).isEqualTo(Initial)

        testSubject.select(PreviewModel(Uri.parse("content://pkg/2.png"), "image/png"))

        assertThat(testSubject.selections.value.type).isEqualTo(Updated)
    }
}

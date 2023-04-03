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

package com.android.intentresolver.contentpreview

import android.content.res.Resources
import android.view.LayoutInflater
import android.view.ViewGroup
import com.android.intentresolver.TestFeatureFlagRepository
import com.android.intentresolver.flags.FeatureFlagRepository
import com.android.intentresolver.flags.Flags
import com.android.intentresolver.widget.ActionRow
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ContentPreviewUiTest {
    private class TestablePreview(private val flags: FeatureFlagRepository) : ContentPreviewUi() {
        override fun getType() = 0

        override fun display(
            resources: Resources?,
            layoutInflater: LayoutInflater?,
            parent: ViewGroup?
        ): ViewGroup {
            throw IllegalStateException()
        }

        // exposing for testing
        fun makeActions(
            system: List<ActionRow.Action>,
            custom: List<ActionRow.Action>
        ): List<ActionRow.Action> {
            return createActions(system, custom, flags)
        }
    }

    @Test
    fun testCreateActions() {
        val featureFlagRepository = TestFeatureFlagRepository(
            mapOf(
                Flags.SHARESHEET_CUSTOM_ACTIONS to true
            )
        )
        val preview = TestablePreview(featureFlagRepository)

        val system = listOf(ActionRow.Action(label="system", icon=null) {})
        val custom = listOf(ActionRow.Action(label="custom", icon=null) {})

        assertThat(preview.makeActions(system, custom)).isEqualTo(custom)
        assertThat(preview.makeActions(system, listOf())).isEqualTo(system)
    }

    @Test
    fun testCreateActions_flagDisabled() {
        val featureFlagRepository = TestFeatureFlagRepository(
            mapOf(
                Flags.SHARESHEET_CUSTOM_ACTIONS to false
            )
        )
        val preview = TestablePreview(featureFlagRepository)

        val system = listOf(ActionRow.Action(label="system", icon=null) {})
        val custom = listOf(ActionRow.Action(label="custom", icon=null) {})

        assertThat(preview.makeActions(system, custom)).isEqualTo(system)
    }
}
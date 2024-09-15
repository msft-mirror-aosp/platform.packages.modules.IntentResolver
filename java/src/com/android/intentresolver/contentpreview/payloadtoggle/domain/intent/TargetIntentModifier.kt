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

import android.content.ClipData
import android.content.ClipDescription.compareMimeTypes
import android.content.Intent
import android.content.Intent.ACTION_SEND
import android.content.Intent.ACTION_SEND_MULTIPLE
import android.content.Intent.EXTRA_STREAM
import android.net.Uri
import com.android.intentresolver.contentpreview.payloadtoggle.shared.model.PreviewModel
import com.android.intentresolver.inject.TargetIntent
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

/** Modifies target intent based on current payload selection. */
fun interface TargetIntentModifier<Item> {
    fun intentFromSelection(selection: Collection<Item>): Intent
}

class TargetIntentModifierImpl<Item>(
    private val originalTargetIntent: Intent,
    private val getUri: Item.() -> Uri,
    private val getMimeType: Item.() -> String?,
) : TargetIntentModifier<Item> {
    override fun intentFromSelection(selection: Collection<Item>): Intent {
        val uris = selection.mapTo(ArrayList()) { it.getUri() }
        val targetMimeType =
            selection.fold(null) { target: String?, item: Item ->
                updateMimeType(item.getMimeType(), target)
            }
        return Intent(originalTargetIntent).apply {
            if (selection.size == 1) {
                action = ACTION_SEND
                putExtra(EXTRA_STREAM, selection.first().getUri())
            } else {
                action = ACTION_SEND_MULTIPLE
                putParcelableArrayListExtra(EXTRA_STREAM, uris)
            }
            type = targetMimeType
            if (uris.isNotEmpty()) {
                clipData =
                    ClipData("", arrayOf(targetMimeType), ClipData.Item(uris[0])).also {
                        for (i in 1 until uris.size) {
                            it.addItem(ClipData.Item(uris[i]))
                        }
                    }
            }
        }
    }

    private fun updateMimeType(itemMimeType: String?, unitedMimeType: String?): String {
        itemMimeType ?: return "*/*"
        unitedMimeType ?: return itemMimeType
        if (compareMimeTypes(itemMimeType, unitedMimeType)) return unitedMimeType
        val slashIdx = unitedMimeType.indexOf('/')
        if (slashIdx >= 0 && unitedMimeType.regionMatches(0, itemMimeType, 0, slashIdx + 1)) {
            return buildString {
                append(unitedMimeType.substring(0, slashIdx + 1))
                append('*')
            }
        }
        return "*/*"
    }
}

@Module
@InstallIn(ViewModelComponent::class)
object TargetIntentModifierModule {
    @Provides
    fun targetIntentModifier(
        @TargetIntent targetIntent: Intent,
    ): TargetIntentModifier<PreviewModel> =
        TargetIntentModifierImpl(targetIntent, { uri }, { mimeType })
}

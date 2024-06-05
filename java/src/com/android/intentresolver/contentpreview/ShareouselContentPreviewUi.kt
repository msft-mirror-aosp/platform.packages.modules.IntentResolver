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
package com.android.intentresolver.contentpreview

import android.content.res.Resources
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.intentresolver.R
import com.android.intentresolver.contentpreview.payloadtoggle.ui.composable.Shareousel
import com.android.intentresolver.contentpreview.payloadtoggle.ui.viewmodel.ShareouselViewModel
import com.android.intentresolver.ui.viewmodel.ChooserViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
class ShareouselContentPreviewUi : ContentPreviewUi() {

    override fun getType(): Int = ContentPreviewType.CONTENT_PREVIEW_IMAGE

    override fun display(
        resources: Resources,
        layoutInflater: LayoutInflater,
        parent: ViewGroup,
        headlineViewParent: View,
    ): ViewGroup = displayInternal(parent, headlineViewParent)

    private fun displayInternal(parent: ViewGroup, headlineViewParent: View): ViewGroup {
        inflateHeadline(headlineViewParent)
        return ComposeView(parent.context).apply {
            setContent {
                val vm: ChooserViewModel = viewModel()
                val viewModel: ShareouselViewModel = vm.shareouselViewModel

                LaunchedEffect(viewModel) { bindHeader(viewModel, headlineViewParent) }

                MaterialTheme(
                    colorScheme =
                        if (isSystemInDarkTheme()) {
                            dynamicDarkColorScheme(LocalContext.current)
                        } else {
                            dynamicLightColorScheme(LocalContext.current)
                        },
                ) {
                    Shareousel(viewModel)
                }
            }
        }
    }

    private suspend fun bindHeader(viewModel: ShareouselViewModel, headlineViewParent: View) {
        coroutineScope {
            launch { bindHeadline(viewModel, headlineViewParent) }
            launch { bindMetadataText(viewModel, headlineViewParent) }
        }
    }

    private suspend fun bindHeadline(viewModel: ShareouselViewModel, headlineViewParent: View) {
        viewModel.headline.collect { headline ->
            headlineViewParent.findViewById<TextView>(R.id.headline)?.apply {
                if (headline.isNotBlank()) {
                    text = headline
                    visibility = View.VISIBLE
                } else {
                    visibility = View.GONE
                }
            }
        }
    }

    private suspend fun bindMetadataText(viewModel: ShareouselViewModel, headlineViewParent: View) {
        viewModel.metadataText.collect { metadata ->
            headlineViewParent.findViewById<TextView>(R.id.metadata)?.apply {
                if (metadata?.isNotBlank() == true) {
                    text = metadata
                    visibility = View.VISIBLE
                } else {
                    visibility = View.GONE
                }
            }
        }
    }
}

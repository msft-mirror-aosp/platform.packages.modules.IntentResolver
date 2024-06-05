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
package com.android.intentresolver.contentpreview.payloadtoggle.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.android.intentresolver.R
import com.android.intentresolver.contentpreview.payloadtoggle.shared.ContentType

@Composable
fun ShareouselCard(
    image: @Composable () -> Unit,
    contentType: ContentType,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        image()
        val topButtonPadding = 12.dp
        Box(modifier = Modifier.padding(topButtonPadding).matchParentSize()) {
            SelectionIcon(selected, modifier = Modifier.align(Alignment.TopStart))
            when (contentType) {
                ContentType.Video ->
                    TypeIcon(
                        R.drawable.ic_play_circle_filled_24px,
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                ContentType.Other ->
                    TypeIcon(
                        R.drawable.chooser_file_generic,
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                ContentType.Image -> Unit // No additional icon needed.
            }
        }
    }
}

@Composable
private fun TypeIcon(drawableResource: Int, modifier: Modifier = Modifier) {
    Icon(
        painterResource(id = drawableResource),
        contentDescription = null, // Type attribute described at a higher level.
        tint = Color.White,
        modifier = Modifier.size(20.dp).then(modifier)
    )
}

@Composable
private fun SelectionIcon(selected: Boolean, modifier: Modifier = Modifier) {
    if (selected) {
        val bgColor = MaterialTheme.colorScheme.primary
        Icon(
            painter = painterResource(id = R.drawable.checkbox),
            tint = MaterialTheme.colorScheme.onPrimary,
            contentDescription = null,
            modifier =
                Modifier.shadow(
                        elevation = 50.dp,
                        spotColor = Color(0x40000000),
                        ambientColor = Color(0x40000000)
                    )
                    .size(20.dp)
                    .drawBehind {
                        drawCircle(color = bgColor, radius = (this.size.width / 2f) - 1f)
                    }
                    .then(modifier)
        )
    } else {
        Box(
            modifier =
                Modifier.shadow(
                        elevation = 50.dp,
                        spotColor = Color(0x40000000),
                        ambientColor = Color(0x40000000),
                    )
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        shape = CircleShape
                    )
                    .clip(CircleShape)
                    .size(20.dp)
                    .background(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f))
                    .then(modifier)
        )
    }
}

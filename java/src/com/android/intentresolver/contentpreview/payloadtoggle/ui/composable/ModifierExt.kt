/*
 * Copyright (C) 2025 The Android Open Source Project
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

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Calls [whenTrue] on this [Modifier] if [condition] is true. */
@Composable
inline fun Modifier.conditional(
    condition: Boolean,
    crossinline whenTrue: @Composable Modifier.() -> Modifier,
): Modifier = if (condition) this.whenTrue() else this

/**
 * Overlays tap regions at the beginning and end of the scrollable region.
 *
 * When tap regions are tapped, [scrollableState] will be scrolled by the size of the modified
 * scrollable multiplied by the [scrollRatio].
 *
 * Note: [scrollableState] must be shared with the scrollable being modified and [vertical] must be
 * true only if the modified scrollable scrolls vertically.
 */
@Composable
fun Modifier.tapToScroll(
    scrollableState: ScrollableState,
    vertical: Boolean = false,
    tapRegionSize: Dp = 48.dp,
    scrollRatio: Float = 0.5f,
): Modifier {
    val scope = rememberCoroutineScope()
    var viewSize by remember { mutableStateOf(0) }
    val isLtrLayoutDirection = LocalLayoutDirection.current == LayoutDirection.Ltr
    val normalizedScrollVector = remember {
        derivedStateOf {
            if (vertical || isLtrLayoutDirection) {
                viewSize * scrollRatio
            } else {
                -viewSize * scrollRatio
            }
        }
    }
    return onGloballyPositioned { viewSize = if (vertical) it.size.height else it.size.width }
        .pointerInput(Unit) {
            val tapRegionSizePx = tapRegionSize.roundToPx()

            awaitEachGesture {
                // Tap to scroll is disabled if the modified composable is not large enough to fit
                // both tap regions.
                if (viewSize < tapRegionSizePx * 2) return@awaitEachGesture

                val down = awaitFirstDown(pass = PointerEventPass.Initial)
                if (down.isConsumed) return@awaitEachGesture

                val downPosition = if (vertical) down.position.y else down.position.x
                val scrollVector =
                    when {
                        // Start taps scroll toward start
                        downPosition <= tapRegionSizePx -> -normalizedScrollVector.value

                        // End taps scroll toward end
                        downPosition >= viewSize - tapRegionSizePx -> normalizedScrollVector.value

                        // Middle taps are ignored
                        else -> return@awaitEachGesture
                    }

                val up =
                    waitForUpOrCancellation(pass = PointerEventPass.Initial)
                        ?: return@awaitEachGesture

                // Long presses are ignored
                if (up.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis)
                    return@awaitEachGesture

                // Swipes are ignored
                if ((up.position - down.position).getDistance() >= viewConfiguration.touchSlop)
                    return@awaitEachGesture

                down.consume()
                up.consume()
                scope.launch { scrollableState.animateScrollBy(scrollVector) }
            }
        }
}

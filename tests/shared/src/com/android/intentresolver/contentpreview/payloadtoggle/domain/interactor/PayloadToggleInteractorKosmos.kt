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

import com.android.intentresolver.backgroundDispatcher
import com.android.intentresolver.contentResolver
import com.android.intentresolver.contentpreview.HeadlineGenerator
import com.android.intentresolver.contentpreview.ImageLoader
import com.android.intentresolver.contentpreview.mimetypeClassifier
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.activityResultRepository
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.cursorPreviewsRepository
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.pendingSelectionCallbackRepository
import com.android.intentresolver.contentpreview.payloadtoggle.data.repository.previewSelectionsRepository
import com.android.intentresolver.contentpreview.payloadtoggle.domain.cursor.payloadToggleCursorResolver
import com.android.intentresolver.contentpreview.payloadtoggle.domain.intent.pendingIntentSender
import com.android.intentresolver.contentpreview.payloadtoggle.domain.intent.targetIntentModifier
import com.android.intentresolver.contentpreview.payloadtoggle.domain.update.selectionChangeCallback
import com.android.intentresolver.contentpreview.uriMetadataReader
import com.android.intentresolver.data.repository.chooserRequestRepository
import com.android.intentresolver.inject.contentUris
import com.android.intentresolver.logging.eventLog
import com.android.intentresolver.packageManager
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture

var Kosmos.focusedItemIndex: Int by Fixture { 0 }
var Kosmos.pageSize: Int by Fixture { 16 }
var Kosmos.maxLoadedPages: Int by Fixture { 3 }

val Kosmos.chooserRequestInteractor
    get() = ChooserRequestInteractor(chooserRequestRepository)

val Kosmos.cursorPreviewsInteractor
    get() =
        CursorPreviewsInteractor(
            interactor = setCursorPreviewsInteractor,
            selectionInteractor = selectionInteractor,
            focusedItemIdx = focusedItemIndex,
            uriMetadataReader = uriMetadataReader,
            pageSize = pageSize,
            maxLoadedPages = maxLoadedPages,
        )

val Kosmos.customActionsInteractor
    get() =
        CustomActionsInteractor(
            activityResultRepo = activityResultRepository,
            bgDispatcher = backgroundDispatcher,
            contentResolver = contentResolver,
            eventLog = eventLog,
            packageManager = packageManager,
            chooserRequestInteractor = chooserRequestInteractor,
        )

val Kosmos.fetchPreviewsInteractor
    get() =
        FetchPreviewsInteractor(
            setCursorPreviews = setCursorPreviewsInteractor,
            selectionRepository = previewSelectionsRepository,
            cursorInteractor = cursorPreviewsInteractor,
            focusedItemIdx = focusedItemIndex,
            selectedItems = contentUris,
            uriMetadataReader = uriMetadataReader,
            cursorResolver = payloadToggleCursorResolver,
        )

val Kosmos.processTargetIntentUpdatesInteractor
    get() =
        ProcessTargetIntentUpdatesInteractor(
            selectionCallback = selectionChangeCallback,
            repository = pendingSelectionCallbackRepository,
            chooserRequestInteractor = updateChooserRequestInteractor,
        )

val Kosmos.selectablePreviewsInteractor
    get() =
        SelectablePreviewsInteractor(
            previewsRepo = cursorPreviewsRepository,
            selectionInteractor = selectionInteractor,
            eventLog = eventLog,
        )

val Kosmos.selectionInteractor
    get() =
        SelectionInteractor(
            selectionsRepo = previewSelectionsRepository,
            targetIntentModifier = targetIntentModifier,
            updateTargetIntentInteractor = updateTargetIntentInteractor,
            mimeTypeClassifier = mimetypeClassifier,
        )

val Kosmos.setCursorPreviewsInteractor
    get() = SetCursorPreviewsInteractor(previewsRepo = cursorPreviewsRepository)

val Kosmos.updateChooserRequestInteractor
    get() =
        UpdateChooserRequestInteractor(
            chooserRequestRepository,
            pendingIntentSender,
        )

val Kosmos.updateTargetIntentInteractor
    get() =
        UpdateTargetIntentInteractor(
            repository = pendingSelectionCallbackRepository,
            chooserRequestInteractor = updateChooserRequestInteractor,
        )

var Kosmos.payloadToggleImageLoader: ImageLoader by Fixture()
var Kosmos.headlineGenerator: HeadlineGenerator by Fixture()

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

package com.android.intentresolver.contentpreview.payloadtoggle.domain.cursor

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.service.chooser.AdditionalContentContract.Columns.URI
import androidx.core.os.bundleOf
import com.android.intentresolver.inject.AdditionalContent
import com.android.intentresolver.inject.ChooserIntent
import com.android.intentresolver.util.cursor.CursorView
import com.android.intentresolver.util.cursor.viewBy
import com.android.intentresolver.util.withCancellationSignal
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import javax.inject.Inject
import javax.inject.Qualifier

/** [CursorResolver] for the [CursorView] underpinning Shareousel. */
class PayloadToggleCursorResolver
@Inject
constructor(
    private val contentResolver: ContentResolver,
    @AdditionalContent private val cursorUri: Uri,
    @ChooserIntent private val chooserIntent: Intent,
) : CursorResolver<Uri?> {
    override suspend fun getCursor(): CursorView<Uri?>? = withCancellationSignal { signal ->
        runCatching {
                contentResolver.query(
                    cursorUri,
                    arrayOf(URI),
                    bundleOf(Intent.EXTRA_INTENT to chooserIntent),
                    signal,
                )
            }
            .getOrNull()
            ?.viewBy {
                getString(0)?.let(Uri::parse)?.takeIf { it.authority != cursorUri.authority }
            }
    }

    @Module
    @InstallIn(ViewModelComponent::class)
    interface Binding {
        @Binds
        @PayloadToggle
        fun bind(cursorResolver: PayloadToggleCursorResolver): CursorResolver<Uri?>
    }
}

/** [CursorResolver] for the [CursorView] underpinning Shareousel. */
@Qualifier @MustBeDocumented @Retention(AnnotationRetention.RUNTIME) annotation class PayloadToggle

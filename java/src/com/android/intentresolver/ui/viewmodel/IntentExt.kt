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
package com.android.intentresolver.ui.viewmodel

import android.content.Intent
import android.content.IntentFilter
import android.content.IntentFilter.MalformedMimeTypeException
import android.net.Uri
import android.os.PatternMatcher

/** Collects Uris from standard locations within the Intent. */
fun Intent.collectUris(): Set<Uri> = buildSet {
    data?.also { add(it) }
    @Suppress("DEPRECATION")
    when (val stream = extras?.get(Intent.EXTRA_STREAM)) {
        is Uri -> add(stream)
        is ArrayList<*> -> addAll(stream.mapNotNull { it as? Uri })
        else -> Unit
    }
    clipData?.apply { (0..<itemCount).mapNotNull { getItemAt(it).uri }.forEach(::add) }
}

fun IntentFilter.addUri(uri: Uri) {
    uri.scheme?.also { addDataScheme(it) }
    uri.host?.also { addDataAuthority(it, null) }
    uri.path?.also { addDataPath(it, PatternMatcher.PATTERN_LITERAL) }
}

fun Intent.createIntentFilter(): IntentFilter? {
    val uris = collectUris()
    if (action == null && uris.isEmpty()) {
        // at least one is required to be meaningful
        return null
    }
    return IntentFilter().also { filter ->
        type?.also {
            try {
                filter.addDataType(it)
            } catch (_: MalformedMimeTypeException) { // ignore malformed type
            }
        }
        action?.also { filter.addAction(it) }
        uris.forEach(filter::addUri)
    }
}

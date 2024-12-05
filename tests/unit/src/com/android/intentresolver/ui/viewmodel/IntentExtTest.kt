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
import android.content.Intent.ACTION_SEND
import android.content.Intent.EXTRA_STREAM
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IntentExtTest {

    @Test
    fun noActionOrUris() {
        val intent = Intent()

        assertThat(intent.createIntentFilter()).isNull()
    }

    @Test
    fun uriInData() {
        val intent = Intent(ACTION_SEND)
        intent.setDataAndType(
            Uri.Builder().scheme("scheme1").encodedAuthority("auth1").path("path1").build(),
            "image/png",
        )

        val filter = intent.createIntentFilter()

        assertThat(filter).isNotNull()
        assertThat(filter!!.dataTypes()[0]).isEqualTo("image/png")
        assertThat(filter.actionsIterator().next()).isEqualTo(ACTION_SEND)
        assertThat(filter.schemesIterator().next()).isEqualTo("scheme1")
        assertThat(filter.authoritiesIterator().next().host).isEqualTo("auth1")
        assertThat(filter.getDataPath(0).path).isEqualTo("/path1")
    }

    @Test
    fun noAction() {
        val intent = Intent()
        intent.setDataAndType(
            Uri.Builder().scheme("scheme1").encodedAuthority("auth1").path("path1").build(),
            "image/png",
        )

        val filter = intent.createIntentFilter()

        assertThat(filter).isNotNull()
        assertThat(filter!!.dataTypes()[0]).isEqualTo("image/png")
        assertThat(filter.countActions()).isEqualTo(0)
        assertThat(filter.schemesIterator().next()).isEqualTo("scheme1")
        assertThat(filter.authoritiesIterator().next().host).isEqualTo("auth1")
        assertThat(filter.getDataPath(0).path).isEqualTo("/path1")
    }

    @Test
    fun singleUriInExtraStream() {
        val intent = Intent(ACTION_SEND)
        intent.type = "image/png"
        intent.putExtra(
            EXTRA_STREAM,
            Uri.Builder().scheme("scheme1").encodedAuthority("auth1").path("path1").build(),
        )

        val filter = intent.createIntentFilter()

        assertThat(filter).isNotNull()
        assertThat(filter!!.dataTypes()[0]).isEqualTo("image/png")
        assertThat(filter.actionsIterator().next()).isEqualTo(ACTION_SEND)
        assertThat(filter.schemesIterator().next()).isEqualTo("scheme1")
        assertThat(filter.authoritiesIterator().next().host).isEqualTo("auth1")
        assertThat(filter.getDataPath(0).path).isEqualTo("/path1")
    }

    @Test
    fun uriInDataAndStream() {
        val intent = Intent(ACTION_SEND)
        intent.setDataAndType(
            Uri.Builder().scheme("scheme1").encodedAuthority("auth1").path("path1").build(),
            "image/png",
        )

        intent.putExtra(
            EXTRA_STREAM,
            Uri.Builder().scheme("scheme2").encodedAuthority("auth2").path("path2").build(),
        )
        val filter = intent.createIntentFilter()

        assertThat(filter).isNotNull()
        assertThat(filter!!.dataTypes()[0]).isEqualTo("image/png")
        assertThat(filter.actionsIterator().next()).isEqualTo(ACTION_SEND)
        assertThat(filter.getDataScheme(0)).isEqualTo("scheme1")
        assertThat(filter.getDataScheme(1)).isEqualTo("scheme2")
        assertThat(filter.getDataAuthority(0).host).isEqualTo("auth1")
        assertThat(filter.getDataAuthority(1).host).isEqualTo("auth2")
        assertThat(filter.getDataPath(0).path).isEqualTo("/path1")
        assertThat(filter.getDataPath(1).path).isEqualTo("/path2")
    }

    @Test
    fun multipleUris() {
        val intent = Intent(ACTION_SEND)
        intent.type = "image/png"
        val uris =
            arrayListOf(
                Uri.Builder().scheme("scheme1").encodedAuthority("auth1").path("path1").build(),
                Uri.Builder().scheme("scheme2").encodedAuthority("auth2").path("path2").build(),
            )
        intent.putExtra(EXTRA_STREAM, uris)

        val filter = intent.createIntentFilter()

        assertThat(filter).isNotNull()
        assertThat(filter!!.dataTypes()[0]).isEqualTo("image/png")
        assertThat(filter.actionsIterator().next()).isEqualTo(ACTION_SEND)
        assertThat(filter.getDataScheme(0)).isEqualTo("scheme1")
        assertThat(filter.getDataScheme(1)).isEqualTo("scheme2")
        assertThat(filter.getDataAuthority(0).host).isEqualTo("auth1")
        assertThat(filter.getDataAuthority(1).host).isEqualTo("auth2")
        assertThat(filter.getDataPath(0).path).isEqualTo("/path1")
        assertThat(filter.getDataPath(1).path).isEqualTo("/path2")
    }

    @Test
    fun multipleUrisWithNullValues() {
        val intent = Intent(ACTION_SEND)
        intent.type = "image/png"
        val uris =
            arrayListOf(
                null,
                Uri.Builder().scheme("scheme1").encodedAuthority("auth1").path("path1").build(),
                null,
            )
        intent.putExtra(EXTRA_STREAM, uris)

        val filter = intent.createIntentFilter()

        assertThat(filter).isNotNull()
        assertThat(filter!!.dataTypes()[0]).isEqualTo("image/png")
        assertThat(filter.actionsIterator().next()).isEqualTo(ACTION_SEND)
        assertThat(filter.getDataScheme(0)).isEqualTo("scheme1")
        assertThat(filter.getDataAuthority(0).host).isEqualTo("auth1")
        assertThat(filter.getDataPath(0).path).isEqualTo("/path1")
    }

    @Test
    fun badMimeType() {
        val intent = Intent(ACTION_SEND)
        intent.type = "badType"
        intent.putExtra(
            EXTRA_STREAM,
            Uri.Builder().scheme("scheme1").encodedAuthority("authority1").path("path1").build(),
        )

        val filter = intent.createIntentFilter()

        assertThat(filter).isNotNull()
        assertThat(filter!!.countDataTypes()).isEqualTo(0)
    }
}

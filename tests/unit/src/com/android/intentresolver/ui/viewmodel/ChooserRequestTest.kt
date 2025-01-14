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
import android.content.Intent.ACTION_CHOOSER
import android.content.Intent.ACTION_SEND
import android.content.Intent.ACTION_SEND_MULTIPLE
import android.content.Intent.ACTION_VIEW
import android.content.Intent.EXTRA_ALTERNATE_INTENTS
import android.content.Intent.EXTRA_CHOOSER_ADDITIONAL_CONTENT_URI
import android.content.Intent.EXTRA_CHOOSER_FOCUSED_ITEM_POSITION
import android.content.Intent.EXTRA_INTENT
import android.content.Intent.EXTRA_REFERRER
import android.content.Intent.EXTRA_TEXT
import android.content.Intent.EXTRA_TITLE
import android.net.Uri
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import com.android.intentresolver.ContentTypeHint
import com.android.intentresolver.data.model.ChooserRequest
import com.android.intentresolver.shared.model.ActivityModel
import com.android.intentresolver.validation.Importance
import com.android.intentresolver.validation.Invalid
import com.android.intentresolver.validation.NoValue
import com.android.intentresolver.validation.Valid
import com.android.systemui.shared.Flags
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

private fun createActivityModel(
    targetIntent: Intent?,
    referrer: Uri? = null,
    additionalIntents: List<Intent>? = null,
    launchedFromPackage: String = "com.android.example",
) =
    ActivityModel(
        Intent(ACTION_CHOOSER).apply {
            targetIntent?.also { putExtra(EXTRA_INTENT, it) }
            additionalIntents?.also { putExtra(EXTRA_ALTERNATE_INTENTS, it.toTypedArray()) }
        },
        launchedFromUid = 10000,
        launchedFromPackage = launchedFromPackage,
        referrer = referrer ?: "android-app://$launchedFromPackage".toUri(),
        false,
    )

class ChooserRequestTest {
    @get:Rule val flagsRule = SetFlagsRule()

    @Test
    fun missingIntent() {
        val model = createActivityModel(targetIntent = null)
        val result = readChooserRequest(model)

        assertThat(result).isInstanceOf(Invalid::class.java)
        result as Invalid<ChooserRequest>

        assertThat(result.errors)
            .containsExactly(NoValue(EXTRA_INTENT, Importance.CRITICAL, Intent::class))
    }

    @Test
    fun referrerFillIn() {
        val referrer = Uri.parse("android-app://example.com")
        val model = createActivityModel(targetIntent = Intent(ACTION_SEND), referrer)
        model.intent.putExtras(bundleOf(EXTRA_REFERRER to referrer))

        val result = readChooserRequest(model)

        assertThat(result).isInstanceOf(Valid::class.java)
        result as Valid<ChooserRequest>

        val fillIn = result.value.getReferrerFillInIntent()
        assertThat(fillIn.hasExtra(EXTRA_REFERRER)).isTrue()
        assertThat(fillIn.getParcelableExtra(EXTRA_REFERRER, Uri::class.java)).isEqualTo(referrer)
    }

    @Test
    fun referrerPackage_isNullWithNonAppReferrer() {
        val referrer = Uri.parse("http://example.com")
        val intent = Intent().putExtras(bundleOf(EXTRA_INTENT to Intent(ACTION_SEND)))

        val model = createActivityModel(targetIntent = intent, referrer = referrer)

        val result = readChooserRequest(model)

        assertThat(result).isInstanceOf(Valid::class.java)
        result as Valid<ChooserRequest>

        assertThat(result.value.referrerPackage).isNull()
    }

    @Test
    fun referrerPackage_fromAppReferrer() {
        val referrer = Uri.parse("android-app://example.com")
        val model = createActivityModel(targetIntent = Intent(ACTION_SEND), referrer)

        model.intent.putExtras(bundleOf(EXTRA_REFERRER to referrer))

        val result = readChooserRequest(model)

        assertThat(result).isInstanceOf(Valid::class.java)
        result as Valid<ChooserRequest>

        assertThat(result.value.referrerPackage).isEqualTo(referrer.authority)
    }

    @Test
    fun payloadIntents_includesTargetThenAdditional() {
        val intent1 = Intent(ACTION_SEND)
        val intent2 = Intent(ACTION_SEND_MULTIPLE)
        val model = createActivityModel(targetIntent = intent1, additionalIntents = listOf(intent2))

        val result = readChooserRequest(model)

        assertThat(result).isInstanceOf(Valid::class.java)
        result as Valid<ChooserRequest>

        assertThat(result.value.payloadIntents).containsExactly(intent1, intent2)
    }

    @Test
    fun testRequest_withOnlyRequiredValues() {
        val intent = Intent().putExtras(bundleOf(EXTRA_INTENT to Intent(ACTION_SEND)))
        val model = createActivityModel(targetIntent = intent)

        val result = readChooserRequest(model)

        assertThat(result).isInstanceOf(Valid::class.java)
        result as Valid<ChooserRequest>

        assertThat(result.value.launchedFromPackage).isEqualTo(model.launchedFromPackage)
    }

    @Test
    fun testRequest_actionSendWithAdditionalContentUri() {
        val uri = Uri.parse("content://org.pkg/path")
        val position = 10
        val model =
            createActivityModel(targetIntent = Intent(ACTION_SEND)).apply {
                intent.putExtra(EXTRA_CHOOSER_ADDITIONAL_CONTENT_URI, uri)
                intent.putExtra(EXTRA_CHOOSER_FOCUSED_ITEM_POSITION, position)
            }

        val result = readChooserRequest(model)

        assertThat(result).isInstanceOf(Valid::class.java)
        result as Valid<ChooserRequest>

        assertThat(result.value.additionalContentUri).isEqualTo(uri)
        assertThat(result.value.focusedItemPosition).isEqualTo(position)
    }

    @Test
    fun testRequest_actionSendWithInvalidAdditionalContentUri() {
        val model =
            createActivityModel(targetIntent = Intent(ACTION_SEND)).apply {
                intent.putExtra(EXTRA_CHOOSER_ADDITIONAL_CONTENT_URI, "__invalid__")
                intent.putExtra(EXTRA_CHOOSER_FOCUSED_ITEM_POSITION, "__invalid__")
            }

        val result = readChooserRequest(model)

        assertThat(result).isInstanceOf(Valid::class.java)
        result as Valid<ChooserRequest>

        assertThat(result.value.additionalContentUri).isNull()
        assertThat(result.value.focusedItemPosition).isEqualTo(0)
    }

    @Test
    fun testRequest_actionSendWithoutAdditionalContentUri() {
        val model = createActivityModel(targetIntent = Intent(ACTION_SEND))

        val result = readChooserRequest(model)

        assertThat(result).isInstanceOf(Valid::class.java)
        result as Valid<ChooserRequest>

        assertThat(result.value.additionalContentUri).isNull()
        assertThat(result.value.focusedItemPosition).isEqualTo(0)
    }

    @Test
    fun testRequest_actionViewWithAdditionalContentUri() {
        val uri = Uri.parse("content://org.pkg/path")
        val position = 10
        val model =
            createActivityModel(targetIntent = Intent(ACTION_VIEW)).apply {
                intent.putExtra(EXTRA_CHOOSER_ADDITIONAL_CONTENT_URI, uri)
                intent.putExtra(EXTRA_CHOOSER_FOCUSED_ITEM_POSITION, position)
            }

        val result = readChooserRequest(model)

        assertThat(result).isInstanceOf(Valid::class.java)
        result as Valid<ChooserRequest>

        assertThat(result.value.additionalContentUri).isNull()
        assertThat(result.value.focusedItemPosition).isEqualTo(0)
        assertThat(result.warnings).isEmpty()
    }

    @Test
    fun testAlbumType() {
        val model = createActivityModel(Intent(ACTION_SEND))
        model.intent.putExtra(
            Intent.EXTRA_CHOOSER_CONTENT_TYPE_HINT,
            Intent.CHOOSER_CONTENT_TYPE_ALBUM,
        )

        val result = readChooserRequest(model)

        assertThat(result).isInstanceOf(Valid::class.java)
        result as Valid<ChooserRequest>

        assertThat(result.value.contentTypeHint).isEqualTo(ContentTypeHint.ALBUM)
        assertThat(result.warnings).isEmpty()
    }

    @Test
    fun metadataText_isPassedText() {
        // Arrange
        val metadataText: CharSequence = "Test metadata text"
        val model =
            createActivityModel(targetIntent = Intent()).apply {
                intent.putExtra(Intent.EXTRA_METADATA_TEXT, metadataText)
            }

        val result = readChooserRequest(model)

        assertThat(result).isInstanceOf(Valid::class.java)
        result as Valid<ChooserRequest>

        assertThat(result.value.metadataText).isEqualTo(metadataText)
    }

    @Test
    fun textSharedTextAndTitle() {
        val text: CharSequence = "Shared text"
        val title: CharSequence = "Title"
        val targetIntent =
            Intent().apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_TEXT, text)
            }
        val model = createActivityModel(targetIntent)

        val result = readChooserRequest(model)

        assertThat(result).isInstanceOf(Valid::class.java)
        (result as Valid<ChooserRequest>).value.let { request ->
            assertThat(request.sharedText).isEqualTo(text)
            assertThat(request.sharedTextTitle).isEqualTo(title)
        }
    }

    @Test
    @DisableFlags(Flags.FLAG_SCREENSHOT_CONTEXT_URL)
    fun testCallerAllowsTextToggle_flagOff() {
        val intent = Intent().putExtras(bundleOf(EXTRA_INTENT to Intent(ACTION_SEND)))
        val model =
            createActivityModel(targetIntent = intent, launchedFromPackage = "com.android.systemui")
        val result = readChooserRequest(model)

        assertThat(result).isInstanceOf(Valid::class.java)
        result as Valid<ChooserRequest>

        assertThat(result.value.callerAllowsTextToggle).isFalse()
    }

    @Test
    @EnableFlags(Flags.FLAG_SCREENSHOT_CONTEXT_URL)
    fun testCallerAllowsTextToggle_sysuiPackage() {
        val intent = Intent().putExtras(bundleOf(EXTRA_INTENT to Intent(ACTION_SEND)))
        val model =
            createActivityModel(targetIntent = intent, launchedFromPackage = "com.android.systemui")
        val result = readChooserRequest(model)

        assertThat(result).isInstanceOf(Valid::class.java)
        result as Valid<ChooserRequest>

        assertThat(result.value.callerAllowsTextToggle).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_SCREENSHOT_CONTEXT_URL)
    fun testCallerAllowsTextToggle_otherPackage() {
        val intent = Intent().putExtras(bundleOf(EXTRA_INTENT to Intent(ACTION_SEND)))
        val model =
            createActivityModel(targetIntent = intent, launchedFromPackage = "com.hello.world")
        val result = readChooserRequest(model)

        assertThat(result).isInstanceOf(Valid::class.java)
        result as Valid<ChooserRequest>

        assertThat(result.value.callerAllowsTextToggle).isFalse()
    }
}

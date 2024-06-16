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
package com.android.intentresolver.data.repository

import android.app.admin.DevicePolicyManager
import android.app.admin.DevicePolicyResources.Strings.Core.FORWARD_INTENT_TO_PERSONAL
import android.app.admin.DevicePolicyResources.Strings.Core.FORWARD_INTENT_TO_WORK
import android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_CANT_ACCESS_PERSONAL
import android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_CANT_ACCESS_WORK
import android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_CANT_SHARE_WITH_PERSONAL
import android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_CANT_SHARE_WITH_WORK
import android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_CROSS_PROFILE_BLOCKED_TITLE
import android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_NO_PERSONAL_APPS
import android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_NO_WORK_APPS
import android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_PERSONAL_TAB
import android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_PERSONAL_TAB_ACCESSIBILITY
import android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_WORK_PROFILE_NOT_SUPPORTED
import android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_WORK_TAB
import android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_WORK_TAB_ACCESSIBILITY
import android.content.res.Resources
import androidx.annotation.OpenForTesting
import com.android.intentresolver.R
import com.android.intentresolver.inject.ApplicationOwned
import javax.inject.Inject
import javax.inject.Singleton

@OpenForTesting
@Singleton
open class DevicePolicyResources
@Inject
constructor(
    @ApplicationOwned private val resources: Resources,
    devicePolicyManager: DevicePolicyManager,
) {
    private val policyResources = devicePolicyManager.resources

    val personalTabLabel by lazy {
        requireNotNull(
            policyResources.getString(RESOLVER_PERSONAL_TAB) {
                resources.getString(R.string.resolver_personal_tab)
            }
        )
    }

    val workTabLabel by lazy {
        requireNotNull(
            policyResources.getString(RESOLVER_WORK_TAB) {
                resources.getString(R.string.resolver_work_tab)
            }
        )
    }

    val personalTabAccessibilityLabel by lazy {
        requireNotNull(
            policyResources.getString(RESOLVER_PERSONAL_TAB_ACCESSIBILITY) {
                resources.getString(R.string.resolver_personal_tab_accessibility)
            }
        )
    }

    val workTabAccessibilityLabel by lazy {
        requireNotNull(
            policyResources.getString(RESOLVER_WORK_TAB_ACCESSIBILITY) {
                resources.getString(R.string.resolver_work_tab_accessibility)
            }
        )
    }

    val forwardToPersonalMessage: String? =
        devicePolicyManager.resources.getString(FORWARD_INTENT_TO_PERSONAL) {
            resources.getString(R.string.forward_intent_to_owner)
        }

    val forwardToWorkMessage by lazy {
        requireNotNull(
            policyResources.getString(FORWARD_INTENT_TO_WORK) {
                resources.getString(R.string.forward_intent_to_work)
            }
        )
    }

    val noPersonalApps by lazy {
        requireNotNull(
            policyResources.getString(RESOLVER_NO_PERSONAL_APPS) {
                resources.getString(R.string.resolver_no_personal_apps_available)
            }
        )
    }

    val noWorkApps by lazy {
        requireNotNull(
            policyResources.getString(RESOLVER_NO_WORK_APPS) {
                resources.getString(R.string.resolver_no_work_apps_available)
            }
        )
    }

    open val crossProfileBlocked by lazy {
        requireNotNull(
            policyResources.getString(RESOLVER_CROSS_PROFILE_BLOCKED_TITLE) {
                resources.getString(R.string.resolver_cross_profile_blocked)
            }
        )
    }

    open fun toPersonalBlockedByPolicyMessage(share: Boolean): String {
        return requireNotNull(if (share) {
            policyResources.getString(RESOLVER_CANT_SHARE_WITH_PERSONAL) {
                resources.getString(R.string.resolver_cant_share_with_personal_apps_explanation)
            }
        } else {
            policyResources.getString(RESOLVER_CANT_ACCESS_PERSONAL) {
                resources.getString(R.string.resolver_cant_access_personal_apps_explanation)
            }
        })
    }

    open fun toWorkBlockedByPolicyMessage(share: Boolean): String {
        return requireNotNull(if (share) {
            policyResources.getString(RESOLVER_CANT_SHARE_WITH_WORK) {
                resources.getString(R.string.resolver_cant_share_with_work_apps_explanation)
            }
        } else {
            policyResources.getString(RESOLVER_CANT_ACCESS_WORK) {
                resources.getString(R.string.resolver_cant_access_work_apps_explanation)
            }
        })
    }

    open fun toPrivateBlockedByPolicyMessage(share: Boolean): String {
        return if (share) {
            resources.getString(R.string.resolver_cant_share_with_private_apps_explanation)
        } else {
            resources.getString(R.string.resolver_cant_access_private_apps_explanation)
        }
    }

    fun getWorkProfileNotSupportedMessage(launcherName: String): String {
        return requireNotNull(
            policyResources.getString(
                RESOLVER_WORK_PROFILE_NOT_SUPPORTED,
                {
                    resources.getString(
                        R.string.activity_resolver_work_profiles_support,
                        launcherName
                    )
                },
                launcherName
            )
        )
    }
}

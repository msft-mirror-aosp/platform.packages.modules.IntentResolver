/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.service.chooser

import android.os.Parcel
import android.os.Parcelable
import com.android.intentresolver.IChooserInteractiveSessionCallback

/** A stub for the potential future API class. */
class ChooserSession(val sessionCallbackBinder: IChooserInteractiveSessionCallback) : Parcelable {
    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        TODO("Not yet implemented")
    }

    companion object CREATOR : Parcelable.Creator<ChooserSession> {
        override fun createFromParcel(source: Parcel): ChooserSession? =
            ChooserSession(
                IChooserInteractiveSessionCallback.Stub.asInterface(source.readStrongBinder())
            )

        override fun newArray(size: Int): Array<out ChooserSession?> = arrayOfNulls(size)
    }
}

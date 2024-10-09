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

package com.android.intentresolver.icons;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;

import androidx.annotation.Nullable;

import com.android.intentresolver.TargetPresentationGetter;

import java.util.function.Consumer;

abstract class BaseLoadIconTask extends AsyncTask<Void, Void, Bitmap> {
    protected final Context mContext;
    protected final TargetPresentationGetter.Factory mPresentationFactory;
    private final Consumer<Bitmap> mCallback;

    BaseLoadIconTask(
            Context context,
            TargetPresentationGetter.Factory presentationFactory,
            Consumer<Bitmap> callback) {
        mContext = context;
        mPresentationFactory = presentationFactory;
        mCallback = callback;
    }

    @Override
    protected final void onPostExecute(@Nullable Bitmap d) {
        mCallback.accept(d);
    }
}

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

package com.android.intentresolver.icons

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.AsyncTask
import android.os.UserHandle
import android.util.SparseArray
import androidx.annotation.GuardedBy
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.android.intentresolver.Flags.targetHoverAndKeyboardFocusStates
import com.android.intentresolver.R
import com.android.intentresolver.TargetPresentationGetter
import com.android.intentresolver.chooser.DisplayResolveInfo
import com.android.intentresolver.chooser.SelectableTargetInfo
import com.android.intentresolver.inject.ActivityOwned
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ActivityContext
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor

/** An actual [TargetDataLoader] implementation. */
// TODO: replace async tasks with coroutines.
class DefaultTargetDataLoader
@AssistedInject
constructor(
    @ActivityContext private val context: Context,
    @ActivityOwned private val lifecycle: Lifecycle,
    @Assisted private val isAudioCaptureDevice: Boolean,
) : TargetDataLoader {
    private val presentationFactory =
        TargetPresentationGetter.Factory(
            context,
            context.getSystemService(ActivityManager::class.java)?.launcherLargeIconDensity
                ?: error("Unable to access ActivityManager"),
        )
    private val nextTaskId = AtomicInteger(0)
    @GuardedBy("self") private val activeTasks = SparseArray<AsyncTask<*, *, *>>()
    private val executor = Dispatchers.IO.asExecutor()

    init {
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    lifecycle.removeObserver(this)
                    destroy()
                }
            }
        )
    }

    override fun getOrLoadAppTargetIcon(
        info: DisplayResolveInfo,
        userHandle: UserHandle,
        callback: Consumer<Drawable>,
    ): Drawable? {
        val taskId = nextTaskId.getAndIncrement()
        LoadIconTask(context, info, presentationFactory) { bitmap ->
                removeTask(taskId)
                callback.accept(bitmap?.toDrawable() ?: loadIconPlaceholder())
            }
            .also { addTask(taskId, it) }
            .executeOnExecutor(executor)
        return null
    }

    override fun getOrLoadDirectShareIcon(
        info: SelectableTargetInfo,
        userHandle: UserHandle,
        callback: Consumer<Drawable>,
    ): Drawable? {
        val taskId = nextTaskId.getAndIncrement()
        LoadDirectShareIconTask(
                context.createContextAsUser(userHandle, 0),
                info,
                presentationFactory,
            ) { bitmap ->
                removeTask(taskId)
                callback.accept(bitmap?.toDrawable() ?: loadIconPlaceholder())
            }
            .also { addTask(taskId, it) }
            .executeOnExecutor(executor)
        return null
    }

    override fun loadLabel(info: DisplayResolveInfo, callback: Consumer<LabelInfo>) {
        val taskId = nextTaskId.getAndIncrement()
        LoadLabelTask(context, info, isAudioCaptureDevice, presentationFactory) { result ->
                removeTask(taskId)
                callback.accept(result)
            }
            .also { addTask(taskId, it) }
            .executeOnExecutor(executor)
    }

    override fun getOrLoadLabel(info: DisplayResolveInfo) {
        if (!info.hasDisplayLabel()) {
            val result =
                LoadLabelTask.loadLabel(context, info, isAudioCaptureDevice, presentationFactory)
            info.displayLabel = result.label
            info.extendedInfo = result.subLabel
        }
    }

    private fun addTask(id: Int, task: AsyncTask<*, *, *>) {
        synchronized(activeTasks) { activeTasks.put(id, task) }
    }

    private fun removeTask(id: Int) {
        synchronized(activeTasks) { activeTasks.remove(id) }
    }

    private fun loadIconPlaceholder(): Drawable =
        requireNotNull(context.getDrawable(R.drawable.resolver_icon_placeholder))

    private fun destroy() {
        synchronized(activeTasks) {
            for (i in 0 until activeTasks.size()) {
                activeTasks.valueAt(i).cancel(false)
            }
            activeTasks.clear()
        }
    }

    private fun Bitmap.toDrawable(): Drawable {
        return if (targetHoverAndKeyboardFocusStates()) {
            HoverBitmapDrawable(this)
        } else {
            BitmapDrawable(context.resources, this)
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(isAudioCaptureDevice: Boolean): DefaultTargetDataLoader
    }
}

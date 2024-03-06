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

package com.android.intentresolver.util.cursor

import android.database.Cursor

/** A [CursorView] that produces chunks/pages from an underlying cursor. */
interface PagedCursor<out E> : CursorView<Sequence<E?>> {
    /** The configured size of each page produced by this cursor. */
    val pageSize: Int
}

/** Returns a [PagedCursor] that produces pages of data from the given [CursorView]. */
fun <E> CursorView<E>.paged(pageSize: Int): PagedCursor<E> =
    object : PagedCursor<E>, Cursor by this@paged {

        init {
            check(pageSize > 0) { "pageSize must be greater than 0" }
        }

        override val pageSize: Int = pageSize

        override fun getCount(): Int =
            this@paged.count.let { it / pageSize + minOf(1, it % pageSize) }

        override fun getPosition(): Int =
            (this@paged.position / pageSize).let { if (this@paged.position < 0) it - 1 else it }

        override fun moveToNext(): Boolean = moveToPosition(position + 1)

        override fun moveToPrevious(): Boolean = moveToPosition(position - 1)

        override fun moveToPosition(position: Int): Boolean =
            this@paged.moveToPosition(position * pageSize)

        override fun readRow(): Sequence<E?> =
            this@paged.startAt(position * pageSize).limit(pageSize).asSequence()
    }

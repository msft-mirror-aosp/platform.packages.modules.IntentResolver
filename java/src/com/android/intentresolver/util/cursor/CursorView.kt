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

/** A [Cursor] that holds values of [E] for each row. */
interface CursorView<out E> : Cursor {
    /**
     * Reads the current row from this [CursorView]. A result of `null` indicates that the row could
     * not be read / value could not be produced.
     */
    fun readRow(): E?
}

/**
 * Returns a [CursorView] from the given [Cursor], and a function [readRow] used to produce the
 * value for a single row.
 */
fun <E> Cursor.viewBy(readRow: Cursor.() -> E): CursorView<E> =
    object : CursorView<E>, Cursor by this@viewBy {
        override fun readRow(): E? = immobilized().readRow()
    }

/** Returns a [CursorView] that begins (index 0) at [newStartIndex] of the given cursor. */
fun <E> CursorView<E>.startAt(newStartIndex: Int): CursorView<E> =
    object : CursorView<E>, Cursor by (this@startAt as Cursor).startAt(newStartIndex) {
        override fun readRow(): E? = this@startAt.readRow()
    }

/** Returns a [CursorView] that is truncated to contain only [count] elements. */
fun <E> CursorView<E>.limit(count: Int): CursorView<E> =
    object : CursorView<E>, Cursor by (this@limit as Cursor).limit(count) {
        override fun readRow(): E? = this@limit.readRow()
    }

/** Retrieves a single row at index [idx] from the [CursorView]. */
operator fun <E> CursorView<E>.get(idx: Int): E? = if (moveToPosition(idx)) readRow() else null

/** Returns a [Sequence] that iterates over the [CursorView] returning each row. */
fun <E> CursorView<E>.asSequence(): Sequence<E?> = sequence {
    for (i in 0 until count) {
        yield(get(i))
    }
}

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
import android.database.CursorWrapper

/** Returns a Cursor that is truncated to contain only [count] elements. */
fun Cursor.limit(count: Int): Cursor =
    object : CursorWrapper(this) {
        override fun getCount(): Int = minOf(count, super.getCount())

        override fun getPosition(): Int = super.getPosition().coerceAtMost(count)

        override fun moveToLast(): Boolean = super.moveToPosition(getCount() - 1)

        override fun isFirst(): Boolean = getCount() != 0 && super.isFirst()

        override fun isLast(): Boolean = getCount() != 0 && super.getPosition() == getCount() - 1

        override fun isAfterLast(): Boolean = getCount() == 0 || super.getPosition() >= getCount()

        override fun isBeforeFirst(): Boolean = getCount() == 0 || super.isBeforeFirst()

        override fun moveToNext(): Boolean = super.moveToNext() && position < getCount()

        override fun moveToPosition(position: Int): Boolean =
            super.moveToPosition(position) && position < getCount()
    }

/** Returns a Cursor that begins (index 0) at [newStartIndex] of the given Cursor. */
fun Cursor.startAt(newStartIndex: Int): Cursor =
    object : CursorWrapper(this) {
        override fun getCount(): Int = (super.getCount() - newStartIndex).coerceAtLeast(0)

        override fun getPosition(): Int = (super.getPosition() - newStartIndex).coerceAtLeast(-1)

        override fun moveToFirst(): Boolean = super.moveToPosition(newStartIndex)

        override fun moveToNext(): Boolean = super.moveToNext() && position < count

        override fun moveToPrevious(): Boolean = super.moveToPrevious() && position >= 0

        override fun moveToPosition(position: Int): Boolean =
            super.moveToPosition(position + newStartIndex) && position >= 0

        override fun isFirst(): Boolean = count != 0 && super.getPosition() == newStartIndex

        override fun isLast(): Boolean = count != 0 && super.isLast()

        override fun isBeforeFirst(): Boolean = count == 0 || super.getPosition() < newStartIndex

        override fun isAfterLast(): Boolean = count == 0 || super.isAfterLast()
    }

/** Returns a read-only non-movable view into the given Cursor. */
fun Cursor.immobilized(): Cursor =
    object : CursorWrapper(this) {
        private val unsupported: Nothing
            get() = error("unsupported")

        override fun moveToFirst(): Boolean = unsupported

        override fun moveToLast(): Boolean = unsupported

        override fun move(offset: Int): Boolean = unsupported

        override fun moveToPosition(position: Int): Boolean = unsupported

        override fun moveToNext(): Boolean = unsupported

        override fun moveToPrevious(): Boolean = unsupported
    }

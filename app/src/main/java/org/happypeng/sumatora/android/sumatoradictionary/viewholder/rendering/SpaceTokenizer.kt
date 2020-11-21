/* Sumatora Dictionary
        Copyright (C) 2020 Nicolas Centa

        This program is free software: you can redistribute it and/or modify
        it under the terms of the GNU General Public License as published by
        the Free Software Foundation, either version 3 of the License, or
        (at your option) any later version.

        This program is distributed in the hope that it will be useful,
        but WITHOUT ANY WARRANTY; without even the implied warranty of
        MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
        GNU General Public License for more details.

        You should have received a copy of the GNU General Public License
        along with this program.  If not, see <http://www.gnu.org/licenses/>.*/

package org.happypeng.sumatora.android.sumatoradictionary.viewholder.rendering

import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.widget.MultiAutoCompleteTextView

class SpaceTokenizer: MultiAutoCompleteTextView.Tokenizer {
    override fun findTokenStart(text: CharSequence?, cursor: Int): Int {
        var i = cursor

        while (text != null && i > 0 && text[i - 1] != ' ') {
            i--
        }

        return i
    }

    override fun findTokenEnd(text: CharSequence?, cursor: Int): Int {
        var i = cursor
        val len: Int = text?.length ?: cursor

        while (text != null && i < len) {
            if (text[i] == ' ') {
                return i
            } else {
                i++
            }
        }

        return len
    }

    override fun terminateToken(text: CharSequence?): CharSequence {
        val i: Int = text?.length ?: 0

        return if (text != null && i > 0 && text[i - 1] == ' ') {
            text
        } else {
            if (text is Spanned) {
                val sp = SpannableString("$text ")
                TextUtils.copySpansFrom(text as Spanned?, 0, text.length,
                        Any::class.java, sp, 0)
                sp
            } else {
                "$text "
            }
        }
    }
}
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
package org.happypeng.sumatora.android.sumatoradictionary.model.action

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryTagName
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.DictionaryTagsActivityIntent
import org.happypeng.sumatora.android.sumatoradictionary.mvibase.MviAction

sealed class DictionaryTagsActivityAction : MviAction

class DictionaryTagsActivityUpdateTagsAction(val tags: List<Pair<DictionaryTagName, List<Long>>>) : DictionaryTagsActivityAction()
class DictionaryTagsActivitySetSeqAction(val seq: Long?) : DictionaryTagsActivityAction()

object DictionaryTagsActivityCloseAction : DictionaryTagsActivityAction()

object DictionaryTagsActivityAddAction : DictionaryTagsActivityAction()
object DictionaryTagsActivityAddCancelAction : DictionaryTagsActivityAction()

object DictionaryTagsActivityEditAction : DictionaryTagsActivityAction()
object DictionaryTagsActivityEditCommitAction : DictionaryTagsActivityAction()
object DictionaryTagsActivityEditCancelAction : DictionaryTagsActivityAction()
object DictionaryTagsActivityEditCommitConfirmAction : DictionaryTagsActivityAction()

class DictionaryTagsActivityEditSelectForDeletionAction(val tag: DictionaryTagName, val select: Boolean) : DictionaryTagsActivityAction()

class DictionaryTagsActivityCreateTagNameAction(val tagName: String) : DictionaryTagsActivityAction()

class DictionaryTagsActivityToggleSelectAction(val tag: DictionaryTagName) : DictionaryTagsActivityAction()
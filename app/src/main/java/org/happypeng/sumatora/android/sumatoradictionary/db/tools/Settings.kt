/* Sumatora Dictionary
        Copyright (C) 2019 Nicolas Centa

        This program is free software: you can redistribute it and/or modify
        it under the terms of the GNU General Public License as published by
        the Free Software Foundation, either version 3 of the License, or
        (at your option) any later version.

        This program is distributed in the hope that it will be useful,
        but WITHOUT ANY WARRANTY; without even the implied warranty of
        MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
        GNU General Public License for more details.

        You should have received a copy of the GNU General Public License
        along with this program.  If not, see <http://www.gnu.org/licenses/>. */
package org.happypeng.sumatora.android.sumatoradictionary.db.tools

import kotlinx.coroutines.launch
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.switchMap
import kotlinx.coroutines.Dispatchers
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentSetting

class Settings {
    private val m_db: MutableLiveData<PersistentDatabase?>

    init {
        m_db = MutableLiveData<PersistentDatabase?>()
    }

    @WorkerThread
    fun postDatabase(aDB: PersistentDatabase?) {
        m_db.postValue(aDB)
    }

    fun getValue(aName: String?): LiveData<String?> {
        return m_db.switchMap<PersistentDatabase?, String?> { input: PersistentDatabase? ->
            if (input == null) return@switchMap null // Handle potential nulls
            input.persistentSettingsDao().getValue(aName)
        }
    }

    @WorkerThread
    fun getValueDirect(aName: String?): String? {
        val db = m_db.getValue()

        if (db != null) {
            return db.persistentSettingsDao().getValueDirect(aName)
        }

        return null
    }

    @WorkerThread
    fun postValue(aName: String, aValue: String) {
        if (m_db.getValue() != null) {
            m_db.getValue()!!.persistentSettingsDao().insert(PersistentSetting(aName, aValue))
        }
    }

    @MainThread
    fun setValue(aName: String, aValue: String) {
        // This will now correctly resolve to the Coroutine launch
        ProcessLifecycleOwner.get().lifecycleScope.launch(Dispatchers.IO) {
            postValue(aName, aValue)
        }
    }

    companion object {
        const val REPOSITORY_URL: String = "repositoryURL"
    }
}

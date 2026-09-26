/* Sumatora Dictionary
        Copyright (C) 2026 Nicolas Centa

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

package org.happypeng.sumatora.android.sumatoradictionary.sync

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// Keystore-backed storage for the Nextcloud app password (see NextcloudCredentials) - this app's
// only other persisted values (Settings.kt) go through plaintext Room PersistentSetting rows,
// which is not appropriate for a credential.
@Singleton
class NextcloudCredentialStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun save(credentials: NextcloudCredentials) {
        prefs.edit()
            .putString(KEY_SERVER, credentials.server)
            .putString(KEY_LOGIN_NAME, credentials.loginName)
            .putString(KEY_APP_PASSWORD, credentials.appPassword)
            .apply()
    }

    fun load(): NextcloudCredentials? {
        val server = prefs.getString(KEY_SERVER, null) ?: return null
        val loginName = prefs.getString(KEY_LOGIN_NAME, null) ?: return null
        val appPassword = prefs.getString(KEY_APP_PASSWORD, null) ?: return null
        return NextcloudCredentials(server, loginName, appPassword)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_FILE_NAME = "nextcloud_sync_credentials"
        private const val KEY_SERVER = "server"
        private const val KEY_LOGIN_NAME = "loginName"
        private const val KEY_APP_PASSWORD = "appPassword"
    }
}

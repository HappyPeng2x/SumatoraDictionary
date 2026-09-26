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

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

// Nextcloud's Login Flow v2 (https://docs.nextcloud.com/server/latest/developer_manual/client_apis/LoginFlow/index.html#login-flow-v2):
// the app never sees the user's real account password, only a server-issued, individually
// revocable app password. Three hand-rolled HTTP calls on HttpURLConnection - no networking
// library needed for this or for NextcloudWebDavClient.
object NextcloudLoginFlow {
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000

    data class Init(val token: String, val pollEndpoint: String, val loginUrl: String)

    // Step 1: ask the server to start a login session. `server` must already be a normalized
    // https://host[:port] base URL (no trailing slash) - see NextcloudSyncActivity for the light
    // normalization applied to whatever the user types in.
    fun initiate(server: String): Init {
        val connection = openConnection("$server/index.php/login/v2", "POST")

        val code = connection.responseCode
        if (code !in 200..299) {
            throw IOException("Login flow init failed: HTTP $code")
        }

        val body = connection.inputStream.use { it.readBytes() }.toString(StandardCharsets.UTF_8)
        val json = JSONObject(body)
        val poll = json.getJSONObject("poll")

        return Init(
            token = poll.getString("token"),
            pollEndpoint = poll.getString("endpoint"),
            loginUrl = json.getString("login")
        )
    }

    // Step 3: ask whether the user has finished authorizing the app yet. Nextcloud returns 404
    // while the flow is still pending (not an error - the caller polls this on an interval), and
    // the token expires after Nextcloud's own ~20 minute window.
    fun poll(pollEndpoint: String, token: String): NextcloudCredentials? {
        val connection = openConnection(pollEndpoint, "POST")
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

        val formBody = "token=" + URLEncoder.encode(token, "UTF-8")
        connection.outputStream.use { it.write(formBody.toByteArray(StandardCharsets.UTF_8)) }

        return when (val code = connection.responseCode) {
            404 -> null
            in 200..299 -> {
                val body = connection.inputStream.use { it.readBytes() }.toString(StandardCharsets.UTF_8)
                val json = JSONObject(body)
                NextcloudCredentials(
                    server = json.getString("server").trimEnd('/'),
                    loginName = json.getString("loginName"),
                    appPassword = json.getString("appPassword")
                )
            }
            else -> throw IOException("Login flow poll failed: HTTP $code")
        }
    }

    private fun openConnection(url: String, method: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        return connection
    }
}

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
        along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.happypeng.sumatora.android.sumatoradictionary.update

import android.app.DownloadManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryControlInfo
import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabaseInitialization
import org.happypeng.sumatora.android.sumatoradictionary.receiver.DictionaryDownloadCompleteReceiver
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.io.BufferedReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPOutputStream
import javax.inject.Inject

// Regression coverage for the real-device bug chain fixed alongside this test: R8 stripping
// WorkManager's InputMerger constructor (proguard-rules.pro), DictionaryUpdateChecker aborting its
// whole manifest loop on one failed download (checkAndEnqueue's try/catch), and a REPOSITORY_URL
// setting stuck on a dead host forever (migrateStaleRepositoryUrl). None of those are reachable
// from a unit test - this drives the real production classes (DictionaryUpdateChecker,
// RemoteDictionaryObject.download() via a real DownloadManager, DictionaryDownloadCompleteReceiver,
// PersistentDatabaseInitialization.promotePendingUpdate/detachIncompatiblePacks) against a fake
// stale pack and a loopback-only HTTP server standing in for the real SumatoraIndex host, so it
// exercises the exact same end-to-end path a phone does without depending on the real internet
// (flaky in CI) or a debug-signed test build having network access to github's raw host blocked.
//
// http://127.0.0.1 requires the debug-only network security config in app/src/debug/res/xml -
// cleartext stays blocked everywhere else, including in release builds.
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DictionaryUpdateEndToEndTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var dbComponent: PersistentDatabaseComponent

    private lateinit var context: Context
    private lateinit var db: PersistentDatabase
    private lateinit var downloadManager: DownloadManager
    private var server: LoopbackHttpServer? = null

    private val type = "gloss"
    private val lang = "_e2e_test"

    @Before
    fun setup() {
        hiltRule.inject()
        context = InstrumentationRegistry.getInstrumentation().targetContext
        db = dbComponent.database
        downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        cleanUpTestRows()
    }

    @After
    fun tearDown() {
        server?.stop()
        cleanUpTestRows()
    }

    private fun cleanUpTestRows() {
        db.installedDictionaryDao().getForTypeLang(type, lang)?.let { row ->
            File(row.file).delete()
            row.pendingFile?.let { File(it).delete() }
            db.installedDictionaryDao().delete(row)
        }
        db.remoteDictionaryObjectDao().getForTypeLang(type, lang)?.let {
            db.remoteDictionaryObjectDao().delete(it)
        }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun gzip(bytes: ByteArray): ByteArray =
        ByteArrayOutputStream().apply { GZIPOutputStream(this).use { it.write(bytes) } }.toByteArray()

    private fun waitForDownload(downloadId: Long): Int {
        repeat(60) {
            downloadManager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                        return status
                    }
                }
            }
            Thread.sleep(500)
        }
        return -1
    }

    @Test
    fun staleGlossPack_checkedDownloadedAndPromoted_becomesCompatible() {
        // 1. "Install a fake stale pack" - version/date 0 is exactly what
        // PersistentDatabaseInitialization.detachIncompatiblePacks() leaves behind for a real
        // incompatible pack (see PersistentDatabaseInitializationTest.detachIncompatible_flagsOldGlossPack),
        // so this starts from the same state a real phone would be in after that runs.
        val oldFile = File(context.getExternalFilesDir(null), "e2e_old_$lang.db")
        oldFile.writeBytes(byteArrayOf(1, 2, 3))
        db.installedDictionaryDao().insert(
            InstalledDictionary(oldFile.absolutePath, "Test pack (stale)", type, lang, 0, 0)
        )

        // 2. Stand up the fake "dictionaries.xml" host and the replacement pack it advertises.
        val payload = "fake dictionary contents for the end-to-end test".toByteArray()
        val gzippedPack = gzip(payload)
        val checksum = sha256Hex(gzippedPack)

        val localServer = LoopbackHttpServer().also { server = it }
        val packUrl = "http://127.0.0.1:${localServer.port}/pack.db.gz"
        val manifestXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <repository version="99" date="20260101">
                <dictionary uri="$packUrl" description="Test E2E pack" type="$type" lang="$lang" sha256="$checksum"/>
            </repository>
        """.trimIndent().toByteArray()
        localServer.setRoute("/dictionaries.xml", "text/xml", manifestXml)
        localServer.setRoute("/pack.db.gz", "application/octet-stream", gzippedPack)

        val manifestUrl = "http://127.0.0.1:${localServer.port}/dictionaries.xml"

        // 3. The same entry point DictionaryUpdateWorker.doWork() calls, against our fake manifest.
        val enqueued = DictionaryUpdateChecker.checkAndEnqueue(context, db, manifestUrl)
        assertEquals("the stale pack should be the one thing enqueued", 1, enqueued)

        val remote = db.remoteDictionaryObjectDao().getForTypeLang(type, lang)
        assertNotNull("checkAndEnqueue should have persisted a RemoteDictionaryObject row", remote)
        assertTrue("a real DownloadManager id should have been obtained", remote!!.downloadId > -1)

        // 4. Let the real DownloadManager actually fetch it from our local server.
        val status = waitForDownload(remote.downloadId)
        assertEquals(
            "download from the local test server should succeed",
            DownloadManager.STATUS_SUCCESSFUL, status
        )

        // 5. The same entry point DictionaryDownloadCompleteReceiver's manifest-registered
        // ACTION_DOWNLOAD_COMPLETE broadcast uses (see DictionaryDownloadCompleteReceiverFailureTest
        // for the same pattern applied to the failure path).
        val receiver = DictionaryDownloadCompleteReceiver()
        receiver.persistentDatabaseComponent = dbComponent
        receiver.handleDownloadComplete(context, remote.downloadId)

        val pending = db.installedDictionaryDao().getForTypeLang(type, lang)
        assertNotNull(pending)
        assertTrue(
            "an update to an already-installed pack must be stashed pending, not attached live",
            pending!!.hasPendingUpdate()
        )
        assertEquals(99, pending.pendingVersion)
        assertEquals(20260101, pending.pendingDate)

        // 6. Simulate "restart Sumatora to apply" - the only place a pending update is ever
        // promoted (see update-pipeline.md: never hot-swapping a live-attached SQLite file).
        PersistentDatabaseInitialization.promotePendingUpdate(db, pending)

        val promoted = db.installedDictionaryDao().getForTypeLang(type, lang)
        assertNotNull(promoted)
        assertEquals("the pack should now be at the manifest's version", 99, promoted!!.version)
        assertEquals(20260101, promoted.date)
        assertFalse(promoted.hasPendingUpdate())
        assertTrue(
            "the promoted file should contain the decompressed pack contents",
            File(promoted.file).readBytes().contentEquals(payload)
        )

        // 7. And it must no longer be reported as an incompatible pack needing an update.
        val info = DictionaryControlInfo()
        PersistentDatabaseInitialization.detachIncompatiblePacks(db, info)
        assertTrue(
            "a freshly-promoted pack must not still show as needing an update",
            info.incompatiblePacks.isEmpty()
        )
    }

    // Minimal hand-rolled HTTP/1.1 server bound to loopback only, just enough to satisfy
    // HttpURLConnection (RemoteManifestFetcher) and DownloadManager (RemoteDictionaryObject.download())
    // GETs against canned byte responses - avoids depending on the real internet (or a test-only
    // library) for a fully offline, deterministic test.
    private class LoopbackHttpServer {
        private val serverSocket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        private val routes = ConcurrentHashMap<String, Pair<String, ByteArray>>()

        val port: Int get() = serverSocket.localPort

        @Volatile
        private var running = true

        private val thread = Thread {
            while (running) {
                try {
                    val socket = serverSocket.accept()
                    handle(socket)
                } catch (e: Exception) {
                    // Expected on stop(): serverSocket.close() unblocks accept() with this.
                }
            }
        }.apply {
            isDaemon = true
            start()
        }

        fun setRoute(path: String, contentType: String, body: ByteArray) {
            routes[path] = contentType to body
        }

        private fun handle(socket: Socket) {
            socket.use {
                val input = BufferedReader(InputStreamReader(it.getInputStream()))
                val requestLine = input.readLine() ?: return
                while (true) {
                    val line = input.readLine() ?: break
                    if (line.isEmpty()) break
                }

                val path = requestLine.split(" ").getOrNull(1) ?: "/"
                val output = it.getOutputStream()
                val route = routes[path]

                if (route == null) {
                    val body = "Not found".toByteArray()
                    output.write(
                        "HTTP/1.1 404 Not Found\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                            .toByteArray()
                    )
                    output.write(body)
                } else {
                    val (contentType, body) = route
                    output.write(
                        ("HTTP/1.1 200 OK\r\nContent-Type: $contentType\r\n" +
                                "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray()
                    )
                    output.write(body)
                }
                output.flush()
            }
        }

        fun stop() {
            running = false
            try {
                serverSocket.close()
            } catch (e: Exception) {
                // Already closed/never opened - nothing left to clean up.
            }
        }
    }
}

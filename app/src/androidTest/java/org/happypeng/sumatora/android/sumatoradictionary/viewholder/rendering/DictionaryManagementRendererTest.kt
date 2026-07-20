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

package org.happypeng.sumatora.android.sumatoradictionary.viewholder.rendering

import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.happypeng.sumatora.android.sumatoradictionary.R
import org.happypeng.sumatora.android.sumatoradictionary.db.RemoteDictionaryObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// DictionaryManagementRenderer builds its rows programmatically (see its class comment) instead
// of via RecyclerView/XML, so the only way to check a state actually renders what
// DictionariesManagementActivity intends is to build a container and inspect the real View tree.
// Exercises the `failed` row state (RemoteDictionaryObject.failed, schema v12) added for the
// "download failed, tap to retry" fix - see DictionaryDownloadCompleteReceiverFailureTest for
// where that state gets written in the first place.
@RunWith(AndroidJUnit4::class)
class DictionaryManagementRendererTest {

    @Test
    fun failedRow_showsFailedCaptionAndRetryButtonThatInvokesOnRetry() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val container = LinearLayout(context)

        val remote = RemoteDictionaryObject(
            "https://example.invalid/x.gz", "Français", "gloss", "fre", 12, 20260719, ""
        )
        val row = DictionaryManagementRow(
            type = "gloss", lang = "fre", description = "Français", version = 12, date = 20260719,
            installed = null, remote = remote, downloading = false, failed = true
        )

        var retried: RemoteDictionaryObject? = null
        DictionaryManagementRenderer.buildRows(
            container, listOf(row),
            onInstall = {}, onDelete = {}, onRetry = { retried = it }
        )

        val captions = collectTextViews(container).map { it.text.toString() }
        assertTrue(
            "expected the failed-status caption among $captions",
            captions.any { it == context.getString(R.string.dictionary_status_failed) }
        )
        // A failed row must not also carry the plain "+" install action - that would let a user
        // trigger a second concurrent download for the same pack instead of retrying the one that
        // just failed.
        assertTrue(
            "a failed row must not still show the install action",
            findByContentDescription(container, context.getString(R.string.install_icon_description)) == null
        )

        val retryButton = findByContentDescription(
            container, context.getString(R.string.retry_icon_description)
        )
        assertNotNull("expected a retry button for a failed row", retryButton)

        retryButton!!.performClick()
        assertEquals(remote, retried)
    }

    private fun collectTextViews(view: View): List<TextView> {
        val result = mutableListOf<TextView>()
        fun visit(v: View) {
            if (v is TextView) result.add(v)
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) visit(v.getChildAt(i))
            }
        }
        visit(view)
        return result
    }

    private fun findByContentDescription(root: ViewGroup, desc: CharSequence): ImageButton? {
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child is ImageButton && desc == child.contentDescription) {
                return child
            }
            if (child is ViewGroup) {
                findByContentDescription(child, desc)?.let { return it }
            }
        }
        return null
    }
}

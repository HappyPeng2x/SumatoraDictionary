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

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Guards the specific proguard-rules.pro line that fixed a real-device bug: R8 silently stripped
// androidx.work.OverwritingInputMerger's no-arg constructor (WorkManager instantiates it via
// reflection for every work execution, not just chained work), which failed every WorkManager job
// on the release build before this rule was added - including "Check for updates" doing nothing
// at all, no exception, no toast, nothing (see DictionaryUpdateEndToEndTest for the business-logic
// side of that same bug chain). A minified-build instrumented test would be the more direct
// regression test, but this project's androidTest setup only runs against the debug variant
// (R8 doesn't run there at all) - this cheap, deterministic JVM check is what actually catches
// someone editing this file and losing the rule, without needing UI automation against a real
// minified release build in CI.
class ProguardRulesTest {

    @Test
    fun proguardRules_keepsWorkManagerInputMergerConstructor() {
        val rules = File("proguard-rules.pro")
        assertTrue("expected ${rules.absolutePath} to exist", rules.exists())

        val text = rules.readText()
        assertTrue(
            "proguard-rules.pro must keep constructors on androidx.work.InputMerger " +
                    "subclasses, or WorkManager jobs silently fail on the release build " +
                    "(NoSuchMethodException instantiating the InputMerger via reflection)",
            Regex("""-keepclassmembers\s+class\s+\*\s+extends\s+androidx\.work\.InputMerger\s*\{[^}]*<init>""")
                .containsMatchIn(text)
        )
    }
}

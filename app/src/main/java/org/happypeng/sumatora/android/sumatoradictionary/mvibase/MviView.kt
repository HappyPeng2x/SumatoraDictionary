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

package org.happypeng.sumatora.android.sumatoradictionary.mvibase

import io.reactivex.rxjava3.core.Observable

/**
 * Object representing a UI that will
 * a) emit its intents to a view model,
 * b) subscribes to a view model for rendering its UI.
 *
 * @param I Top class of the [MviIntent] that the [MviView] will be emitting.
 * @param S Top class of the [MviViewState] the [MviView] will be subscribing to.
 */
interface MviView<I : MviIntent, in S : MviViewState> {
    /**
     * Unique [Observable] used by the [MviViewModel]
     * to listen to the [MviView].
     * All the [MviView]'s [MviIntent]s must go through this [Observable].
     */
    fun intents(): Observable<I>

    /**
     * Entry point for the [MviView] to render itself based on a [MviViewState].
     */
    fun render(state: S)
}
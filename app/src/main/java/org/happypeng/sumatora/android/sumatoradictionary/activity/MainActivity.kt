/* Sumatora Dictionary
        Copyright (C) 2018 Nicolas Centa

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
package org.happypeng.sumatora.android.sumatoradictionary.activity

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.PersistableBundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationView
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import org.happypeng.sumatora.android.sumatoradictionary.BuildConfig
import org.happypeng.sumatora.android.sumatoradictionary.DictionaryApplication
import org.happypeng.sumatora.android.sumatoradictionary.R
import org.happypeng.sumatora.android.sumatoradictionary.fragment.BookmarkFragment
import org.happypeng.sumatora.android.sumatoradictionary.fragment.QueryFragment
import org.happypeng.sumatora.android.sumatoradictionary.fragment.SettingsFragment
import org.happypeng.sumatora.android.sumatoradictionary.fragment.SettingsFragment.SettingsFragmentActions
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.*
import org.happypeng.sumatora.android.sumatoradictionary.model.status.MainActivityNavigationStatus
import org.happypeng.sumatora.android.sumatoradictionary.model.status.MainActivityStatus
import org.slf4j.LoggerFactory

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), SettingsFragmentActions {
    private val initialStatus = MainActivityStatus(isClosed = false,
            navigationStatus = MainActivityNavigationStatus.SEARCH, searchTerm = null,
            drawerOpen = false)
    private val intentSubject: Subject<MainActivityIntent> = PublishSubject.create()
    private val statusObservable: Observable<MainActivityStatus> =
            intentSubject.scan(initialStatus,
                    { mainActivityStatus: MainActivityStatus,
                      mainActivityIntent: MainActivityIntent ->
                        mainActivityStatus.copy(
                                navigationStatus = when(mainActivityIntent) {
                                    MainActivityNavigateBookmarksIntent -> MainActivityNavigationStatus.BOOKMARKS
                                    MainActivityNavigateSearchIntent -> MainActivityNavigationStatus.SEARCH
                                    MainActivityNavigateSettingsIntent -> MainActivityNavigationStatus.SETTINGS
                                    MainActivityBackPressedIntent -> MainActivityNavigationStatus.SEARCH
                                    else -> mainActivityStatus.navigationStatus
                                },
                                isClosed = when(mainActivityIntent) {
                                    MainActivityCloseIntent -> true
                                    MainActivityBackPressedIntent ->
                                        when(mainActivityStatus.navigationStatus) {
                                            MainActivityNavigationStatus.SEARCH -> true
                                            else -> false
                                        }
                                    else -> false
                                },
                                searchTerm = when(mainActivityIntent) {
                                    is MainActivitySearchIntent -> mainActivityIntent.term
                                    else -> mainActivityStatus.searchTerm
                                },
                                drawerOpen = when(mainActivityIntent) {
                                    MainActivityHomePressedIntent -> true
                                    is MainActivityNavigationIntent -> false
                                    else -> mainActivityStatus.drawerOpen
                                }
                        )
                    }).takeUntil { it.isClosed }.doAfterTerminate { finish() }

    private val logger = run {
        if (BuildConfig.DEBUG_DICTIONARY_ACTIVITY) {
            LoggerFactory.getLogger(this.javaClass)
        } else {
            null
        }
    }

    private fun startActivityWithDelay(activity: Class<*>) {
        Handler().postDelayed({ startActivity(Intent(this@MainActivity, activity)) }, DELAY_MILLIS.toLong())
    }

    private fun switchToFragment(newFragment: Fragment, tag: String) {
        val fragmentTransaction = supportFragmentManager.beginTransaction()

        for (f in supportFragmentManager.fragments) {
            fragmentTransaction.detach(f)
        }

        val attachFragment = supportFragmentManager.findFragmentByTag(tag)

        if (attachFragment == null) {
            fragmentTransaction.add(R.id.dictionary_fragment_container, newFragment, tag)
        } else {
            fragmentTransaction.attach(attachFragment)
        }

        fragmentTransaction.commit()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BuildConfig.DEBUG_DICTIONARY_ACTIVITY) {
            logger?.info("onCreate()")
        }

        val dictionarySearchFragment = QueryFragment()
        val bookmarkFragment = BookmarkFragment()
        val settingsFragment = SettingsFragment()

        setContentView(R.layout.activity_dictionary)

        val navigationView: NavigationView = findViewById(R.id.activity_main_navigation_view)

        navigationView.setNavigationItemSelectedListener { pMenuItem ->
            intentSubject.onNext(
                    when (pMenuItem.itemId) {
                        R.id.navigation_view_item_search -> MainActivityNavigateSearchIntent
                        R.id.navigation_view_item_bookmarks -> MainActivityNavigateBookmarksIntent
                        R.id.navigation_view_item_settings -> MainActivityNavigateSettingsIntent
                        R.id.navigation_view_item_about -> MainActivityNavigateAboutIntent
                        else -> MainActivityNoOpIntent
                    })

            true
        }

        val drawerLayout: DrawerLayout = findViewById(R.id.nav_drawer)

        statusObservable.map { it.drawerOpen }
                .distinctUntilChanged()
                .subscribe {
                    if (it) {
                        drawerLayout.openDrawer(GravityCompat.START)
                    } else {
                        drawerLayout.closeDrawer(GravityCompat.START)
                    }
                }

        statusObservable.distinctUntilChanged { t1, t2 ->
            (t1.searchTerm == null && t2.searchTerm == null) ||
                    t1.searchTerm.equals(t2.searchTerm) }
                .subscribe {
                    if (it.searchTerm != null) {
                        when (it.navigationStatus) {
                            MainActivityNavigationStatus.SEARCH ->
                                dictionarySearchFragment.setIntentSearchTerm(it.searchTerm)

                            MainActivityNavigationStatus.BOOKMARKS ->
                                bookmarkFragment.setIntentSearchTerm(it.searchTerm)

                            MainActivityNavigationStatus.SETTINGS -> { }
                        }
                    }
                }

        statusObservable.map { it.navigationStatus }
                .subscribe { it: MainActivityNavigationStatus ->
                    when (it) {
                        MainActivityNavigationStatus.SEARCH -> {
                            switchToFragment(dictionarySearchFragment, SEARCH_FRAGMENT_TAG)
                            navigationView.setCheckedItem(R.id.navigation_view_item_search)
                        }

                        MainActivityNavigationStatus.BOOKMARKS -> {
                            switchToFragment(bookmarkFragment, BOOKMARK_FRAGMENT_TAG)
                            navigationView.setCheckedItem(R.id.navigation_view_item_bookmarks)
                        }

                        MainActivityNavigationStatus.SETTINGS -> {
                            switchToFragment(settingsFragment, SETTINGS_FRAGMENT_TAG)
                            navigationView.setCheckedItem(R.id.navigation_view_item_settings)
                        }
                    }
                }
    }

    private fun transformSearchIntent(aIntent: Intent) {
        if (aIntent.hasExtra("query")) {
            aIntent.putExtra("SEARCH_TERM", aIntent.getStringExtra("query"))
        }
    }

    private fun displayIntent(aIntent: Intent?) {
        if (BuildConfig.DEBUG_DICTIONARY_ACTIVITY) {
            if (logger != null) {
                if (aIntent == null) {
                    logger.info("intent is null")
                } else {
                    logger.info("intent action is " + aIntent.action)
                    if (aIntent.extras != null) {
                        for (key in aIntent.extras!!.keySet()) {
                            logger.info("intent has key $key")
                            val `val` = aIntent.extras!![key]
                            if (`val` != null) {
                                logger.info("type " + `val`.javaClass.name + " value " + `val`.toString())
                            } else {
                                logger.info("value is null")
                            }
                        }
                    } else {
                        logger.info("intent has no extras")
                    }
                }
            }
        }
    }

    private fun processIntent(aIntent: Intent?) {
        if (BuildConfig.DEBUG_DICTIONARY_ACTIVITY) {
            displayIntent(aIntent)
        }
        if (aIntent != null && aIntent.hasExtra("SEARCH_TERM")) {
            if (BuildConfig.DEBUG_DICTIONARY_ACTIVITY) {
                logger?.info("setting search term to " + aIntent.getStringExtra("SEARCH_TERM"))
            }
            if ((Intent.ACTION_SEARCH == aIntent.action || Intent.ACTION_MAIN == aIntent.action) &&
                    aIntent.hasExtra("SEARCH_TERM")) {

                val term = aIntent.getStringExtra("SEARCH_TERM")

                if (term != null) {
                    intentSubject.onNext(MainActivitySearchIntent(term))
                }

                aIntent.removeExtra("SEARCH_TERM")
            }
        }
    }

    override fun onOptionsItemSelected(pMenuItem: MenuItem): Boolean {
        when (pMenuItem.itemId) {
            android.R.id.home -> intentSubject.onNext(MainActivityHomePressedIntent)
        }

        return true
    }

    override fun onNewIntent(aIntent: Intent) {
        super.onNewIntent(aIntent)
        transformSearchIntent(aIntent)
        intent = aIntent
    }

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {
        intentSubject.onNext(MainActivityCloseIntent)

        super.onSaveInstanceState(outState, outPersistentState)
    }

    override fun onResume() {
        super.onResume()
        if (BuildConfig.DEBUG_DICTIONARY_ACTIVITY) {
            logger?.info("onResume called")
        }
        val intent = intent
        if (intent != null && intent.hasExtra("SEARCH_TERM")) {
            processIntent(intent)
        }
    }

    override fun onAttachFragment(fragment: Fragment) {
        if (fragment is SettingsFragment) {
            fragment.setFragmentActions(this)
        }
    }

    override fun displayLog() {
        /*
        if (m_debugFragment == null) {
            m_debugFragment = DebugFragment()
        }
        switchToFragment(m_debugFragment!!, DEBUG_FRAGMENT_TAG)
         */
    }

    override fun manageDictionaries() {
        //startActivityWithDelay(DictionariesManagementActivity.class);
    }

    override fun setRepositoryURL(aUrl: String) {
        //if (m_app == null) {
        //    return
        //}

        //m_app.getSettings().setValue(Settings.REPOSITORY_URL, aUrl);
    }

    override fun onBackPressed() {
        intentSubject.onNext(MainActivityBackPressedIntent)
    }

    companion object {
        private const val SEARCH_FRAGMENT_TAG = "SEARCH_FRAGMENT"
        private const val BOOKMARK_FRAGMENT_TAG = "BOOKMARK_FRAGMENT"
        private const val SETTINGS_FRAGMENT_TAG = "SETTINGS_FRAGMENT"
        private const val DEBUG_FRAGMENT_TAG = "DEBUG_FRAGMENT"
        private const val DELAY_MILLIS = 250
    }
}
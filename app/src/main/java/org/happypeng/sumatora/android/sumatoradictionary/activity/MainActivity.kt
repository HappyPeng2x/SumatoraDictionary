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
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationView
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.happypeng.sumatora.android.sumatoradictionary.BuildConfig
import org.happypeng.sumatora.android.sumatoradictionary.R
import org.happypeng.sumatora.android.sumatoradictionary.fragment.BaseFragment
import org.happypeng.sumatora.android.sumatoradictionary.fragment.BookmarkFragment
import org.happypeng.sumatora.android.sumatoradictionary.fragment.QueryFragment
import org.happypeng.sumatora.android.sumatoradictionary.fragment.SettingsFragment
import org.happypeng.sumatora.android.sumatoradictionary.model.MainActivityModel
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.*
import org.happypeng.sumatora.android.sumatoradictionary.model.state.MainActivityNavigationStatus
import org.slf4j.LoggerFactory

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainActivityModel by viewModels()
    private val compositeDisposable = CompositeDisposable()

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

    private fun switchToFragment(navigate: Boolean,
                                 newFragment: MainActivityNavigationStatus): Fragment {
        val tag = when (newFragment) {
            MainActivityNavigationStatus.SEARCH -> SEARCH_FRAGMENT_TAG
            MainActivityNavigationStatus.BOOKMARKS -> BOOKMARK_FRAGMENT_TAG
            MainActivityNavigationStatus.SETTINGS -> SETTINGS_FRAGMENT_TAG
        }

        val existingFragment = supportFragmentManager.findFragmentByTag(tag)

        val attachFragment = existingFragment ?: when (newFragment) {
            MainActivityNavigationStatus.SEARCH -> QueryFragment()
            MainActivityNavigationStatus.BOOKMARKS -> BookmarkFragment()
            MainActivityNavigationStatus.SETTINGS -> SettingsFragment()
        }

        if (existingFragment == null || navigate) {
            val fragmentTransaction = supportFragmentManager.beginTransaction()

            for (f in supportFragmentManager.fragments) {
                fragmentTransaction.detach(f)
            }

            if (existingFragment == null) {
                fragmentTransaction.add(R.id.dictionary_fragment_container, attachFragment, tag)
            } else {
                fragmentTransaction.attach(attachFragment)
            }

            fragmentTransaction.commit()
        }

        return attachFragment
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (BuildConfig.DEBUG_DICTIONARY_ACTIVITY) {
            logger?.info("onCreate()")
        }

        setContentView(R.layout.activity_dictionary)

        val navigationView: NavigationView = findViewById(R.id.activity_main_navigation_view)

        navigationView.setNavigationItemSelectedListener { pMenuItem ->
            when (pMenuItem.itemId) {
                R.id.navigation_view_item_search ->
                    viewModel.sendIntent(MainActivityNavigateSearchIntent)
                R.id.navigation_view_item_bookmarks ->
                    viewModel.sendIntent(MainActivityNavigateBookmarksIntent)
                R.id.navigation_view_item_settings ->
                    viewModel.sendIntent(MainActivityNavigateSettingsIntent)
                R.id.navigation_view_item_about -> {
                    viewModel.sendIntent(MainActivityNavigateAboutIntent)
                    startActivityWithDelay(AboutActivity::class.java)
                }
            }

            true
        }

        val drawerLayout: DrawerLayout = findViewById(R.id.nav_drawer)

        drawerLayout.addDrawerListener(object: DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
            }

            override fun onDrawerOpened(drawerView: View) {
            }

            override fun onDrawerClosed(drawerView: View) {
                viewModel.sendIntent(MainActivityDrawerClosedIntent)
            }

            override fun onDrawerStateChanged(newState: Int) {
            }
        })

        compositeDisposable.add(viewModel.stateObservable.map { it.drawerOpen }
                .distinctUntilChanged()
                .subscribe {
                    if (it) {
                        drawerLayout.openDrawer(GravityCompat.START)
                    } else {
                        drawerLayout.closeDrawer(GravityCompat.START)
                    }
                })

        compositeDisposable.add(viewModel.stateObservable
                .filter { it.navigate || it.changeTerm }
                .subscribe
                { intent ->
                    val fragment = switchToFragment(intent.navigate, intent.navigationStatus)

                    if (fragment is BaseFragment && intent.changeTerm) {
                        fragment.setIntentSearchTerm(intent.searchTerm)
                    }

                    if (intent.navigate) {
                        when (intent.navigationStatus) {
                            MainActivityNavigationStatus.SEARCH ->
                                navigationView.setCheckedItem(R.id.navigation_view_item_search)
                            MainActivityNavigationStatus.BOOKMARKS ->
                                navigationView.setCheckedItem(R.id.navigation_view_item_bookmarks)
                            MainActivityNavigationStatus.SETTINGS ->
                                navigationView.setCheckedItem(R.id.navigation_view_item_settings)
                        }
                    }
                })

        compositeDisposable.add(viewModel.stateObservable.filter { it.finished }
                .subscribe { finish() })
    }

    private fun transformSearchIntent(aIntent: Intent) {
        if (aIntent.hasExtra("query")) {
            aIntent.putExtra("SEARCH_TERM", aIntent.getStringExtra("query"))
            aIntent.removeExtra("query")
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

    fun processIntent(aIntent: Intent?) {
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
                val setSearchFragment = aIntent.getBooleanExtra("SET_SEARCH_FRAGMENT", false)

                if (term != null) {
                    if (!setSearchFragment) {
                        viewModel.sendIntent(MainActivitySearchIntent(term))
                    } else {
                        viewModel.sendIntent(MainActivitySetSearchFragmentSearchIntent(term))
                    }
                }

                aIntent.removeExtra("SEARCH_TERM")
                aIntent.removeExtra("SET_SEARCH_FRAGMENT")
            }
        }
    }

    override fun onOptionsItemSelected(pMenuItem: MenuItem): Boolean {
        when (pMenuItem.itemId) {
            android.R.id.home -> viewModel.sendIntent(MainActivityHomePressedIntent)
        }

        return true
    }

    override fun onNewIntent(aIntent: Intent) {
        super.onNewIntent(aIntent)
        transformSearchIntent(aIntent)
        intent = aIntent
    }

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {
        viewModel.sendIntent(MainActivityCloseIntent)

        super.onSaveInstanceState(outState, outPersistentState)
    }

    override fun onDestroy() {
        compositeDisposable.dispose()

        super.onDestroy()
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

    override fun onBackPressed() {
        viewModel.sendIntent(MainActivityBackPressedIntent)
    }

    companion object {
        private const val SEARCH_FRAGMENT_TAG = "SEARCH_FRAGMENT"
        private const val BOOKMARK_FRAGMENT_TAG = "BOOKMARK_FRAGMENT"
        private const val SETTINGS_FRAGMENT_TAG = "SETTINGS_FRAGMENT"
        private const val DELAY_MILLIS = 250
    }
}
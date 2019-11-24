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

package org.happypeng.sumatora.android.sumatoradictionary;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.view.GravityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;

import android.view.MenuItem;

import android.os.Bundle;
import android.os.Handler;

import com.google.android.material.navigation.NavigationView;

import org.happypeng.sumatora.android.sumatoradictionary.fragment.DebugFragment;
import org.happypeng.sumatora.android.sumatoradictionary.fragment.DictionaryQueryFragment;
import org.happypeng.sumatora.android.sumatoradictionary.fragment.SettingsFragment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Dictionary extends AppCompatActivity implements SettingsFragment.SettingsFragmentActions {
    private DrawerLayout m_drawer_layout;

    private DictionaryQueryFragment m_dictionaryBookmarkFragment;
    private boolean m_dictionaryBookmarkFragmentShown;

    private DictionaryQueryFragment m_dictionarySearchFragment;
    private boolean m_dictionarySearchFragmentShown;

    private DebugFragment m_debugFragment;
    private boolean m_debugFragmentShown;

    private SettingsFragment m_settingsFragment;
    private boolean m_settingsFragmentShown;

    // Fragment sequence: 1. search (always on) 2. bookmarks or settings 3. if 2 is settings, debug

    private static String SEARCH_FRAGMENT_TAG = "SEARCH_FRAGMENT";
    private static String BOOKMARK_FRAGMENT_TAG = "BOOKMARK_FRAGMENT";
    private static String SETTINGS_FRAGMENT_TAG = "SETTINGS_FRAGMENT";
    private static String DEBUG_FRAGMENT_TAG = "DEBUG_FRAGMENT";

    private NavigationView m_navigationView;

    private Logger m_log;

    private static final int DELAY_MILLIS = 250;

    private void startActivityWithDelay(@NonNull final Class activity) {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(Dictionary.this, activity));
            }
        }, DELAY_MILLIS);
    }

    private void switchToFragment(Fragment aFragment, String aTag) {
        FragmentManager fm = getSupportFragmentManager();

        FragmentTransaction fragmentTransaction = fm.beginTransaction();
        fragmentTransaction.replace(R.id.dictionary_fragment_container, aFragment, aTag);

        // We use our manual stack instead
        // fragmentTransaction.addToBackStack(aTag);

        fragmentTransaction.commit();
    }

    private void switchToSearchFragment() {
        if (m_dictionarySearchFragment == null) {
            if (BuildConfig.DEBUG_DICTIONARY_ACTIVITY) {
                m_log.info("search fragment created");
            }

            m_dictionarySearchFragment = new DictionaryQueryFragment();
            m_dictionarySearchFragment.setParameters(1, null, false,
                    "", false, true, "");
        }

        switchToFragment(m_dictionarySearchFragment, SEARCH_FRAGMENT_TAG);

        m_debugFragmentShown = false;
        m_dictionaryBookmarkFragmentShown = false;
        m_settingsFragmentShown = false;
        m_dictionarySearchFragmentShown = true;

        if (m_navigationView != null) {
            m_navigationView.setCheckedItem(R.id.navigation_view_item_search);
        }
    }

    private void switchToBookmarksFragment() {
        if (m_dictionaryBookmarkFragment == null) {
            m_dictionaryBookmarkFragment = new DictionaryQueryFragment();
            m_dictionaryBookmarkFragment.setParameters(2, "SELECT seq FROM DictionaryBookmark", true,
                    "Bookmarks", true, false, "DictionaryBookmark");
        }

        switchToFragment(m_dictionaryBookmarkFragment, BOOKMARK_FRAGMENT_TAG);

        m_debugFragmentShown = false;
        m_settingsFragmentShown = false;
        m_dictionaryBookmarkFragmentShown = true;
        m_dictionarySearchFragmentShown = false;

        if (m_navigationView != null) {
            m_navigationView.setCheckedItem(R.id.navigation_view_item_bookmarks);
        }
    }

    private void switchToSettingsFragment() {
        if (m_settingsFragment == null) {
            m_settingsFragment = new SettingsFragment();
        }

        switchToFragment(m_settingsFragment, SETTINGS_FRAGMENT_TAG);

        m_debugFragmentShown = false;
        m_settingsFragmentShown = true;
        m_dictionaryBookmarkFragmentShown = false;
        m_dictionarySearchFragmentShown = false;

        if (m_navigationView != null) {
            m_navigationView.setCheckedItem(R.id.navigation_view_item_settings);
        }
    }

    private void switchToDebugFragment() {
        if (m_debugFragment == null) {
            m_debugFragment = new DebugFragment();
        }

        switchToFragment(m_debugFragment, DEBUG_FRAGMENT_TAG);

        m_debugFragmentShown = true;
        m_settingsFragmentShown = true;
        m_dictionaryBookmarkFragmentShown = false;
        m_dictionarySearchFragmentShown = false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (BuildConfig.DEBUG_DICTIONARY_ACTIVITY) {
            m_log = LoggerFactory.getLogger(this.getClass());

            m_log.info("onCreate started");
        }

        setContentView(R.layout.activity_dictionary);

        m_navigationView = (NavigationView) findViewById(R.id.activity_main_navigation_view);
        m_navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem pMenuItem) {
                m_drawer_layout.closeDrawer(GravityCompat.START);

                switch (pMenuItem.getItemId()) {
                    case R.id.navigation_view_item_search:
                        switchToSearchFragment();
                        break;
                    case R.id.navigation_view_item_about:
                        startActivityWithDelay(AboutActivity.class);
                        break;
                    case R.id.navigation_view_item_bookmarks:
                        switchToBookmarksFragment();
                        break;
                    case R.id.navigation_view_item_settings:
                        switchToSettingsFragment();
                        break;
                }

                return true;
            }
        });

        m_drawer_layout = (DrawerLayout) findViewById(R.id.nav_drawer);

        switchToSearchFragment();
    }

    private void transformSearchIntent(Intent aIntent) {
        if (aIntent.hasExtra("query")) {
            aIntent.putExtra("SEARCH_TERM", aIntent.getStringExtra("query"));
        }
    }

    private void displayIntent(Intent aIntent) {
        if (BuildConfig.DEBUG_DICTIONARY_ACTIVITY) {
            if (m_log != null) {
                if (aIntent == null) {
                    m_log.info("intent is null");
                } else {
                    m_log.info("intent action is " + aIntent.getAction());

                    if (aIntent.getExtras() != null) {
                        for (String key : aIntent.getExtras().keySet()) {
                            m_log.info("intent has key " + key);

                            Object val = aIntent.getExtras().get(key);

                            if (val != null) {
                                m_log.info("type " + val.getClass().getName() + " value " + val.toString());
                            } else {
                                m_log.info("value is null");
                            }
                        }
                    } else {
                        m_log.info("intent has no extras");
                    }
                }
            }
        }
    }

    private void processIntent(Intent aIntent) {
        if (BuildConfig.DEBUG_DICTIONARY_ACTIVITY) {
            displayIntent(aIntent);
        }

        if (aIntent != null && aIntent.hasExtra("SEARCH_TERM"))
        {
            if (BuildConfig.DEBUG_DICTIONARY_ACTIVITY) {
                m_log.info("setting search term to " + aIntent.getStringExtra("SEARCH_TERM"));
            }

            if (Intent.ACTION_SEARCH.equals(aIntent.getAction())) {
                if (m_dictionaryBookmarkFragmentShown) {
                    m_dictionaryBookmarkFragment.setIntentSearchTerm(aIntent.getStringExtra("SEARCH_TERM"));
                } else if (m_dictionarySearchFragmentShown) {
                    m_dictionarySearchFragment.setIntentSearchTerm(aIntent.getStringExtra("SEARCH_TERM"));
                }
            } else {
                switchToSearchFragment();
                m_dictionarySearchFragment.setIntentSearchTerm(aIntent.getStringExtra("SEARCH_TERM"));
            }

            aIntent.removeExtra("SEARCH_TERM");
        }
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem pMenuItem) {
        switch (pMenuItem.getItemId()) {
            case android.R.id.home:
                m_drawer_layout.openDrawer(GravityCompat.START);
                break;
        }

        return true;
    }

    @Override
    protected void onNewIntent(Intent aIntent) {
        super.onNewIntent(aIntent);

        transformSearchIntent(aIntent);
        setIntent(aIntent);
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (BuildConfig.DEBUG_DICTIONARY_ACTIVITY) {
            m_log.info("onStop called");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (BuildConfig.DEBUG_DICTIONARY_ACTIVITY) {
            m_log.info("onResume called");
        }

        Intent intent = getIntent();

        if (intent != null && intent.hasExtra("SEARCH_TERM")) {
            processIntent(intent);
        }
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        if (fragment instanceof SettingsFragment) {
            SettingsFragment settingsFragment = (SettingsFragment) fragment;

            settingsFragment.setFragmentActions(this);
        }
    }

    @Override
    public void displayLog() {
        switchToDebugFragment();
    }

    @Override
    public void onBackPressed() {
        if (m_debugFragmentShown) {
            switchToSettingsFragment();
        } else if (m_settingsFragmentShown || m_dictionaryBookmarkFragmentShown) {
            switchToSearchFragment();
        } else {
            finish();
        }

        // We use our manual stack instead

        /* FragmentManager fm = getSupportFragmentManager();
        if (fm.getBackStackEntryCount() > 1) {
            fm.popBackStack();
        } else {
            finish();
        }*/
    }
}

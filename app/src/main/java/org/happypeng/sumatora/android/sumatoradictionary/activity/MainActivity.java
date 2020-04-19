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

package org.happypeng.sumatora.android.sumatoradictionary.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Observer;

import com.google.android.material.navigation.NavigationView;

import org.happypeng.sumatora.android.sumatoradictionary.BuildConfig;
import org.happypeng.sumatora.android.sumatoradictionary.DictionaryApplication;
import org.happypeng.sumatora.android.sumatoradictionary.R;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.Settings;
import org.happypeng.sumatora.android.sumatoradictionary.fragment.BookmarkFragment;
import org.happypeng.sumatora.android.sumatoradictionary.fragment.DebugFragment;
import org.happypeng.sumatora.android.sumatoradictionary.fragment.DictionarySearchFragment;
import org.happypeng.sumatora.android.sumatoradictionary.fragment.QueryFragment;
import org.happypeng.sumatora.android.sumatoradictionary.fragment.SettingsFragment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainActivity extends AppCompatActivity
        implements SettingsFragment.SettingsFragmentActions {
    private DrawerLayout m_drawer_layout;

    private BookmarkFragment m_dictionaryBookmarkFragment;
    private DictionarySearchFragment m_dictionarySearchFragment;
    private DebugFragment m_debugFragment;
    private SettingsFragment m_settingsFragment;

    private DictionaryApplication m_app;

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
                startActivity(new Intent(MainActivity.this, activity));
            }
        }, DELAY_MILLIS);
    }

    private void addFragmentToBackStack(Fragment aFragment, String aTag) {
        FragmentManager fm = getSupportFragmentManager();

        FragmentTransaction fragmentTransaction = fm.beginTransaction();

        fragmentTransaction.replace(R.id.dictionary_fragment_container, aFragment, aTag);
        fragmentTransaction.addToBackStack(aTag);

        fragmentTransaction.commit();
    }

    private void popFragmentFromBackStack(String aTag) {
        FragmentManager fm = getSupportFragmentManager();

        fm.popBackStack(aTag, 0);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (BuildConfig.DEBUG_DICTIONARY_ACTIVITY) {
            m_log = LoggerFactory.getLogger(this.getClass());

            m_log.info("onCreate()");
        }

        m_app = (DictionaryApplication) getApplication();

        setContentView(R.layout.activity_dictionary);

        if (savedInstanceState == null) {
            m_dictionarySearchFragment = new DictionarySearchFragment();
            addFragmentToBackStack(m_dictionarySearchFragment, SEARCH_FRAGMENT_TAG);
        } else {
            FragmentManager fm = getSupportFragmentManager();
            m_dictionarySearchFragment = (DictionarySearchFragment) fm.findFragmentByTag(SEARCH_FRAGMENT_TAG);
        }

        m_navigationView = findViewById(R.id.activity_main_navigation_view);
        m_navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem pMenuItem) {
                m_drawer_layout.closeDrawer(GravityCompat.START);

                switch (pMenuItem.getItemId()) {
                    case R.id.navigation_view_item_search:
                        popFragmentFromBackStack(SEARCH_FRAGMENT_TAG);
                        break;
                    case R.id.navigation_view_item_about:
                        startActivityWithDelay(AboutActivity.class);
                        break;
                    case R.id.navigation_view_item_bookmarks:
                        if (m_dictionaryBookmarkFragment == null) {
                            m_dictionaryBookmarkFragment = new BookmarkFragment();
                        }

                        addFragmentToBackStack(m_dictionaryBookmarkFragment, BOOKMARK_FRAGMENT_TAG);
                        break;
                    case R.id.navigation_view_item_settings:
                        if (m_settingsFragment == null) {
                            m_settingsFragment = new SettingsFragment();
                        }

                        addFragmentToBackStack(new SettingsFragment(), SETTINGS_FRAGMENT_TAG);
                        break;
                }

                return true;
            }
        });

        m_drawer_layout = findViewById(R.id.nav_drawer);
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

            if ((Intent.ACTION_SEARCH.equals(aIntent.getAction()) ||
                Intent.ACTION_MAIN.equals(aIntent.getAction())) &&
                aIntent.hasExtra("SEARCH_TERM")) {
                FragmentManager fm = getSupportFragmentManager();

                String fragmentTag = fm.getBackStackEntryAt(fm.getBackStackEntryCount() - 1).getName();

                if (SEARCH_FRAGMENT_TAG.equals(fragmentTag)) {
                    m_dictionarySearchFragment.setIntentSearchTerm(aIntent.getStringExtra("SEARCH_TERM"));
                } else if (BOOKMARK_FRAGMENT_TAG.equals(fragmentTag)) {
                    m_dictionaryBookmarkFragment.setIntentSearchTerm(aIntent.getStringExtra("SEARCH_TERM"));
                } else {
                    popFragmentFromBackStack(SEARCH_FRAGMENT_TAG);

                    m_dictionarySearchFragment.setIntentSearchTerm(aIntent.getStringExtra("SEARCH_TERM"));
                }

                aIntent.removeExtra("SEARCH_TERM");
            }
        }
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
        if (m_debugFragment == null) {
            m_debugFragment = new DebugFragment();
        }

        addFragmentToBackStack(m_debugFragment, DEBUG_FRAGMENT_TAG);
    }

    @Override
    public void manageDictionaries() {
        startActivityWithDelay(DictionariesManagementActivity.class);
    }

    @Override
    public void setRepositoryURL(String aUrl) {
        if (m_app == null) {
            return;
        }

        m_app.getSettings().setValue(Settings.REPOSITORY_URL, aUrl);
    }

    @Override
    public void onBackPressed() {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.getBackStackEntryCount() > 1) {
            String fragmentTag = fm.getBackStackEntryAt(fm.getBackStackEntryCount() - 2).getName();

            fm.popBackStack();

            if (BOOKMARK_FRAGMENT_TAG.equals(fragmentTag)) {
                m_navigationView.setCheckedItem(R.id.navigation_view_item_bookmarks);
            } else if (SEARCH_FRAGMENT_TAG.equals(fragmentTag)) {
                m_navigationView.setCheckedItem(R.id.navigation_view_item_search);
            } else if (SETTINGS_FRAGMENT_TAG.equals(fragmentTag) || DEBUG_FRAGMENT_TAG.equals(fragmentTag)) {
                m_navigationView.setCheckedItem(R.id.navigation_view_item_settings);
            }
        } else {
            finish();
        }
    }
}

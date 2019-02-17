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

import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.view.MenuItem;

import android.os.Bundle;
import android.os.Handler;

import android.os.StrictMode;

import com.google.android.material.navigation.NavigationView;

import org.happypeng.sumatora.android.sumatoradictionary.fragment.DictionaryBookmarkFragment;
import org.happypeng.sumatora.android.sumatoradictionary.fragment.DictionarySearchFragment;

import java.io.FileDescriptor;

public class Dictionary extends AppCompatActivity {
    private DrawerLayout m_drawer_layout;

    private DictionaryBookmarkFragment m_dictionaryBookmarkFragment;
    private DictionarySearchFragment m_dictionarySearchFragment;

    private final boolean DEVELOPER_MODE = false;

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
        fragmentTransaction.commit();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (DEVELOPER_MODE) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .penaltyDeath()
                    .build());
        }

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_dictionary);

        final NavigationView nv = (NavigationView) findViewById(R.id.activity_main_navigation_view);
        nv.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem pMenuItem) {
                m_drawer_layout.closeDrawer(GravityCompat.START);

                switch (pMenuItem.getItemId()) {
                    case R.id.navigation_view_item_search:
                        if (m_dictionarySearchFragment == null) {
                            m_dictionarySearchFragment = new DictionarySearchFragment();
                        }

                        switchToFragment(m_dictionarySearchFragment, "SEARCH_FRAGMENT");

                        break;
                    case R.id.navigation_view_item_about:
                        startActivityWithDelay(AboutActivity.class);
                        break;
                    case R.id.navigation_view_item_bookmarks:
                        if (m_dictionaryBookmarkFragment == null) {
                            m_dictionaryBookmarkFragment = new DictionaryBookmarkFragment();
                        }

                        switchToFragment(m_dictionaryBookmarkFragment, "BOOKMARK_FRAGMENT");

                        break;
                }

                return true;
            }
        });

        m_drawer_layout = (DrawerLayout) findViewById(R.id.nav_drawer);

        Intent receivedIntent = getIntent();
        String receivedAction = getIntent().getAction();
        String receivedType = receivedIntent.getType();

        if (receivedAction != null && receivedAction.equals(Intent.ACTION_SEND)) {
            Uri receivedUri = (Uri) receivedIntent.getParcelableExtra(Intent.EXTRA_STREAM);

            try {
                ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(receivedUri, "r");
                FileDescriptor fd = pfd.getFileDescriptor();

                // treat

                pfd.close();
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println(e.toString());
            }

            if (m_dictionaryBookmarkFragment == null) {
                m_dictionaryBookmarkFragment = new DictionaryBookmarkFragment();
            }

            switchToFragment(m_dictionaryBookmarkFragment, "BOOKMARK_FRAGMENT");
        } else {
            if (m_dictionarySearchFragment == null) {
                m_dictionarySearchFragment = new DictionarySearchFragment();
            }

            switchToFragment(m_dictionarySearchFragment, "SEARCH_FRAGMENT");
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
}

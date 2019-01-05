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

import android.content.DialogInterface;
import android.app.AlertDialog;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.core.view.GravityCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.room.Room;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.MenuItem;

import android.os.Bundle;
import android.os.AsyncTask;
import android.os.Handler;

import android.os.StrictMode;

import android.widget.ProgressBar;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.EditText;

import com.google.android.material.navigation.NavigationView;

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryControlDao;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryEntry;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryEntryDao;
import org.happypeng.sumatora.android.sumatoradictionary.model.DictionaryViewModel;

public class Dictionary extends AppCompatActivity {
    private Context m_context;
    private int m_grid_rows;

    private boolean m_list_building;

    private RecyclerView m_recyclerView;

    private ImageButton m_search_button;
    private ImageButton m_magic_cross;

    private EditText m_edit_text;

    private ProgressBar m_progress_bar;
    private TextView m_status_text;

    private DrawerLayout m_drawer_layout;

    private void setInPreparation()
    {
        m_status_text.setVisibility(View.VISIBLE);
        m_progress_bar.setVisibility(View.VISIBLE);

        m_progress_bar.setIndeterminate(true);
        m_progress_bar.animate();

        m_search_button.setEnabled(false);

        m_status_text.setText("Loading database...");
    }

    private void setSearchingDictionary()
    {
        m_status_text.setVisibility(View.VISIBLE);
        m_progress_bar.setVisibility(View.VISIBLE);

        m_progress_bar.setIndeterminate(true);
        m_progress_bar.animate();

        m_search_button.setEnabled(false);

        m_status_text.setText("Searching in the dictionary...");
    }

    private void setNoResultsFound()
    {
        m_progress_bar.setIndeterminate(false);
        m_progress_bar.setMax(0);

        m_search_button.setEnabled(true);

        m_status_text.setText("No results found.");

        m_status_text.setVisibility(View.VISIBLE);
        m_progress_bar.setVisibility(View.GONE);
    }

    private void setReady()
    {
        m_progress_bar.setIndeterminate(false);
        m_progress_bar.setMax(0);

        m_search_button.setEnabled(true);

        m_status_text.setText("");

        m_status_text.setVisibility(View.GONE);
        m_progress_bar.setVisibility(View.GONE);
    }

    private final boolean DEVELOPER_MODE = false;

    private static final int DELAY_MILLIS = 250;

    private void startActivityWithDelay(@NonNull final Class activity) {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(m_context, activity));
            }
        }, DELAY_MILLIS);
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

        // Set-up the nice Android-ish UI

        final Toolbar tb = (Toolbar) findViewById(R.id.nav_toolbar);
        setSupportActionBar(tb);

        final ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp);
        actionBar.setDisplayHomeAsUpEnabled(true);

        final NavigationView nv = (NavigationView) findViewById(R.id.activity_main_navigation_view);
        nv.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem pMenuItem) {
                m_drawer_layout.closeDrawer(GravityCompat.START);

                switch (pMenuItem.getItemId()) {
                    case R.id.navigation_view_item_about:
                        startActivityWithDelay(AboutActivity.class);
                        break;
                }

                return true;
            }
        });

        m_drawer_layout = (DrawerLayout) findViewById(R.id.nav_drawer);

        m_context = this;
        m_grid_rows = 1;

        m_search_button = (ImageButton) findViewById(R.id.button);

        m_magic_cross = (ImageButton) findViewById(R.id.magic_cross);
        m_edit_text = (EditText) findViewById(R.id.editText);

        m_progress_bar = (ProgressBar) findViewById(R.id.progressBar);
        m_status_text = (TextView) findViewById(R.id.statusText);

        setInPreparation();

        // Setup recycler view
        m_recyclerView = (RecyclerView) findViewById(R.id.recyclerView);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(RecyclerView.VERTICAL);
        m_recyclerView.setLayoutManager(layoutManager);

        m_edit_text.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() > 0) {
                    m_magic_cross.setVisibility(android.view.View.VISIBLE);
                } else {
                    m_magic_cross.setVisibility(android.view.View.GONE);
                }
            }
        });

        // New room code
        final DictionaryViewModel viewModel = ViewModelProviders.of(this).get(DictionaryViewModel.class);

        viewModel.getDatabaseReady().observe(this,
                new Observer<Boolean>() {
                    @Override
                    public void onChanged(Boolean aDbReady) {
                        if (aDbReady) {
                            setReady();
                        } else {
                            setInPreparation();
                        }
                    }
                });

        if (viewModel.getDatabaseReady().getValue()) {
            setInPreparation();
        } else {
            setReady();
        }

        final DictionaryPagedListAdapter pagedListAdapter = new DictionaryPagedListAdapter();

        m_recyclerView.setAdapter(pagedListAdapter);

        // New search button logic
        viewModel.getSearchEntries().observe(this, new Observer<PagedList<DictionaryEntry>>() {
            @Override
            public void onChanged(PagedList<DictionaryEntry> aList) {
                setReady();

                pagedListAdapter.submitList(aList);
            }
        });

        m_search_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setSearchingDictionary();

                viewModel.search(m_edit_text.getText().toString(), "eng");
            }
        });

        m_magic_cross.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                m_edit_text.setText("");
            }
        });

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

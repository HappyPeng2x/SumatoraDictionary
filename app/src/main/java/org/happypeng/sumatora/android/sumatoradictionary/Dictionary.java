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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;

import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;

import android.os.Bundle;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;

import android.os.StrictMode;

import android.widget.ProgressBar;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.EditText;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.Cursor;

import com.google.android.material.navigation.NavigationView;

import java.util.LinkedList;

public class Dictionary extends AppCompatActivity {
    private SQLiteDatabase m_db;

    private boolean m_db_loading;
    private Context m_context;
    private int m_grid_rows;

    private boolean m_list_building;

    private RecyclerView m_recyclerView;
    private LinkedList<DictionaryElement> m_output_list;
    private RecyclerView.Adapter m_adapter;

    private Handler m_handler;

    private ImageButton m_search_button;
    private ImageButton m_magic_cross;

    private EditText m_edit_text;

    private ProgressBar m_progress_bar;
    private TextView m_status_text;

    private boolean m_search_running;

    private SearchDictionaryTask m_search_task;

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

    private void setBuildingList(int aCount, int aPosition)
    {
        if (!m_list_building) {
            m_status_text.setVisibility(View.VISIBLE);
            m_progress_bar.setVisibility(View.VISIBLE);

            m_progress_bar.setIndeterminate(false);
            m_progress_bar.setMax(aCount);

            m_status_text.setText("Building results list...");
        }

        m_progress_bar.setProgress(aPosition);
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

    private class SearchDictionaryTask extends AsyncTask<String, Void, Void>
    {
        int m_count;

        SearchDictionaryTask()
        {
            m_count = 0;
        }

        protected Void doInBackground(String... a_params)
        {
            if (a_params.length < 1)
            {
                return null;
            }

            String s = a_params[0];

            try {
                m_db.execSQL("DELETE FROM results_seq");

                m_db.execSQL("INSERT INTO results_seq SELECT writings.seq, writings.keb, NULL, " +
                        "(writings.keb = \"" + s + "\"), " +
                        "(writings.keb LIKE \"" + s + "%\"), " +
                        "(writings.keb LIKE \"%" + s + "\"), " +
                        "(writings.keb LIKE \"%" + s + "%\"), " +
                        "NOT writings_prio.ke_pri IS NULL, " +
                        "0, 0, 0, 0, " +
                        "NOT readings_prio.re_pri IS NULL " +
                        "FROM (writings LEFT JOIN writings_prio ON writings.seq=writings_prio.seq) " +
                        " LEFT JOIN readings_prio ON writings.seq=readings_prio.seq " +
                        "WHERE writings.keb LIKE \"%" + s + "%\" ");

                m_db.execSQL("INSERT INTO results_seq SELECT readings.seq, NULL, readings.reb, " +
                        "0, 0, 0, 0, " +
                        "NOT writings_prio.ke_pri IS NULL, " +
                        "(readings.reb = \"" + s + "\"), " +
                        "(readings.reb LIKE \"" + s + "%\"), " +
                        "(readings.reb LIKE \"%" + s + "\"), " +
                        "(readings.reb LIKE \"%" + s + "%\"), " +
                        "NOT readings_prio.re_pri IS NULL " +
                        "FROM (readings LEFT JOIN readings_prio ON readings.seq=readings_prio.seq) " +
                        " LEFT JOIN writings_prio ON readings.seq=writings_prio.seq " +
                        "WHERE readings.reb LIKE \"%" + s + "%\"");

                Cursor cur = m_db.rawQuery
                        ("SELECT DISTINCT seq FROM results_seq " +
                        "ORDER BY keb_exact DESC, reb_exact DESC, keb_prio DESC, reb_prio DESC, " +
                                        "keb_start DESC, reb_start DESC, " +
                                        "keb_end DESC, reb_end DESC, " +
                                        "keb_include DESC, reb_include DESC, " +
                                        "keb, reb", null);

                m_count = cur.getCount();

                while (cur.moveToNext() && !isCancelled()) {
                    DictionaryElement ele =
                            new DictionaryElement(m_db, cur.getInt(0),"eng");

                    m_output_list.add(ele);

                    if (m_handler != null) {
                        Message m = new Message();
                        Bundle b = new Bundle();

                        b.putInt("position", m_output_list.size() - 1);
                        b.putInt("count", m_count);
                        m.setData(b);

                        m_handler.sendMessage(m);
                    }
                }

                cur.close();

                m_db.execSQL("DELETE FROM results_seq");
            } catch (SQLiteException e) {
                e.printStackTrace();
            }

            return null;
        }

        protected void onPostExecute(Void a_param)
        {
            m_search_running = false;
            m_search_button.setImageResource(R.drawable.ic_outline_search_24px);
            m_search_button.setEnabled(true);
            m_list_building = false;

            if (m_count == 0) {
                setNoResultsFound();
            } else {
                setReady();
            }
        }

        protected void onPreExecute()
        {
            m_search_running = true;
            m_search_button.setImageResource(R.drawable.ic_outline_stop_24px);
            m_search_button.setEnabled(true);
        }

        protected void onCancelled(Void a_return)
        {
            m_search_running = false;
            m_search_button.setImageResource(R.drawable.ic_outline_search_24px);
            m_search_button.setEnabled(true);
            m_list_building = false;

            setReady();
        }
    }

    private class LoadDBTask extends AsyncTask<Void, Void, Void>
    {
        private Context m_context;

        LoadDBTask(Context a_context)
        {
            super();

            m_context = a_context;
        }

        protected Void doInBackground(Void... a_params)
        {
            m_db =  DatabaseTool.getDB(m_context);

            return null;
        }

        protected void onPostExecute(Void a_param)
        {
            setReady();

            m_db_loading = false;
        }
    }

    private void DBfailureDialog()
    {
        if (m_db == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            builder.setMessage("Could not open database, aborting.");
            builder.setTitle("Fatal error");
            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    finish();
                }
            });

            AlertDialog dialog = builder.create();

            dialog.show();
        }
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
        m_db_loading = true;
        m_grid_rows = 1;

        m_search_button = (ImageButton) findViewById(R.id.button);

        m_search_running = false;

        m_magic_cross = (ImageButton) findViewById(R.id.magic_cross);
        m_edit_text = (EditText) findViewById(R.id.editText);

        m_progress_bar = (ProgressBar) findViewById(R.id.progressBar);
        m_status_text = (TextView) findViewById(R.id.statusText);

        setInPreparation();

        new LoadDBTask(this).execute();

        // Setup recycler view
        m_recyclerView = (RecyclerView) findViewById(R.id.recyclerView);

        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);

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
                if (!m_search_running) {
                    setReady();
                }

                if (s.length() > 0) {
                    m_magic_cross.setVisibility(android.view.View.VISIBLE);
                } else {
                    m_magic_cross.setVisibility(android.view.View.GONE);
                }
            }
        });

        // Setup search button
        m_search_button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View a_view) {
                if (m_db == null) {
                    DBfailureDialog();

                    return;
                }

                if (!m_search_running) {
                    m_output_list = new LinkedList<DictionaryElement>();
                    m_adapter = new DictionaryAdapter(m_db, m_output_list);
                    m_recyclerView.setAdapter(m_adapter);

                    setSearchingDictionary();

                    m_search_task = new SearchDictionaryTask();
                    m_search_task.execute(m_edit_text.getText().toString());
                } else {
                    m_search_task.cancel(false);
                }
            }
        });

        m_magic_cross.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (m_search_running) {
                    m_search_task.cancel(false);
                }

                m_edit_text.setText("");
            }
        });

        // Setup handler
        m_handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message a_inputMessage) {
                int position = a_inputMessage.getData().getInt("position");
                int count = a_inputMessage.getData().getInt("count");

                if (m_adapter != null) {
                    m_adapter.notifyItemInserted(position);

                    setBuildingList(count, position);
                }
            }
        };
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();

        if (m_db != null) {
            m_db.close();

            m_db = null;
        }
    }

/*    @Override
    public boolean onCreateOptionsMenu(Menu pMenu) {
        getMenuInflater().inflate(R.menu.activity_menu, pMenu);
        return true;
    }*/

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

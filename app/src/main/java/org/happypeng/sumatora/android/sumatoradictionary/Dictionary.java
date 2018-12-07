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

import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.GravityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBar;

import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;

import android.os.Bundle;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;

import android.os.StrictMode;

import android.widget.ProgressBar;
import android.widget.Button;
import android.widget.TextView;
import android.widget.EditText;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.Cursor;

import java.util.LinkedList;

public class Dictionary extends AppCompatActivity {
    private SQLiteDatabase m_db;

    private boolean m_db_loading;
    private Context m_context;
    private int m_grid_rows;

    private RecyclerView m_recyclerView;
    private LinkedList<DictionaryElement> m_output_list;
    private RecyclerView.Adapter m_adapter;

    private Handler m_handler;

    private Button m_search_button;

    private boolean m_search_running;

    private SearchDictionaryTask m_search_task;

    private DrawerLayout m_drawer_layout;

    private void setInPreparation()
    {
        ProgressBar pb = findViewById(R.id.progressBar);
        TextView st = findViewById(R.id.statusText);

        pb.setIndeterminate(true);
        pb.animate();

        m_search_button.setEnabled(false);

        st.setText("Loading database...");
    }

    private void setSearchingDictionary()
    {
        ProgressBar pb = findViewById(R.id.progressBar);
        TextView st = findViewById(R.id.statusText);

        pb.setIndeterminate(true);
        pb.animate();

        m_search_button.setEnabled(false);

        st.setText("Searching in the dictionary...");
    }

    private void setReady()
    {
        ProgressBar pb = findViewById(R.id.progressBar);
        TextView st = findViewById(R.id.statusText);

        pb.setIndeterminate(false);

        m_search_button.setEnabled(true);

        st.setText("");
    }

    private class SearchDictionaryTask extends AsyncTask<String, Void, Void>
    {
        SearchDictionaryTask()
        {
        }

        protected Void doInBackground(String... a_params)
        {
            if (a_params.length < 1)
            {
                return null;
            }

            String s = a_params[0];

            try {
                Cursor cur = m_db.rawQuery
                        ("SELECT DISTINCT writings.seq " +
                                        "FROM (writings LEFT JOIN writings_prio ON writings.seq=writings_prio.seq AND writings.keb_id=writings_prio.keb_id), " +
                                        "(readings LEFT JOIN readings_prio ON readings.seq=readings_prio.seq AND readings.reb_id=readings_prio.reb_id)" +
                                        "WHERE writings.seq=readings.seq " +
                                        "AND (writings.keb LIKE \"%" + s + "%\" OR readings.reb LIKE \"%" + s + "%\") " +
                                        "GROUP BY writings.seq " +
                                        "ORDER BY  " +
                                        " (writings.keb = \"" + s + "\")*(20 - writings.keb_id) DESC, " +
                                        " (readings.reb = \"" + s + "\")*(20 - readings.reb_id) DESC, " +
                                        " (readings.reb LIKE \"" + s + "%\")*(20 - readings.reb_id) DESC, " +
                                        " writings_prio.ke_pri IS NULL ASC, readings_prio.re_pri IS NULL ASC, " +
                                        " (writings.keb LIKE \"" + s + "%\")*(20 - writings.keb_id) DESC, " +
                                        " writings.keb, readings.reb",
                                null);

                while (cur.moveToNext() && !isCancelled()) {
                    DictionaryElement ele =
                            new DictionaryElement(m_db, cur.getInt(0),"eng");

                    m_output_list.add(ele);

                    if (m_handler != null) {
                        Message m = new Message();
                        Bundle b = new Bundle();

                        b.putInt("position", m_output_list.size() - 1);
                        m.setData(b);

                        m_handler.sendMessage(m);
                    }
                }

                cur.close();
            } catch (SQLiteException e) {
                e.printStackTrace();
            }

            return null;
        }

        protected void onPostExecute(Void a_param)
        {
            m_search_running = false;
            m_search_button.setText("Search");
            m_search_button.setEnabled(true);

            setReady();
        }

        protected void onPreExecute()
        {
            m_search_running = true;
            m_search_button.setText("Stop");
            m_search_button.setEnabled(true);
        }

        protected void onCancelled(Void a_return)
        {
            m_search_running = false;
            m_search_button.setText("Search");
            m_search_button.setEnabled(true);

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

        Toolbar tb = (Toolbar) findViewById(R.id.nav_toolbar);
        setSupportActionBar(tb);

/*        ActionBar ab = getSupportActionBar();
        ab.setDisplayHomeAsUpEnabled(true);

        Drawable normalDrawable = getDrawable(R.drawable.ic_outline_menu_24px);
        Drawable wrapDrawable = DrawableCompat.wrap(normalDrawable);
        DrawableCompat.setTint(wrapDrawable, getColor(R.color.mal_color_icon_dark_theme));

        ab.setHomeAsUpIndicator(wrapDrawable);*/

        m_drawer_layout = (DrawerLayout) findViewById(R.id.nav_drawer);

        m_context = this;
        m_db_loading = true;
        m_grid_rows = 1;

        m_search_button = (Button) findViewById(R.id.button);

        m_search_running = false;

        setInPreparation();

        new LoadDBTask(this).execute();

        // Setup recycler view
        m_recyclerView = (RecyclerView) findViewById(R.id.recyclerView);

        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);

        m_recyclerView.setLayoutManager(layoutManager);

        // Setup search button
        findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
            public void onClick(View a_view) {
                if (m_db == null) {
                    DBfailureDialog();

                    return;
                }

                if (!m_search_running) {
                    m_output_list = new LinkedList<DictionaryElement>();
                    m_adapter = new DictionaryAdapter(m_db, m_output_list);
                    m_recyclerView.setAdapter(m_adapter);

                    EditText input_edit = findViewById(R.id.editText);

                    setSearchingDictionary();

                    m_search_task = new SearchDictionaryTask();
                    m_search_task.execute(input_edit.getText().toString());
                } else {
                    m_search_task.cancel(false);
                }
            }
        });

        // Setup handler
        m_handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message a_inputMessage) {
                if (m_adapter != null) {
                    m_adapter.notifyItemInserted(a_inputMessage.getData().getInt("position"));
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

    @Override
    public boolean onCreateOptionsMenu(Menu pMenu) {
        getMenuInflater().inflate(R.menu.activity_menu, pMenu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem pMenuItem) {
        switch (pMenuItem.getItemId()) {
            case R.id.about:
                Intent intent = new Intent(this, AboutActivity.class);
                startActivity(intent);
                return true;
/*            case android.R.id.home:
                m_drawer_layout.openDrawer(GravityCompat.START);
                return true;*/
        }

        return super.onOptionsItemSelected(pMenuItem);
    }
}

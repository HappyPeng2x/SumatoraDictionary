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
import android.support.v7.app.AppCompatActivity;
import android.app.AlertDialog;
import android.content.Context;

import android.support.v7.widget.LinearLayoutManager;
import android.view.View;
import android.view.LayoutInflater;

import android.os.Bundle;
import android.os.AsyncTask;

import android.widget.ProgressBar;
import android.widget.Button;
import android.widget.TextView;
import android.widget.EditText;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.Cursor;

import android.support.v7.widget.RecyclerView;

import java.util.LinkedList;

public class Dictionary extends AppCompatActivity {
    private SQLiteDatabase m_db;
    private boolean m_db_loading;
    private Context m_context;
    private int m_grid_rows;

    private void setInPreparation()
    {
        ProgressBar pb = findViewById(R.id.progressBar);
        Button bu = findViewById(R.id.button);
        TextView st = findViewById(R.id.statusText);

        pb.setIndeterminate(true);
        pb.animate();

        bu.setEnabled(false);

        st.setText("Loading database...");
    }

    private void setReady()
    {
        ProgressBar pb = findViewById(R.id.progressBar);
        Button bu = findViewById(R.id.button);
        TextView st = findViewById(R.id.statusText);

        pb.setIndeterminate(false);

        bu.setEnabled(true);

        st.setText("");
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
            System.err.println("Starting background task...");

            m_db =  DatabaseTool.getDB(m_context);

            System.err.println("Ending background task...");

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dictionary);

        m_context = this;
        m_db_loading = true;
        m_grid_rows = 1;

        setInPreparation();

        new LoadDBTask(this).execute();

        // Setup recycler view
        final RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recyclerView);

        recyclerView.setHasFixedSize(true);

        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);

        recyclerView.setLayoutManager(layoutManager);

        // Setup search button
        findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
            public void onClick(View a_view) {
                if (m_db == null) {
                    DBfailureDialog();

                    return;
                }

                EditText input_edit = findViewById(R.id.editText);

                try {
                    Cursor cur = m_db.rawQuery
                            ("SELECT DISTINCT writings.seq FROM writings, readings WHERE writings.seq=readings.seq " +
                                    "AND (writings.keb LIKE \"%" + input_edit.getText() + "%\" OR readings.reb LIKE \"%" + input_edit.getText() + "%\") " +
                                    "GROUP BY writings.seq " +
                                    "ORDER BY (writings.keb = \"" + input_edit.getText() + "\")*(20 - writings.keb_id) DESC, " +
                                            " (readings.reb = \"" + input_edit.getText() + "\")*(20 - readings.reb_id) DESC, " +
                                            " (writings.keb LIKE \"" + input_edit.getText() + "%\")*(20 - writings.keb_id) DESC, " +
                                            " (readings.reb LIKE \"" + input_edit.getText() + "%\")*(20 - readings.reb_id) DESC",
                            null);
                    LinkedList<DictionaryElement> output_list = new LinkedList<DictionaryElement>();

                    while (cur.moveToNext()) {
                        DictionaryElement ele =
                                new DictionaryElement(m_db, cur.getInt(0));

                        output_list.add(ele);
                    }

                    cur.close();

                    RecyclerView.Adapter adapter = new DictionaryAdapter(m_db, output_list);

                    recyclerView.setAdapter(adapter);
                } catch (SQLiteException e) {
                   e.printStackTrace();
                }
            }
        });
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
}

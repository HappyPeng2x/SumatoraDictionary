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

import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Button;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.TwoLineListItem;

import android.database.sqlite.SQLiteDatabase;

import android.support.v7.widget.RecyclerView;

import java.io.IOException;
import java.io.StringWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedList;
import java.util.Iterator;

import net.java.sen.StringTagger;
import net.java.sen.SenFactory;

import net.java.sen.dictionary.Token;


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

                ArrayList<Token> reuse_list = new ArrayList<Token>();
                List<Token> output_list;
                StringTagger tagger = SenFactory.getStringTagger(null, false);

                EditText input_edit = findViewById(R.id.editText);
                String analyze_string;

                try {
                    analyze_string = input_edit.getText().toString();

                    output_list = tagger.analyze(analyze_string, reuse_list);

                    RecyclerView.Adapter adapter = new DictionaryAdapter(output_list);

                    recyclerView.setAdapter(adapter);
                } catch (IOException e) {
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

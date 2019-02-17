package org.happypeng.sumatora.android.sumatoradictionary;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.fragment.DictionaryBookmarkFragment;
import org.happypeng.sumatora.android.sumatoradictionary.fragment.DictionarySearchFragment;
import org.happypeng.sumatora.android.sumatoradictionary.model.DictionaryBookmarkFragmentModel;
import org.happypeng.sumatora.android.sumatoradictionary.model.DictionaryBookmarkImportActivityModel;
import org.happypeng.sumatora.android.sumatoradictionary.xml.DictionaryBookmarkXML;

import java.io.FileDescriptor;
import java.io.InputStream;
import java.util.List;

public class DictionaryBookmarksImportActivity extends AppCompatActivity {
    private ProgressBar m_progressBar;
    private TextView m_statusText;
    private RecyclerView m_recyclerView;
    private List<DictionarySearchElement> m_bookmarks;

    private void setInPreparation()
    {
        m_statusText.setVisibility(View.VISIBLE);
        m_progressBar.setVisibility(View.VISIBLE);

        m_progressBar.setIndeterminate(true);
        m_progressBar.animate();

        m_statusText.setText("Loading database...");
    }

    private void setReady()
    {
        m_progressBar.setIndeterminate(false);
        m_progressBar.setMax(0);

        m_statusText.setText("");

        m_statusText.setVisibility(View.GONE);
        m_progressBar.setVisibility(View.GONE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dictionary_bookmarks_import);

        // set up toolbar
        final Toolbar tb = (Toolbar) findViewById(R.id.dictionary_bookmarks_import_toolbar);
        setSupportActionBar(tb);

        // set up progress bar
        m_progressBar = (ProgressBar) findViewById(R.id.dictionary_bookmarks_import_progressbar);
        m_statusText = (TextView) findViewById(R.id.dictionary_bookmarks_import_statustext);

        // set up recycler view
        m_recyclerView = (RecyclerView) findViewById(R.id.dictionary_bookmarks_import_recyclerview);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(RecyclerView.VERTICAL);
        m_recyclerView.setLayoutManager(layoutManager);

        final DictionaryBookmarkImportActivityModel viewModel = ViewModelProviders.of(this).get(DictionaryBookmarkImportActivityModel.class);
        final DictionaryListAdapter listAdapter = new DictionaryListAdapter(true);

        listAdapter.submitList(null);

        viewModel.getBookmarkElements().observe(this,
                new Observer<List<DictionarySearchElement>>()
                {
                    @Override
                    public void onChanged(List<DictionarySearchElement> dictionarySearchElements) {
                        listAdapter.submitList(dictionarySearchElements);

                        m_bookmarks = dictionarySearchElements;
                    }
                });

        m_recyclerView.setAdapter(listAdapter);

        setInPreparation();

        // perform intent
        Intent receivedIntent = getIntent();
        String receivedAction = getIntent().getAction();
        String receivedType = receivedIntent.getType();

        if (receivedAction == null) {
            finish();
            return;
        }

        final Uri data = receivedIntent.getData();

        if (data == null) {
            finish();
            return;
        }

        final DictionaryApplication app = (DictionaryApplication) viewModel.getApplication();

        app.getDictionaryDatabase().observe(this, new Observer<DictionaryDatabase>() {
            @Override
            public void onChanged(DictionaryDatabase dictionaryDatabase) {
                if (dictionaryDatabase != null) {
                    viewModel.importBookmarks(data);
                    setReady();
                } else {
                    setInPreparation();
                }
            }
        });

        viewModel.getErrorLive().observe(this, new Observer<Integer>() {
            @Override
            public void onChanged(Integer integer) {
                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(DictionaryBookmarksImportActivity.this);

                alertDialogBuilder.setMessage("Impossible to import file. Please check the contents.");
                alertDialogBuilder.setTitle("Error");
                alertDialogBuilder.setCancelable(true);

                alertDialogBuilder.setPositiveButton("OK",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        });

                alertDialogBuilder.create().show();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean res = super.onCreateOptionsMenu(menu);
        final DictionaryBookmarkImportActivityModel viewModel = ViewModelProviders.of(DictionaryBookmarksImportActivity.this).get(DictionaryBookmarkImportActivityModel.class);

        getMenuInflater().inflate(R.menu.bookmark_import_toolbar_menu, menu);

        menu.findItem(R.id.import_bookmarks).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (m_bookmarks != null) {
                    viewModel.commitBookmarks();

                    finish();
                } else {
                    System.err.println("Clicked on validate before loading bookmarks to import");
                }

                return false;
            }
        });

        menu.findItem(R.id.cancel_import).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                viewModel.deleteAll();

                finish();

                return false;
            }
        });

        colorMenu(menu);

        return res;
    }

    private void colorMenu(Menu aMenu) {
        TypedValue typedValue = new TypedValue();

        TypedArray a = obtainStyledAttributes(typedValue.data, new int[] { R.attr.colorButtonNormal });
        int color = a.getColor(0, 0);

        a.recycle();

        for (int i = 0; i < aMenu.size(); i++) {
            MenuItem item = aMenu.getItem(i);
            Drawable icon = item.getIcon();
            if (icon != null) {
                icon.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            }
        }
    }
}

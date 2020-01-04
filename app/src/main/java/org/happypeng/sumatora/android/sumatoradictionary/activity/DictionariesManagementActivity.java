/* Sumatora Dictionary
        Copyright (C) 2019 Nicolas Centa

        This program is free software: you can redistribute it and/or modify
        it under the terms of the GNU General Public License as published by
        the Free Software Foundation, either version 3 of the License, or
        (at your option) any later version.

        This program is distributed in the hope that it will be useful,
        but WITHOUT ANY WARRANTY; without even the implied warranty of
        MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
        GNU General Public License for more details.

        You should have received a copy of the GNU General Public License
        along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.happypeng.sumatora.android.sumatoradictionary.activity;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.happypeng.sumatora.android.sumatoradictionary.R;
import org.happypeng.sumatora.android.sumatoradictionary.adapter.DictionaryObjectAdapter;
import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary;
import org.happypeng.sumatora.android.sumatoradictionary.db.RemoteDictionaryObject;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.ValueHolder;
import org.happypeng.sumatora.android.sumatoradictionary.model.DictionariesManagementActivityModel;
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.DictionaryObjectViewHolder;

import java.util.List;

public class DictionariesManagementActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {
    public static int AUTH_REQUEST_GET_DICTIONARIES_LIST = 1;

    private DictionariesManagementActivityModel mViewModel;

    public DictionariesManagementActivity() {
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_dictionaries_management);

        final Toolbar tb = (Toolbar) findViewById(R.id.activity_dictionaries_management_toolbar);
        setSupportActionBar(tb);

        final ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        mViewModel = ViewModelProviders.of(this).get(DictionariesManagementActivityModel.class);

        final ProgressBar progressBar = (ProgressBar) findViewById(R.id.activity_dictionaries_management_progressbar);
        final TextView statusText = (TextView) findViewById(R.id.activity_dictionaries_management_statustext);

        mViewModel.getStatus().observe(this,
                new Observer<Integer>() {
                    @Override
                    public void onChanged(Integer integer) {
                        if (integer == DictionariesManagementActivityModel.STATUS_INITIALIZING) {
                            progressBar.setVisibility(View.VISIBLE);
                            statusText.setVisibility(View.VISIBLE);

                            progressBar.setIndeterminate(true);
                            progressBar.animate();

                            statusText.setText("Initializing...");
                        } else if (integer == DictionariesManagementActivityModel.STATUS_DOWNLOADING) {
                            progressBar.setVisibility(View.VISIBLE);
                            statusText.setVisibility(View.VISIBLE);

                            progressBar.setIndeterminate(true);
                            progressBar.animate();

                            statusText.setText("Downloading dictionaries list...");
                        } else if (integer == DictionariesManagementActivityModel.STATUS_READY) {
                            progressBar.setVisibility(View.INVISIBLE);
                            statusText.setVisibility(View.INVISIBLE);

                            progressBar.setIndeterminate(false);
                            progressBar.setMax(0);

                            statusText.setText("Ready");
                        } else if (integer == DictionariesManagementActivityModel.STATUS_DOWNLOAD_ERROR) {
                            progressBar.setVisibility(View.INVISIBLE);
                            statusText.setVisibility(View.VISIBLE);

                            progressBar.setIndeterminate(false);
                            progressBar.setMax(0);

                            String errorText = mViewModel.getDownloadError();

                            if (errorText != null) {
                                statusText.setText("Download error: " + errorText);
                            } else {
                                statusText.setText("Download error");
                            }

                            AlertDialog.Builder builder = new AlertDialog.Builder(DictionariesManagementActivity.this);
                            builder.setMessage("Download error occured, retry?")
                                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {
                                            fetchDictionariesList();
                                        }
                                    })
                                    .setNegativeButton("No", new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {
                                        }
                                    });

                            builder.create().show();
                        }
                    }
                });

        if (ContextCompat.checkSelfPermission(DictionariesManagementActivity.this,
                Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(DictionariesManagementActivity.this,
                    new String[] { Manifest.permission.INTERNET },
                    AUTH_REQUEST_GET_DICTIONARIES_LIST);
        } else {
            fetchDictionariesList();
        }


        final View updatePanel =
                findViewById(R.id.activity_dictionaries_management_update_panel);
        updatePanel.setVisibility(View.GONE);

        /* final RecyclerView updateRv =
                (RecyclerView) findViewById(R.id.activity_dictionaries_management_update_rv);

        final LinearLayoutManager updateLl = new LinearLayoutManager(this);
        updateLl.setOrientation(RecyclerView.VERTICAL);

        updateRv.setLayoutManager(updateLl);

        final DictionaryObjectAdapter updateAdapter = new DictionaryObjectAdapter(false,
                false, null, null);
        updateRv.setAdapter(updateAdapter); */

        final View installPanel =
                findViewById(R.id.activity_dictionaries_management_install_panel);
        installPanel.setVisibility(View.GONE);

        final RecyclerView installRv =
                (RecyclerView) findViewById(R.id.activity_dictionaries_management_install_rv);

        final LinearLayoutManager installLl = new LinearLayoutManager(this);
        installLl.setOrientation(RecyclerView.VERTICAL);

        installRv.setLayoutManager(installLl);

        final DictionaryObjectAdapter<RemoteDictionaryObject> installAdapter = new DictionaryObjectAdapter<>(true,
                false, new DictionaryObjectViewHolder.OnClickListener<RemoteDictionaryObject>() {
            @Override
            public void onClick(final RemoteDictionaryObject aEntry) {

                mViewModel.startDownload(aEntry);

                System.out.println("Install: " + aEntry.description);
            }
        }, null);
        installRv.setAdapter(installAdapter);

        final View downloadsPanel =
                findViewById(R.id.activity_dictionaries_management_downloads_panel);
        downloadsPanel.setVisibility(View.GONE);

        final RecyclerView downloadsRv =
                (RecyclerView) findViewById(R.id.activity_dictionaries_management_downloads_rv);

        final LinearLayoutManager downloadsLl = new LinearLayoutManager(this);
        downloadsLl.setOrientation(RecyclerView.VERTICAL);

        downloadsRv.setLayoutManager(downloadsLl);

        final DictionaryObjectAdapter<RemoteDictionaryObject> downloadsAdapter = new DictionaryObjectAdapter<>(false, false,
                null, null);
        downloadsRv.setAdapter(downloadsAdapter);

        final View removePanel =
                findViewById(R.id.activity_dictionaries_management_remove_panel);
        removePanel.setVisibility(View.GONE);

        final RecyclerView removeRv =
                (RecyclerView) findViewById(R.id.activity_dictionaries_management_remove_rv);

        final LinearLayoutManager removeLl = new LinearLayoutManager(this);
        removeLl.setOrientation(RecyclerView.VERTICAL);

        removeRv.setLayoutManager(removeLl);

        final DictionaryObjectAdapter<InstalledDictionary> removeAdapter = new DictionaryObjectAdapter<>(false,
                true, null,
                new DictionaryObjectViewHolder.OnClickListener<InstalledDictionary>() {
                    @Override
                    public void onClick(InstalledDictionary aEntry) {
                        mViewModel.uninstall(aEntry);

                        System.out.println("Uninstall: " + aEntry.description);
                    }
                });
        removeRv.setAdapter(removeAdapter);

        final ValueHolder<Boolean> hasUpdatable = new ValueHolder<>(false);

        mViewModel.getRemoteDictionaryObjects().observe(this,
                new Observer<List<RemoteDictionaryObject>>() {
                    @Override
                    public void onChanged(List<RemoteDictionaryObject> remoteDictionaryObjects) {
                        if (remoteDictionaryObjects != null && remoteDictionaryObjects.size() > 0 &&
                                (hasUpdatable.getValue() == null ||  !hasUpdatable.getValue())) {
                            installPanel.setVisibility(View.VISIBLE);
                            installAdapter.submitList(remoteDictionaryObjects);

                            updatePanel.setVisibility(View.GONE);
                        } else {
                            installPanel.setVisibility(View.GONE);
                            installAdapter.submitList(null);
                        }
                    }
                });

        mViewModel.getActiveDownloads().observe(this,
                new Observer<List<RemoteDictionaryObject>>() {
                    @Override
                    public void onChanged(List<RemoteDictionaryObject> remoteDictionaryObjects) {
                        if (remoteDictionaryObjects != null && remoteDictionaryObjects.size() > 0) {
                            downloadsPanel.setVisibility(View.VISIBLE);
                            downloadsAdapter.submitList(remoteDictionaryObjects);
                        } else {
                            downloadsPanel.setVisibility(View.GONE);
                            downloadsAdapter.submitList(null);
                        }
                    }
                });

        mViewModel.getInstalledDictionaries().observe(this,
                new Observer<List<InstalledDictionary>>() {
                    @Override
                    public void onChanged(List<InstalledDictionary> remoteDictionaryObjects) {
                        if (remoteDictionaryObjects != null && remoteDictionaryObjects.size() > 0) {
                            removePanel.setVisibility(View.VISIBLE);
                            removeAdapter.submitList(remoteDictionaryObjects);
                        } else {
                            removePanel.setVisibility(View.GONE);
                            removeAdapter.submitList(null);
                        }
                    }
                });

        mViewModel.getUpdatableDictionaries().observe(this,
                new Observer<List<RemoteDictionaryObject>>() {
                    @Override
                    public void onChanged(List<RemoteDictionaryObject> remoteDictionaryObjects) {
                        if (remoteDictionaryObjects != null && remoteDictionaryObjects.size() > 0) {
                            hasUpdatable.setValue(true);

                            installPanel.setVisibility(View.GONE);
                            installAdapter.submitList(null);

                            updatePanel.setVisibility(View.VISIBLE);
                        }
                    }
                });
    }

    @MainThread
    public void fetchDictionariesList() {
        if (mViewModel != null) {
            mViewModel.fetchDictionariesList();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == AUTH_REQUEST_GET_DICTIONARIES_LIST) {
            // Internet access to get dictionaries list was granted
            fetchDictionariesList();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}

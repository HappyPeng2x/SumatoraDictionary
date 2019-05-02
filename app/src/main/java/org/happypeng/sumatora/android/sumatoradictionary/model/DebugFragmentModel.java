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

package org.happypeng.sumatora.android.sumatoradictionary.model;

import android.app.Application;
import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;

public class DebugFragmentModel extends AndroidViewModel {
    final private MutableLiveData<String> mLog;
    final private File mLogsFile;

    public DebugFragmentModel(@NonNull Application application) {
        super(application);

        mLog = new MutableLiveData<>();

        File filesDir = getApplication().getFilesDir();
        File logsDir = new File(filesDir, "logs");

        mLogsFile = new File(logsDir, "log.txt");
    }

    public void readLog() {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(mLogsFile)));

                    String line;
                    StringBuilder sb = new StringBuilder();

                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                        sb.append("\n");
                    }

                    br.close();

                    mLog.postValue(sb.toString());
                } catch (FileNotFoundException e) {
                    mLog.postValue("No log data available.");
                } catch (Exception e) {
                    e.printStackTrace();
                }

                return null;
            }
        }.execute();
    }

    public LiveData<String> getLog() {
        return mLog;
    }

    public File getLogsFile() {
        return mLogsFile;
    }
}

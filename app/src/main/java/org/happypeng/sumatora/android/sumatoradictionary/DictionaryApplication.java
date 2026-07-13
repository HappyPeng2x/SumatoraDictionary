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
        along with this program.  If not, see <http://www.gnu.org/licenses/>.*/

package org.happypeng.sumatora.android.sumatoradictionary;

import android.os.StrictMode;

import androidx.hilt.work.HiltWorkerFactory;
import androidx.multidex.MultiDexApplication;
import androidx.work.Configuration;

import com.google.android.material.color.DynamicColors;

import org.happypeng.sumatora.android.sumatoradictionary.update.DictionaryUpdateWorker;

import javax.inject.Inject;

import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
public class DictionaryApplication extends MultiDexApplication implements Configuration.Provider {
    //private DownloadEventReceiver m_downloadEventReceiver;

    @Inject
    HiltWorkerFactory workerFactory;

    @Override
    public void onCreate() {
        super.onCreate();

        // Android 12+: derive the app's colors from the user's wallpaper instead of the
        // static palette in styles.xml. No-op on older versions.
        DynamicColors.applyToActivitiesIfAvailable(this);

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build());
        }

        DictionaryUpdateWorker.enqueuePeriodic(this);
    }

    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build();
    }
}

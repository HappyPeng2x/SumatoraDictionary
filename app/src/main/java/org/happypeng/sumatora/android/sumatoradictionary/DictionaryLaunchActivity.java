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

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;

import android.os.Build;
import android.os.Bundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// We use this class so that Dictionary is brought in the front.
// It is as if the application had been manually launched and the search word typed inside.
// This is not the behavior expected for PROCESS_TEXT.
// The behavior expected is that a new activity will be created for each app where PROCESS_TEXT is invoked.
// However it is not supported by our current database structure.

public class DictionaryLaunchActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Logger log = LoggerFactory.getLogger(DictionaryLaunchActivity.class);

        log.info("onCreate started");

        Intent inboundIntent = getIntent();
        String receivedAction = inboundIntent.getAction();

        String searchTerm = null;

        log.info("receivedAction = " + receivedAction);

        if (inboundIntent.getExtras() != null) {
            for (String key : inboundIntent.getExtras().keySet()) {
                log.info("inboundIntent has key " + key);

                Object val = inboundIntent.getExtras().get(key);

                if (val != null) {
                    log.info("type " + val.getClass().getName() + " value " + val.toString());
                } else {
                    log.info("value is null");
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                receivedAction != null && receivedAction.equals(Intent.ACTION_PROCESS_TEXT)) {
            searchTerm = inboundIntent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT).toString();
        } else if (receivedAction != null && receivedAction.equals(Intent.ACTION_SEND))  {
            searchTerm = inboundIntent.getStringExtra(Intent.EXTRA_TEXT);
        }

        final Intent notificationIntent = new Intent(this, Dictionary.class);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        if (searchTerm != null) {
            notificationIntent.putExtra("SEARCH_TERM", searchTerm);
        }

        startActivity(notificationIntent);

        finish();
    }
}

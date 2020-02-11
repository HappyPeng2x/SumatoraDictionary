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

package org.happypeng.sumatora.android.sumatoradictionary.db.tools;

import android.content.Context;

import androidx.sqlite.db.SupportSQLiteOpenHelper;

import io.requery.android.database.DatabaseErrorHandler;
import io.requery.android.database.sqlite.SQLiteDatabase;
import io.requery.android.database.sqlite.SQLiteDatabaseConfiguration;

import java.util.Collections;

public class SumatoraSQLiteOpenHelperFactory implements SupportSQLiteOpenHelper.Factory {
    private final Iterable<ConfigurationOptions> configurationOptions;

    @SuppressWarnings("WeakerAccess")
    public SumatoraSQLiteOpenHelperFactory(Iterable<ConfigurationOptions> configurationOptions) {
        this.configurationOptions = configurationOptions;
    }

    public SumatoraSQLiteOpenHelperFactory() {
        this(Collections.<ConfigurationOptions>emptyList());
    }

    @Override
    public SupportSQLiteOpenHelper create(SupportSQLiteOpenHelper.Configuration config) {
        return new CallbackSQLiteOpenHelper(config.context, config.name, config.callback, configurationOptions);
    }

    private static final class CallbackSQLiteOpenHelper extends io.requery.android.database.sqlite.SQLiteOpenHelper {

        private final SupportSQLiteOpenHelper.Callback callback;
        private final Iterable<ConfigurationOptions> configurationOptions;

        CallbackSQLiteOpenHelper(Context context, String name, SupportSQLiteOpenHelper.Callback cb, Iterable<ConfigurationOptions> ops) {
            super(context, name, null, cb.version, new CallbackDatabaseErrorHandler(cb));
            this.callback = cb;
            this.configurationOptions = ops;
        }

        @Override
        public void onConfigure(SQLiteDatabase db) {
            callback.onConfigure(db);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            callback.onCreate(db);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            callback.onUpgrade(db, oldVersion, newVersion);
        }

        @Override
        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            callback.onDowngrade(db, oldVersion, newVersion);
        }

        @Override
        public void onOpen(SQLiteDatabase db) {
            callback.onOpen(db);

            db.addFunction("split_offsets", 4, new SQLiteDatabase.Function() {
                @Override
                public void callback(Args args, Result result) {
                    String arg = args.getString(0);
                    String exp = args.getString(1);
                    String[] elements = arg.split(exp);
                    int pos = args.getInt(2);
                    int def = args.getInt(3);

                    if (pos > elements.length) {
                        result.set(def);
                    } else {
                        result.set(Integer.parseInt(elements[pos]));
                    }
                }
            });
        }

        @Override protected SQLiteDatabaseConfiguration createConfiguration(String path, int openFlags) {
            SQLiteDatabaseConfiguration config = super.createConfiguration(path, openFlags);

            for (ConfigurationOptions option : configurationOptions) {
                config = option.apply(config);
            }

            return config;
        }
    }

    private static final class CallbackDatabaseErrorHandler implements DatabaseErrorHandler {

        private final SupportSQLiteOpenHelper.Callback callback;

        CallbackDatabaseErrorHandler(SupportSQLiteOpenHelper.Callback callback) {
            this.callback = callback;
        }

        @Override
        public void onCorruption(SQLiteDatabase db) {
            callback.onCorruption(db);
        }
    }

    public interface ConfigurationOptions {
        SQLiteDatabaseConfiguration apply(SQLiteDatabaseConfiguration configuration);
    }
}

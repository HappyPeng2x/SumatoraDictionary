/* Sumatora Dictionary
        Copyright (C) 2019 Nicolas Centa

        This program is free software: you can redistribute it and/or modify
        it under the terms of the GNU General Public License as published by
        the Free Software Foundation, either version 3 of the License, or
        (at your option) any later version.

        This program is distributed in the hope that it will be useful,
        but WITHOUT ANY WARRANTY; without even the implied warranty of
        MERCHANTABILITY or FITNESS FOR A     PARTICULAR PURPOSE.  See the
        GNU General Public License for more details.

        You should have received a copy of the GNU General Public License
        along with this program.  If not, see <http://www.gnu.org/licenses/>.*/

package org.happypeng.sumatora.android.sumatoradictionary.db;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import org.happypeng.sumatora.android.sumatoradictionary.BuildConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class PersistentDatabaseParameters {
    public static final String DATABASE_NAME = "JMdict.db";
    public static final String PERSISTENT_DATABASE_NAME = "PersistentDatabase.db";

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            Logger log;

            if (BuildConfig.DEBUG_DB_MIGRATION) {
                log = LoggerFactory.getLogger(this.getClass());

                log.info("Starting database migration");
            }

            database.execSQL("CREATE TABLE IF NOT EXISTS DictionarySearchResult (`entryOrder` INTEGER NOT NULL, `seq` INTEGER NOT NULL, `readingsPrio` TEXT, `readings` TEXT, `writingsPrio` TEXT, `writings` TEXT, `pos` TEXT, `xref` TEXT, `ant` TEXT, `misc` TEXT, `lsource` TEXT, `dial` TEXT, `s_inf` TEXT, `field` TEXT, `lang` TEXT, `gloss` TEXT, PRIMARY KEY(`seq`))");
            database.execSQL("CREATE TABLE IF NOT EXISTS DictionaryBookmarkElement (`entryOrder` INTEGER NOT NULL, `seq` INTEGER NOT NULL, `readingsPrio` TEXT, `readings` TEXT, `writingsPrio` TEXT, `writings` TEXT, `pos` TEXT, `xref` TEXT, `ant` TEXT, `misc` TEXT, `lsource` TEXT, `dial` TEXT, `s_inf` TEXT, `field` TEXT, `lang` TEXT, `gloss` TEXT, `bookmark` INTEGER NOT NULL, PRIMARY KEY(`seq`))");
            database.execSQL("CREATE TABLE IF NOT EXISTS DictionaryBookmarkImport (`entryOrder` INTEGER NOT NULL, `seq` INTEGER NOT NULL, `readingsPrio` TEXT, `readings` TEXT, `writingsPrio` TEXT, `writings` TEXT, `pos` TEXT, `xref` TEXT, `ant` TEXT, `misc` TEXT, `lsource` TEXT, `dial` TEXT, `s_inf` TEXT, `field` TEXT, `lang` TEXT, `gloss` TEXT, `bookmark` INTEGER NOT NULL, PRIMARY KEY(`seq`))");
            database.execSQL("CREATE TABLE IF NOT EXISTS DictionaryBookmark (`seq` INTEGER NOT NULL, `bookmark` INTEGER NOT NULL, PRIMARY KEY(`seq`))");

            if (BuildConfig.DEBUG_DB_MIGRATION) {
                log.info("Database migration ended");
            }
        }
    };

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            Logger log;

            if (BuildConfig.DEBUG_DB_MIGRATION) {
                log = LoggerFactory.getLogger(this.getClass());

                log.info("Starting database migration");
            }

            database.execSQL("DROP TABLE IF EXISTS DictionaryBookmarkElement");

            database.execSQL("CREATE TABLE IF NOT EXISTS DictionaryDisplayElement (`ref` INTEGER NOT NULL, `entryOrder` INTEGER NOT NULL, `seq` INTEGER NOT NULL, `readingsPrio` TEXT, `readings` TEXT, `writingsPrio` TEXT, `writings` TEXT, `pos` TEXT, `xref` TEXT, `ant` TEXT, `misc` TEXT, `lsource` TEXT, `dial` TEXT, `s_inf` TEXT, `field` TEXT, `lang` TEXT, `gloss` TEXT, PRIMARY KEY(`ref`, `seq`))");
            database.execSQL("CREATE TABLE IF NOT EXISTS DictionaryElement (`ref` INTEGER NOT NULL, `entryOrder` INTEGER NOT NULL, `seq` INTEGER NOT NULL, PRIMARY KEY(`ref`, `seq`))");

            if (BuildConfig.DEBUG_DB_MIGRATION) {
                log.info("Database migration ended");
            }
        }
    };

    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            Logger log;

            if (BuildConfig.DEBUG_DB_MIGRATION) {
                log = LoggerFactory.getLogger(this.getClass());

                log.info("Starting database migration");
            }

            database.execSQL("DROP TABLE IF EXISTS DictionarySearchResult");

            if (BuildConfig.DEBUG_DB_MIGRATION) {
                log.info("Database migration ended");
            }
        }
    };

    public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            Logger log;

            if (BuildConfig.DEBUG_DB_MIGRATION) {
                log = LoggerFactory.getLogger(this.getClass());

                log.info("Starting database migration");
            }

            database.execSQL("DROP TABLE IF EXISTS DictionaryBookmarkImport");
            database.execSQL("CREATE TABLE IF NOT EXISTS DictionaryBookmarkImport (`ref` INTEGER NOT NULL, `seq` INTEGER NOT NULL, PRIMARY KEY(`ref`, `seq`))");

            database.execSQL("CREATE TABLE IF NOT EXISTS InstalledDictionary (`description` TEXT, `type` TEXT NOT NULL, `lang` TEXT NOT NULL, `version` INTEGER NOT NULL, `date` INTEGER NOT NULL, `file` TEXT NOT NULL, PRIMARY KEY(`type`, `lang`))");
            database.execSQL("CREATE TABLE IF NOT EXISTS RemoteDictionaryObject (`description` TEXT, `type` TEXT NOT NULL, `lang` TEXT NOT NULL, `version` INTEGER NOT NULL, `date` INTEGER NOT NULL, `file` TEXT NOT NULL, `localFile` TEXT NOT NULL, `downloadId` INTEGER NOT NULL, PRIMARY KEY(`type`, `lang`))");
            database.execSQL("CREATE TABLE IF NOT EXISTS LocalDictionaryObject (`description` TEXT, `type` TEXT NOT NULL, `lang` TEXT NOT NULL, `version` INTEGER NOT NULL, `date` INTEGER NOT NULL, `file` TEXT NOT NULL, PRIMARY KEY(`type`, `lang`))");
            database.execSQL("CREATE TABLE IF NOT EXISTS AssetDictionaryObject (`description` TEXT, `type` TEXT NOT NULL, `lang` TEXT NOT NULL, `version` INTEGER NOT NULL, `date` INTEGER NOT NULL, `file` TEXT NOT NULL, PRIMARY KEY(`type`, `lang`))");


            if (BuildConfig.DEBUG_DB_MIGRATION) {
                log.info("Database migration ended");
            }
        }
    };

    public static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            Logger log;

            if (BuildConfig.DEBUG_DB_MIGRATION) {
                log = LoggerFactory.getLogger(this.getClass());

                log.info("Starting database migration");
            }

            database.execSQL("DROP TABLE IF EXISTS DictionaryDisplayElement");

            database.execSQL("CREATE TABLE IF NOT EXISTS DictionaryDisplayElement (`ref` INTEGER NOT NULL, `entryOrder` INTEGER NOT NULL, `seq` INTEGER NOT NULL, `readingsPrio` TEXT, `readings` TEXT, `writingsPrio` TEXT, `writings` TEXT, `pos` TEXT, `xref` TEXT, `ant` TEXT, `misc` TEXT, `lsource` TEXT, `dial` TEXT, `s_inf` TEXT, `field` TEXT, `lang` TEXT, `lang_setting` TEXT, `gloss` TEXT, `example_sentences` TEXT, PRIMARY KEY(`ref`, `seq`))");
            database.execSQL("CREATE TABLE IF NOT EXISTS PersistentLanguageSettings (`ref` INTEGER NOT NULL, `lang` TEXT NOT NULL, `backupLang` TEXT, PRIMARY KEY(`ref`))");
        }
    };

    public static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            Logger log;

            if (BuildConfig.DEBUG_DB_MIGRATION) {
                log = LoggerFactory.getLogger(this.getClass());

                log.info("Starting database migration");
            }

            database.execSQL("DROP TABLE IF EXISTS DictionaryDisplayElement");

            database.execSQL("CREATE TABLE IF NOT EXISTS DictionaryDisplayElement (`ref` INTEGER NOT NULL, `entryOrder` INTEGER NOT NULL, `seq` INTEGER NOT NULL, `readingsPrio` TEXT, `readings` TEXT, `writingsPrio` TEXT, `writings` TEXT, `pos` TEXT, `xref` TEXT, `ant` TEXT, `misc` TEXT, `lsource` TEXT, `dial` TEXT, `s_inf` TEXT, `field` TEXT, `lang` TEXT, `lang_setting` TEXT, `gloss` TEXT, `example_sentences` TEXT, `example_translations` TEXT, PRIMARY KEY(`ref`, `seq`))");
        }
    };

    public static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            Logger log;

            if (BuildConfig.DEBUG_DB_MIGRATION) {
                log = LoggerFactory.getLogger(this.getClass());

                log.info("Starting database migration");
            }

            database.execSQL("ALTER TABLE DictionaryBookmark ADD COLUMN memo TEXT");

            database.execSQL("DROP TABLE IF EXISTS DictionaryBookmarkImport");
            database.execSQL("CREATE TABLE IF NOT EXISTS DictionaryBookmarkImport (`ref` INTEGER NOT NULL, `seq` INTEGER NOT NULL, `bookmark` INTEGER NOT NULL, `memo` TEXT, PRIMARY KEY(`ref`, `seq`))");

            database.execSQL("DROP TABLE IF EXISTS DictionarySearchElement");
            database.execSQL("CREATE TABLE IF NOT EXISTS DictionarySearchElement (`ref` INTEGER NOT NULL, `entryOrder` INTEGER NOT NULL, `seq` INTEGER NOT NULL, `readingsPrio` TEXT, `readings` TEXT, `writingsPrio` TEXT, `writings` TEXT, `pos` TEXT, `xref` TEXT, `ant` TEXT, `misc` TEXT, `lsource` TEXT, `dial` TEXT, `s_inf` TEXT, `field` TEXT, `lang` TEXT, `lang_setting` TEXT, `gloss` TEXT, `example_sentences` TEXT, `example_translations` TEXT, `bookmark` INTEGER NOT NULL, `memo` TEXT, PRIMARY KEY(`ref`, `seq`))");
        }
    };

    public static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            Logger log;

            if (BuildConfig.DEBUG_DB_MIGRATION) {
                log = LoggerFactory.getLogger(this.getClass());

                log.info("Starting database migration");
            }

            database.execSQL("ALTER TABLE DictionaryBookmark ADD COLUMN tags TEXT");

            database.execSQL("DROP TABLE IF EXISTS DictionaryBookmarkImport");
            database.execSQL("CREATE TABLE IF NOT EXISTS DictionaryBookmarkImport (`ref` INTEGER NOT NULL, `seq` INTEGER NOT NULL, `bookmark` INTEGER NOT NULL, `memo` TEXT, `tags` TEXT, PRIMARY KEY(`ref`, `seq`))");

            database.execSQL("DROP TABLE IF EXISTS DictionarySearchElement");
            database.execSQL("CREATE TABLE IF NOT EXISTS DictionarySearchElement (`ref` INTEGER NOT NULL, `entryOrder` INTEGER NOT NULL, `seq` INTEGER NOT NULL, `readingsPrio` TEXT, `readings` TEXT, `writingsPrio` TEXT, `writings` TEXT, `pos` TEXT, `xref` TEXT, `ant` TEXT, `misc` TEXT, `lsource` TEXT, `dial` TEXT, `s_inf` TEXT, `field` TEXT, `lang` TEXT, `lang_setting` TEXT, `gloss` TEXT, `example_sentences` TEXT, `example_translations` TEXT, `bookmark` INTEGER NOT NULL, `memo` TEXT, `tags` TEXT, PRIMARY KEY(`ref`, `seq`))");

            database.execSQL("CREATE TABLE IF NOT EXISTS DictionaryBookmarkTag (`seq` INTEGER NOT NULL, `tag` TEXT NOT NULL, PRIMARY KEY(`seq`, `tag`))");
        }
    };

    public static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("DROP TABLE IF EXISTS DictionarySearchElement");
            database.execSQL("CREATE TABLE IF NOT EXISTS DictionarySearchElement ("
                    + "`ref` INTEGER NOT NULL, `entryOrder` INTEGER NOT NULL, `seq` INTEGER NOT NULL, "
                    + "`readingsPrio` TEXT, `readings` TEXT, `writingsPrio` TEXT, `writings` TEXT, "
                    + "`pos` TEXT, `xref` TEXT, `ant` TEXT, `misc` TEXT, `lsource` TEXT, `dial` TEXT, "
                    + "`s_inf` TEXT, `field` TEXT, `lang` TEXT, `lang_setting` TEXT, `gloss` TEXT, "
                    + "`example_sentences` TEXT, `example_translations` TEXT, "
                    + "`bookmark` INTEGER NOT NULL, `memo` TEXT, `tags` TEXT, `furigana` TEXT, "
                    + "PRIMARY KEY(`ref`, `seq`))");
        }
    };

    public static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("DROP TABLE IF EXISTS DictionarySearchElement");
            database.execSQL("CREATE TABLE IF NOT EXISTS DictionarySearchElement ("
                    + "`ref` INTEGER NOT NULL, `entryOrder` INTEGER NOT NULL, `seq` INTEGER NOT NULL, "
                    + "`readingsPrio` TEXT, `readings` TEXT, `writingsPrio` TEXT, `writings` TEXT, "
                    + "`pos` TEXT, `xref` TEXT, `ant` TEXT, `misc` TEXT, `lsource` TEXT, `dial` TEXT, "
                    + "`s_inf` TEXT, `field` TEXT, `lang` TEXT, `lang_setting` TEXT, `gloss` TEXT, "
                    + "`example_sentences` TEXT, `example_translations` TEXT, "
                    + "`bookmark` INTEGER NOT NULL, `memo` TEXT, `tags` TEXT, `furigana` TEXT, "
                    + "`score` INTEGER NOT NULL, `stagk` TEXT, `stagr` TEXT, "
                    + "`example_matched_tokens` TEXT, `deinflection_label` TEXT, "
                    + "`is_proper_noun` INTEGER NOT NULL, `proper_noun_types` TEXT, "
                    + "PRIMARY KEY(`ref`, `seq`))");
        }
    };

    // Schema v2 migration: DictionarySearchElement moves from a flat DictionaryEntry-shaped
    // cache row to entry_id/form_id + match metadata (Database.md "Query Result Shape").
    // InstalledDictionary/AssetDictionaryObject/RemoteDictionaryObject/LocalDictionaryObject
    // hold no durable user data (just dictionary-file bookkeeping re-derived from
    // dictionaries.xml/downloads at startup) so they're recreated empty rather than migrated;
    // DictionaryBookmark/DictionaryBookmarkTag/PersistentSetting/PersistentLanguageSettings are
    // untouched.
    public static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // DictionaryElement (the reverse-search staging table) is no longer needed: schema v2's
            // leaner DictionarySearchElement means reverse gloss search can insert directly in one
            // pass instead of staging (ref, entryOrder, seq) rows before a second join+delete pass.
            database.execSQL("DROP TABLE IF EXISTS DictionaryElement");

            database.execSQL("DROP TABLE IF EXISTS DictionarySearchElement");
            database.execSQL("CREATE TABLE IF NOT EXISTS DictionarySearchElement ("
                    + "`ref` INTEGER NOT NULL, `entryOrder` INTEGER NOT NULL, `entry_id` INTEGER NOT NULL, "
                    + "`seq` INTEGER NOT NULL, `form_id` INTEGER, `match_kind` TEXT, `matched_text` TEXT, "
                    + "`original_query` TEXT, `dictionary_form` TEXT, `deinflection_label` TEXT, "
                    + "`rank` INTEGER NOT NULL, `bookmark` INTEGER NOT NULL, `memo` TEXT, `tags` TEXT, "
                    + "PRIMARY KEY(`ref`, `entry_id`))");

            database.execSQL("DROP TABLE IF EXISTS InstalledDictionary");
            database.execSQL("CREATE TABLE IF NOT EXISTS InstalledDictionary (`description` TEXT, `type` TEXT NOT NULL, `lang` TEXT NOT NULL, `version` INTEGER NOT NULL, `date` INTEGER NOT NULL, `file` TEXT NOT NULL, PRIMARY KEY(`type`, `lang`))");

            database.execSQL("DROP TABLE IF EXISTS AssetDictionaryObject");
            database.execSQL("CREATE TABLE IF NOT EXISTS AssetDictionaryObject (`description` TEXT, `type` TEXT NOT NULL, `lang` TEXT NOT NULL, `version` INTEGER NOT NULL, `date` INTEGER NOT NULL, `file` TEXT NOT NULL, PRIMARY KEY(`type`, `lang`))");

            database.execSQL("DROP TABLE IF EXISTS RemoteDictionaryObject");
            database.execSQL("CREATE TABLE IF NOT EXISTS RemoteDictionaryObject (`description` TEXT, `type` TEXT NOT NULL, `lang` TEXT NOT NULL, `version` INTEGER NOT NULL, `date` INTEGER NOT NULL, `file` TEXT NOT NULL, `localFile` TEXT NOT NULL, `downloadId` INTEGER NOT NULL, PRIMARY KEY(`type`, `lang`))");

            database.execSQL("DROP TABLE IF EXISTS LocalDictionaryObject");
            database.execSQL("CREATE TABLE IF NOT EXISTS LocalDictionaryObject (`description` TEXT, `type` TEXT NOT NULL, `lang` TEXT NOT NULL, `version` INTEGER NOT NULL, `date` INTEGER NOT NULL, `file` TEXT NOT NULL, PRIMARY KEY(`type`, `lang`))");
        }
    };
}

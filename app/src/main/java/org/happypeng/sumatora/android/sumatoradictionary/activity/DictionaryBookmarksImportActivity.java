package org.happypeng.sumatora.android.sumatoradictionary.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import org.happypeng.sumatora.android.sumatoradictionary.R;
import org.happypeng.sumatora.android.sumatoradictionary.fragment.BookmarkImportFragment;

public class DictionaryBookmarksImportActivity extends AppCompatActivity {
    private static String BOOKMARKS_IMPORT_FRAGMENT_TAG = "BOOKMARKS_IMPORT_FRAGMENT";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_dictionary_bookmarks_import);

        BookmarkImportFragment fragment = new BookmarkImportFragment();

        FragmentManager fm = getSupportFragmentManager();

        FragmentTransaction fragmentTransaction = fm.beginTransaction();
        fragmentTransaction.replace(R.id.dictionary_bookmarks_import_fragment_container, fragment,
                BOOKMARKS_IMPORT_FRAGMENT_TAG);

        fragmentTransaction.commit();

        Intent receivedIntent = getIntent();
        String receivedAction = getIntent().getAction();

        if (receivedAction == null) {
            finish();
            return;
        }

        final Uri data = receivedIntent.getData();

        if (data == null) {
            finish();
            return;
        }

        fragment.processUri(data);
    }
}

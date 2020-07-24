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

package org.happypeng.sumatora.android.sumatoradictionary.component;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmark;

import java.io.File;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ActivityContext;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.android.scopes.ActivityRetainedScoped;
import dagger.hilt.android.scopes.ActivityScoped;
import dagger.hilt.android.scopes.FragmentScoped;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.observers.DisposableCompletableObserver;
import io.reactivex.rxjava3.schedulers.Schedulers;

@FragmentScoped
public class BookmarkShareComponent {
    private static final String AUTHORITY = "org.happypeng.sumatora.android.sumatoradictionary.fileprovider";

    final Context context;
    final PersistentDatabaseComponent persistentDatabaseComponent;
    final BookmarkComponent bookmarkComponent;


    @Inject
    BookmarkShareComponent(@ActivityContext final Context context,
                           final PersistentDatabaseComponent persistentDatabaseComponent,
                           final BookmarkComponent bookmarkComponent) {
        this.context = context;
        this.persistentDatabaseComponent = persistentDatabaseComponent;
        this.bookmarkComponent = bookmarkComponent;
    }

    public Disposable shareBookmarks() {
        File parentDir = new File(context.getFilesDir(), "bookmarks");
        final File outputFile = new File(parentDir, "bookmarks.json");

        parentDir.mkdirs();

        return Completable.fromAction(() -> {
            List<DictionaryBookmark> bookmarks = persistentDatabaseComponent.getDatabase()
                    .dictionaryBookmarkDao().getAll();

            ObjectMapper mapper = new ObjectMapper();
            mapper.writeValue(outputFile, bookmarks);
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(new DisposableCompletableObserver() {
                    @Override
                    public void onComplete() {
                        Uri contentUri = FileProvider.getUriForFile
                                (context, AUTHORITY, outputFile);

                        Intent sharingIntent = new Intent(Intent.ACTION_SEND);

                        sharingIntent.setType("text/*");
                        sharingIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                        context.startActivity(Intent.createChooser(sharingIntent, "Share bookmarks"));
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        Toast.makeText(context, "Bookmarks sharing failed...", Toast.LENGTH_LONG).show();

                        System.err.println(e.toString());
                    }
                });
    }
}

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

package org.happypeng.sumatora.android.sumatoradictionary.fragment;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;

import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.happypeng.sumatora.android.sumatoradictionary.R;
import org.happypeng.sumatora.android.sumatoradictionary.model.DebugFragmentModel;

public class DebugFragment extends Fragment {
    private static final String AUTHORITY = "org.happypeng.sumatora.android.sumatoradictionary.fileprovider";

    private TextView mTextView;
    private DebugFragmentModel mViewModel;

    public DebugFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final AppCompatActivity activity = (AppCompatActivity) getActivity();
        final View view = inflater.inflate(R.layout.fragment_debug, container, false);

        mViewModel = ViewModelProviders.of(getActivity()).get(DebugFragmentModel.class);

        final Toolbar tb = (Toolbar) view.findViewById(R.id.debug_fragment_toolbar);
        activity.setSupportActionBar(tb);

        final ActionBar actionBar = activity.getSupportActionBar();
        actionBar.setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp);
        actionBar.setDisplayHomeAsUpEnabled(true);

        setHasOptionsMenu(true);

        mTextView = (TextView) view.findViewById(R.id.debug_text_view);

        mViewModel.getLog().observe(this,
                new Observer<String>() {
                    @Override
                    public void onChanged(String s) {
                        mTextView.setText(s);
                    }
                });

        mViewModel.readLog();

        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        inflater.inflate(R.menu.debug_toolbar_menu, menu);

        menu.findItem(R.id.debug_fragment_share).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                Uri contentUri = FileProvider.getUriForFile
                        (getActivity().getApplicationContext(),
                                AUTHORITY, mViewModel.getLogsFile());

                Intent sharingIntent = new Intent(Intent.ACTION_SEND);

                sharingIntent.setType("text/*");
                sharingIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                startActivity(Intent.createChooser(sharingIntent, "Share logs"));

                return false;
            }
        });

        menu.findItem(R.id.debug_fragment_reload).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                mViewModel.readLog();

                return false;
            }
        });

        colorMenu(menu);
    }

    private void colorMenu(Menu aMenu) {
        Context ctx = getContext();

        if (ctx == null)
            return;

        TypedValue typedValue = new TypedValue();

        TypedArray a = ctx.obtainStyledAttributes(typedValue.data, new int[] { R.attr.colorButtonNormal });
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

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

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.happypeng.sumatora.android.sumatoradictionary.R;

public class SettingsFragment extends Fragment {
    public interface SettingsFragmentActions {
        void displayLog();
        void manageDictionaries();
    }

    private SettingsFragmentActions mActions;

    public SettingsFragment() {
    }

    public void setFragmentActions(SettingsFragmentActions aActions) {
        mActions = aActions;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final AppCompatActivity activity = (AppCompatActivity) getActivity();
        final View view = inflater.inflate(R.layout.fragment_settings, container, false);

        final Toolbar tb = (Toolbar) view.findViewById(R.id.settings_fragment_toolbar);
        activity.setSupportActionBar(tb);

        final ActionBar actionBar = activity.getSupportActionBar();
        actionBar.setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp);
        actionBar.setDisplayHomeAsUpEnabled(true);

        view.findViewById(R.id.settings_display_log).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mActions != null) {
                    mActions.displayLog();
                }
            }
        });

        view.findViewById(R.id.settings_manage_dictionaries).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mActions != null) {
                            mActions.manageDictionaries();
                        }
                    }
                }
        );

        return view;
    }
}

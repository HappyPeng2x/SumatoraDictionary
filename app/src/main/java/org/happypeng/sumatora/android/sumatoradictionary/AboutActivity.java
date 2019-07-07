/* Sumatora Dictionary
        Copyright (C) 2018 Nicolas Centa

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

import android.content.Context;

import android.content.Intent;

import android.content.pm.PackageManager;
import android.net.Uri;

import androidx.core.content.ContextCompat;

import com.danielstone.materialaboutlibrary.MaterialAboutActivity;

import com.danielstone.materialaboutlibrary.items.MaterialAboutItemOnClickAction;
import com.danielstone.materialaboutlibrary.model.MaterialAboutList;
import com.danielstone.materialaboutlibrary.model.MaterialAboutCard;
import com.danielstone.materialaboutlibrary.items.MaterialAboutTitleItem;
import com.danielstone.materialaboutlibrary.items.MaterialAboutActionItem;
import com.danielstone.materialaboutlibrary.ConvenienceBuilder;

public class AboutActivity extends MaterialAboutActivity {
    @Override
    protected MaterialAboutList getMaterialAboutList(Context context) {
        final Context activity_context = context;

        String versionName;

        try {
            versionName = context.getPackageManager().getPackageInfo("org.happypeng.sumatora.android.sumatoradictionary", 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            versionName = "";
        }

        return new MaterialAboutList.Builder()
                .addCard(new MaterialAboutCard.Builder()
                        .addItem(new MaterialAboutTitleItem.Builder()
                                .text("Sumatora Dictionary")
                                .icon(R.drawable.ic_sumatora_icon)
                                .build())
                        .addItem(new MaterialAboutActionItem.Builder()
                                .text("Version")
                                .subText(versionName)
                                .icon(R.drawable.ic_outline_info_24px)
                                .build())
                        .addItem(new MaterialAboutActionItem.Builder()
                                .text("License")
                                .subText("GPLv3")
                                .icon(R.drawable.ic_outline_class_24px)
                                .setOnClickAction(new MaterialAboutItemOnClickAction() {
                                    @Override
                                    public void onClick() {
                                        Intent intent = new Intent(activity_context, LicenseActivity.class);
                                        intent.putExtra("asset", "GPLv3.txt");
                                        startActivity(intent);
                                    }
                                })
                                .build())
                        .addItem(new MaterialAboutActionItem.Builder()
                                .text("Please refer to the items below for other credits.")
                                .build())
                        .build())

                .addCard(new MaterialAboutCard.Builder()
                        .title("JMDict")
                        .addItem(new MaterialAboutActionItem.Builder()
                                .text("Copyright")
                                .icon(R.drawable.ic_outline_copyright_24px)
                                .subText("James William Breen and The Electronic Dictionary Research and Development Group")
                                .build())
                        .addItem(new MaterialAboutActionItem.Builder()
                            .text("License")
                            .subText("Creative Commons Attribution-ShareAlike License (V3.0)")
                            .icon(R.drawable.ic_outline_class_24px)
                            .setOnClickAction(new MaterialAboutItemOnClickAction() {
                            @Override
                            public void onClick() {
                                Intent intent = new Intent(activity_context, LicenseActivity.class);
                                intent.putExtra("asset", "CC-by-SA-3.0.txt");
                                startActivity(intent);
                            }
                        })
                        .build())
                        .addItem(ConvenienceBuilder.createWebsiteActionItem(activity_context,
                                ContextCompat.getDrawable(activity_context, R.drawable.ic_outline_public_24px), "Visit Website",
                                true,
                                Uri.parse("https://www.edrdg.org/jmdict/j_jmdict.html")))
                        .build())

                .addCard(new MaterialAboutCard.Builder()
                        .title("Material About Library")
                        .addItem(new MaterialAboutActionItem.Builder()
                                .text("Copyright")
                                .icon(R.drawable.ic_outline_copyright_24px)
                                .subText("Daniel Stone")
                                .build())
                        .addItem(new MaterialAboutActionItem.Builder()
                                .text("License")
                                .subText("Apache License, Version 2.0")
                                .icon(R.drawable.ic_outline_class_24px)
                                .setOnClickAction(new MaterialAboutItemOnClickAction() {
                                    @Override
                                    public void onClick() {
                                        Intent intent = new Intent(activity_context, LicenseActivity.class);
                                        intent.putExtra("asset", "Apache-2.0.txt");
                                        startActivity(intent);
                                    }
                                })
                                .build())
                        .addItem(ConvenienceBuilder.createWebsiteActionItem(activity_context,
                                ContextCompat.getDrawable(activity_context, R.drawable.ic_outline_public_24px), "Visit Website",
                                true,
                                Uri.parse("https://github.com/daniel-stoneuk/material-about-library")))
                        .build())

                .addCard(new MaterialAboutCard.Builder()
                        .title("Material Design Icons")
                        .addItem(new MaterialAboutActionItem.Builder()
                                .text("Copyright")
                                .icon(R.drawable.ic_outline_copyright_24px)
                                .subText("Google")
                                .build())
                        .addItem(new MaterialAboutActionItem.Builder()
                                .text("License")
                                .subText("Apache License, Version 2.0")
                                .icon(R.drawable.ic_outline_class_24px)
                                .setOnClickAction(new MaterialAboutItemOnClickAction() {
                                    @Override
                                    public void onClick() {
                                        Intent intent = new Intent(activity_context, LicenseActivity.class);
                                        intent.putExtra("asset", "Apache-2.0.txt");
                                        startActivity(intent);
                                    }
                                })
                                .build())
                        .addItem(ConvenienceBuilder.createWebsiteActionItem(activity_context,
                                ContextCompat.getDrawable(activity_context, R.drawable.ic_outline_public_24px), "Visit Website",
                                true,
                                Uri.parse("https://material.io/tools/icons/")))
                        .build())

                .addCard(new MaterialAboutCard.Builder()
                        .title("Creative Tail - 40 Flat Animal Icons")
                        .addItem(new MaterialAboutActionItem.Builder()
                                .text("Copyright")
                                .icon(R.drawable.ic_outline_copyright_24px)
                                .subText("Creative Tail")
                                .build())
                        .addItem(new MaterialAboutActionItem.Builder()
                                .text("License")
                                .subText("Creative Commons Attribution International License (V4.0)")
                                .icon(R.drawable.ic_outline_class_24px)
                                .setOnClickAction(new MaterialAboutItemOnClickAction() {
                                    @Override
                                    public void onClick() {
                                        Intent intent = new Intent(activity_context, LicenseActivity.class);
                                        intent.putExtra("asset", "CC-by-4.0.txt");
                                        startActivity(intent);
                                    }
                                })
                                .build())
                        .addItem(ConvenienceBuilder.createWebsiteActionItem(activity_context,
                                ContextCompat.getDrawable(activity_context, R.drawable.ic_outline_public_24px), "Visit Website",
                                true,
                                Uri.parse("https://www.creativetail.com/40-free-flat-animal-icons/")))
                        .build())

                .addCard(new MaterialAboutCard.Builder()
                        .title("Material Design Navigation Drawer")
                        .addItem(new MaterialAboutActionItem.Builder()
                                .text("Copyright")
                                .icon(R.drawable.ic_outline_copyright_24px)
                                .subText("Pablo Costa")
                                .build())
                        .addItem(new MaterialAboutActionItem.Builder()
                                .text("License")
                                .subText("MIT License")
                                .icon(R.drawable.ic_outline_class_24px)
                                .setOnClickAction(new MaterialAboutItemOnClickAction() {
                                    @Override
                                    public void onClick() {
                                        Intent intent = new Intent(activity_context, LicenseActivity.class);
                                        intent.putExtra("asset", "MIT.txt");
                                        startActivity(intent);
                                    }
                                })
                                .build())
                        .addItem(ConvenienceBuilder.createWebsiteActionItem(activity_context,
                                ContextCompat.getDrawable(activity_context, R.drawable.ic_outline_public_24px), "Visit Website",
                                true,
                                Uri.parse("https://github.com/Sottti/Material-Design-Nav-Drawer/tree/using_design_support_library")))
                        .build())

                .addCard(new MaterialAboutCard.Builder()
                        .title("A nice tiger photo")
                        .addItem(new MaterialAboutActionItem.Builder()
                                .text("Copyright")
                                .icon(R.drawable.ic_outline_copyright_24px)
                                .subText("Blake Meyer")
                                .build())
                        .addItem(new MaterialAboutActionItem.Builder()
                                .text("License")
                                .subText("Unsplash")
                                .icon(R.drawable.ic_outline_class_24px)
                                .setOnClickAction(new MaterialAboutItemOnClickAction() {
                                    @Override
                                    public void onClick() {
                                        Intent intent = new Intent(activity_context, LicenseActivity.class);
                                        intent.putExtra("asset", "Unsplash.txt");
                                        startActivity(intent);
                                    }
                                })
                                .build())
                        .addItem(ConvenienceBuilder.createWebsiteActionItem(activity_context,
                                ContextCompat.getDrawable(activity_context, R.drawable.ic_outline_public_24px), "Visit Website",
                                true,
                                Uri.parse("https://unsplash.com/photos/5RBXc7R-YWs")))
                        .build())

                .addCard(new MaterialAboutCard.Builder()
                        .title("logback-android")
                        .addItem(new MaterialAboutActionItem.Builder()
                                .text("Copyright")
                                .icon(R.drawable.ic_outline_copyright_24px)
                                .subText("Anthony Trinh and QOS.ch")
                                .build())
                        .addItem(new MaterialAboutActionItem.Builder()
                                .text("License")
                                .subText("Apache License, Version 2.0")
                                .icon(R.drawable.ic_outline_class_24px)
                                .setOnClickAction(new MaterialAboutItemOnClickAction() {
                                    @Override
                                    public void onClick() {
                                        Intent intent = new Intent(activity_context, LicenseActivity.class);
                                        intent.putExtra("asset", "Apache-2.0.txt");
                                        startActivity(intent);
                                    }
                                })
                                .build())
                        .addItem(ConvenienceBuilder.createWebsiteActionItem(activity_context,
                                ContextCompat.getDrawable(activity_context, R.drawable.ic_outline_public_24px),
                                "Visit Website",
                                true,
                                Uri.parse("https://tony19.github.io/logback-android/index.html")))
                        .build())

                .build();
    }

    @Override
    protected CharSequence getActivityTitle() {
        return getString(R.string.mal_title_about);
    }
}

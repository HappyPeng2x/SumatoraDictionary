package org.happypeng.sumatora.android.sumatoradictionary.viewholder;

import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.happypeng.sumatora.android.sumatoradictionary.R;
import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.DictionaryAction;

public class DictionaryActionViewHolder extends RecyclerView.ViewHolder {
    public interface OnClickListener {
        void onClick(DictionaryAction aEntry);
    }

    private final TextView mDescription;

    private final ImageButton mInstallButton;
    private final ImageButton mDeleteButton;

    private final OnClickListener mInstallListener;
    private final OnClickListener mDeleteListener;

    public DictionaryActionViewHolder(@NonNull View itemView,
                                      boolean aInstallButton,
                                      boolean aDeleteButton,
                                      OnClickListener aInstallListener,
                                      OnClickListener aDeleteListener) {
        super(itemView);

        mDescription = (TextView) itemView.findViewById(R.id.dictionary_card_description);

        mInstallButton =
                (ImageButton) itemView.findViewById(R.id.dictionary_card_install);
        mDeleteButton =
                (ImageButton) itemView.findViewById(R.id.dictionary_card_delete);

        if (!aInstallButton) {
            mInstallButton.setVisibility(View.GONE);
        }

        if (!aDeleteButton) {
            mDeleteButton.setVisibility(View.GONE);
        }

        mInstallListener = aInstallListener;
        mDeleteListener = aDeleteListener;
    }

    public void bindTo(final DictionaryAction aEntry) {
        if (aEntry == null) { return; }

        InstalledDictionary downloadDictionary = aEntry.getDownloadDictionary();
        InstalledDictionary installedDictionary = aEntry.getInstalledDictionary();

        if (downloadDictionary != null) {
            mDescription.setText(downloadDictionary.description);
        } else if (installedDictionary != null){
            mDescription.setText(installedDictionary.description);
        }

        if (mInstallListener != null) {
            mInstallButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mInstallListener.onClick(aEntry);
                }
            });
        }

        if (mDeleteListener != null) {
            mDeleteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mDeleteListener.onClick(aEntry);
                }
            });
        }
    }
}

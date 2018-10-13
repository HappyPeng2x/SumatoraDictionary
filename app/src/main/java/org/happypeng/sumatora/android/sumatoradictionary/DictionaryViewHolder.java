package org.happypeng.sumatora.android.sumatoradictionary;

import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.View;
import android.widget.TextView;

import net.java.sen.dictionary.Token;

public class DictionaryViewHolder extends ViewHolder {
    private TextView textViewView;
    private TextView textViewView2;

    //itemView est la vue correspondante à 1 cellule
    public DictionaryViewHolder(View itemView) {
        super(itemView);

        //c'est ici que l'on fait nos findView

        textViewView = (TextView) itemView.findViewById(R.id.text);
        textViewView2 = (TextView) itemView.findViewById(R.id.text2);
    }

    //puis ajouter une fonction pour remplir la cellule en fonction d'un MyObject
    public void bind(Token pToken){
        textViewView.setText(pToken.getSurface());
        textViewView2.setText(pToken.getMorpheme().getPartOfSpeech());
    }
}

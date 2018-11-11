package org.happypeng.sumatora.android.sumatoradictionary;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import android.widget.TextView;

public class LicenseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_license);

        TextView text_view = (TextView) findViewById(R.id.licence_text_view);
        StringBuilder sb = new StringBuilder();
        String asset = getIntent().getCharSequenceExtra("asset").toString();

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open(asset)));

            String line;

            while ((line = reader.readLine()) != null) {
                sb.append(line);
                sb.append("\n");
            }

            reader.close();
        } catch (IOException e) {
            System.err.println(e.toString());
        }

        text_view.setText(sb.toString());
    }
}

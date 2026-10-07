package com.akanshaa.mobile;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class Smoke extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView t = new TextView(this);
        t.setText("Akanshaa build chain OK");
        setContentView(t);
    }
}

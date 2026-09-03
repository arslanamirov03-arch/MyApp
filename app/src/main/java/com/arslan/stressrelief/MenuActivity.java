package com.arslan.stressrelief;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

public final class MenuActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);

        View start = findViewById(R.id.btnStart);
        start.setOnClickListener(v ->
                startActivity(new Intent(MenuActivity.this, ChooseActivity.class)));
    }
}

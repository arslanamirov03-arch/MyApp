package com.arslan.stressrelief;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

public final class ChooseActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_choose);

        findViewById(R.id.cardFire).setOnClickListener(v ->
                startActivity(new Intent(ChooseActivity.this, FireActivity.class)));
        findViewById(R.id.cardStorm).setOnClickListener(v ->
                startActivity(new Intent(ChooseActivity.this, StormActivity.class)));
    }
}

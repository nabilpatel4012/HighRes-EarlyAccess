package org.tensorflow.lite.examples.superresolution;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Objects;

public class Launcher extends AppCompatActivity {
    private static final int SPLASH_SCREEN = 3000;
    Animation topAnim, bottomAnim;
    ImageView logo;
    TextView app_name;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        Objects.requireNonNull(getSupportActionBar()).hide();
        setContentView(R.layout.activity_launcher);
        topAnim = AnimationUtils.loadAnimation(this,R.anim.top_animation);
        bottomAnim = AnimationUtils.loadAnimation(this,R.anim.bottom_animation);

        logo = findViewById(R.id.launcher);
        app_name = findViewById(R.id.name);
        logo.setAnimation(topAnim);
        app_name.setAnimation(bottomAnim);
        new Handler() .postDelayed(() -> {
            Intent intent = new Intent(Launcher.this,MainActivity.class);
            startActivity(intent);
            finish();
        },SPLASH_SCREEN);
    }
}
package com.my.music;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

public class SplashActivity extends Activity {

    private static final int SPLASH_DURATION = 1500; // 1.5 Seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Views
        ImageView icon = findViewById(R.id.splashIcon);
        TextView text = findViewById(R.id.splashText);
        
        View footer = findViewById(R.id.splashFooter); // [নতুন লাইন]

        // 1. Zoom In Animation for Icon
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(icon, "scaleX", 0.5f, 1.0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(icon, "scaleY", 0.5f, 1.0f);
        ObjectAnimator alphaIcon = ObjectAnimator.ofFloat(icon, "alpha", 0.0f, 1.0f);

        // 2. Slide Up & Fade In Animation for Text
        ObjectAnimator translateY = ObjectAnimator.ofFloat(text, "translationY", 50f, 0f);
        ObjectAnimator alphaText = ObjectAnimator.ofFloat(text, "alpha", 0.0f, 1.0f);
        // [নতুন লজিক]: Footer Fade In Animation
        ObjectAnimator alphaFooter = ObjectAnimator.ofFloat(footer, "alpha", 0.0f, 1.0f);

        // Combine Animations
        AnimatorSet animatorSet = new AnimatorSet();
        // নিচের লাইনে 'alphaFooter' যুক্ত করা হয়েছে
        animatorSet.playTogether(scaleX, scaleY, alphaIcon, translateY, alphaText, alphaFooter); 
        
        animatorSet.setDuration(1000); 
        animatorSet.setInterpolator(new DecelerateInterpolator());
        animatorSet.start();
        

        // 3. Navigate to Main Activity after Delay
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                startActivity(intent);
                finish(); // Remove Splash from back stack
                
                // Smooth Transition (Fade In/Out)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        }, SPLASH_DURATION);
    }
}
package com.projecttango.experiments.javamotiontracking;

import android.app.Application;

import com.firebase.client.Firebase;
/**
 * Initialize Firebase with the application context.
 * This must happen before the client is used.
 */
public class MyApplication extends Application{
    @Override
    public void onCreate(){
        super.onCreate();
        Firebase.setAndroidContext(this);
    }
}

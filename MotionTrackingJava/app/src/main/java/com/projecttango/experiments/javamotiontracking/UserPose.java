package com.projecttango.experiments.javamotiontracking;

public class UserPose {
    private String mUserId;
    private float mX;
    private float mY;
    private float mZ;

    public UserPose(){}
    public UserPose(String userId, float[] translation) {
        mUserId = userId;
        mX = translation[0];
        mY = translation[1];
        mZ = translation[2];
    }
    public void setUserId(String userId){
        mUserId = userId;
    }

    public void setTranslation(float[] translation) {
        mX = translation[0];
        mY = translation[1];
        mZ = translation[2];
    }

    public String getUserId() {
        return mUserId;
    }

    public float getX() {
        return mX;
    }

    public float getY() {
        return mY;
    }

    public float getZ() {
        return mZ;
    }
}

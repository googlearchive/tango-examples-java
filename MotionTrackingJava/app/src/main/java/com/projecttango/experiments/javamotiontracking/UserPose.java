package com.projecttango.experiments.javamotiontracking;

public class UserPose {
    private String mUserId;
    private String mPose;
    static private String FORMAT = "%.2f";

    public UserPose(){}
    public UserPose(String userId, float[] translation, float[] orientation) {
        mUserId = userId;
        setPose(translation, orientation);
    }
    public void setUserId(String userId){
        mUserId = userId;
    }

    public void setPose(float[] translation, float[] orientation) {
        mPose = String.format(FORMAT, translation[0]) + "," +
                String.format(FORMAT, translation[1]) + "," +
                String.format(FORMAT, translation[2]);
//                String.format(FORMAT, orientation[0]) + "," +
//                String.format(FORMAT, orientation[1]) + "," +
//                String.format(FORMAT, orientation[2]) + "," +
//                String.format(FORMAT, orientation[3]);
    }

    public String getUserId() {
        return mUserId;
    }

    public String getPoseString() {
        return mPose;
    }
}

package com.projecttango.experiments.javamotiontracking;

import org.rajawali3d.math.vector.Vector3;

public class UserPose {
    private long mTimestamp;
    private Vector3 mPosition;

    public UserPose(){}
    public UserPose(long timestamp, Vector3 position) {
        mTimestamp = timestamp;
        mPosition = position;
    }
    public void setTimestamp(long timestamp){
        mTimestamp = timestamp;
    }

    public void setPosition(Vector3 position) {
        mPosition = position;
    }

    public long getTimestamp() {
        return mTimestamp;
    }

    public Vector3 getPosition() {
        return mPosition;
    }
}

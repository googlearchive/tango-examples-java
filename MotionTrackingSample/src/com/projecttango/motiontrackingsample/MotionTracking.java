package com.projecttango.motiontrackingsample;

import java.text.DecimalFormat;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import com.google.atap.tangoapi.VioStatus;
import com.google.atap.tangoclient.TangoClient;

/**
 * Example showing how to get Motion Tracking Data and Display it on UI.
 */
public class MotionTracking extends Activity {
	
	public interface poseUpdateListener{
		void onPoseUpdate(VioStatus pose);
	}
	
	private poseUpdateListener poseCallBack;
	private TangoClient mTangoClient; 
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_motion_tracking);
        final TextView rot0 = (TextView) findViewById(R.id.rotation0);
        final TextView rot1 = (TextView) findViewById(R.id.rotation1);
        final TextView rot2 = (TextView) findViewById(R.id.rotation2);
        final TextView rot3 = (TextView) findViewById(R.id.rotation3);
        final TextView pos0 = (TextView) findViewById(R.id.positionX);
        final TextView pos1 = (TextView) findViewById(R.id.positionY);
        final TextView pos2 = (TextView) findViewById(R.id.positionZ);
        mTangoClient = new TangoClient(this, false, false, false);  
        poseCallBack = new poseUpdateListener(){
            @Override
            public void onPoseUpdate(final VioStatus status) {
              final DecimalFormat fourDec = new DecimalFormat("0.0000");
              runOnUiThread(new Runnable() {
                @Override
                public void run() {
                  try {
                    rot0.setText(fourDec.format(status.rotation[0]));
                    rot1.setText(fourDec.format(status.rotation[1]));
                    rot2.setText(fourDec.format(status.rotation[2]));
                    rot3.setText(fourDec.format(status.rotation[3]));
                    pos0.setText(fourDec.format(status.translation[0]));
                    pos1.setText(fourDec.format(status.translation[1]));
                    pos2.setText(fourDec.format(status.translation[2]));
                  } catch (Exception e) {
                    e.printStackTrace();
                  } 
                }
              });
            }
          };
          poseUpdater();          
    }
	
	  @Override
	  public void onDestroy() {
	    super.onDestroy();
	    mTangoClient.shutdown();
	    poseCallBack = null;
	  }

	  @Override
	  public void onPause() {
	    super.onPause();
	    // Stop and exit the app if the user switches to something else.
	    // The current myriad chip will power down if the screen goes off, so
	    // just ending the app is probably the safest thing to do here.
	    finish();
	  }
    
    public void poseUpdater()
    {
    	 new Thread(new Runnable() {
             @Override
             public void run() {
                 while (mTangoClient != null) {
                     try {
                         mTangoClient.applicationDoStep();
                         if (poseCallBack != null){
                             poseCallBack.onPoseUpdate(mTangoClient.getLatestStatus(TangoClient.POSE_ESTIMATOR));
                         }               
                         Thread.sleep(50);
                     } catch (Exception e) {
                         e.printStackTrace();
                     }
                 }
             }
         }).start();
    }
}

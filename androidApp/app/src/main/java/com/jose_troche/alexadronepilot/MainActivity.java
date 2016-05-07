package com.jose_troche.alexadronepilot;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.jose_troche.alexadronepilot.aws_iot.IotDroneSubscriber;
import com.jose_troche.alexadronepilot.parrot.DroneListActivity;
import com.jose_troche.alexadronepilot.parrot.MiniDrone;

import com.parrot.arsdk.arcommands.ARCOMMANDS_MINIDRONE_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM;
import com.parrot.arsdk.arcommands.ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM;
import com.parrot.arsdk.arcontroller.ARCONTROLLER_DEVICE_STATE_ENUM;
import com.parrot.arsdk.ardiscovery.ARDiscoveryDeviceService;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    // Drone Variables
    private MiniDrone mMiniDrone;
    private ProgressDialog mConnectionProgressDialog;
    private TextView mBatteryLabel;
    private Button mTakeOffLandBt;
    private Handler mHandler;

    // AWS IoT Variables
    private TextView iotCommand;
    private TextView iotStatus;

    private IotDroneSubscriber mIotSubscriber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initAwsIot();

        initIHM();

        mHandler = new Handler();
        Intent intent = getIntent();
        ARDiscoveryDeviceService service = intent.getParcelableExtra(DroneListActivity.EXTRA_DEVICE_SERVICE);
        mMiniDrone = new MiniDrone(this, service);
        mMiniDrone.addListener(mMiniDroneListener);
    }

    @Override
    protected void onStart() {
        super.onStart();

        // show a loading view while the minidrone is connecting
        if ((mMiniDrone != null) && !(ARCONTROLLER_DEVICE_STATE_ENUM.ARCONTROLLER_DEVICE_STATE_RUNNING.equals(mMiniDrone.getConnectionState())))
        {
            mConnectionProgressDialog = new ProgressDialog(this, R.style.AppCompatAlertDialogStyle);
            mConnectionProgressDialog.setIndeterminate(true);
            mConnectionProgressDialog.setMessage("Connecting ...");
            mConnectionProgressDialog.setCancelable(false);
            mConnectionProgressDialog.show();

            // if the connection to the MiniDrone fails, finish the activity
            if (!mMiniDrone.connect()) {
                finish();
            }
        }
    }


    @Override
    public void onBackPressed() {
        mIotSubscriber.disconnect();

        if (mMiniDrone != null)
        {
            mConnectionProgressDialog = new ProgressDialog(this, R.style.AppCompatAlertDialogStyle);
            mConnectionProgressDialog.setIndeterminate(true);
            mConnectionProgressDialog.setMessage("Disconnecting ...");
            mConnectionProgressDialog.setCancelable(false);
            mConnectionProgressDialog.show();

            if (!mMiniDrone.disconnect()) {
                finish();
            }
        } else {
            finish();
        }
    }

    private void initAwsIot() {
        iotCommand = (TextView) findViewById(R.id.iotMessage);
        iotStatus =  (TextView) findViewById(R.id.iotStatus);

        mIotSubscriber = new IotDroneSubscriber(this, mIotDroneSubscriberListener);
    }

    private void initIHM() {

        findViewById(R.id.emergencyBt).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            mMiniDrone.emergency();
            }
        });

        mTakeOffLandBt = (Button) findViewById(R.id.takeOffOrLandBt);
        mTakeOffLandBt.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            switch (mMiniDrone.getFlyingState()) {
                case ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_LANDED:
                    mMiniDrone.takeOff();
                    break;
                case ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_FLYING:
                case ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_HOVERING:
                    mMiniDrone.land();
                    break;
                default:
            }
            }
        });

        mBatteryLabel = (TextView) findViewById(R.id.batteryLabel);
    }

    private final IotDroneSubscriber.Listener mIotDroneSubscriberListener = new IotDroneSubscriber.Listener()
    {
        @Override
        public void onConnectionStatusChanged(String status) {
            iotStatus.setText(status);
        }

        @Override
        public void onCommandReceived(String command, long duration) {
            Handler handler = new Handler();
            iotCommand.setText(command);

            switch (command.toLowerCase().trim()){
                case "take off":
                    mMiniDrone.takeOff();
                    break;

                case "land":
                    mMiniDrone.land();
                    break;

                case "fly up":
                case "go up":
                case "up":
                    setGaz(50, duration);
                    break;

                case "fly down":
                case "go down":
                case "down":
                    setGaz(-50, duration);
                    break;

                case "forward":
                    setPitch(50, duration);
                    break;

                case "backward":
                    setPitch(-50, duration);
                    break;

                case "right":
                    setRoll(50, duration);
                    break;

                case "left":
                    setRoll(-50, duration);
                    break;

                case "spin right":
                    setYaw(50, duration);
                    break;

                case "spin left":
                    setYaw(-50, duration);
                    break;

                case "flip":
                    mMiniDrone.flip();
                    break;

                case "take picture":
                case "picture":
                    mMiniDrone.takePicture();
                    break;
            }
        }
    };

    private void setPitch(int pct, long duration){
        mMiniDrone.setPitch((byte) pct);
        mMiniDrone.setFlag((byte) 1);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mMiniDrone.setPitch((byte) 0);
                mMiniDrone.setFlag((byte) 0);
            }
        }, duration);
    }

    private void setRoll(int pct, long duration){
        mMiniDrone.setRoll((byte) pct);
        mMiniDrone.setFlag((byte) 1);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mMiniDrone.setRoll((byte) 0);
                mMiniDrone.setFlag((byte) 0);
            }
        }, duration);
    }

    private void setYaw(int pct, long duration){
        mMiniDrone.setYaw((byte) pct);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mMiniDrone.setYaw((byte) 0);
            }
        }, duration);
    }

    private void setGaz(int pct, long duration){
        mMiniDrone.setGaz((byte) pct);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mMiniDrone.setGaz((byte) 0);
            }
        }, duration);
    }



    private final MiniDrone.Listener mMiniDroneListener = new MiniDrone.Listener() {
        @Override
        public void onDroneConnectionChanged(ARCONTROLLER_DEVICE_STATE_ENUM state) {
            switch (state)
            {
                case ARCONTROLLER_DEVICE_STATE_RUNNING:
                    mConnectionProgressDialog.dismiss();
                    break;

                case ARCONTROLLER_DEVICE_STATE_STOPPED:
                    // if the deviceController is stopped, go back to the previous activity
                    mConnectionProgressDialog.dismiss();
                    finish();
                    break;

                default:
                    break;
            }
        }

        @Override
        public void onBatteryChargeChanged(int batteryPercentage) {
            mBatteryLabel.setText(String.format("%d%%", batteryPercentage));
        }

        @Override
        public void onPilotingStateChanged(ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_ENUM state) {
            switch (state) {
                case ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_LANDED:
                    mTakeOffLandBt.setText("Take off");
                    mTakeOffLandBt.setEnabled(true);
                    break;
                case ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_FLYING:
                case ARCOMMANDS_MINIDRONE_PILOTINGSTATE_FLYINGSTATECHANGED_STATE_HOVERING:
                    mTakeOffLandBt.setText("Land");
                    mTakeOffLandBt.setEnabled(true);
                    break;
                default:
                    mTakeOffLandBt.setEnabled(false);
            }
        }

        @Override
        public void onDownloadComplete(String mediaName) {
            Log.i(TAG, "Download completed");
        }

        @Override
        public void onPictureTaken(ARCOMMANDS_MINIDRONE_MEDIARECORDEVENT_PICTUREEVENTCHANGED_ERROR_ENUM error) {
            Log.i(TAG, "Picture has been taken");
        }

        @Override
        public void onMatchingMediasFound(int nbMedias) {}

        @Override
        public void onDownloadProgressed(String mediaName, int progress) {}

    };

}

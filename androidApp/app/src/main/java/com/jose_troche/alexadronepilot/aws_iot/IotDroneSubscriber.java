package com.jose_troche.alexadronepilot.aws_iot;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.iot.AWSIotKeystoreHelper;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttClientStatusCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttManager;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttNewMessageCallback;
import com.amazonaws.mobileconnectors.iot.AWSIotMqttQos;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.iot.AWSIotClient;
import com.amazonaws.services.iot.model.AttachPrincipalPolicyRequest;
import com.amazonaws.services.iot.model.CreateKeysAndCertificateRequest;
import com.amazonaws.services.iot.model.CreateKeysAndCertificateResult;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.security.KeyStore;
import java.util.UUID;

/**
 * Created by jtroche on 5/6/16.
 */
public class IotDroneSubscriber {
    private static final String TAG = "IotDroneSubscriber";
    private final Context mContext;
    private final Handler mUiThreadHandler;
    private Listener mListener;

    // --- AWS IoT Constants to modify per your configuration ---

    // Endpoint Prefix = random characters at the beginning of the custom AWS
    // IoT endpoint
    // describe endpoint call returns: XXXXXXXXXX.iot.<region>.amazonaws.com,
    // endpoint prefix string is XXXXXXX
    private static final String CUSTOMER_SPECIFIC_ENDPOINT_PREFIX = "XXXXXXX";
    // Cognito pool ID. For this app, pool needs to be unauthenticated pool with
    // AWS IoT permissions.
    private static final String COGNITO_POOL_ID = "<pool_id>";
    // Name of the AWS IoT policy to attach to a newly created certificate
    private static final String AWS_IOT_POLICY_NAME = "Policy";

    // Region of AWS IoT
    private static final Regions MY_REGION = Regions.US_EAST_1;
    // Filename of KeyStore file on the filesystem
    private static final String KEYSTORE_NAME = "iot_keystore";
    // Password for the private key in the KeyStore
    private static final String KEYSTORE_PASSWORD = "password";
    // Certificate and key aliases in the KeyStore
    private static final String CERTIFICATE_ID = "default";
    // The IoT Drone Topic
    private static final String MQTT_DRONE_TOPIC = "$aws/things/Drone/shadow/update";

    // ---- End of AWS IoT Constants ---

    private AWSIotMqttManager mqttManager;

    private String mqttKeyStorePath;

    public interface Listener {
        void onConnectionStatusChanged(String status);
        
        void onCommandReceived(String command, long duration);
    }

    public IotDroneSubscriber(Context context, Listener listener) {
        mContext = context;
        mListener = listener;

        // A handler for the main (UI) thread
        mUiThreadHandler = new Handler(context.getMainLooper());

        // MQTT client IDs are required to be unique per AWS IoT account.
        String clientId = UUID.randomUUID().toString();

        // AWS Region
        Region region = Region.getRegion(MY_REGION);

        // MQTT Client
        mqttManager = new AWSIotMqttManager(clientId, region, CUSTOMER_SPECIFIC_ENDPOINT_PREFIX);

        // Set keepalive to 10 seconds.  Will recognize disconnects more quickly but will also send
        // MQTT pings every 10 seconds.
        mqttManager.setKeepAlive(10);

        // The path to the keystore that contains the credentials to connect to IoT
        mqttKeyStorePath = mContext.getFilesDir().getPath();

        initializeConnection();
    }

    public void disconnect(){
        mqttManager.disconnect();
    }

    private void subscribeToTopic(String topic){
        Log.d(TAG, "Mqtt topic = " + topic);

        try {
            mqttManager.subscribeToTopic(topic, AWSIotMqttQos.QOS0,
                new AWSIotMqttNewMessageCallback() {
                    @Override
                    public void onMessageArrived(final String topic, final byte[] data) {
                        try {
                            String message = new String(data, "UTF-8");
                            final String command = (new JSONObject(message)).getString("command");
                            Log.d(TAG, "Command arrived:");
                            Log.d(TAG, "   Topic: " + topic);
                            Log.d(TAG, " Command: " + command);

                            mUiThreadHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    mListener.onCommandReceived(command, 1000 /* duration = 1 sec*/);
                                }
                            });

                        } catch (UnsupportedEncodingException e) {
                            Log.e(TAG, "Message encoding error.", e);
                        } catch (JSONException e) {
                            Log.e(TAG, "JSON Message decoding error.", e);
                        }
                    }
                });
        } catch (Exception e) {
            Log.e(TAG, "Subscription error.", e);
        }
    }

    private void connectToIoTAndSubscribe(KeyStore mqttKeyStore){
        Log.d(TAG, "Connecting to IoT...");

        try {
            mqttManager.connect(mqttKeyStore, new AWSIotMqttClientStatusCallback() {
                @Override
                public void onStatusChanged(final AWSIotMqttClientStatus status,
                                            final Throwable throwable) {
                    final String connectionStatus;
                    Log.d(TAG, "Status = " + String.valueOf(status));
                    
                    if (status == AWSIotMqttClientStatus.Connecting) {
                        connectionStatus = "Connecting...";
                    } else if (status == AWSIotMqttClientStatus.Connected) {
                        connectionStatus = "Connected";
                        subscribeToTopic(MQTT_DRONE_TOPIC);
                    } else if (status == AWSIotMqttClientStatus.Reconnecting) {
                        if (throwable != null) {
                            Log.e(TAG, "Connection error.", throwable);
                        }
                        connectionStatus = "Reconnecting";
                    } else if (status == AWSIotMqttClientStatus.ConnectionLost) {
                        if (throwable != null) {
                            Log.e(TAG, "Connection error.", throwable);
                        }
                        connectionStatus = "Disconnected";
                    } else {
                        connectionStatus = "Disconnected";
                    }

                    mUiThreadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mListener.onConnectionStatusChanged(connectionStatus);
                        }
                    });

                }
            });
        } catch (final Exception e) {
            Log.e(TAG, "Connection error.", e);
            mUiThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.onConnectionStatusChanged("IoT connection error: " + e.getMessage());
                }
            });
        }
    }

    private void initializeConnection(){
        KeyStore mqttKeyStore = null;
        try { // Try to get the mqtt keystore that has the credentials to connect to IoT
            if (AWSIotKeystoreHelper.isKeystorePresent(mqttKeyStorePath, KEYSTORE_NAME)) {
                if (AWSIotKeystoreHelper.keystoreContainsAlias(CERTIFICATE_ID, mqttKeyStorePath,
                        KEYSTORE_NAME, KEYSTORE_PASSWORD)) {
                    Log.i(TAG, "Certificate " + CERTIFICATE_ID
                            + " found in keystore - using for MQTT.");
                    // load keystore from file into memory to pass on connection
                    mqttKeyStore = AWSIotKeystoreHelper.getIotKeystore(CERTIFICATE_ID,
                            mqttKeyStorePath, KEYSTORE_NAME, KEYSTORE_PASSWORD);
                } else {
                    Log.i(TAG, "Key/cert " + CERTIFICATE_ID + " not found in keystore.");
                }
            } else {
                Log.i(TAG, "Keystore " + mqttKeyStorePath + "/" + KEYSTORE_NAME + " not found.");
            }
        } catch (Exception e) {
            Log.e(TAG, "An error occurred retrieving cert/key from keystore.", e);
        }

        if (mqttKeyStore != null) {
            connectToIoTAndSubscribe(mqttKeyStore);
        }
        else {
            asyncCreateMqttKeyStoreAndConnect();
        }
    }

    // Create a key and certificate, and store them in key store
    private void asyncCreateMqttKeyStoreAndConnect(){
        Log.i(TAG, "Cert/key not found in keystore - creating new key and certificate.");

        new Thread(new Runnable() {
            @Override
            public void run() {
            try {
                KeyStore mqttKeyStore = null;

                // Initialize the AWS Cognito credentials provider
                CognitoCachingCredentialsProvider credentialsProvider = new CognitoCachingCredentialsProvider(
                        mContext.getApplicationContext(), // context
                        COGNITO_POOL_ID, // Identity Pool ID
                        MY_REGION // Region
                );

                // IoT Client for creation of key and certificate
                AWSIotClient mIotAndroidClient = new AWSIotClient(credentialsProvider);
                mIotAndroidClient.setRegion(Region.getRegion(MY_REGION));

                // Create a new private key and certificate. This call
                // creates both on the server and returns them to the
                // device.
                CreateKeysAndCertificateRequest createKeysAndCertificateRequest =
                        new CreateKeysAndCertificateRequest();
                createKeysAndCertificateRequest.setSetAsActive(true);
                final CreateKeysAndCertificateResult createKeysAndCertificateResult;
                createKeysAndCertificateResult =
                        mIotAndroidClient.createKeysAndCertificate(createKeysAndCertificateRequest);
                Log.i(TAG, "Cert ID: " +
                        createKeysAndCertificateResult.getCertificateId() + " created.");

                // Attach a policy to the newly created certificate.
                // This flow assumes the policy was already created in
                // AWS IoT and we are now just attaching it to the
                // certificate.
                AttachPrincipalPolicyRequest policyAttachRequest =
                        new AttachPrincipalPolicyRequest();
                policyAttachRequest.setPolicyName(AWS_IOT_POLICY_NAME);
                policyAttachRequest.setPrincipal(createKeysAndCertificateResult
                        .getCertificateArn());
                mIotAndroidClient.attachPrincipalPolicy(policyAttachRequest);
                Log.i(TAG, "Policy attached to certificate");


                // store in keystore for use in MQTT client
                // saved as alias "default" so a new certificate isn't
                // generated each run of this application
                AWSIotKeystoreHelper.saveCertificateAndPrivateKey(CERTIFICATE_ID,
                        createKeysAndCertificateResult.getCertificatePem(),
                        createKeysAndCertificateResult.getKeyPair().getPrivateKey(),
                        mqttKeyStorePath, KEYSTORE_NAME, KEYSTORE_PASSWORD);

                // load keystore from file into memory to pass on
                // connection
                mqttKeyStore = AWSIotKeystoreHelper.getIotKeystore(CERTIFICATE_ID,
                        mqttKeyStorePath, KEYSTORE_NAME, KEYSTORE_PASSWORD);

                // Now connect to IoT with new mqttKeyStore
                connectToIoTAndSubscribe(mqttKeyStore);

            } catch (Exception e) {
                Log.e(TAG, "Exception occurred when generating new private key and certificate.", e);
            }
            }
        }).start();
    }
}

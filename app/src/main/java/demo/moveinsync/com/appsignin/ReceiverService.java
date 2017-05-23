package demo.moveinsync.com.appsignin;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import demo.moveinsync.com.appsignin.utils.WifiUtils;

import static demo.moveinsync.com.appsignin.ReceiverService.WifiTasksHandler.SCAN_FOR_WIFI_RESULTS;
import static demo.moveinsync.com.appsignin.ReceiverService.WifiTasksHandler.WAIT_FOR_CONNECT_ACTION_TIMEOUT;
import static demo.moveinsync.com.appsignin.ReceiverService.WifiTasksHandler.WAIT_FOR_RECONNECT_ACTION_TIMEOUT;
import static demo.moveinsync.com.appsignin.utils.WifiUtils.connectToOpenHotspot;

public class ReceiverService extends Service {

    private WifiManager wifiManager;
    private SharedPreferences preferences;

    private WifiScanner mWifiScanReceiver;
    private WifiScanner mNwChangesReceiver;
    private WifiTasksHandler m_wifiScanHandler;
    private String mConnectedSSID;

    private boolean m_areOtherNWsDisabled = false;
    private static String TAG_SENDER_FILES_LISTING = "sender_files_listing";
    private static final Long SYNCTIME = 800L;
    private static final String LASTCONNECTEDTIME = "LASTCONNECTEDTIME";
    private static final String LASTDISCONNECTEDTIME = "LASTDISCONNECTEDTIME";

    private static final String TAG = "AppSignin Receiver";
    private String employeeId = "";










    public static final String PATH_FILES = "http://%s:%s/signin/%s";
    public static final String PATH_STATUS = "http://%s:%s/status";
    public static final String PATH_FILE_DOWNLOAD = "http://%s:%s/file/%s";

    private ContactSenderAPITask mUrlsTask;
    private ContactSenderAPITask mStatusCheckTask;

    private static String mPort, mSenderName, mSenderIp, mSenderSSID;

    static final int CHECK_SENDER_STATUS = 100;
    static final int SENDER_DATA_FETCH = 101;

    private static Context appContext;

    private String fileToDownload;

    private volatile boolean isFileListingDone = false;

    private static final int SENDER_DATA_FETCH_RETRY_LIMIT = 3;
    private int senderDownloadsFetchRetry = SENDER_DATA_FETCH_RETRY_LIMIT, senderStatusCheckRetryLimit = SENDER_DATA_FETCH_RETRY_LIMIT;








    public ReceiverService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        employeeId = intent.getStringExtra("EmployeeId");
        m_wifiScanHandler = new WifiTasksHandler(this);
        startSenderScan();
        wifiManager.setWifiEnabled(true);
        boolean isConnectedToShareThemAp = WifiUtils.isWifiConnectedToSTAccessPoint(getApplicationContext());
        if (isConnectedToShareThemAp) {
            unRegisterForScanResults();
            String ssid = wifiManager.getConnectionInfo().getSSID();
            Log.d(TAG, "wifi is connected/connecting to ShareThem ap, ssid: " + ssid);
            mConnectedSSID = ssid;
            String accessPointAddress = WifiUtils.getAccessPointIpAddress(this);
        } else {
            Log.d(TAG, "wifi isn't connected to ShareThem ap, initiating sender search..");
            resetSenderSearch();
        }
        return START_NOT_STICKY;
    }

    public void resetSenderSearch() {
        startSenderScan();
    }

    /**
     * Entry point to start receiver mode. Makes calls to register necessary broadcast receivers to start scanning for SHAREthem Wifi Hotspot.
     *
     * @return
     */
    private boolean startSenderScan() {
        registerAndScanForWifiResults();
        registerForNwChanges();
        return true;
    }

    /**
     * Registers for {@link WifiManager#SCAN_RESULTS_AVAILABLE_ACTION} action and also calls a method to start Wifi Scan action.
     */
    private void registerAndScanForWifiResults() {
        if (null == mWifiScanReceiver)
            mWifiScanReceiver = new WifiScanner();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(mWifiScanReceiver, intentFilter);
        startWifiScan();
    }

    /**
     * Registers for {@link WifiManager#NETWORK_STATE_CHANGED_ACTION} action
     */
    private void registerForNwChanges() {
        if (null == mNwChangesReceiver)
            mNwChangesReceiver = new WifiScanner();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        registerReceiver(mNwChangesReceiver, intentFilter);
    }

    private void unRegisterForScanResults() {
        stopWifiScan();
        try {
            if (null != mWifiScanReceiver)
                unregisterReceiver(mWifiScanReceiver);
        } catch (Exception e) {
            Log.e(TAG, "exception while un-registering wifi changes.." + e.getMessage());
        }
    }

    private void unRegisterForNwChanges() {
        try {
            if (null != mNwChangesReceiver)
                unregisterReceiver(mNwChangesReceiver);
        } catch (Exception e) {
            Log.e(TAG, "exception while un-registering NW changes.." + e.getMessage());
        }
    }

    private void startWifiScan() {
        m_wifiScanHandler.removeMessages(SCAN_FOR_WIFI_RESULTS);
        m_wifiScanHandler.sendMessageDelayed(m_wifiScanHandler.obtainMessage(SCAN_FOR_WIFI_RESULTS), 500);
    }

    private void stopWifiScan() {
        if (null != m_wifiScanHandler)
            m_wifiScanHandler.removeMessages(SCAN_FOR_WIFI_RESULTS);
    }


    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    static class WifiTasksHandler extends Handler {
        static final int SCAN_FOR_WIFI_RESULTS = 100;
        static final int WAIT_FOR_CONNECT_ACTION_TIMEOUT = 101;
        static final int WAIT_FOR_RECONNECT_ACTION_TIMEOUT = 102;
        private WeakReference<ReceiverService> mActivity;

        WifiTasksHandler(ReceiverService activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            final ReceiverService activity = mActivity.get();
            if (null == activity)
                return;
            switch (msg.what) {
                case SCAN_FOR_WIFI_RESULTS:
                    if (null != activity.wifiManager)
                        activity.wifiManager.startScan();
                    break;
                case WAIT_FOR_CONNECT_ACTION_TIMEOUT:
                    Log.e(TAG, "cant connect to sender's hotspot by increasing priority, try the dirty way..");
                    activity.m_areOtherNWsDisabled = WifiUtils.connectToOpenHotspot(activity.wifiManager, (String) msg.obj, true);
                    Message m = obtainMessage(WAIT_FOR_RECONNECT_ACTION_TIMEOUT);
                    m.obj = msg.obj;
                    sendMessageDelayed(m, 6000);
                    break;
                case WAIT_FOR_RECONNECT_ACTION_TIMEOUT:
                    Log.e(TAG, "Even the dirty hack couldn't do it, prompt user to chose it fromWIFI settings..");
                    activity.disableReceiverMode();
                    Toast.makeText(activity.getApplicationContext(), "Could not connect to SHAREthem Hotspot. Please go to Wifi settings and chose " + msg.obj + " to start video transfer from Sender", Toast.LENGTH_LONG).show();
                    Log.d(TAG, "Could not connect to SHAREthem Hotspot. Please go to Wifi settings and chose " + msg.obj + " to start video transfer from Sender");
                    break;
            }
        }
    }

    /**
     * Disables and removes SHAREthem wifi configuration from Wifi Settings. Also does cleanup work to remove handlers, un-register receivers etc..
     */
    private void disableReceiverMode() {
        if (!TextUtils.isEmpty(mConnectedSSID)) {
            if (m_areOtherNWsDisabled)
                WifiUtils.removeSTWifiAndEnableOthers(wifiManager, mConnectedSSID);
            else
                WifiUtils.removeWifiNetwork(wifiManager, mConnectedSSID);
        }
        m_wifiScanHandler.removeMessages(WAIT_FOR_CONNECT_ACTION_TIMEOUT);
        m_wifiScanHandler.removeMessages(WAIT_FOR_RECONNECT_ACTION_TIMEOUT);
        unRegisterForScanResults();
        unRegisterForNwChanges();
    }

    private void connectToWifi(String ssid) {
        WifiInfo info = wifiManager.getConnectionInfo();
        unRegisterForScanResults();
        boolean resetWifiScan;
        if (info.getSSID().equals(ssid)) {
            Log.d(TAG, "Already connected to ShareThem, add sender Files listing fragment");
            resetWifiScan = false;
            addSenderFilesListingFragment(WifiUtils.getAccessPointIpAddress(getApplicationContext()), ssid);
            //Show file selection here and allow them to send files. In our case pre-select the file.
        } else {
            Log.d("ShareThem receiver", "Connecting to " + ssid);
            resetWifiScan = !connectToOpenHotspot(wifiManager, ssid, false);
            Log.e(TAG, "connection attempt to ShareThem wifi is " + (!resetWifiScan ? "success!!!" : "FAILED..!!!"));
        }
        //if wap isnt successful, start wifi scan
        if (resetWifiScan) {
            Toast.makeText(this, "Failed to connect with" + ssid + " Hotspot. Retrying again..", Toast.LENGTH_SHORT).show();
            Log.d("ShareThem receiver", "Scanning for a SHAREthem Hotspot");
            startSenderScan();
        } else {
            Message message = m_wifiScanHandler.obtainMessage(WAIT_FOR_CONNECT_ACTION_TIMEOUT);
            message.obj = ssid;
            m_wifiScanHandler.sendMessageDelayed(message, 7000);
        }
    }

    Handler downReqHandler = new Handler();

    private class WifiScanner extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) && !WifiUtils.isWifiConnectedToSTAccessPoint(getApplicationContext())) {
                List<ScanResult> mScanResults = ((WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE)).getScanResults();
                boolean foundSTWifi = false;
                for (ScanResult result : mScanResults) {
                    Log.d(TAG, "SSID Found is: "+result.SSID);
                    if (WifiUtils.isShareThemSSID(result.SSID) && WifiUtils.isOpenWifi(result)) {
                        Log.d(TAG, "signal level: " + result.level);
                        connectToWifi(result.SSID);
                        foundSTWifi = true;
                        break;
                    }
                }
                if (!foundSTWifi) {
                    Log.e(TAG, "no ST wifi found, starting scan again!!");
                    startWifiScan();
                }
            } else if (intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                NetworkInfo netInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (ConnectivityManager.TYPE_WIFI == netInfo.getType()) {
                    WifiInfo info = wifiManager.getConnectionInfo();
                    SupplicantState supState = info.getSupplicantState();
                    Log.d(TAG, "NETWORK_STATE_CHANGED_ACTION, ssid: " + info.getSSID() + ", ap ip: " + WifiUtils.getAccessPointIpAddress(getApplicationContext()) + ", sup state: " + supState);
                    if (null == preferences)
                        preferences = getSharedPreferences(
                                getPackageName(), Context.MODE_PRIVATE);
                    if (WifiUtils.isShareThemSSID(info.getSSID())) {
                        if (System.currentTimeMillis() - preferences.getLong(LASTCONNECTEDTIME, 0) >= SYNCTIME && supState.equals(SupplicantState.COMPLETED)) {
                            mConnectedSSID = info.getSSID();
                            m_wifiScanHandler.removeMessages(WAIT_FOR_CONNECT_ACTION_TIMEOUT);
                            m_wifiScanHandler.removeMessages(WAIT_FOR_RECONNECT_ACTION_TIMEOUT);
                            final String ip = WifiUtils.getAccessPointIpAddress(getApplicationContext());
                            preferences.edit().putLong(LASTCONNECTEDTIME, System.currentTimeMillis()).commit();
                            Log.d(TAG, "client connected to ShareThem hot spot. AP ip address: " + ip);
                            DownloadRequestRunnable downReq = new DownloadRequestRunnable(ip, info.getSSID());
                            downReqHandler.postDelayed(downReq, 3000);
                            //Show file selection here and allow them to send files. In our case pre-select the file. info.getSSID();
                        }
//                        else if (!netInfo.isConnectedOrConnecting() && System.currentTimeMillis() - Prefs.getInstance().loadLong(LASTDISCONNECTEDTIME, 0) >= SYNCTIME) {
//                            Prefs.getInstance().saveLong(LASTDISCONNECTEDTIME, System.currentTimeMillis());
//                            if (LogUtil.LOG)
//                                LogUtil.e(TAG, "AP disconnedted..");
//                            Toast.makeText(context, "Sender Wifi Hotspot disconnected. Retrying to connect..", Toast.LENGTH_SHORT).show();
//                            resetSenderSearch();
//                        }
                    }
                }
            }
        }
    }

    private void addSenderFilesListingFragment(String ip, String ssid) {
        String[] senderInfo = setConnectedUi(ssid);
        Log.d(TAG, "Adding reuqest with ip:"+ip+"  ssid:"+ssid+"  senderInfo[1]:"+senderInfo[1]);
        if (null == senderInfo) {
            Log.e(TAG, "Cant retrieve port and name info from SSID");
            return;
        }
        Log.d(TAG, "adding files fragment with ip: " + ip);

        mUrlsTask = new ContactSenderAPITask(SENDER_DATA_FETCH);
        mUrlsTask.execute(String.format(PATH_FILES, ip, senderInfo[1], employeeId));
    }

    private void onDataFetchError() {
        Log.d(TAG, "Server contact Receipt failed");
    }

    private void loadListing(String result) {
        Log.d(TAG, "Received result: "+result);

    }

    private class ContactSenderAPITask extends AsyncTask<String, Void, String> {

        int mode;
        boolean error;

        ContactSenderAPITask(int mode) {
            this.mode = mode;
        }

        @Override
        protected String doInBackground(String... urls) {
            error = false;
            try {
                return downloadDataFromSender(urls[0]);
            } catch (IOException e) {
                e.printStackTrace();
                error = true;
                Log.e(TAG, "Exception: " + e.getMessage());
                return null;
            }
        }

        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(String result) {
            switch (mode) {
                case SENDER_DATA_FETCH:
                    if (error) {
                        if (senderDownloadsFetchRetry >= 0) {
                            --senderDownloadsFetchRetry;
                            Log.d(TAG, "Retires = " + senderDownloadsFetchRetry);
                            return;
                        } else senderDownloadsFetchRetry = SENDER_DATA_FETCH_RETRY_LIMIT;
                        onDataFetchError();
                    } else {
                        loadListing(result);
                        Log.d(TAG, "File listing is done");
                        isFileListingDone = true;
                    }
                    break;
                case CHECK_SENDER_STATUS:
                    if (error) {
                        if (senderStatusCheckRetryLimit > 1) {
                            --senderStatusCheckRetryLimit;
                            Log.d(TAG, "SenderStatusCheckRetryLimit: " + senderStatusCheckRetryLimit);
                        } else {
                            senderStatusCheckRetryLimit = SENDER_DATA_FETCH_RETRY_LIMIT;
                            Toast.makeText(appContext, "Receiver error. Sender disconnected.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.e(TAG, "Check sender status.");
                    }
                    break;
            }

        }

        private String downloadDataFromSender(String apiUrl) throws IOException {
            InputStream is = null;
            try {
                URL url = new URL(apiUrl);
                Log.d(TAG, "Connecting to url "+apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setReadTimeout(10000 /* milliseconds */);
                conn.setConnectTimeout(15000 /* milliseconds */);
                conn.setRequestMethod("GET");
                conn.setDoInput(true);
                // Starts the query
                conn.connect();
//                int response =
                conn.getResponseCode();
//                Log.d(TAG, "The response is: " + response);
                is = conn.getInputStream();
                // Convert the InputStream into a string
                return readIt(is);
            } finally {
                if (is != null) {
                    is.close();
                }
            }
        }

        private String readIt(InputStream stream) throws IOException {
            Writer writer = new StringWriter();

            char[] buffer = new char[1024];
            try {
                Reader reader = new BufferedReader(
                        new InputStreamReader(stream, "UTF-8"));
                int n;
                while ((n = reader.read(buffer)) != -1) {
                    writer.write(buffer, 0, n);
                }
            } finally {
                stream.close();
            }
            Log.d(TAG, "Received output "+writer.toString());
            final String output = writer.toString();
            downReqHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "Signin request response is: "+output, Toast.LENGTH_LONG).show();
                }
            });
            return writer.toString();
        }
    }

    private String[] setConnectedUi(String ssid) {
        String[] senderInfo = WifiUtils.getSenderInfoFromSSID(ssid);
        if (null == senderInfo || senderInfo.length != 2)
            return null;
        String ip = WifiUtils.getThisDeviceIp(getApplicationContext());
        return senderInfo;
    }

    class DownloadRequestRunnable implements Runnable {

        String ip;
        String ssid;
        DownloadRequestRunnable(String ip, String ssid) {
            this.ip = ip;
            this.ssid = ssid;
        }
        @Override
        public void run() {
            addSenderFilesListingFragment(ip, ssid);
        }
    }



}

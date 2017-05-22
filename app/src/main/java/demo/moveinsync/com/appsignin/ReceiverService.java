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
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.lang.ref.WeakReference;
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

    public ReceiverService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
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
                            addSenderFilesListingFragment(ip, info.getSSID());
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
        if (null == senderInfo) {
            Log.e(TAG, "Cant retrieve port and name info from SSID");
            return;
        }
        Log.d(TAG, "adding files fragment with ip: " + ip);
        FilesListingFragment files_listing_fragment = FilesListingFragment.getInstance(getApplicationContext(), ip, ssid, senderInfo[0], senderInfo[1]);
        files_listing_fragment.onCreateView();
    }

    private String[] setConnectedUi(String ssid) {
        String[] senderInfo = WifiUtils.getSenderInfoFromSSID(ssid);
        if (null == senderInfo || senderInfo.length != 2)
            return null;
        String ip = WifiUtils.getThisDeviceIp(getApplicationContext());
        return senderInfo;
    }

}

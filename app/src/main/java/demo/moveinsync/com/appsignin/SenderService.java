package demo.moveinsync.com.appsignin;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.WifiConfiguration;
import android.os.Build;
import android.os.IBinder;
import android.support.v7.widget.SwitchCompat;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;

import java.util.Arrays;

import demo.moveinsync.com.appsignin.utils.HotspotControl;
import demo.moveinsync.com.appsignin.utils.Utils;
import demo.moveinsync.com.appsignin.utils.WifiUtils;

public class SenderService extends Service {

    public static final String PREFERENCES_KEY_SHARED_FILE_PATHS = "sharethem_shared_file_paths";
    public static final String PREFERENCES_KEY_DATA_WARNING_SKIP = "sharethem_data_warning_skip";
    private static final int REQUEST_WRITE_SETTINGS = 1;

    private BroadcastReceiver m_p2pServerUpdatesListener;
    private HotspotControl hotspotControl;
    private boolean isApEnabled = false;
    private boolean shouldAutoConnect = true;
    private String[] m_sharedFilePaths = null;
    private Intent startIntent;

    private String TAG = "AppSignin Sender";

    public SenderService() {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        this.startIntent = intent;
        hotspotControl = HotspotControl.getInstance(getApplicationContext());

        //if file paths are found, save'em into preferences. OR find them in prefs
        if (intent.hasExtra(SHAREthemService.EXTRA_FILE_PATHS))
            m_sharedFilePaths = intent.getStringArrayExtra(SHAREthemService.EXTRA_FILE_PATHS);
        SharedPreferences prefs = getSharedPreferences(getPackageName(), Context.MODE_PRIVATE);
        if (null == m_sharedFilePaths)
            m_sharedFilePaths = Utils.toStringArray(prefs.getString(PREFERENCES_KEY_SHARED_FILE_PATHS, null));
        else
            prefs.edit().putString(PREFERENCES_KEY_SHARED_FILE_PATHS, new JSONArray(Arrays.asList(m_sharedFilePaths)).toString()).commit();

        changeApControlCheckedStatus(true);
        Toast.makeText(SenderService.this, "Initializing Wifi hotspot..", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Initializing Wifi hotspot..");
        enableAp();

        m_p2pServerUpdatesListener = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int intentType = intent.getIntExtra(SHAREthemService.ShareIntents.TYPE, 0);
                if (intentType == SHAREthemService.ShareIntents.Types.FILE_TRANSFER_STATUS) {
                    String fileName = intent.getStringExtra(SHAREthemService.ShareIntents.SHARE_SERVER_UPDATE_FILE_NAME);
                    updateReceiverListItem(intent.getStringExtra(SHAREthemService.ShareIntents.SHARE_CLIENT_IP), intent.getIntExtra(SHAREthemService.ShareIntents.SHARE_TRANSFER_PROGRESS, -1), intent.getStringExtra(SHAREthemService.ShareIntents.SHARE_SERVER_UPDATE_TEXT), fileName);
                } else if (intentType == SHAREthemService.ShareIntents.Types.AP_DISABLED_ACKNOWLEDGEMENT) {
                    shouldAutoConnect = false;
                    resetSenderUi(false);
                }
            }
        };
        registerReceiver(m_p2pServerUpdatesListener, new IntentFilter(SHAREthemService.ShareIntents.SHARE_SERVER_UPDATES_INTENT_ACTION));

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private void resetSenderUi(boolean disableAP) {
        Toast.makeText(SenderService.this, "File sharing is stopped", Toast.LENGTH_LONG).show();
        Log.d(TAG, "File sharing is stopped");
        if (disableAP)
            disableAp();
        else {
            changeApControlCheckedStatus(false);
        }
    }

    //region: Hotspot Control
    private void enableAp() {
        startP2pSenderWatchService();
        refreshApData();
    }

    private void disableAp() {
        //Send STOP action to service
        Intent p2pServiceIntent = new Intent(getApplicationContext(), SHAREthemService.class);
        p2pServiceIntent.setAction(SHAREthemService.WIFI_AP_ACTION_STOP);
        startService(p2pServiceIntent);
        isApEnabled = false;
    }

    /**
     * Starts {@link SHAREthemService} with intent action {@link SHAREthemService#WIFI_AP_ACTION_START} to enableShareThemHotspot Hotspot and start {@link SHAREthemServer}.
     */
    private void startP2pSenderWatchService() {
        Intent p2pServiceIntent = new Intent(getApplicationContext(), SHAREthemService.class);
        p2pServiceIntent.putExtra(SHAREthemService.EXTRA_FILE_PATHS, m_sharedFilePaths);
        if (null != startIntent) {
            p2pServiceIntent.putExtra(SHAREthemService.EXTRA_PORT, startIntent.getIntExtra(SHAREthemService.EXTRA_PORT, 0));
            p2pServiceIntent.putExtra(SHAREthemService.EXTRA_SENDER_NAME, startIntent.getStringExtra(SHAREthemService.EXTRA_SENDER_NAME));
        }
        p2pServiceIntent.setAction(SHAREthemService.WIFI_AP_ACTION_START);
        startService(p2pServiceIntent);
    }

    /**
     * Starts {@link SHAREthemService} with intent action {@link SHAREthemService#WIFI_AP_ACTION_START_CHECK} to make {@link SHAREthemService} constantly check for Hotspot status. (Sometimes Hotspot tend to stop if stayed idle for long enough. So this check makes sure {@link SHAREthemService} is only alive if Hostspot is enaled.)
     */
    private void startHostspotCheckOnService() {
        Intent p2pServiceIntent = new Intent(getApplicationContext(), SHAREthemService.class);
        p2pServiceIntent.setAction(SHAREthemService.WIFI_AP_ACTION_START_CHECK);
        startService(p2pServiceIntent);
    }

    /**
     * Calls methods - {@link SenderService#updateApStatus()} & {@link SenderService#listApClients()} which are responsible for displaying Hotpot information and Listing connected clients to the same
     */
    private void refreshApData() {
        updateApStatus();
        listApClients();
    }

    /**
     * Updates Hotspot configuration info like Name, IP if enabled.<br> Posts a message to {@link } to call itself every 1500ms
     */
    private void updateApStatus() {
        if (!HotspotControl.isSupported()) {
            Toast.makeText(SenderService.this, "Warning: Hotspot mode not supported!", Toast.LENGTH_SHORT);
            Log.d(TAG, "Warning: Hotspot mode not supported!");
        }
        if (hotspotControl.isEnabled()) {
            if (!isApEnabled) {
                isApEnabled = true;
                startHostspotCheckOnService();
            }
            WifiConfiguration config = hotspotControl.getConfiguration();
            String ip = Build.VERSION.SDK_INT >= 23 ? WifiUtils.getHostIpAddress() : hotspotControl.getHostIpAddress();
            if (TextUtils.isEmpty(ip))
                ip = "";
            else
                ip = ip.replace("/", "");
            Toast.makeText(SenderService.this, "Open Receiver App in \\'Receiver mode\\' \\n(Or)\\n• Open WiFi settings and connect \\n" + config.SSID + " Hotspot &amp; enter\\n" + "http://" + ip + ":" + hotspotControl.getShareServerListeningPort() + "\\non a browser\\'s address bar", Toast.LENGTH_SHORT);
            Log.d(TAG, "Open Receiver App in \\'Receiver mode\\' \\n(Or)\\n• Open WiFi settings and connect \\n" + config.SSID + " Hotspot &amp; enter\\n" + "http://" + ip + ":" + hotspotControl.getShareServerListeningPort() + "\\non a browser\\'s address bar");
            Toast.makeText(SenderService.this, String.valueOf(m_sharedFilePaths.length), Toast.LENGTH_SHORT);
            Log.d(TAG, String.valueOf(m_sharedFilePaths.length));
        }
    }


    /**
     * Changes checked status without invoking listener. Removes @{@link android.widget.CompoundButton.OnCheckedChangeListener} on @{@link SwitchCompat} button before changing checked status
     *
     * @param checked if <code>true</code>, sets @{@link SwitchCompat} checked.
     */
    private void changeApControlCheckedStatus(boolean checked) {
        if (checked == false) {
            //Sender is disabled. Act accordingly
        } else {
            //Sender is enabled. Act accordingly
        }
    }
    //endregion: Hotspot Control

    /**
     * Calls {@link HotspotControl#getConnectedWifiClients(int, HotspotControl.WifiClientConnectionListener)} to get Clients connected to Hotspot.<br>
     * Constantly adds/updates receiver items on {@link SenderService receivers list}
     * <br> Posts a message to {@link } to call itself every 1000ms
     */
    private synchronized void listApClients() {
        if (hotspotControl == null) {
            return;
        }
        hotspotControl.getConnectedWifiClients(2000,
                new HotspotControl.WifiClientConnectionListener() {
                    public void onClientConnectionAlive(final HotspotControl.WifiScanResult wifiScanResult) {
                        Log.d(TAG, "New client: " + wifiScanResult.ip);
                    }

                    @Override
                    public void onClientConnectionDead(final HotspotControl.WifiScanResult c) {
                        Log.d(TAG, "client: " + c.ip + " disconnected");
                    }

                    public void onWifiClientsScanComplete() {
                        //Rerun the listApClinets using some handler every 1 second
                    }
                }

        );
    }

    private void updateReceiverListItem(String ip, int progress, String updatetext, String fileName) {
        if (updatetext.contains("Error in file transfer")) {
            Log.e(TAG, "Error in file transfer");
            resetTransferInfo(fileName);
            return;
        } else {
            Log.e(TAG, "no list item found with this IP******");
        }
    }

    void resetTransferInfo(String fileName) {
        Log.e(TAG, "resetTransferInfo - " + fileName);
    }

}

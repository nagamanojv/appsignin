package demo.moveinsync.com.appsignin;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;
import java.util.Random;

import demo.moveinsync.com.appsignin.utils.HotspotControl;
import demo.moveinsync.com.appsignin.utils.Utils;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION = 100;
    private EditText employeeIdText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        employeeIdText = (EditText) findViewById(R.id.text_empid);
    }

    @TargetApi(23)
    private boolean checkLocationPermission() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION
            );
            return false;
        }
        return true;
    }

    public void sendFiles(View view) {
        if (Utils.isShareServiceRunning(getApplicationContext())) {
            startService(new Intent(getApplicationContext(), SenderService.class));
            return;
        }
        Random random = new Random();
        int randomPort = 52000 + random.nextInt(500);
        String[] files = new String[]{"/mnt/sdcard/Downloads/BMTCWomenPassengerSecurity.apk"};
        Intent intent = new Intent(getApplicationContext(), SenderService.class);
        intent.putExtra(SHAREthemService.EXTRA_FILE_PATHS, files);
        intent.putExtra(SHAREthemService.EXTRA_PORT, randomPort);
        intent.putExtra(SHAREthemService.EXTRA_SENDER_NAME, "Sri");
        startService(intent);
    }

    public void receiveFiles(View view) {
        HotspotControl hotspotControl = HotspotControl.getInstance(getApplicationContext());
        if (null != hotspotControl && hotspotControl.isEnabled()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Sender(Hotspot) mode is active. Please disable it to proceed with Receiver mode");
            builder.setNeutralButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.cancel();
                }
            });
            builder.show();
            return;
        }
        Intent receiverStartIntent = new Intent(getApplicationContext(), ReceiverService.class);
        receiverStartIntent.putExtra("EmployeeId", employeeIdText.getText().toString());
        startService(receiverStartIntent);
    }



}

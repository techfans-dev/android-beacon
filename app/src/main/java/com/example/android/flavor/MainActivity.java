/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.flavor;


import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

/**
 * {@link MainActivity} shows a list of Android platform releases.
 * For each release, display the name, version number, and image.
 */
public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION = 1;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothScanner;
    public static BluetoothLeService leService;
    private boolean mScanning;
    private BluetoothGatt bluetoothGatt;
    private static final long SCAN_PERIOD = 10000;
    private LeDeviceAdapter leDeviceListAdapter;
    private MainActivity mainActivity;
    private boolean isLost = false;
    private double mytagDistance = -1;
    private double doorTagDistance = -1;
    private String lostDevice = "";
    private Location lastLocation = null;
    private int lostRSSI = -1;
    private boolean alertShown = false;
    private Location currentLoc = null;
    private final LocationListener mLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(final Location location) {
            //your code here
            currentLoc = location;
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {

        }

        @Override
        public void onProviderDisabled(String provider) {

        }
    };

    public Handler mainHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == 1) {
                Intent intent = new Intent(MainActivity.this, PanelActivity.class);
                startActivity(intent);
            }
            if (msg.what == 2) {
//                Toast.makeText(this, "Device disconnected", Toast.LENGTH_SHORT).show();
//                System.out.println("active device;    " + PanelActivity.activeDevice.getlastDeviceName());
//                LocalBroadcastManager.getInstance(MainActivity.this).sendBroadcast()
                Intent intent = new Intent(MainActivity.this, LostActivity.class);
                startActivity(intent);
            }

        }
    };

    ArrayList<DeviceItem> bleDevices = new ArrayList<DeviceItem>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        LocationManager mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }

        assert mLocationManager != null;
        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0,
                0, mLocationListener);
        mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, mLocationListener);

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bluetoothScanner = bluetoothAdapter.getBluetoothLeScanner();


        // if scanner is available and adapter is enabled
        if (bluetoothScanner != null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {

                if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.ACCESS_COARSE_LOCATION)) {

                } else {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                            MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION);
                }
            } else {
                // Permission has already been granted
                if(bluetoothScanner != null)
                    scanLeDevice(true);
            }
        }

        leDeviceListAdapter = new LeDeviceAdapter(this, bleDevices);
        ListView listView = (ListView) findViewById(R.id.listview_flavor);
        listView.setAdapter(leDeviceListAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                PanelActivity.activeDevice.setlastDeviceName(bleDevices.get(position).getDevice().getName());
                BluetoothGatt gatt = leService.connect(getApplicationContext(), bleDevices.get(position).getDevice());
                if(gatt != null){
                    System.out.println("gatt; " + gatt);
                }
            }
        });

        leService = new BluetoothLeService(mainHandler);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (this) {
                    while(true) {
                        try {
                            wait(5000);
                        } catch (InterruptedException ioe) {
                            ioe.printStackTrace();
                        }

                        runOnUiThread(new Runnable() {
                            public void run() {
                                for (int i = 0; i < bleDevices.size(); i++) {
                                    long timeDiff = System.currentTimeMillis() - bleDevices.get(i).getLastDiscoveredSec();
                                    if (timeDiff > 7000) {  // if not connected over 10 sec, set rssi to 0 so we know device not available
                                        bleDevices.get(i).setRSSI(0);
                                    }
                                }
                            }
                        });
                    }
                }
            }
        });
        t.start();

    }



    private void scanLeDevice(final boolean enable) {
        // when device accel is low(device not moving) lets put this to ScanSettings.SCAN_MODE_OPPORTUNISTIC
        // that doesn't start scan itself at all, just listens so power consume is very low
        // scan setting: SCAN_MODE_LOW_LATENCY the best performace less latency
        // power consuming more
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        if (enable) {
            mScanning = true;
            bluetoothScanner.startScan(null, settings, leScanCallback);
        } else {
            mScanning = false;
            bluetoothScanner.stopScan(leScanCallback);
        }
    }

    private ScanCallback leScanCallback = new ScanCallback() {
        @TargetApi(Build.VERSION_CODES.O)
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            System.out.println("scaning devices;;;;" + bleDevices.size());
            System.out.println("scanning...");


            for(int i=0; i< bleDevices.size(); i++) {
                // if device is already in the list only update rssi values
                if(bleDevices.get(i).getAddress().equals(result.getDevice().getAddress())) {
                    bleDevices.get(i).setRSSI(result.getRssi());
                    bleDevices.get(i).setLastDiscoveredSec(System.currentTimeMillis());
                    bleDevices.get(i).setStatus("");
                    bleDevices.get(i).setDistance(getDistance(result.getRssi(),-69));
                    bleDevices.get(i).setLastLoc(currentLoc);
                    leDeviceListAdapter.notifyDataSetChanged();

                    // if timed out set as 0
                    for (int ii = 0; ii < bleDevices.size(); ii++) {
                        if(bleDevices.get(ii).getDeviceTag() != null) {
                            if (bleDevices.get(ii).getDeviceTag().equals(("device"))) {
                                mytagDistance = bleDevices.get(ii).getDistance();
                                lostDevice = result.getDevice().getName();
                                lastLocation = bleDevices.get(ii).getLastLoc();
                                lostRSSI = bleDevices.get(ii).getRSSI();
                            }

                            if(bleDevices.get(ii).getDeviceTag().equals("door")) {
                                doorTagDistance = bleDevices.get(i).getDistance();
                            }

                            if((mytagDistance > 10 || lostRSSI == 0)  // if device is 10m out or device not available
                                    && ((doorTagDistance > -1 && doorTagDistance < 1.5)     // door tag is in 5m range
                                    && alertShown == false)                               // alert is not shown
                            ) {
                                System.out.println("This is time to ring...");
                                try {
                                    Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                                    Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
                                    r.play();

                                } catch (Exception e) {
                                    e.printStackTrace();
                                }

                                AlertDialog.Builder builder1 = new AlertDialog.Builder(MainActivity.this);
                                builder1.setMessage("You forgot to bring this thing? DeviceName:" + lostDevice + " last Discovered Location(LatLng):" +
                                        (lastLocation != null?lastLocation.getLatitude():0) +", " +
                                        (lastLocation != null?lastLocation.getLongitude():0));
                                builder1.setCancelable(true);

                                builder1.setPositiveButton(
                                        "OK",
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog, int id) {
                                                alertShown = false;
                                                dialog.cancel();
                                            }
                                        });
                                AlertDialog alert11 = builder1.create();
                                alert11.show();

                                alertShown = true;

                                bleDevices.get(ii).setStatus("Leaving this device???");
                                isLost = true;
                            } else {
                                bleDevices.get(ii).setStatus("");
                            }
                        }
                    }

                    return;
                }
            }
            // check mytag is still available
            // and check door beacon is also available
            // lost condition:
            // if mytag readtime > 5000, if doortag < 5000

            // new device, add it to the list
            if(result.getDevice().getName() != null && result.getDevice().getName() != "") {
                DeviceItem devItem = new DeviceItem();
                devItem.setDevice(result.getDevice());
                devItem.setRSSI(result.getRssi());
                devItem.setDistance(getDistance(result.getRssi(), -69));
                devItem.setLastLoc(currentLoc);
                bleDevices.add(devItem);
                leDeviceListAdapter.notifyDataSetChanged();
            }
//                    bleDevices.add(new DeviceItem(device.getName(), "1.6",1 ));
//            System.out.println("Device Name: " + result.getDevice().getName() + " rssi: " + result.getRssi() + "\n" );
//            System.out.println("get tx power;" + result.getTxPower());

            // auto scroll for text view
//            final int scrollAmount = peripheralTextView.getLayout().getLineTop(peripheralTextView.getLineCount()) - peripheralTextView.getHeight();
//            // if there is no need to scroll, scrollAmount will be <=0
//            if (scrollAmount > 0)
//                peripheralTextView.scrollTo(0, scrollAmount);
        }
    };

    public double getDistance(int rssi, int txPower) {
        /*
         * RSSI = TxPower - 10 * n * lg(d)
         * n = 2 (in free space)
         *
         * d = 10 ^ ((TxPower - RSSI) / (10 * n))
         */
        System.out.println( "get distance;" + Math.pow(10d, ((double) txPower - rssi) / (10 * 2)));
        return Math.pow(10d, ((double) txPower - rssi) / (10 * 2));
    }

//    private BluetoothAdapter.LeScanCallback leScanCallback =
//        new BluetoothAdapter.LeScanCallback() {
//        @Override
//        public void onLeScan(final BluetoothDevice device, final int rssi,
//                             final byte[] scanRecord) {
//
//            runOnUiThread(new Runnable() {
//                @Override
//                public void run() {
//
//
//
//                }
//            });
//        }
//    };

}

/**
 * Created by michael on 4/23/15.
 */
package com.example.michael.pulsesensor;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;

import com.example.michael.dataserverlib.DataService;

import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class PulseSensorService extends DataService<PulseSensor> {
    private BluetoothGattCharacteristic characteristicTx = null;
    private RBLService mBluetoothLeService;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mDevice = null;
    private String mDeviceAddress;

    private boolean flag = true;
    private boolean connState = false;
    private boolean scanFlag = false;
    private char[] rxBuf = new char[100];
    private int rxPos    = 0;
    private boolean rxTxnProgress = false;
    final private char START_CHAR = '>';
    final private char END_CHAR   = '\n';

    private byte[] data = new byte[3];
    private static final long SCAN_PERIOD = 4000;

    final private static char[] hexArray = { '0', '1', '2', '3', '4', '5', '6',
            '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName,
                                       IBinder service) {
            mBluetoothLeService = ((RBLService.LocalBinder) service)
                    .getService();
            if (!mBluetoothLeService.initialize()) {
                Toast.makeText(getApplicationContext(), "Unable to initialize Bluetooth",
                        Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (RBLService.ACTION_GATT_DISCONNECTED.equals(action)) {
                Toast.makeText(getApplicationContext(), "Disconnected",
                        Toast.LENGTH_SHORT).show();
            } else if (RBLService.ACTION_GATT_SERVICES_DISCOVERED
                    .equals(action)) {
                Toast.makeText(getApplicationContext(), "Connected",
                        Toast.LENGTH_SHORT).show();
                getGattService(mBluetoothLeService.getSupportedGattService());
            } else if (RBLService.ACTION_DATA_AVAILABLE.equals(action)) {
                data = intent.getByteArrayExtra(RBLService.EXTRA_DATA);
                readAnalogInValue(data);
            } else if (RBLService.ACTION_GATT_RSSI.equals(action)) {
                //sensor.bpm = Integer.parseInt(intent.getStringExtra(RBLService.EXTRA_DATA));
            }
        }
    };
    private void startReadRssi() {
        new Thread() {
            public void run() {

                while (flag) {
                    mBluetoothLeService.readRssi();
                    try {
                        sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            };
        }.start();
    }
    private void readAnalogInValue(byte[] data) {

        char [] arr = new char[data.length+1];
        for (int i = 0; i < data.length; ++i) {
            char c = (char) data[i];
            arr[i] = c;
            if(rxTxnProgress == false) {
                if(c == START_CHAR) {
                    rxPos = 0;
                    rxTxnProgress = true;
                    continue;
                }
            } else {
                if(c == END_CHAR) {
                    rxBuf[rxPos] = '\0';
                    //sensor.bpm = Integer.parseInt(rxBuf);
                    String s = new String(rxBuf,0,rxPos);
                    if(s.length() > 1) {
                        System.out.println(s);
                        if (s.charAt(0) == 'B') {
                            try {
                                int val = Integer.parseInt(s.substring(1));
                                sensor.bpm = val;
                            } catch (NumberFormatException e) {
                                //drop on floor
                            }
                        }
                    }
                    //Restart txn
                    rxPos = 0;
                    rxTxnProgress = false;
                } else {
                    rxBuf[rxPos++] = c;
                }
            }
        }
    }

    private void getGattService(BluetoothGattService gattService) {
        if (gattService == null)
            return;

        startReadRssi();

        characteristicTx = gattService
                .getCharacteristic(RBLService.UUID_BLE_SHIELD_TX);

        BluetoothGattCharacteristic characteristicRx = gattService
                .getCharacteristic(RBLService.UUID_BLE_SHIELD_RX);
        mBluetoothLeService.setCharacteristicNotification(characteristicRx,
                true);
        mBluetoothLeService.readCharacteristic(characteristicRx);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();

        intentFilter.addAction(RBLService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(RBLService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(RBLService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(RBLService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(RBLService.ACTION_GATT_RSSI);

        return intentFilter;
    }

    private void scanLeDevice() {
        new Thread() {

            @Override
            public void run() {
                mBluetoothAdapter.startLeScan(mLeScanCallback);

                try {
                    Thread.sleep(SCAN_PERIOD);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                mBluetoothAdapter.stopLeScan(mLeScanCallback);
            }
        }.start();
    }

    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi,
                             final byte[] scanRecord) {

            new Thread(new Runnable() {
                @Override
                public void run() {
                    byte[] serviceUuidBytes = new byte[16];
                    String serviceUuid = "";
                    for (int i = 32, j = 0; i >= 17; i--, j++) {
                        serviceUuidBytes[j] = scanRecord[i];
                    }
                    serviceUuid = bytesToHex(serviceUuidBytes);
                    if (stringToUuidString(serviceUuid).equals(
                            RBLGattAttributes.BLE_SHIELD_SERVICE
                                    .toUpperCase(Locale.ENGLISH))) {
                        mDevice = device;
                    }
                }
            }).start();
        }
    };

    private String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for (int j = 0; j < bytes.length; j++) {
            v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    private String stringToUuidString(String uuid) {
        StringBuffer newString = new StringBuffer();
        newString.append(uuid.toUpperCase(Locale.ENGLISH).substring(0, 8));
        newString.append("-");
        newString.append(uuid.toUpperCase(Locale.ENGLISH).substring(8, 12));
        newString.append("-");
        newString.append(uuid.toUpperCase(Locale.ENGLISH).substring(12, 16));
        newString.append("-");
        newString.append(uuid.toUpperCase(Locale.ENGLISH).substring(16, 20));
        newString.append("-");
        newString.append(uuid.toUpperCase(Locale.ENGLISH).substring(20, 32));

        return newString.toString();
    }

    @Override
    public ServiceTimes setupTimes() {
        ServiceTimes times = super.setupTimes();
        times.maxReadResponseTime  = 100;
        times.maxWriteResponseTime = 10;
        times.sensorPeriod         = 1000;
        return times;
    }
    //Driver modelled methods
    @Override
    public void open(){
        sensor = new PulseSensor();
        System.out.println("Pulse sensor opened");
        if (!getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "Ble not supported", Toast.LENGTH_SHORT)
                    .show();
        }

        final BluetoothManager mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Ble not supported", Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        Intent gattServiceIntent = new Intent(this,
                RBLService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        System.out.println("Pulse sensor opened until here");

        if (scanFlag == false) {
            scanLeDevice();
            Timer mTimer = new Timer();
            mTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (mDevice != null) {
                        mDeviceAddress = mDevice.getAddress();
                        mBluetoothLeService.connect(mDeviceAddress);
                        scanFlag = true;
                        System.out.println("Connected to BLE device");
                    } else {
                        System.out.println("Unable to connect to any BLE devices");
                    }
                }
            }, SCAN_PERIOD);
        }
        if(mBluetoothLeService != null && mDeviceAddress != null) {
            mBluetoothLeService.connect(mDeviceAddress);
        }
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(
                    BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(enableBtIntent);
            //TODO
            //startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }
    //Reads modify sensor
    @Override
    public void readAsync() {
        //sensor.bpm ++;
    }
    @Override
    public void readPeriodic() {
        //sensor.bpm += 100;
    }
    //Writes use sensor to work with actual hadware
    @Override
    public void writeAsync() {
        //Write to sensor
        String s = "Hello World!";
        byte[] buf = new byte[s.length()];
        for(int i = 0; i < s.length(); ++i) {
            buf[i] = (byte) s.charAt(i);
        }
        characteristicTx.setValue(buf);
        mBluetoothLeService.writeCharacteristic(characteristicTx);
    }
    @Override
    public void writePeriodic() {
        //Write to sensor periodically
    }
    @Override
    public void close() {
        System.out.println("PulseSensor Closed");
        mBluetoothLeService.disconnect();
        mBluetoothLeService.close();
        flag = false;
        unregisterReceiver(mGattUpdateReceiver);
        if (mServiceConnection != null) {
            unbindService(mServiceConnection);
        }
    }
}

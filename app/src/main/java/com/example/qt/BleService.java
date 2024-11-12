package com.example.qt;

import android.Manifest;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BleService extends Service {
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;

    private final MutableLiveData <BluetoothDevice> connectedDevice = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isScanning = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> bleStatus = new MutableLiveData<>(BluetoothProfile.STATE_DISCONNECTED);
    private final MutableLiveData<List<BluetoothDevice>> deviceList = new MutableLiveData<>(new ArrayList<>());
    private static final int SCAN_PERIOD = 10000; // 10 seconds

    public LiveData<List<BluetoothDevice>> getDeviceList() {
        return deviceList;
    }


    public LiveData<Boolean> getIsScanning() {
        return isScanning;
    }

    public LiveData<Integer> getBleStatus() {
        return bleStatus;
    }

    public LiveData<BluetoothDevice> getConnectedDevice() {
        return connectedDevice;
    }

    private final IBinder binder =  new LocalBinder();

    public class LocalBinder extends Binder {
        BleService getService() {
            return BleService.this;
        }
    }

    public interface BleCallback {
        void onDeviceFound(BluetoothDevice device);
        void onScanFinished();
        void onConnected(BluetoothDevice device);
        void onDisconnected();
        void onConnectionFailed();
    }

    private BleCallback callback;

    private final String TAG = "BleService";

    @Override
    public void onCreate() {
        super.onCreate();
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void setCallback(BleCallback callback) {
        this.callback = callback;
    }

    public void startScan() {
        if (!isScanning.getValue()) {
            isScanning.postValue(true);
            deviceList.postValue(new ArrayList<>());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
            } else {
                if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
            }
            Log.d(TAG, "startScan: ");
            if (bluetoothLeScanner == null) {
                BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
                bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            }
            bluetoothLeScanner.startScan(scanCallback);
            // 设置一个定时器来停止扫描
            new Handler(Looper.getMainLooper()).postDelayed(this::stopScan, SCAN_PERIOD);
        }
    }

    public void stopScan() {
        if (isScanning.getValue()) {
            isScanning.postValue(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
            } else {
                if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
            }
            bluetoothLeScanner.stopScan(scanCallback);
            if (callback != null) {
                callback.onScanFinished();
            }
        }
    }

    public void connectToDevice(BluetoothDevice device) {
        Log.d(TAG, "connectToDevice: " + bleStatus.getValue());
        if (!(bleStatus.getValue() == BluetoothProfile.STATE_CONNECTING)) {
            bleStatus.postValue(BluetoothProfile.STATE_CONNECTING);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12 及以上版本才需要检查 BLUETOOTH_CONNECT 权限
                if (ActivityCompat.checkSelfPermission(BleService.this,
                        Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "No BLUETOOTH_CONNECT permission");
                    return;
                }
            }


            Log.d(TAG, "connectToDevice: OK" + device);
            bluetoothGatt = device.connectGatt(this, false, gattCallback);
            Log.d(TAG, "connectToDevice: " + bluetoothGatt);
        }
    }

    public void disconnect() {
        if (bluetoothGatt != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12 及以上版本才需要检查 BLUETOOTH_CONNECT 权限
                if (ActivityCompat.checkSelfPermission(BleService.this,
                        Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "No BLUETOOTH_CONNECT permission");
                    return;
                }
            }

            bluetoothGatt.disconnect();
        }
    }



    private boolean containsDevice(List<BluetoothDevice> list, BluetoothDevice device) {
        if (device == null) {
            return  false;
        }
        for (BluetoothDevice listDevice : list) {
            if (listDevice.getAddress().equals(device.getAddress())) {
                return true;
            }
        }
        return false;
    }




    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if (device == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12 及以上版本才需要检查 BLUETOOTH_CONNECT 权限
                if (ActivityCompat.checkSelfPermission(BleService.this,
                        Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "No BLUETOOTH_CONNECT permission");
                    return;
                }
            }


            Log.i("QTBLE", "onScanResult: " + device.getName() );

            List<BluetoothDevice> items = deviceList.getValue();
            if (device.getName() != null && !containsDevice(items, device)) {
                items.add(device);
                Log.i("QTBLE", "add item: " + device.getName() );
                deviceList.postValue(items);
                if (callback != null) {
                    callback.onDeviceFound(device);
                }
            }
        }
    };

    private BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectedDevice.postValue(gatt.getDevice());
                if (callback != null) {
                    callback.onConnected(connectedDevice.getValue());
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Android 12 及以上版本才需要检查 BLUETOOTH_CONNECT 权限
                    if (ActivityCompat.checkSelfPermission(BleService.this,
                            Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        Log.d(TAG, "No BLUETOOTH_CONNECT permission");
                        return;
                    }
                }

                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectedDevice.postValue(null);
                if (callback != null) {
                    callback.onDisconnected();
                }
            }

            bleStatus.postValue(newState);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {

            Log.d(TAG, "onServicesDiscovered: " + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // 服务发现完成，现在可以安全地写入特征

            }
        }

    };

    private static final UUID CHARACTER_ID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"); // 标准串口服务UUID
    private static final UUID SERVICE_ID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"); // 标准串口服务UUID




    // 写入特征
    public void writeCharacteristic(String value) {
        if (bluetoothGatt == null) {
            Log.w("BLEConnection", "BluetoothGatt not initialized");
            return;
        }
        BluetoothGattService customService = bluetoothGatt.getService(SERVICE_ID);
        if (customService == null) {
            Log.w(TAG, "writeCharacteristic: servie not exist" );
            return;
        }


        BluetoothGattCharacteristic serialCharacteristic = customService.getCharacteristic(CHARACTER_ID);
        if (serialCharacteristic == null) {
            Log.w(TAG, "writeCharacteristic: character not exists" );
            return;
        }
        Log.d(TAG, "writeCharacteristic: **" + value + "**");
        serialCharacteristic.setValue(value);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 及以上版本才需要检查 BLUETOOTH_CONNECT 权限
            if (ActivityCompat.checkSelfPermission(BleService.this,
                    Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "No BLUETOOTH_CONNECT permission");
                return;
            }
        }

        bluetoothGatt.writeCharacteristic(serialCharacteristic);
    }



    @Override
    public void onDestroy() {
        super.onDestroy();
        if (bluetoothGatt != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12 及以上版本才需要检查 BLUETOOTH_CONNECT 权限
                if (ActivityCompat.checkSelfPermission(BleService.this,
                        Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "No BLUETOOTH_CONNECT permission");
                    return;
                }
            }


            bluetoothGatt.close();
            bluetoothGatt = null;
        }
    }
}
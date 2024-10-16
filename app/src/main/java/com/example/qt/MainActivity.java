package com.example.qt;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;


import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;

import android.Manifest;


import com.example.qt.databinding.ToolbarBinding;
import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.qt.databinding.ActivityMainBinding;

import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, BleService.BleCallback {


    private ActivityMainBinding binding;
    private ToolbarBinding toolbarBinding;

    private View conectionStatus;

    private BluetoothAdapter bluetoothAdapter;

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_PERMISSIONS = 2;


    private BleService bleService;
    private boolean bound = false;

    private ServiceConnection bleConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            BleService.LocalBinder binder = (BleService.LocalBinder) service;
            bleService = binder.getService();
            bleService.setCallback(MainActivity.this);
            bound = true;
            observeBleStatus();

        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            bound = false;
        }
    };

    private final String TAG = "BLEQT";

    private void observeBleStatus() {
        bleService.getIsScanning().observe(this, isScanning -> {
            // 更新 UI 以反映扫描状态
            updateScanningUI(isScanning);
        });

        bleService.getBleStatus().observe(this, bleStatus -> {
            // 更新 UI 以反映连接状态
            deviceListAdapter.updateStatus(bleStatus);
            updateBleStatusUI(bleStatus);
        });

        bleService.getConnectedDevice().observe(this, device -> {
            // 更新 UI 以反映连接的设备
            runOnUiThread(() -> {
                deviceListAdapter.updateConnectedDevice(device);
                updateConnectedDeviceUI(device);
            });

        });

        bleService.getDeviceList().observe(this, items -> {
            if (listView == null) return;
            runOnUiThread(() -> {
                    Log.i(TAG, "observeBleStatus: device list " + items.size());
                    deviceListAdapter.updateDeviceList(items);

            });
        });
    }

    private void updateScanningUI(boolean isScanning) {
        runOnUiThread(() -> {
            if (refreshButton != null) {
                refreshButton.setVisibility(isScanning ? View.INVISIBLE : View.VISIBLE);

            }

            if (scanProgressBar != null) {
                scanProgressBar.setVisibility(isScanning ? View.VISIBLE : View.GONE);

            }
        });

    }

    private void updateBleStatusUI(int status) {
        runOnUiThread(() -> {
            switch (status) {
                case BluetoothProfile.STATE_DISCONNECTED -> {
                    toolbarBinding.tvDeviceName.setText("未连接");
                    updateConnectionStatus(false);
                }
                case BluetoothProfile.STATE_CONNECTING -> {
                    toolbarBinding.tvDeviceName.setText("连接中");
                    updateConnectionStatus(false);
                }
            }
        });

    }

    private void updateConnectedDeviceUI(BluetoothDevice device) {
        runOnUiThread(() -> {
            if (device == null) {
                toolbarBinding.tvDeviceName.setText("未连接");
                updateConnectionStatus(false);
            } else {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                updateConnectionStatus(true);
                toolbarBinding.tvDeviceName.setText(device.getName());
            }
        });

    }


    private boolean permissionsGranted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        toolbarBinding = ToolbarBinding.bind(binding.getRoot().findViewById(R.id.toolbar));

        setSupportActionBar(toolbarBinding.toolbar);

        // 初始化视图
        conectionStatus = toolbarBinding.connectionStatusIndicator;

        toolbarBinding.btnBluetooth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 处理蓝牙按钮点击
                showBluetoothDialog();
            }
        });


        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "设备不支持蓝牙", Toast.LENGTH_SHORT).show();
//            finish();
            return;
        }

        BluetoothLeScanner bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bluetoothLeScanner == null) {
            Toast.makeText(this, "设备不支持蓝牙", Toast.LENGTH_SHORT).show();
//            finish();
            return;
        }

        deviceListAdapter = new DeviceListAdapter(this);

        checkPermissionsAndEnableBluetooth();




//        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
//        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
//        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        initButtons();

        Intent intent = new Intent(this, BleService.class);
        bindService(intent, bleConnection, Context.BIND_AUTO_CREATE);

    }

    private void initButtons() {
        setupButton(binding.btnUp, "0");
        setupButton(binding.btnDown, "1");
        setupButton(binding.btnLeft, "2");
        setupButton(binding.btnRight, "3");
        binding.btnF1.setOnClickListener(this);
        binding.btnF2.setOnClickListener(this);
        binding.btnF3.setOnClickListener(this);
        binding.btnF4.setOnClickListener(this);
        binding.btnF5.setOnClickListener(this);
        binding.btnF6.setOnClickListener(this);
        binding.btnF7.setOnClickListener(this);
        binding.btnF8.setOnClickListener(this);
        binding.btnF9.setOnClickListener(this);
    }

    // 方向键点击逻辑
    private Runnable repeatingMessageRunnable;
    private static final long LONG_PRESS_DURATION = 500; // 长按判定时间，500毫秒
    private static final long MESSAGE_INTERVAL = 30; // 长按时消息发送间隔，30毫秒

    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isLongPress = false;


    @SuppressLint("ClickableViewAccessibility")
    private void setupButton(ImageButton button, String direction) {
        button.setOnTouchListener(new View.OnTouchListener() {
            private Runnable longPressRunnable = new Runnable() {
                @Override
                public void run() {
                    isLongPress = true;
                    startRepeatingMessage(direction);
                }
            };

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:

                        isLongPress = false;
                        handler.postDelayed(longPressRunnable, LONG_PRESS_DURATION);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        handler.removeCallbacks(longPressRunnable);
                        stopRepeatingMessage();
                        if (!isLongPress) {
                            sendToBle(direction);
                        }
                        break;
                }
                return false;
            }
        });

    }

    private void startRepeatingMessage(String message) {
        repeatingMessageRunnable = new Runnable() {
            @Override
            public void run() {
               sendToBle(message);
                handler.postDelayed(this, MESSAGE_INTERVAL);
            }
        };
        handler.post(repeatingMessageRunnable);
    }

    private void stopRepeatingMessage() {
        if (repeatingMessageRunnable != null) {
            handler.removeCallbacks(repeatingMessageRunnable);
        }
    }



    private void checkPermissionsAndEnableBluetooth() {
        String[] permissions;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions = new String[]{Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION};
            // 检查这些权限
        } else {
            permissions = new String[]{Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION};
            // 检查这些权限
        }


        boolean allPermissionsGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false;
                break;
            }
        }

        if (allPermissionsGranted) {
            permissionsGranted = true;
            enableBluetooth();
        } else {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS);
        }

    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                showBluetoothDialog();
            } else {
                Toast.makeText(this, "需要蓝牙权限才能继续", Toast.LENGTH_SHORT).show();
            }
        }
    }


    private DeviceListAdapter deviceListAdapter;

    private ProgressBar scanProgressBar;
    private ImageButton refreshButton;
    private  ListView listView;

    private AlertDialog scanDialog;

    private void showBluetoothDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.ble_items, null);
        listView = view.findViewById(R.id.listViewDevices);
        listView.setAdapter(deviceListAdapter);
        scanProgressBar = view.findViewById(R.id.scanProgressBar);

        refreshButton = view.findViewById(R.id.refreshButton);
        refreshButton.setOnClickListener(v -> bleService.startScan());

        builder.setView(view).setNegativeButton("取消", (dialog, which) -> bleService.stopScan()).setOnDismissListener(dialog -> bleService.stopScan());

        scanDialog = builder.create();
        scanDialog.show();


        listView.setOnItemClickListener((parent, view1, position, id) -> {
            BluetoothDevice device = bleService.getDeviceList().getValue().get(position);
            bleService.connectToDevice(device);
        });

        bleService.startScan();
    }


    private void sendToBle(String msg) {
        bleService.writeCharacteristic(msg);
    }

    @Override
    public void onClick(View v) {
     if (v == binding.btnF1) {
            sendToBle("A");
        } else if (v == binding.btnF2) {
            sendToBle("B");
        } else if (v == binding.btnF3) {
            sendToBle("C");
        } else if (v == binding.btnF4) {
            sendToBle("D");
        } else if (v == binding.btnF5) {
            sendToBle("E");
        } else if (v == binding.btnF6) {
            sendToBle("F");
        } else if (v == binding.btnF7) {
            sendToBle("G");
        } else if (v == binding.btnF8) {
            sendToBle("H");
        }else if (v == binding.btnF9) {
         sendToBle("I");
     }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bound) {
            unbindService(bleConnection);
            bound = false;
        }
    }


    @SuppressLint("MissingPermission")
    private void enableBluetooth() {

        if (!permissionsGranted) {
            checkPermissionsAndEnableBluetooth();
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            Toast.makeText(this, "已打开蓝牙", Toast.LENGTH_SHORT).show();
//            startDiscovery();
        }
    }


    public void updateConnectionStatus(boolean connected) {
        GradientDrawable indicator = (GradientDrawable) conectionStatus.getBackground();

        if (connected) {
            indicator.setColor(ContextCompat.getColor(this, android.R.color.holo_green_light));
        } else {
            indicator.setColor(ContextCompat.getColor(this, android.R.color.white));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.exit) {
            showExitConfirmationDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    // 显示退出确认对话框
    private void showExitConfirmationDialog() {
        new AlertDialog.Builder(this).setTitle("确认退出").setMessage("您确定要退出应用吗？").setPositiveButton("确认", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                exitApp();
            }
        }).setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // 用户选择取消，不做任何操作
                dialog.dismiss();
            }
        }).show();
    }


    private void exitApp() {
        // 在这里添加任何需要在退出前执行的清理操作

        // 显示一个Toast消息
//        Toast.makeText(this, "正在退出应用", Toast.LENGTH_SHORT).show();

        // 退出应用
        finishAffinity();
    }


    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {
        super.onPointerCaptureChanged(hasCapture);
    }

    @Override
    public void onDeviceFound(BluetoothDevice device) {

    }

    @Override
    public void onScanFinished() {

    }

    @Override
    public void onConnected(BluetoothDevice device) {
        runOnUiThread(() -> {
            Toast.makeText(this, "连接成功", Toast.LENGTH_SHORT).show();
            // 关闭连接弹窗
            if (scanDialog != null && scanDialog.isShowing()) {
                scanDialog.dismiss();
            }
        });
    }

    @Override
    public void onDisconnected() {
        runOnUiThread(() -> {
            Toast.makeText(this, "连接断开", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onConnectionFailed() {

    }
}
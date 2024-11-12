package com.example.qt;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;

public class DeviceListAdapter extends ArrayAdapter<BluetoothDevice> {
    private final List<BluetoothDevice> devices = new ArrayList<>();
    private BluetoothDevice connectedDevice;
    private boolean isConnecting;
    private Context context;

    public DeviceListAdapter(Context context) {
        super(context, 0);
        this.context = context;
    }
    private final  String TAG = "DeviceListAdapter";
    @Override
    public int getCount() {
        return devices.size();
    }


    @Override
    public BluetoothDevice getItem(int position) {
        return devices.get(position);
    }

    public  void updateDeviceList(List<BluetoothDevice> items) {

        devices.clear();
        devices.addAll(items);
        Log.i(TAG, "updateDeviceList: " + devices.size());
        notifyDataSetChanged();
    }

    public  void  updateStatus(int status) {
        this.isConnecting = status == BluetoothProfile.STATE_CONNECTING;
        notifyDataSetChanged();
    }

    public void updateConnectedDevice(BluetoothDevice device) {
        this.connectedDevice = device;
        notifyDataSetChanged();
    }



    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        Log.d("DeviceListAdapter", "getView called for position: " + position);

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.ble_item, parent, false);
        }

        BluetoothDevice device = getItem(position);
        TextView deviceName = convertView.findViewById(R.id.deviceName);
        ImageView statusIcon = convertView.findViewById(R.id.statusIcon);

        if (device != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12 及以上版本才需要检查 BLUETOOTH_CONNECT 权限
                if (ActivityCompat.checkSelfPermission(context,
                        Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "No BLUETOOTH_CONNECT permission");
                    return null;
                }
            }


            deviceName.setText(device.getName() != null ? device.getName() : device.getAddress());

            if (isSameDevice(connectedDevice, device)) {
                statusIcon.setImageResource(R.drawable.ic_checked);
                convertView.setBackgroundColor(Color.parseColor("#E0F7FA")); // 浅蓝色背景
            } else {
                statusIcon.setImageResource(R.drawable.ic_unchecked);
                convertView.setBackgroundColor(Color.TRANSPARENT);
            }

            convertView.setEnabled(!isConnecting);
        }

        return convertView;
    }


    private boolean isSameDevice(BluetoothDevice a, BluetoothDevice b) {
        if (a == null || b == null) {
            return false;
        }

        return a.getAddress().equals(b.getAddress());
    }


}
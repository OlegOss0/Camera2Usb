package com.example.camera2usb;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;


public class RunTimeReceiver extends BroadcastReceiver {
    private final String TAG = MainActivity.class.getSimpleName();
    private final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    private CameraManager mCameraManager;
    private Context context;
    UsbDevice device;


    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (ACTION_USB_PERMISSION.equals(action)) {
            synchronized (this) {
                device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    if(device != null){
                        //call method to set up device communication
                    }
                }
                else {
                    Log.d(TAG, "permission denied for device " + device);
                }
            }
        }
        if(UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)){
            device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        }
        if(UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)){
            device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        }
    }
}

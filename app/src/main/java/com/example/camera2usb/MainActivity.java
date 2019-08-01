


package com.example.camera2usb;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;


import static android.hardware.usb.UsbConstants.USB_DIR_IN;
import static android.hardware.usb.UsbConstants.USB_DIR_OUT;

public class MainActivity extends AppCompatActivity {
    private CameraManager mCameraManager;
    private UsbManager mUsbManager;
    private Handler mHandler;
    CameraManager.AvailabilityCallback mAvailableCallBackp;
    CameraManager.TorchCallback mTorchCallback;
    private String[] camerasId;
    private CameraHolder cameraHolder;
    private HashMap<String,CameraHolder> cameras = new HashMap<>();
    HashMap<String, UsbDevice> usbDeviceHM;
    private UsbDevice usbVideoDevice;
    private UsbInterface usbVideoInterface;
    private Camera2ApiManager camera2ApiManager;
    RunTimeReceiver usbReceiver;
    ArrayList<MyUsbInerface> myUsbInerfaceArrayList = new ArrayList<>();
    Button btnSwich;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btnSwich = findViewById(R.id.button);
        mHandler = new Handler();
        usbReceiver = new RunTimeReceiver();


        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
            return;
        }

        Camera2ApiManager.getInstance().setupCameraHotplugListener();
        //findUsbCameras();

    }

    private void findUsbCameras() {
        mUsbManager = (UsbManager) this.getSystemService(Context.USB_SERVICE);
        usbDeviceHM = mUsbManager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = usbDeviceHM.values().iterator();
        UsbInterface intf = null;
        while (deviceIterator.hasNext()) {
            UsbDevice device = deviceIterator.next();
            if(device.getDeviceClass() == UsbConstants.USB_CLASS_VIDEO) {
                usbVideoDevice = device;
            }
            usbVideoInterface = findVideoInterface(device);
            int count = usbVideoInterface.getEndpointCount();
            System.out.println();
        }
    }
    private UsbInterface findVideoInterface(UsbDevice device) {
        for (int nIf = 0; nIf < device.getInterfaceCount(); nIf++) {
            UsbInterface usbInterface = device.getInterface(nIf);
            int endPointCount = usbInterface.getEndpointCount();
            if(endPointCount > 0){
                MyUsbInerface myUsbInerface = new MyUsbInerface(usbInterface);
                for(int i = 0; i < endPointCount; i++){
                    UsbEndpoint usbEndpoint = usbInterface.getEndpoint(i);
                    int direction = usbEndpoint.getDirection();
                    switch (direction){
                        case USB_DIR_OUT:
                            myUsbInerface.setDirection("OUT");
                        case USB_DIR_IN:
                            myUsbInerface.setDirection("IN");
                    }
                    myUsbInerface.addEndpoint(usbInterface.getEndpoint(i));
                }
                myUsbInerfaceArrayList.add(myUsbInerface);
            }
            System.out.println();

            /*if (UsbConstants.USB_CLASS_VIDEO == usbInterface.getInterfaceClass()) {
                return usbInterface;
            }*/
        }

        UsbDeviceConnection mConnection =  mUsbManager.openDevice(device);
        if (mConnection == null) try {
            throw new IOException("Can't open USB connection:" + device.getDeviceName());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,  int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        //Camera2ApiManager.getInstance().setupCameraHotplugListener();
        findUsbCameras();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Camera2ApiManager.getInstance().onViewResumed(this);
    }

    private class MyUsbInerface{
        private UsbInterface usbInterface;
        private ArrayList<UsbEndpoint> endpointArrayList = new ArrayList<>();
        private String usbDirection = "";

        MyUsbInerface(UsbInterface usbInterface){
            this.usbInterface = usbInterface;
        }

        public void addEndpoint(UsbEndpoint usbEndpoint){
            endpointArrayList.add(usbEndpoint);
        }

        public ArrayList<UsbEndpoint> getEndpointArrayList(){
            return endpointArrayList;
        }

        public void setDirection(String s){
            this.usbDirection = s;
        }
    }
}

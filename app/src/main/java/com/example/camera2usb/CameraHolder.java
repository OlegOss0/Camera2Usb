package com.example.camera2usb;

import android.hardware.camera2.CameraCharacteristics;
import android.view.View;

class CameraHolder {
    public final String id;
    private CameraDirection direction;
    private final CameraCharacteristics characteristics;
    private View view;

    enum CameraDirection {FRONT, FASING, EXTERNAL}

    private boolean isAvailable;

    public CameraHolder(CameraCharacteristics characteristics, String id) {
        this.characteristics = characteristics;
        this.id = id;
        setDirection(characteristics.get(CameraCharacteristics.LENS_FACING));
    }

    public void setDirection(int direction) {
        this.direction = CameraDirection.values()[direction];
    }

    public void setView(View view){
        this.view = view;
    }

    public void setAvailable(boolean available) {
        this.isAvailable = available;
    }

    public boolean isAvailable() {
        return isAvailable;
    }


}

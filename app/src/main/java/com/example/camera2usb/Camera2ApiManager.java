package com.example.camera2usb;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

class Camera2ApiManager {
    private static Camera2ApiManager INSTANCE;
    private CameraManager mCameraManager;
    private Handler mBackgroundHandler, mBackgroundHandler2;
    HandlerThread mBackgroundThread, mBackgroundThread2;
    private CameraManager.AvailabilityCallback mAvailableCallback;
    private CameraManager.TorchCallback mTorchCallback;
    private CameraCaptureSession.CaptureCallback mCaptureCallBackListener;
    private String[] camerasId;
    private CameraHolder cameraHolder;
    private HashMap<String, CameraHolder> cameras = new HashMap<>();
    private TextureView mTextureViewCam1, mTextureViewCam2;
    ImageReader mImageReader;
    private Size mPreviewSize;
    private Activity mActivity;
    //Orientation of the camera sensor
    private int mSensorOrientation;
    private File mFile;
    private static final String TAG = Camera2ApiManager.class.getSimpleName();
    private static final int MAX_PREVIEW_WIDTH = 1920;
    private static final int MAX_PREVIEW_HEIGHT = 1080;
    Size optimalSize;
    private Semaphore openLock = new Semaphore(1);
    CameraStateCallBack cameraStateCallBack, cameraStateCallBack2;


    public static Camera2ApiManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new Camera2ApiManager();
        }
        return INSTANCE;
    }

    private void setUpCamerasHolders(String[] camerasId) {
        for (int i = 0; i < camerasId.length; i++) {
            String id = camerasId[i];
            CameraHolder cameraHolder = null;
            try {
                cameraHolder = new CameraHolder(mCameraManager.getCameraCharacteristics(Integer.toString(i)), id);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            cameras.put(id, cameraHolder);
        }
    }

    public void setupCameraHotplugListener() {
        mCameraManager = (CameraManager) App.getAppCtx().getSystemService(Context.CAMERA_SERVICE);
        mBackgroundHandler = new Handler();
        try {
            setUpCamerasHolders(mCameraManager.getCameraIdList());
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        initAvailabilityCallback();
        initTorchCallback();
        mCameraManager.registerAvailabilityCallback(mAvailableCallback, mBackgroundHandler);
    }

    private void initTorchCallback() {
        mTorchCallback = new CameraManager.TorchCallback() {
            @Override
            public void onTorchModeUnavailable(String cameraId) {
                super.onTorchModeUnavailable(cameraId);
            }

            @Override
            public void onTorchModeChanged(String cameraId, boolean enabled) {
                super.onTorchModeChanged(cameraId, enabled);
            }
        };
    }

    private void initAvailabilityCallback() {
        mAvailableCallback = new CameraManager.AvailabilityCallback() {
            @Override
            public void onCameraAvailable(String cameraId) {
                super.onCameraAvailable(cameraId);
                cameras.get(cameraId).setAvailable(true);

            }

            @Override
            public void onCameraUnavailable(String cameraId) {
                super.onCameraUnavailable(cameraId);
                cameras.get(cameraId).setAvailable(false);
            }
        };
    }

    public void onViewResumed(Activity view) {
        mActivity = view;
        mTextureViewCam1 = view.findViewById(R.id.texture_view1);
        mTextureViewCam2 = view.findViewById(R.id.texture_view2);
        mTextureViewCam1.setSurfaceTextureListener(new SurfaceTextureListener());
        mTextureViewCam2.setSurfaceTextureListener(new SurfaceTextureListener());
        Button button = view.findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FrameLayout fl1, fl2;
                fl1 = view.findViewById(R.id.fl1);
                fl2 = view.findViewById(R.id.fl2);

                ViewGroup.LayoutParams lp1 = fl1.getLayoutParams();
                ViewGroup.LayoutParams lp2 = fl2.getLayoutParams();

                fl1.setLayoutParams(lp2);
                fl2.setLayoutParams(lp1);

                fl1.setForegroundGravity(1);
            }
        });

    }


    private class SurfaceTextureListener implements TextureView.SurfaceTextureListener {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    }

    @SuppressLint("MissingPermission")
    private void openCamera(int width, int height) {
        String camId = getAvalCamId();
        if(camId.equals("-1"))return;
        cameras.get(camId).setAvailable(false);
        setUpCameraOutputs(width, height, camId);
        if (mPreviewSize == null)
            return;
        try {
            openLock.tryAcquire(3L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        startBackgroundThread(camId);

        if(camId.equals("0")){
            cameraStateCallBack = new CameraStateCallBack();
            try {
                mCameraManager.openCamera(camId, cameraStateCallBack, mBackgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }else if(camId.equals("1")){
            cameraStateCallBack2 = new CameraStateCallBack();
            try {
                mCameraManager.openCamera(camId, cameraStateCallBack2, mBackgroundHandler2);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    private String getAvalCamId() {
        Iterator<String> iterator = cameras.keySet().iterator();
        while (iterator.hasNext()){
            String id = iterator.next();
            if(cameras.get(id).isAvailable()){
                return id;
            }
        }
        return "-1";
    }


    private void setUpCameraOutputs(int width, int height, String camId) {
        CameraCharacteristics characteristics = null;
        try {
            characteristics = mCameraManager.getCameraCharacteristics(camId);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        //получаем пооедживаемые камерой режимы
        StreamConfigurationMap map = characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) {
            return;
        }
        Size largest = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea());
        mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(), ImageFormat.JPEG, /*maxImages*/2);

        mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);

        // Find out if we need to swap dimension to get the preview size relative to sensor
        // coordinate.
        int displayRotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        //noinspection ConstantConditions
        /*mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        boolean swappedDimensions = false;
        switch (displayRotation) {
            case Surface.ROTATION_0:
            case Surface.ROTATION_180:
                if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                    swappedDimensions = true;
                }
                break;
            case Surface.ROTATION_90:
            case Surface.ROTATION_270:
                if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                    swappedDimensions = true;
                }
                break;
            default:
                Log.e(TAG, "Display rotation is invalid: " + displayRotation);
        }*/

        Point displaySize = new Point();
        mActivity.getWindowManager().getDefaultDisplay().getSize(displaySize);
        int rotatedPreviewWidth = width;
        int rotatedPreviewHeight = height;
        int maxPreviewWidth = displaySize.x;
        int maxPreviewHeight = displaySize.y;
        /*if (swappedDimensions) {
            rotatedPreviewWidth = height;
            rotatedPreviewHeight = width;
            maxPreviewWidth = displaySize.y;
            maxPreviewHeight = displaySize.x;
        }*/
        if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
            maxPreviewWidth = MAX_PREVIEW_WIDTH;
        }
        if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
            maxPreviewHeight = MAX_PREVIEW_HEIGHT;
        }
        mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth, maxPreviewHeight, largest);
        // We fit the aspect ratio of TextureView to the size of preview we picked.
        int orientation = mActivity.getResources().getConfiguration().orientation;

        // Check if the flash is supported.
        Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        //mFlashSupported = available == null ? false : available;
    }

    public void onViewPaused(View view) {
    }

static class CompareSizesByArea implements Comparator<Size> {

    @Override
    public int compare(Size lhs, Size rhs) {
        // We cast here to ensure the multiplications won't overflow
        return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                (long) rhs.getWidth() * rhs.getHeight());
    }

}

    private void startBackgroundThread(String id) {
        if(id.equals("0")){
            mBackgroundThread = new HandlerThread("CameraBackground" + id);
            mBackgroundThread.start();
            mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        }else{
            mBackgroundThread2 = new HandlerThread("CameraBackground" + id);
            mBackgroundThread2.start();
            mBackgroundHandler2 = new Handler(mBackgroundThread.getLooper());
        }

    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage(), mFile));
        }
    };

private static class ImageSaver implements Runnable {
    /**
     * The JPEG image
     */
    private final Image mImage;
    /**
     * The file we save the image into.
     */
    private final File mFile;

    ImageSaver(Image image, File file) {
        mImage = image;
        mFile = file;
    }

    @Override
    public void run() {
        ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        FileOutputStream output = null;
        try {
            output = new FileOutputStream(mFile);
            output.write(bytes);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            mImage.close();
            if (null != output) {
                try {
                    output.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}

    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }
        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

private class CameraStateCallBack extends CameraDevice.StateCallback {
    private CameraDevice cameraDevice;

    @Override
    public void onOpened(CameraDevice camera) {
        cameraDevice = camera;
        openLock.release();
        String id = camera.getId();
        final TextureView textureView = id.contains("0") ? mTextureViewCam1 : mTextureViewCam2;

        //ImageReader reader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.JPEG, 5);
        List<Surface> outputSurfaces = new ArrayList<>();
        outputSurfaces.add(mImageReader.getSurface());
        outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));

        textureView.getSurfaceTexture().setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        try {
            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            CaptureRequest.Builder builder = null;
                            try {
                                builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                            if (builder != null) {
                                try {
                                    Surface surface = new Surface(textureView.getSurfaceTexture());
                                    builder.addTarget(surface);
                                    session.setRepeatingRequest(builder.build(), new CameraCaptureCallBackListener(), mBackgroundHandler);
                                } catch (CameraAccessException e) {
                                    e.printStackTrace();
                                }
                            }

                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {

                        }
                    }
                    , mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }


    }

    @Override
    public void onDisconnected(CameraDevice camera) {

    }

    @Override
    public void onError(CameraDevice camera, int error) {

    }
}


private class CameraCaptureCallBackListener extends CameraCaptureSession.CaptureCallback {
    @Override
    public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult) {
        super.onCaptureProgressed(session, request, partialResult);
    }

    @Override
    public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
        super.onCaptureCompleted(session, request, result);
    }
}

}

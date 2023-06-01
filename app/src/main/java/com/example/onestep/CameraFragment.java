package com.example.onestep;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@SuppressLint("ValidFragment")
public class CameraFragment extends Fragment {
    private ConnectionCallback connectionCallback;
    private ImageReader.OnImageAvailableListener imageAvailableListener;
    private Size inputSize;
    private String cameraId;

    private AutoFitTextureView autoFitTextureView = null;

    private HandlerThread backgroundThread = null;
    private Handler backgroundHandler = null;

    private Size previewSize;
    private int sensorOrientation;

    private final Semaphore cameraOpenCloseLock = new Semaphore(1);

    private CameraDevice cameraDevice;
    private CaptureRequest.Builder previewRequestBuilder;
    private ImageReader previewReader;
    private CameraCaptureSession captureSession;

    private CameraFragment(final ConnectionCallback callback, //1.프래그먼트 생성 및 연결
                           final ImageReader.OnImageAvailableListener imageAvailableListener,
                           final Size inputSize,
                           final String cameraId) {
        this.connectionCallback = callback;
        this.imageAvailableListener = imageAvailableListener;
        this.inputSize = inputSize;
        this.cameraId = cameraId;
    }

    public static CameraFragment newInstance(
            final ConnectionCallback callback,
            final ImageReader.OnImageAvailableListener imageAvailableListener,
            final Size inputSize,
            final String cameraId) {
        return new CameraFragment(callback, imageAvailableListener, inputSize, cameraId);
    }

    //4.UI 구성 함수
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        autoFitTextureView = view.findViewById(R.id.autoFitTextureView);
    }

    public void onResume() { //5.카메라 연결 시작
        super.onResume();
        startBackgroundThread(); //18.백그라운드 스레드 시작

        if(!autoFitTextureView.isAvailable())
            autoFitTextureView.setSurfaceTextureListener(surfaceTextureListener);
        else
            openCamera(autoFitTextureView.getWidth(), autoFitTextureView.getHeight());
    }

    public void onPause() { //16.카메라 연결 해제
        closeCamera();
        stopBackgroundThread(); //20.백그라운드 스레드 종료 호출
        super.onPause();
    }

    private void startBackgroundThread() { //17.백그라운드 스레드 생성
        backgroundThread = new HandlerThread("ImageListener");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() { //19.백그라운드 스레드 종료
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }
    }

    private final TextureView.SurfaceTextureListener surfaceTextureListener = //6.surface Texture 호출
            new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(
                        final SurfaceTexture texture, final int width, final int height) {
                    openCamera(width, height);
                }

                @Override
                public void onSurfaceTextureSizeChanged(
                        final SurfaceTexture texture, final int width, final int height) {
                    configureTransform(width, height);
                }

                @Override
                public boolean onSurfaceTextureDestroyed(final SurfaceTexture texture) {
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(final SurfaceTexture texture) {
                }
            };

    @SuppressLint("MissingPermission") //7.카메라 연결
    private void openCamera(final int width, final int height) {
        final Activity activity = getActivity();
        final CameraManager manager =
                (CameraManager)activity.getSystemService(Context.CAMERA_SERVICE);

        setupCameraOutputs(manager);
        configureTransform(width, height);

        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                Toast.makeText(getContext(),
                        "Time out waiting to lock camera opening.",
                        Toast.LENGTH_LONG).show();
                activity.finish();
            } else {
                manager.openCamera(cameraId, stateCallback, backgroundHandler);
            }
        } catch (final InterruptedException | CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setupCameraOutputs(CameraManager manager) { //8.카메라 출력 크기와 방향 설정
        try {
            final CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

            final StreamConfigurationMap map =characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

            previewSize = chooseOptimalSize(
                    map.getOutputSizes(SurfaceTexture.class),
                    inputSize.getWidth(),
                    inputSize.getHeight());

            final int orientation = getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                autoFitTextureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
            } else {
                autoFitTextureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
            }
        } catch (final CameraAccessException cae) {
            cae.printStackTrace();
        }

        connectionCallback.onPreviewSizeChosen(previewSize, sensorOrientation);
    }

    private void configureTransform(final int viewWidth, final int viewHeight) { //11.가로세로 크기가 변경되면 호출
        final Activity activity = getActivity();
        if (null == autoFitTextureView || null == previewSize || null == activity) {
            return;
        }

        final int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        final Matrix matrix = new Matrix();
        final RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        final RectF bufferRect =
                new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        final float centerX = viewRect.centerX();
        final float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(
                    centerX - bufferRect.centerX(),
                    centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            final float scale = Math.max(
                    (float) viewHeight / previewSize.getHeight(),
                    (float) viewWidth / previewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        autoFitTextureView.setTransform(matrix);
    }

    protected Size chooseOptimalSize(final Size[] choices, final int width, final int height) { //9.최적의 카메라 출력 크기 계산
        final int minSize = Math.min(width, height);
        final Size desiredSize = new Size(width, height);

        final List<Size> bigEnough = new ArrayList<Size>();
        final List<Size> tooSmall = new ArrayList<Size>();
        for (final Size option : choices) {
            if (option.equals(desiredSize)) {
                return desiredSize;
            }

            if (option.getHeight() >= minSize && option.getWidth() >= minSize) {
                bigEnough.add(option);
            } else {
                tooSmall.add(option);
            }
        }

        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            return Collections.max(tooSmall, new CompareSizesByArea());
        }
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() { //12.캡쳐 세션 생성
        @Override
        public void onOpened(final CameraDevice cd) {
            cameraOpenCloseLock.release();
            cameraDevice = cd;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(final CameraDevice cd) {
            cameraOpenCloseLock.release();
            cd.close();
            cameraDevice = null;
        }

        @Override
        public void onError(final CameraDevice cd, final int error) {
            cameraOpenCloseLock.release();
            cd.close();
            cameraDevice = null;
            final Activity activity = getActivity();
            if (null != activity) {
                activity.finish();
            }
        }
    };

    private void createCameraPreviewSession() { //13.미리보기 세션 생성
        try {
            final SurfaceTexture texture = autoFitTextureView.getSurfaceTexture();
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

            final Surface surface = new Surface(texture);

            previewReader = ImageReader.newInstance(previewSize.getWidth(),
                    previewSize.getHeight(), ImageFormat.YUV_420_888, 2);
            previewReader.setOnImageAvailableListener(imageAvailableListener, backgroundHandler);

            previewRequestBuilder = cameraDevice.createCaptureRequest(
                    CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);
            previewRequestBuilder.addTarget(previewReader.getSurface());

            previewRequestBuilder.set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            previewRequestBuilder.set(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);

//            previewRequestBuilder.set(
//                    CaptureRequest.FLASH_MODE,
//                    CameraMetadata.FLASH_MODE_TORCH);

            cameraDevice.createCaptureSession(
                    Arrays.asList(surface, previewReader.getSurface()),
                    sessionStateCallback, null);
        } catch (final CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private final CameraCaptureSession.StateCallback sessionStateCallback = //14.카메라 캡쳐 세션의 상태 콜백 함수
            new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(final CameraCaptureSession cameraCaptureSession) {
                    if (null == cameraDevice) {
                        return;
                    }

                    captureSession = cameraCaptureSession;
                    try {
                        captureSession.setRepeatingRequest(previewRequestBuilder.build(), null, null);
                    } catch (final CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(final CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(getActivity(), "CameraCaptureSession Failed", Toast.LENGTH_SHORT).show();
                }
            };

    private void closeCamera() { //카메라 연결 해제
        try {
            cameraOpenCloseLock.acquire();
            if (null != captureSession) {
                captureSession.close();
                captureSession = null;
            }
            if (null != cameraDevice) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (null != previewReader) {
                previewReader.close();
                previewReader = null;
            }
        } catch (final InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            cameraOpenCloseLock.release();
        }
    }
    public interface ConnectionCallback { //2.콜백 함수 정의
        void onPreviewSizeChosen(Size size, int cameraRotation);
    }

    static class CompareSizesByArea implements Comparator<Size> { //10.넓이를 기준으로 사이즈 비교
        @Override
        public int compare(final Size lhs, final Size rhs) {
            return Long.signum(
                    (long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }
    }
}

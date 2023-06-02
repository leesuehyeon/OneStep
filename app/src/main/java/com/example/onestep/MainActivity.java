package com.example.onestep;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.Fragment;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import android.speech.tts.TextToSpeech;

public class MainActivity extends AppCompatActivity {

    private static final String CAMERA_PERMISSION = Manifest.permission.CAMERA;
    private static final int PERMISSION_REQUEST_CODE = 1;

    private TextView textView;
    private Classifier cls;

    private int previewWidth = 0;
    private int previewHeight = 0;
    private int sensorOrientation = 0;

    private Bitmap rgbFrameBitmap = null;

    private HandlerThread handlerThread;
    private Handler handler;

    private boolean isProcessingFrame = false;

    private TextToSpeech tts;

    LocationActivity locationActivity = new LocationActivity();

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) { //1.액티비티 초기화 및 권한 요청
        super.onCreate(null);
        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        textView = findViewById(R.id.textView);

        cls = new Classifier(this);
        try {
            cls.init();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        if(checkSelfPermission(CAMERA_PERMISSION)
                == PackageManager.PERMISSION_GRANTED) {
            setFragment();
        } else {
            requestPermissions(new String[]{CAMERA_PERMISSION},
                    PERMISSION_REQUEST_CODE);
        }

        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() { // 음성 인식 안내 객체 초기화
            @Override
            public void onInit(int status) {
                if(status!=android.speech.tts.TextToSpeech.ERROR) {
                    tts.setLanguage(Locale.KOREAN);
                }
            }
        });
    }

    protected synchronized void onDestroy() {
        cls.finish();
        super.onDestroy();
    }

    public synchronized void onResume() {
        super.onResume();

        handlerThread = new HandlerThread("InferenceThread");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    @Override
    public synchronized void onPause() {
        handlerThread.quitSafely();
        try {
            handlerThread.join();
            handlerThread = null;
            handler = null;
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }

        super.onPause();
    }

    public synchronized void onStart() {
        super.onStart();
    }

    @Override
    public synchronized void onStop() {
        super.onStop();
    }

    public void onRequestPermissionsResult( //2.권한 요청 결과에 따른 콜백 함수
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == PERMISSION_REQUEST_CODE) {
            if(grantResults.length > 0 && allPermissionsGranted(grantResults)) {
                setFragment();
            } else {
                Toast.makeText(this, "permission denied", Toast.LENGTH_LONG).show();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private boolean allPermissionsGranted(final int[] grantResults) {
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    protected void setFragment() { //3.액티비티에 프래그먼트 추가
        Size inputSize = cls.getModelInputSize();
        String cameraId = chooseCamera();

        if(inputSize.getWidth() > 0 && inputSize.getHeight() > 0 && !cameraId.isEmpty()) {
            Fragment fragment = CameraFragment.newInstance(
                    (size, rotation) -> {
                        previewWidth = size.getWidth();
                        previewHeight = size.getHeight();
                        sensorOrientation = rotation - getScreenOrientation();
                    },
                    reader->processImage(reader),
                    inputSize,
                    cameraId);

            getFragmentManager().beginTransaction().replace(
                    R.id.fragment, fragment).commit();
        } else {
            Toast.makeText(this, "Can't find camera", Toast.LENGTH_SHORT).show();
        }
    }

    private String chooseCamera() { //4.기기에서 적절한 카메라 선택
        final CameraManager manager =
                (CameraManager)getSystemService(Context.CAMERA_SERVICE);
        try {
            for (final String cameraId : manager.getCameraIdList()) {
                final CameraCharacteristics characteristics =
                        manager.getCameraCharacteristics(cameraId);

                final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    return cameraId;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        return "";
    }

    protected int getScreenOrientation() { //5.화면의 회전 여부를 확인
        switch (getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_270:
                return 270;
            case Surface.ROTATION_180:
                return 180;
            case Surface.ROTATION_90:
                return 90;
            default:
                return 0;
        }
    }

    protected void processImage(ImageReader reader) { //6.딥러닝 모델 추론
        if (previewWidth == 0 || previewHeight == 0) {
            return;
        }

        if(rgbFrameBitmap == null) {
            rgbFrameBitmap = Bitmap.createBitmap(
                    previewWidth,
                    previewHeight,
                    Bitmap.Config.ARGB_8888);
        }

        if (isProcessingFrame) {
            return;
        }

        isProcessingFrame = true;

        final Image image = reader.acquireLatestImage();
        if (image == null) {
            isProcessingFrame = false;
            return;
        }

        YuvToRgbConverter.yuvToRgb(this, image, rgbFrameBitmap);

        runInBackground(() -> {
            if (cls != null && cls.isInitialized()) {
                final Pair<String, Float> output = cls.classify(rgbFrameBitmap, sensorOrientation);

                runOnUiThread(() -> {
                    String resultStr = String.format(Locale.ENGLISH,
                            "class : %s, prob : %.2f%%",
                            output.first, output.second * 100);

                    String blocksResult = String.format(Locale.ENGLISH, "%s", output.first);

                    // 결과 화면 출력
                    textView.setText(resultStr);

                    // 음성 안내 기능
                    TextToSpeech(blocksResult);

                    //위치 추적 기능
                    FindLocation(blocksResult);
                });
            }
            image.close();
            isProcessingFrame = false;
        });
    }

    protected void TextToSpeech(String blocksResult) { //음성 안내 기능
        if(blocksResult.equals("0 normal")) {
            String textOne = "정상 점자블록입니다.";
            tts.setPitch(1.0f); // 높낮이
            tts.setSpeechRate(1.0f); // 빠르기
            tts.speak(textOne, TextToSpeech.QUEUE_FLUSH, null);
        }

        if(blocksResult.equals("1 damage")) {
            String textTwo = "훼손된 점자블록입니다.";
            tts.setPitch(1.0f); // 높낮이
            tts.setSpeechRate(1.0f); // 빠르기
            tts.speak(textTwo, TextToSpeech.QUEUE_FLUSH, null);
        }

        if(blocksResult.equals("2 obstacle")) {
            String textThree = "앞에 장애물이 있습니다.";
            tts.setPitch(1.0f); // 높낮이
            tts.setSpeechRate(1.0f); // 빠르기
            tts.speak(textThree, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    protected void FindLocation(String blocksResult) { //위치 찾기 기능
        if(blocksResult.equals("1 damage")) {
            if (locationActivity.checkPermissionForLocation(this)) {
                locationActivity.startLocationUpdates(this);
            }
        }
    }

    public void onDestory() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        super.onDestroy();
    }

    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }
}
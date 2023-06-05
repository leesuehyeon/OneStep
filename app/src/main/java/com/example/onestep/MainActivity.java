package com.example.onestep;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.util.Pair;
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import android.speech.tts.TextToSpeech;

public class MainActivity extends AppCompatActivity {

    private static final String CAMERA_PERMISSION = Manifest.permission.CAMERA;
    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final int REQUEST_PERMISSION_LOCATION = 2;
    private static final int PERMISSION_CODE = 3;

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
    protected void onCreate(Bundle savedInstanceState) { //액티비티 초기화 및 권한 요청
        super.onCreate(null);
        setContentView(R.layout.activity_main);

        //액티비티가 실행되는 동안 화면이 꺼지지 않고 계속 켜져 있도록 설정
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //모델 추론 결과 값을 출력할 텍스트뷰 연결
        textView = findViewById(R.id.textView);

        cls = new Classifier(this);
        try {
            cls.init();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        if(checkSelfPermission(CAMERA_PERMISSION) //권한 부여 확인
                == PackageManager.PERMISSION_GRANTED) {
            requestLocationPermissions();
        } else {
            requestPermissions(new String[]{CAMERA_PERMISSION},
                    PERMISSION_REQUEST_CODE);
        }

        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() { //음성 안내 기능
            @Override
            public void onInit(int status) {
                if(status!=android.speech.tts.TextToSpeech.ERROR) {
                    tts.setLanguage(Locale.KOREAN);
                }
            }
        });
    }

    // 위치 권한 설정
    private void requestLocationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //안드로이드 6.0 이상일 경우 런타임 권한 요청
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSION_LOCATION);
            } else {
                // 위치 권한이 이미 허용된 경우 처리
                requestStoragePermissions();
                //setFragment();
            }
        } else {
            // 안드로이드 6.0 미만일 경우 위치 권한이 자동으로 부여되므로 처리
            requestStoragePermissions();
            //setFragment();
        }
    }

    // 저장소 권한 설정
    private void requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_CODE);
        } else {
            setFragment(); //프래그먼트 생성
        }
    }

    protected synchronized void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }

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

    public void onRequestPermissionsResult( //권한 요청 결과에 따른 콜백 함수
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        /*if(requestCode == PERMISSION_REQUEST_CODE) {
            if(grantResults.length > 0 && allPermissionsGranted(grantResults)) {
                setFragment();
            } else {
                Toast.makeText(this, "permission denied", Toast.LENGTH_LONG).show();
            }
        }*/
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && allPermissionsGranted(grantResults)) {
                // 카메라 권한이 허용된 경우 위치 권한을 요청
                requestLocationPermissions();
            } else {
                // 카메라 권한이 거부된 경우 처리
                Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_PERMISSION_LOCATION) {
            if (grantResults.length > 0 && allPermissionsGranted(grantResults)) {
                // 위치 권한이 허용된 경우 저장소 접근 권한을 요청
                requestStoragePermissions();
            } else {
                // 위치 권한이 거부된 경우 처리
                Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == PERMISSION_CODE) {
            if (grantResults.length > 0 && allPermissionsGranted(grantResults)) {
                // 저장소 접근 권한이 허용된 경우 시작
                setFragment();
            } else {
                // 저장소 접근 권한이 거부된 경우 처리
                Toast.makeText(this, "저장소 접근 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
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

    protected void setFragment() { //액티비티에 프래그먼트 추가
        Size inputSize = cls.getModelInputSize();
        String cameraId = chooseCamera();

        if(inputSize.getWidth() > 0 && inputSize.getHeight() > 0 && !cameraId.isEmpty()) {
            Fragment fragment = CameraFragment.newInstance(
                    (size, rotation) -> { //size를 통해 최적의 카메라 해상도를 구하는 데 기준이 되는 크기를 전달
                        previewWidth = size.getWidth();
                        previewHeight = size.getHeight();
                        sensorOrientation = rotation - getScreenOrientation();
                    },
                    reader->processImage(reader), //이미지 전송
                    inputSize,
                    cameraId);

            getFragmentManager().beginTransaction().replace(
                    R.id.fragment, fragment).commit();
        } else {
            Toast.makeText(this, "카메라를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private String chooseCamera() { //기기에서 적절한 카메라를 선택하여 카메라 ID를 반환
        final CameraManager manager =
                (CameraManager)getSystemService(Context.CAMERA_SERVICE); //기기에 포함된 카메라의 ID를 얻어옴
        try {
            for (final String cameraId : manager.getCameraIdList()) {
                final CameraCharacteristics characteristics =
                        manager.getCameraCharacteristics(cameraId); //얻어온 카메라 ID를 통해 해당 카메라의 특성 파악

                final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) { //후면 카메라를 사용
                    return cameraId;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        return "";
    }

    protected int getScreenOrientation() { //기기 회면의 회전 여부를 확인
        switch (getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_270: //회면 회전 값 270도
                return 270;
            case Surface.ROTATION_180: //회면 회전 값 180도
                return 180;
            case Surface.ROTATION_90: //회면 회전 값 90도
                return 90;
            default:
                return 0;
        }
    }

    protected synchronized void processImage(ImageReader reader) { //딥러닝 모델 추론
        if (previewWidth == 0 || previewHeight == 0) {
            return;
        }

        if(rgbFrameBitmap == null) {
            rgbFrameBitmap = Bitmap.createBitmap(
                    previewWidth,
                    previewHeight,
                    Bitmap.Config.ARGB_8888); //전달받은 이미지를 ARGB_8888 비트맵으로 변환
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

        runInBackground(() -> { //모델에 추론 요청
            if (cls != null && cls.isInitialized()) { //딥러닝 모델 추론은 백그라운드 스레드에서 동작
                final Pair<String, Float> output = cls.classify(rgbFrameBitmap, sensorOrientation);

                runOnUiThread(() -> { //추론 결과 텍스트뷰 출력은 메인 스레드에서 동작

                    String resultStr = String.format(Locale.ENGLISH,
                            "class : %s, prob : %.2f%%",
                            output.first, output.second * 100);


                    textView.setText(resultStr); //결과 화면 출력
                });

                String blocksResult = String.format(Locale.ENGLISH, "%s", output.first);

                TwoSignal(blocksResult); //음성 안내 & 진동 신호 기능
                FindLocation(blocksResult, rgbFrameBitmap); //위치 찾기 기능 & 사진 전송 기능
            }
            image.close();
            isProcessingFrame = false;
        });
    }

    protected void TwoSignal(String blocksResult) { //음성 안내 & 진동 신호 기능
        if(blocksResult.equals("2 obstacle")) {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE); //진동 신호 기능
            vibrator.vibrate(300); //0.3초간 진동

            String textThree = "앞에 장애물이 있습니다.";
            tts.setPitch(1.0f); //높낮이
            tts.setSpeechRate(1.0f); //빠르기
            tts.speak(textThree, TextToSpeech.QUEUE_FLUSH, null); //음성 안내 기능

            try {
                Thread.sleep(3000); //3초 지연
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    };

    protected void FindLocation(String blocksResult, Bitmap rgbFrameBitmap) { //위치 찾기 기능
        if(blocksResult.equals("1 damage")) {
            if (locationActivity.checkPermissionForLocation(this)) {
                if (locationActivity.checkPermissionForStorage(this)) {
                    locationActivity.startLocationUpdates(this, rgbFrameBitmap);
                }
            }
        }
    }

    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }
}
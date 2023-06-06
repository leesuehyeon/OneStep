package com.example.onestep;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

public class SplashActivity extends AppCompatActivity {
    private TextToSpeech tts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TTS();
    }

    private void TTS() {
        tts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() { //음성 안내 기능
            @Override
            public void onInit(int status) {
                if(status!=android.speech.tts.TextToSpeech.ERROR) {
                    tts.setLanguage(Locale.KOREAN);
                    String text = "앱을 시작합니다. 후면 카메라를 바닥을 향해 놓아 주세요.";
                    tts.setPitch(1.0f); //높낮이
                    tts.setSpeechRate(1.0f); //빠르기
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
                }
            }
        });

        moveMain();
    }

    private void moveMain() {
        new Handler().postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                startActivity(intent);
                finish();
            }
        }, 10000); //10초 후 main activity로 이동
    }

    protected synchronized void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        super.onDestroy();
    }
}
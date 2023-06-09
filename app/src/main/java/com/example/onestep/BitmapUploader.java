package com.example.onestep;


import android.graphics.Bitmap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class BitmapUploader {
    private static final String EC2_INSTANCE_IP = "15.165.159.230"; // EC2 인스턴스의 IP 주소
    private static final String EC2_USERNAME = "ec2-user"; // EC2 인스턴스에 접속할 사용자명
    private static final String EC2_KEY_PATH = "/home/ec2-user/.ssh/onekey.pem"; // EC2 인스턴스에 접속하기 위한 키페어 파일 경로
    private static final String REMOTE_DIRECTORY = "/home/ec2-user/images/"; // EC2 인스턴스에서 이미지를 저장할 디렉토리 경로

    public void uploadBitmap(Bitmap bitmap) {
        try {
            // 비트맵을 파일로 저장
            File file = saveBitmapToFile(bitmap);

            // 파일을 EC2 인스턴스로 업로드
            uploadFileToEC2(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private File saveBitmapToFile(Bitmap bitmap) throws IOException {
        // 임시 파일 생성
        File file = File.createTempFile("bitmap_", ".png");

        // 비트맵을 파일로 저장
        FileOutputStream fileOutputStream = new FileOutputStream(file);
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream);
        fileOutputStream.flush();
        fileOutputStream.close();

        return file;
    }

    private void uploadFileToEC2(File file) {
        try {
            // 파일을 EC2 인스턴스로 업로드
            String command = String.format("scp -i %s %s %s@%s:%s",
                    EC2_KEY_PATH, file.getAbsolutePath(), EC2_USERNAME, EC2_INSTANCE_IP, REMOTE_DIRECTORY);
            Process process = Runtime.getRuntime().exec(command);

            // 업로드 결과 확인
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("File uploaded successfully");
            } else {
                System.out.println("File upload failed");
            }

            // 임시 파일 삭제
            file.delete();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}


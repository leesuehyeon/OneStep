package com.example.onestep;

<<<<<<< HEAD
import android.graphics.Bitmap;
import android.util.Log;

import java.io.ByteArrayOutputStream;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.S3Exception;

public class BitmapUploader {
    private static final String ACCESS_KEY = "AKIA35AVS5GOGWSALY6F";
    private static final String SECRET_KEY = "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";

    public static void bitmapUploadToS3(Bitmap bitmap, String bucketName, String s3Key) {
        // AWS 자격 증명 및 지역 설정
        AwsBasicCredentials credentials = AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY);
        Region region = Region.AP_NORTHEAST_2; // 서울
        S3Client s3 = S3Client.builder().region(region).credentialsProvider(StaticCredentialsProvider.create(credentials)).build();

        try {
            // 비트맵을 바이트 배열로 전환
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            byte[] bitmapData = outputStream.toByteArray();

            // S3 버킷에 파일 업로드
            //s3 객체로 버킷과 상호작용
            PutObjectRequest objRequest = PutObjectRequest.builder().bucket(bucketName).key(s3Key).build();
            s3.putObject(objRequest, RequestBody.fromBytes(bitmapData));

            Log.d("BitmapUploader", "비트맵 파일을 S3 버킷 " + bucketName + "의 " + s3Key + "에 성공적으로 업로드 하였습니다.");
        } catch (S3Exception e) {
            Log.e("BitmapUploader", "파일 업로드 중 오류가 발생했습니다: " + e.awsErrorDetails().errorMessage());
=======

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
>>>>>>> origin/master
            e.printStackTrace();
        }
    }
}
<<<<<<< HEAD
=======

>>>>>>> origin/master

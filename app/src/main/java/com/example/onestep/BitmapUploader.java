package com.example.onestep;

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
            PutObjectRequest objRequest = PutObjectRequest.builder().bucket(bucketName).key(s3Key).build();
            s3.putObject(objRequest, RequestBody.fromBytes(bitmapData));

            Log.d("BitmapUploader", "비트맵 파일을 S3 버킷 " + bucketName + "의 " + s3Key + "에 성공적으로 업로드 하였습니다.");
        } catch (S3Exception e) {
            Log.e("BitmapUploader", "파일 업로드 중 오류가 발생했습니다: " + e.awsErrorDetails().errorMessage());
            e.printStackTrace();
        }
    }
}

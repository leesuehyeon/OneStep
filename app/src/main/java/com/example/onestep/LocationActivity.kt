package com.example.onestep

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.TextureView
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

class LocationActivity : AppCompatActivity() {
    private var mFusedLocationProviderClient: FusedLocationProviderClient? = null // 현재 위치를 가져오기 위한 변수
    lateinit var mLastLocation: Location // 위치 값을 가지고 있는 객체
    //solution2
    var mLocationRequest = LocationRequest.create().apply {
        priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }
    private val REQUEST_PERMISSION_LOCATION = 2
    private val PERMISSION_CODE = 3

    private var previousLocation: Location? = null
    private lateinit var autoFitTextureView: AutoFitTextureView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_camera)

        autoFitTextureView = findViewById(R.id.autoFitTextureView)
    }

    // 위치 권한이 있는지 확인하는 메서드
    fun checkPermissionForLocation(context: Context): Boolean {
        // Android 6.0 Marshmallow 이상에서는 위치 권한에 추가 런타임 권한이 필요
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                true
            } else {
                // 권한이 없으므로 권한 요청 알림 보내기
                ActivityCompat.requestPermissions(context as Activity, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_PERMISSION_LOCATION)
                false
            }
        } else {
            true
        }
    }

    // 저장소 접근 권한이 있는지 확인하는 메서드
    fun checkPermissionForStorage(context: Context): Boolean {
        // Android 6.0 Marshmallow 이상에서는 저장소 접근 권한에 추가 런타임 권한이 필요
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                true
            } else {
                // 권한이 없으므로 권한 요청 알림 보내기
                ActivityCompat.requestPermissions(context as Activity, arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), PERMISSION_CODE)
                false
            }
        } else {
            true
        }
    }

    //solution1
    /*fun startLocationUpdates(context: Context, rgbFrameBitmap: Bitmap) {
        //FusedLocationProviderClient의 인스턴스를 생성.
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        mFusedLocationProviderClient!!.lastLocation
            .addOnSuccessListener { location: Location? ->
                location?.let {
                    onLocationChanged(context, location, rgbFrameBitmap)
                }
            }
    }*/

    //solution2
    fun startLocationUpdates(context: Context, rgbFrameBitmap: Bitmap) {
        //FusedLocationProviderClient의 인스턴스를 생성.
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        // 기기의 위치에 관한 정기 업데이트를 요청하는 메서드 실행
        // 지정한 루퍼 스레드(Looper.myLooper())에서 콜백(mLocationCallback)으로 위치 업데이트를 요청
        val mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                // 시스템에서 받은 location 정보를 onLocationChanged()에 전달
                locationResult.lastLocation
                onLocationChanged(context, locationResult.lastLocation, rgbFrameBitmap)
            }
        }

        mFusedLocationProviderClient!!.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper())
    }

    // 시스템으로 부터 받은 위치정보를 화면에 갱신해주는 메소드
    private fun onLocationChanged(context: Context, location: Location, rgbFrameBitmap: Bitmap) {
        mLastLocation = location
        val address = getAddress(context, mLastLocation).addressLine

        val latitude = mLastLocation.latitude
        val longitude = mLastLocation.longitude
        val address_s = address.toString()

        if (previousLocation != null && locationThreshold(previousLocation!!, mLastLocation)) {
            Log.v("address_4", latitude.toString())
            Log.v("address_5", longitude.toString())
            Log.v("address_6", address_s)

            //사진 캡처 및 MySQL에 데이터 저장
            saveToMySQL(context, latitude, longitude, address_s, rgbFrameBitmap)
        } else if (previousLocation == null) {
            Log.v("address_1", latitude.toString())
            Log.v("address_2", longitude.toString())
            Log.v("address_3", address_s)

            saveToMySQL(context, latitude, longitude, address_s, rgbFrameBitmap)
        }

        previousLocation = mLastLocation
    }

    data class AddressData(
        val addressLine: String?
    )

    //위도, 경도를 주소로 변환
    private fun getAddress(context: Context, location: Location): AddressData {
        return try {
            val geocoder = Geocoder(context, Locale.KOREA)
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            if (addresses != null && addresses.isNotEmpty()) {
                val address = addresses[0]
                AddressData(
                    address.getAddressLine(0)
                )
            } else {
                AddressData(null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            getAddress(context, location)
        }
    }

    //이동 거리 역치 설정
    private fun locationThreshold(previousLocation: Location, currentLocation: Location): Boolean {
        val distanceThreshold = 0.5
        val distance = previousLocation.distanceTo(currentLocation)
        return distance >= distanceThreshold
    }

    /*private fun captureScreen(): Bitmap {
        val rootView: View = window.decorView.rootView
        val screenshot: Bitmap = Bitmap.createBitmap(rootView.width, rootView.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(screenshot)
        rootView.draw(canvas)

        return screenshot
    }*/

    private fun saveScreenshot(context: Context, screenshot: Bitmap): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "screenshot_$timeStamp.png"

        val file = File(context.externalCacheDir, fileName)
        val fileOutputStream = FileOutputStream(file)
        screenshot.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
        fileOutputStream.flush()
        fileOutputStream.close()

        return file
    }

    private fun uploadFileToServer(file: File, latitude: Double, longitude: Double, address: String) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val url = "http://your_server_url/upload.php" // 파일을 업로드할 PHP 파일의 URL을 입력해주세요

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", file.name, file.asRequestBody("image/*".toMediaTypeOrNull()))
                    .addFormDataPart("latitude", latitude.toString())
                    .addFormDataPart("longitude", longitude.toString())
                    .addFormDataPart("address", address)
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                val client = OkHttpClient()
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    // 업로드 성공
                    val responseBody = response.body?.string()
                    // 서버에서 반환한 응답 처리
                    // responseBody 변수에 서버의 응답이 담겨 있습니다.
                } else {
                    // 업로드 실패
                    // 실패 처리 로직 작성
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // 예외 처리 로직 작성
            }
        }
    }

    private fun saveToMySQL(context: Context, latitude: Double, longitude: Double, address: String, rgbFrameBitmap: Bitmap) {
        val file: File = saveScreenshot(context, rgbFrameBitmap)
        if (file.exists()) {
            uploadFileToServer(file, latitude, longitude, address)
        }

    }

}
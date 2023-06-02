package com.example.onestep

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import java.io.IOException
import java.util.*
import kotlin.properties.Delegates

class LocationActivity : AppCompatActivity() {
    private var mFusedLocationProviderClient: FusedLocationProviderClient? = null // 현재 위치를 가져오기 위한 변수
    lateinit var mLastLocation: Location // 위치 값을 가지고 있는 객체
    //solution2
    /*var mLocationRequest = LocationRequest.create().apply {
        priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }*/
    private val REQUEST_PERMISSION_LOCATION = 10

    private var previousLocation: Location? = null

    //lateinit var button: Button
    /*lateinit var text1: TextView
    lateinit var text2: TextView
    lateinit var text3: TextView*/

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        /*button = findViewById(R.id.button)
        text1 = findViewById(R.id.text1)
        text2 = findViewById(R.id.text2)
        text3 = findViewById(R.id.text3)*/

        // 버튼 이벤트를 통해 현재 위치 찾기
        /*button.setOnClickListener {
            if (checkPermissionForLocation(this)) {
                startLocationUpdates()
            }
        }*/
    }

    //solution1
    fun startLocationUpdates(context: Context) {
        //FusedLocationProviderClient의 인스턴스를 생성.
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        mFusedLocationProviderClient!!.lastLocation
            .addOnSuccessListener { location: Location? ->
                location?.let {
                    onLocationChanged(context, location)
                }
            }
    }

    //solution2
    /*fun startLocationUpdates(context: Context) {
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
                onLocationChanged(context, locationResult.lastLocation)
            }
        }

        mFusedLocationProviderClient!!.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper())
    }*/

    // 시스템으로 부터 받은 위치정보를 화면에 갱신해주는 메소드
    fun onLocationChanged(context: Context, location: Location) {
        mLastLocation = location
        //text1.text = "위도 : " + mLastLocation.latitude // 갱신 된 위도
        //text2.text = "경도 : " + mLastLocation.longitude // 갱신 된 경도
        val address = getAddress(context, mLastLocation).addressLine
        //text3.text = "주소 : " + address

        val latitude = mLastLocation.latitude
        val longitude = mLastLocation.longitude
        val address_s = address.toString()

        Log.v("address_1", latitude.toString())
        Log.v("address_2", longitude.toString())
        Log.v("address_3", address_s)

        if (previousLocation != null && locationChanged(previousLocation!!, mLastLocation)) {
            Log.v("address_4", latitude.toString())
            Log.v("address_5", longitude.toString())
            Log.v("address_6", address_s)
        }

        previousLocation = mLastLocation
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

    // 사용자에게 권한 요청 후 결과에 대한 처리 로직
    /*override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSION_LOCATION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates(MainActivity@this)
            } else {
                Log.d("ttt", "onRequestPermissionsResult() _ 권한 허용 거부")
                Toast.makeText(MainActivity@this, "권한이 없어 해당 기능을 실행할 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }*/

    data class AddressData(
        val addressLine: String?
    )

    fun getAddress(context: Context, location: Location): AddressData {
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

    fun locationChanged(previousLocation: Location, currentLocation: Location): Boolean {
        val distanceThreshold = 0.5
        val distance = previousLocation.distanceTo(currentLocation)
        return distance >= distanceThreshold
    }
}
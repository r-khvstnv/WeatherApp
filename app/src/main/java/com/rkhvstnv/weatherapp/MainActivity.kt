package com.rkhvstnv.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.core.view.ViewCompat
import com.google.android.gms.location.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.rkhvstnv.weatherapp.databinding.ActivityMainBinding
import com.rkhvstnv.weatherapp.models.WeatherResponse
import com.rkhvstnv.weatherapp.network.WeatherService
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    //fused client
    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        //check location
        if (!isLocationFunctionalityEnabled()){
            //goto settings
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }else{
            requestAppropriateLocation()
        }

        binding.ivRefresh.setOnClickListener{
            requestAppropriateLocation()
        }
    }


    /** Next method show dialog which offer user to get permissions if previously their was rejected*/
    private fun showRationalPermissionDialog(){
        val permissionAlertDialog = AlertDialog.Builder(this).setMessage(R.string.st_permission_needed_to_be_granted)
        permissionAlertDialog.setPositiveButton(getString(R.string.st_go_to_settings)){ _: DialogInterface, _: Int ->
            try {
                //переходим к настройкам приложения где можно предоставить разрешение
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }catch (e: ActivityNotFoundException){
                e.printStackTrace()
            }
        }

        permissionAlertDialog.setNegativeButton(R.string.st_cancel){ dialogInterface: DialogInterface, _: Int ->
            dialogInterface.dismiss()
        }

        permissionAlertDialog.show()
    }
    private fun requestAppropriateLocation(){
        Dexter.withContext(this).withPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION)
            .withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    if (report!!.areAllPermissionsGranted()) {
                        //get current location
                        createLocationRequest()
                    } else {
                        Toast.makeText(this@MainActivity, "Some permissions are denied",
                            Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissionList: MutableList<PermissionRequest>?,
                    permisssionToken: PermissionToken?
                ) {
                    //go to settings
                    showRationalPermissionDialog()
                }
            }).onSameThread().check()
    }

    //check gps availability
    private fun isLocationFunctionalityEnabled(): Boolean{
        //access to the system location services
        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    //current location
    @SuppressLint("MissingPermission")
    private fun createLocationRequest(){
        //initialize current location
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val locationRequest = LocationRequest.create()
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        mFusedLocationClient.requestLocationUpdates(locationRequest, mLocationCallback, Looper.myLooper()!!)
    }
    private val mLocationCallback = object : LocationCallback(){
        override fun onLocationResult(result: LocationResult) {
            val lastLocation = result.lastLocation
            val latitude = lastLocation.latitude
            val longitude = lastLocation.longitude

            getLocationWeatherDetails(latitude, longitude)
            //otherwise refresh won't be worked
            mFusedLocationClient.removeLocationUpdates(this)

        }
    }

    //location details
    private fun getLocationWeatherDetails(lat: Double, long: Double){
        if(Constants.isNetworkAvailable(this)){
            //build connection
            val retrofit: Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create()).build()
            //create response
            val service: WeatherService = retrofit.create(WeatherService::class.java)
            //create call
            val listCall: Call<WeatherResponse> = service.getWeather(lat, long, Constants.METRIC_UNIT, Constants.API_ID)
            showBackgroundProgress()
            //asynchronous send request
            listCall.enqueue(object : Callback<WeatherResponse> {
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    hideBackgroundProgress()
                    if (response.isSuccessful) {
                        val weatherList: WeatherResponse = response.body()!!
                        setUI(weatherList)
                        //binding.tvCurrentDate.text = weatherList.toString()
                    } else {
                        Toast.makeText(this@MainActivity, "${response.code()}", Toast.LENGTH_SHORT).show()
                    }

                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Log.e("WeatherResponse", t.message.toString())
                    hideBackgroundProgress()
                }

            })
        } else{
            Toast.makeText(this, "Network is unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    //background top
    private fun showBackgroundProgress(){
        binding.pbBackgroundProgress.visibility = View.VISIBLE
        ViewCompat.animate(binding.pbBackgroundProgress).translationY(100f).duration = 500
    }
    private fun hideBackgroundProgress(){
        ViewCompat.animate(binding.pbBackgroundProgress).translationY(-100f).duration = 500
        Handler(Looper.getMainLooper()).postDelayed({binding.pbBackgroundProgress.visibility = View.GONE}, 1000)
    }

    //set images
    private fun setUI(weatherList: WeatherResponse){
        //go from top top bottom of layout
        binding.tvCity.text = weatherList.name
        binding.tvCountry.text = weatherList.sys.country

        binding.tvCurrentDate.text = getCurrentDate()

        binding.tvCurrentTemp.text = weatherList.main.temp.toInt().toString()
        binding.tvMinTemp.text = weatherList.main.temp_min.toString()
        binding.tvMaxTemp.text = weatherList.main.temp_max.toString()


        for (i in weatherList.weather.indices){
            binding.tvDescription.text = weatherList.weather[i].description
            setRightImage(weatherList.weather[i].id)
        }

        binding.tvSunrise.text = getUnixTime(weatherList.sys.sunrise)
        binding.tvSunset.text = getUnixTime(weatherList.sys.sunset)

        binding.tvCurrentWind.text = weatherList.wind.speed.toString() + " ${getString(R.string.st_m_s)}"
    }

    private fun setRightImage(id: Int){
        when(id){
            in 200..232 -> binding.ivIcon.setImageResource(R.drawable.ic_thunderstorm)
            in 300..321 -> binding.ivIcon.setImageResource(R.drawable.ic_drizzle)
            in 500..531 -> binding.ivIcon.setImageResource(R.drawable.ic_showers)
            600 -> binding.ivIcon.setImageResource(R.drawable.ic_snow_light)
            in 601..602 -> binding.ivIcon.setImageResource(R.drawable.ic_snow)
            in 611..622 -> binding.ivIcon.setImageResource(R.drawable.ic_sleet)
            in 701..771 -> binding.ivIcon.setImageResource(R.drawable.ic_foggy)
            781 -> binding.ivIcon.setImageResource(R.drawable.ic_tornado)
            800 -> binding.ivIcon.setImageResource(R.drawable.ic_sunny)
            801 -> binding.ivIcon.setImageResource(R.drawable.ic_clear_cloudy)
            802 -> binding.ivIcon.setImageResource(R.drawable.ic_partly_cloudy)
            803 -> binding.ivIcon.setImageResource(R.drawable.ic_cloudy)
            804 -> binding.ivIcon.setImageResource(R.drawable.ic_mostly_cloudy)

        }

    }
    //time from json
    private fun getUnixTime(timex: Long): String{
        val date = Date(timex * 1000L)
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }
    private fun getCurrentDate(): String{
        val date = System.currentTimeMillis()
        val sdf = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault())
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }
}
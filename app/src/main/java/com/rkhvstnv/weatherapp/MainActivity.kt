package com.rkhvstnv.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.*
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import com.google.android.gms.location.*
import com.google.gson.Gson
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
    //shared preferences
    private lateinit var mSharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        /** In the next line will be stored last request weather info*/
        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)
        /** Show weather info on the screen*/
        setUI()
        /** Check Location availability and request weather update*/
        if (!isLocationFunctionalityEnabled()){
            //otherwise goto settings
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }else{
            requestAppropriateLocation()
        }

        /**Refresh button*/
        binding.ivRefresh.setOnClickListener{
            requestAppropriateLocation()
        }
    }


    /** Next method show dialog which offer user to get permissions if previously they was rejected*/
    private fun showRationalPermissionDialog(){
        val permissionAlertDialog = AlertDialog.Builder(this).setMessage(R.string.st_permission_needed_to_be_granted)
        //positive button
        permissionAlertDialog.setPositiveButton(getString(R.string.st_go_to_settings)){ _: DialogInterface, _: Int ->
            try {
                //go to app settings
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }catch (e: ActivityNotFoundException){
                e.printStackTrace()
            }
        }
        //negative button
        permissionAlertDialog.setNegativeButton(R.string.st_cancel){ dialogInterface: DialogInterface, _: Int ->
            dialogInterface.dismiss()
        }

        permissionAlertDialog.show()
    }
    /** Next method check permission for using location modules
     *      and after that call current location method*/
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

    /** Next method check that location modules are turned on*/
    private fun isLocationFunctionalityEnabled(): Boolean{
        //access to the system location services
        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /** Next method request current device location, based on the latitude and longitude*/
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
            /** Remove Location updates, otherwise refresh user can't to refresh weather info*/
            mFusedLocationClient.removeLocationUpdates(this)
        }
    }

    /** Next method request weather info from open-source using Retrofit Api
     * and store received data in SharedPreference*/
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

            //notify user that request in process
            showBackgroundProgress()

            //asynchronous send request
            listCall.enqueue(object : Callback<WeatherResponse> {
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if (response.isSuccessful) {
                        //get data
                        val weatherList: WeatherResponse = response.body()!!
                        /**Convert data to Json stream. It will be lately used for shared preferences.
                         *      This way improve UE, since information will already be displayed instead of a blank screen*/
                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor = mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                        editor.apply()

                        //update info on the screen
                        setUI()
                        //hide progress bar
                        hideBackgroundProgress()
                    } else {
                        Toast.makeText(this@MainActivity, "Error: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }

                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Log.e("WeatherResponse", t.message.toString())
                    Toast.makeText(this@MainActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                    hideBackgroundProgress()
                }

            })
        } else{
            Toast.makeText(this, "Network is unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    /** Next 2 methods show or hide progress bar with corresponding animation*/
    private fun showBackgroundProgress(){
        binding.pbBackgroundProgress.visibility = View.VISIBLE
        ViewCompat.animate(binding.pbBackgroundProgress).translationY(100f).duration = 500
    }
    private fun hideBackgroundProgress(){
        ViewCompat.animate(binding.pbBackgroundProgress).translationY(-100f).duration = 500
        Handler(Looper.getMainLooper()).postDelayed({binding.pbBackgroundProgress.visibility = View.GONE}, 1000)
    }

    /** Next method set all received data from SharedPreferences on the screen.
     *          SharedPreferences updates in getLocationWeatherDetails every time */
    @SuppressLint("SetTextI18n")
    private fun setUI(){
        val weatherResponseJsonString = mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA, null)

        if (!weatherResponseJsonString.isNullOrEmpty()){
            //convert data back
            val weatherList = Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)

            /** Set data from top to bottom of the screen*/
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
    }

    /** Next method show right image for correspondent weather, based on it's id*/
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

    /** Next method convert received time for readable format*/
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
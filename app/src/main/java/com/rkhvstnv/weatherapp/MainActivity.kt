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
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import com.google.android.gms.location.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.rkhvstnv.weatherapp.databinding.ActivityMainBinding

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
        //locationRequest.interval = 1000 todo
        //locationRequest.numUpdates = 10
        mFusedLocationClient.requestLocationUpdates(locationRequest, mLocationCallback, Looper.myLooper()!!)
    }
    private val mLocationCallback = object : LocationCallback(){
        override fun onLocationResult(result: LocationResult) {
            val lastLocation = result.lastLocation
            val latitude = lastLocation.latitude
            val longitude = lastLocation.longitude
            getLocationWeatherDetails()

        }
    }

    //location details
    private fun getLocationWeatherDetails(){
        if(Constants.isNetworkAvailable(this)){
            Toast.makeText(this, "yes", Toast.LENGTH_SHORT).show()
        } else{
            Toast.makeText(this, "no", Toast.LENGTH_SHORT).show()
        }
    }
}
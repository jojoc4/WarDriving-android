package ch.jojoc4.wardriving

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.OnSuccessListener

class MainActivity() : AppCompatActivity() {

    private val PERMISSIONS_FINE_LOCATION: Int = 99
    private val DEFAULT_UPDATE_INTERVAL: Long = 3000
    private val FASTEST_UPDATE_INTERVAL: Long = 1000

    private lateinit var locationCallback: LocationCallback


    //UI vars
    private lateinit var sw_locationsupdates: Switch
    private lateinit var sw_gps: Switch
    private lateinit var tv_accuracy: TextView
    private lateinit var tv_lat: TextView
    private lateinit var tv_address: TextView
    private lateinit var tv_altitude: TextView
    private lateinit var tv_longitude: TextView
    private lateinit var tv_sensor: TextView
    private lateinit var tv_speed: TextView
    private lateinit var tv_updates: TextView

    //used to remember if tracking is on or off
    private var updateOn = false

    //Location request config file
    private lateinit var locationRequest: LocationRequest


    //Google location service API
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tv_lat = findViewById<TextView>(R.id.tv_lat)
        tv_accuracy = findViewById<TextView>(R.id.tv_accuracy)
        tv_address = findViewById<TextView>(R.id.tv_address)
        tv_altitude = findViewById<TextView>(R.id.tv_altitude)
        tv_longitude = findViewById<TextView>(R.id.tv_lon)
        tv_sensor = findViewById<TextView>(R.id.tv_sensor)
        tv_speed = findViewById<TextView>(R.id.tv_speed)
        tv_updates = findViewById<TextView>(R.id.tv_updates)
        sw_gps = findViewById<Switch>(R.id.sw_gps)
        sw_locationsupdates = findViewById<Switch>(R.id.sw_locationsupdates)

        // set location request properties
        locationRequest = LocationRequest.create()?.apply {
            interval = DEFAULT_UPDATE_INTERVAL
            fastestInterval = FASTEST_UPDATE_INTERVAL
            priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
        }

        //set precision setting
        sw_gps.setOnClickListener {
            if(sw_gps.isChecked()){
                locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                tv_sensor.setText("Using GPS")
            }else{
                locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
                tv_sensor.setText("Not using GPS")
            }
        }

        sw_locationsupdates.setOnClickListener {
            if(sw_locationsupdates.isChecked()){
                startLocationUpdate()
            }else{
                stopLocationUpdate()
            }
        }

        updateGPS()


        locationCallback = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult) {
                for (location in p0.locations){
                    updateUIValues(location)
                }

            }
        }

    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdate() {
        tv_updates.setText("Location is being tracked")
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        updateGPS()
    }

    private fun stopLocationUpdate() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        tv_updates.setText("Location is not being tracked")
        tv_longitude.setText("Not Tracking location")
        tv_lat.setText("Not Tracking location")
        tv_accuracy.setText("Not Tracking location")
        tv_altitude.setText("Not Tracking location")
        tv_speed.setText("Not Tracking location")
        tv_address.setText("Not Tracking location")
        tv_sensor.setText("Not Tracking location")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode){
            PERMISSIONS_FINE_LOCATION -> {
                if(grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    updateGPS()
                }else{
                    Toast.makeText(this, "You must grant location permission for this app to work", Toast.LENGTH_LONG)
                    finish()
                }
            }
        }
    }

    private fun updateGPS() {
        //get permission
        //get current loc from fused
        //update ui
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){
            fusedLocationProviderClient!!.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    updateUIValues(location)
                }
            }
        }else{
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSIONS_FINE_LOCATION)
            }
        }
    }

    private fun updateUIValues(location: Location) {
        tv_lat.setText(location.latitude.toString())
        tv_longitude.setText(location.longitude.toString())
        tv_accuracy.setText(location.accuracy.toString())

        if(location.hasAltitude()) {
            tv_altitude.setText(location.altitude.toString())
        }else{
            tv_altitude.setText("Not available")
        }
        if(location.hasSpeed()) {
            tv_speed.setText(location.speed.toString())
        }else{
            tv_speed.setText("Not available")
        }

        var geocoder = Geocoder(this)

        try{
            var addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            tv_address.setText(addresses.get(0).getAddressLine(0))
        }catch (e: Exception){
            tv_address.setText("No address detected")
        }
    }
}
package ch.jojoc4.wardriving

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.database.DatabaseUtils
import android.location.Geocoder
import android.location.Location
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*


class MainActivity() : AppCompatActivity() {

    private val PERMISSIONS_ALL: Int = 99
    private val SCAN_INTERVAL: Long = 30000

    //callback functions
    private lateinit var locationCallback: LocationCallback
    private lateinit var wifiScanCallback: BroadcastReceiver

    //UI vars
    private lateinit var sw_scan: Switch
    private lateinit var tv_accuracy: TextView
    private lateinit var tv_lat: TextView
    private lateinit var tv_address: TextView
    private lateinit var tv_altitude: TextView
    private lateinit var tv_longitude: TextView
    private lateinit var tv_wifi: TextView
    private lateinit var tv_db: TextView
    private lateinit var btn_clean: Button

    //Location request config file
    private lateinit var locationRequest: LocationRequest

    //Google location service API
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    //keeps last location
    private lateinit var lastLocation: Location

    //Wifi manager
    private lateinit var wifiManager: WifiManager

    //Database helper
    private lateinit var dbHelper: WDDbHelper


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //get UI elements
        tv_lat = findViewById<TextView>(R.id.tv_lat)
        tv_accuracy = findViewById<TextView>(R.id.tv_accuracy)
        tv_address = findViewById<TextView>(R.id.tv_address)
        tv_altitude = findViewById<TextView>(R.id.tv_altitude)
        tv_longitude = findViewById<TextView>(R.id.tv_lon)
        tv_wifi = findViewById<TextView>(R.id.tv_wifi)
        tv_db = findViewById<TextView>(R.id.tv_db)
        sw_scan = findViewById<Switch>(R.id.sw_scan)
        btn_clean = findViewById<Switch>(R.id.btn_clean)


        // set location request properties
        locationRequest = LocationRequest.create()?.apply {
            interval = SCAN_INTERVAL
            fastestInterval = SCAN_INTERVAL
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        //Get wifi manager
        wifiManager = this.getSystemService(Context.WIFI_SERVICE) as WifiManager

        //setup db
        dbHelper = WDDbHelper(this)

        //count records in db
        var db = dbHelper.readableDatabase
        val numRows = DatabaseUtils.queryNumEntries(db, "entry").toInt()
        tv_db.setText(numRows.toString())


        //setup scan toogle
        sw_scan.setOnClickListener {
            if(sw_scan.isChecked()){
                startScan()
            }else{
                stopScan()
            }
        }

        //make first scan
        initialScan()

        //setup location callback
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult) {
                for (location in p0.locations){
                    lastLocation=location
                    wifiManager.startScan()
                }

            }
        }

        //setup wifi callback
        wifiScanCallback = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                if (success) {
                    scanSuccess()
                }
            }
        }

        //connect to wifi update broadcast
        val intentFilter = IntentFilter()
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        registerReceiver(wifiScanCallback, intentFilter)

        //setup cleaning button
        btn_clean.setOnClickListener {
            var db = dbHelper.writableDatabase
            db.execSQL("DELETE from 'entry'")
            val numRows = DatabaseUtils.queryNumEntries(db, "entry").toInt()
            tv_db.setText(numRows.toString())
        }

    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        initialScan()
    }

    private fun stopScan() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        tv_longitude.setText("No Tracking")
        tv_lat.setText("No Tracking")
        tv_accuracy.setText("No Tracking")
        tv_altitude.setText("No Tracking")
        tv_address.setText("No Tracking")
        tv_wifi.setText("No Tracking")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode){
            PERMISSIONS_ALL -> {
                if(
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && grantResults[1] == PackageManager.PERMISSION_GRANTED
                    && grantResults[2] == PackageManager.PERMISSION_GRANTED
                ){
                    initialScan()
                }else{
                    Toast.makeText(this, "You must grant permissions for this app to work", Toast.LENGTH_LONG)
                    finish()
                }
            }
        }
    }

    private fun initialScan() {
        //get permissions
        //get current loc from fused
        //update ui
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        if(
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED
        ){
            fusedLocationProviderClient!!.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    lastLocation = location
                    updateUIValues(lastLocation, "launch scan")
                }
            }
        }else{
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                requestPermissions(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE
                ), PERMISSIONS_ALL)
            }
        }
    }


    private fun scanSuccess() {
        val results = wifiManager.scanResults
        var wifi = ""
        for(result in results){
            wifi+=result.SSID+"; "
        }
        updateUIValues(lastLocation, wifi)
        saveToDb(lastLocation, results)
    }

    private fun saveToDb(location: Location, results: List<ScanResult>){
        //get db access
        var db = dbHelper.writableDatabase

        //prepare location string
        var geocoder = Geocoder(this)
        val loc = "{" +
                "lon" + location.longitude +
                "lat" + location.latitude +
                "alt" + location.altitude +
                "add" + geocoder.getFromLocation(location.latitude, location.longitude, 1).get(0).getAddressLine(0) +
                "}"

        for (wifi in results){
            val w = "{" +
                    "ssid" + wifi.SSID +
                    "bssid" + wifi.BSSID +
                    "freq" + wifi.frequency +
                    "level" + wifi.level +
                    "}"

            val values = ContentValues().apply {
                put("location", loc)
                put("wifi", w)
            }

            db.insert("entry", null, values)
            var nbr = tv_db.text.toString().toInt()+1
            tv_db.setText(nbr.toString())
        }

    }

    private fun updateUIValues(location: Location, wifi: String) {
        tv_lat.setText(location.latitude.toString())
        tv_longitude.setText(location.longitude.toString())
        tv_accuracy.setText(location.accuracy.toString())

        if(location.hasAltitude()) {
            tv_altitude.setText(location.altitude.toString())
        }else{
            tv_altitude.setText("Not available")
        }

        var geocoder = Geocoder(this)

        try{
            var addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            tv_address.setText(addresses.get(0).getAddressLine(0))
        }catch (e: Exception){
            tv_address.setText("No address detected")
        }

        tv_wifi.setText(wifi)
    }
}
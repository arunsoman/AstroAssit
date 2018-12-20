package a.astroassist

import a.astroassist.BuildConfig.APPLICATION_ID
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.support.design.widget.Snackbar
import android.support.design.widget.Snackbar.LENGTH_INDEFINITE
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import kotlin.math.tan


class AstroAssist : AppCompatActivity() {

    private val TAG = "AstroAssist"
    private val REQUEST_PERMISSIONS_REQUEST_CODE = 34
    private val df = SimpleDateFormat("yyyy-MM-dd")
    private lateinit var locationManager: LocationManager
    private lateinit var locationListener: LocationListener

    private lateinit var latitudeText: TextView
    private lateinit var longitudeText: TextView
    private lateinit var altText: TextView

    private lateinit var magInclination: TextView
    private  var magInc= 0.0

    private var lng= 0.0
    private var lat= 0.0

    private var alt= 0.0
    private lateinit var azText: TextView
    private lateinit var az_transText: TextView
    private lateinit var eleText: TextView

    private lateinit var ele_transText: TextView

    private lateinit var calcButton: Button
    private lateinit var refreshButton: Button
    private lateinit var location: Location
    private var updated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_astro_assist)
        latitudeText = findViewById(R.id.latitude_text)
        longitudeText = findViewById(R.id.longitude_text)
        altText = findViewById(R.id.alt_text)

        magInclination = findViewById(R.id.MagInc_label)

        azText= findViewById(R.id.az_len_label)
        az_transText = findViewById(R.id.az_translate_label)
        azText.text = .5.toString()

        eleText= findViewById(R.id.ele_len_label)
        ele_transText = findViewById(R.id.ele_translate_label)
        eleText.text = 1.toString()

        calcButton = findViewById(R.id.Calculate)
        refreshButton = findViewById(R.id.Refresh)

        calcButton.setOnClickListener{
            computeAZ()
            computeEle()
            Log.d("CalcClicked", "Done")
        }

        refreshButton.setOnClickListener{
            setLocation(location)
            Log.d("refreshClicked", "Done")
        }
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

    }

    override fun onStart() {
        super.onStart()

        if (!checkPermissions()) {
            requestPermissions()
        } else {
            getLastLocation()
        }
    }

    override fun onPause() {
        super.onPause()
        locationManager.removeUpdates(locationListener)
    }

    override fun onResume() {
        super.onResume()
        registerLocationUpdates()
    }

    private fun setLocation(loc:Location){
        latitudeText.text = String.format(resources
            .getString(R.string.latitude_label), loc.latitude)
        longitudeText.text = resources
            .getString(R.string.longitude_label, loc.longitude)
        altText.text = resources
            .getString(R.string.altitude_label, loc.altitude)
        lng = loc.longitude
        lat = loc.latitude
        alt = (loc.altitude/1000)
    }

    private fun getLastLocation() {
        Log.d("Location", "API called")
        locationListener = object : LocationListener {

            override fun onLocationChanged(loc: Location) {
                if(updated == false){
                    setLocation(loc)
                    getMagneticInclination()
                    updated = true;
                    Log.d("Location", "Display set")
                }
                location = loc;
            }

            override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
            }

            override fun onProviderEnabled(provider: String) {
            }

            override fun onProviderDisabled(provider: String) {
            }
        }
        registerLocationUpdates()
    }

    @SuppressLint("MissingPermission")
    private fun registerLocationUpdates(){
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 5.0f, locationListener)
    }

    private fun showSnackbar(
        snackStrId: Int,
        actionStrId: Int = 0,
        listener: View.OnClickListener? = null
    ) {
        val snackbar = Snackbar.make(findViewById(android.R.id.content), getString(snackStrId),
            LENGTH_INDEFINITE)
        if (actionStrId != 0 && listener != null) {
            snackbar.setAction(getString(actionStrId), listener)
        }
        snackbar.show()
    }

    private fun checkPermissions() =
        ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) == PERMISSION_GRANTED

    private fun startLocationPermissionRequest() {
        ActivityCompat.requestPermissions(this, arrayOf(ACCESS_FINE_LOCATION),
            REQUEST_PERMISSIONS_REQUEST_CODE)
    }

    private fun requestPermissions() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, ACCESS_FINE_LOCATION)) {
            Log.i(TAG, "Displaying permission rationale to provide additional context.")
            showSnackbar(R.string.permission_rationale, android.R.string.ok, View.OnClickListener {
                startLocationPermissionRequest()
            })
        } else {
            Log.i(TAG, "Requesting permission")
            startLocationPermissionRequest()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        Log.i(TAG, "onRequestPermissionResult")
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            when {
                grantResults.isEmpty() -> Log.i(TAG, "User interaction was cancelled.")
                (grantResults[0] == PackageManager.PERMISSION_GRANTED) -> getLastLocation()
            else -> {
                    showSnackbar(R.string.permission_denied_explanation, R.string.settings,
                        View.OnClickListener {
                            val intent = Intent().apply {
                                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                data = Uri.fromParts("package", APPLICATION_ID, null)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            startActivity(intent)
                        })
                }
            }
        }
    }

    private fun getMagneticInclination(){

        val c = Calendar.getInstance().getTime()
        val formattedDate = df.format(c)
        val queue = Volley.newRequestQueue(this)
        val url = "http://geomag.bgs.ac.uk/web_service/GMModels/wmm/2015v2/?latitude=$lat"+
                "&longitude=$lng&altitude=$alt&date=$formattedDate+&format=json"
// Request a string response from the provided URL.
        val stringRequest = StringRequest(
            Request.Method.GET, url,
            Response.Listener<String> { response ->
                val temp = response.split("\"declination\": {")[1]
                    .split('}')[0].split(":")

                val value = temp[2].toDouble()
                magInc = if(temp[1].contains("down")){ value*-1 } else{ value }
                magInclination.text = magInc.toString()
                computeAZ()
                computeEle()
            },
            Response.ErrorListener { error ->
                println("Error => $error")
                    magInclination.text = "That didn't work!"
            })
        queue.add(stringRequest)
        Log.d("MagenticDeclination", "Done")
    }

    private fun computeAZ(){
        if(magInclination.text != magInc.toString()){
            //TODo
        }
        az_transText.text = resources.getString(R.string.az_translate_label).
            format(if(magInc < 0){ "W "} else{"E "},
                getOppositeSideInInch(Math.abs(magInc),
                    azText.text.toString().toDouble()
                    ))
        Log.d("App", "computeAZ")
    }

    private fun computeEle(){
        ele_transText.text =resources
            .getString(R.string.Ele_translate_label, getOppositeSideInInch(lat,
                eleText.text.toString().toDouble()
                ))
        Log.d("App", "computeEle")
    }

    private  fun  getOppositeSideInInch(angle: Double, adjacent: Double):Double{
        val result = tan(angle)*adjacent*39.3701
        Log.d("App", "getOppositeSideInInch($angle:ang, $adjacent:adjacent):$result")
        return result
    }
}
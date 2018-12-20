package a.astroassist

import a.astroassist.BuildConfig.APPLICATION_ID
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlin.math.tan


class AstroAssist : AppCompatActivity() {

    private val TAG = "AstroAssist"
    private val REQUEST_PERMISSIONS_REQUEST_CODE = 34


    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var latitudeText: TextView
    private lateinit var longitudeText: TextView
    private lateinit var altText: TextView
    private lateinit var magInclination: TextView

    private var lng= 0.0
    private var lat= 0.0
    private var alt= 0.0

    private lateinit var azText: TextView
    private lateinit var az_transText: TextView
    private lateinit var eleText: TextView
    private lateinit var ele_transText: TextView

    private  var magInc= 0.0

    private lateinit var calcButton: Button
    private lateinit var refreshButton: Button


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_astro_assist)
        latitudeText = findViewById(R.id.latitude_text)
        longitudeText = findViewById(R.id.longitude_text)
        altText = findViewById(R.id.alt_text)

        magInclination = findViewById(R.id.MagInc_label)

        azText= findViewById(R.id.az_len_label)
        az_transText = findViewById(R.id.az_translate_label)
        azText.text = 1.0.toString()

        eleText= findViewById(R.id.ele_len_label)
        ele_transText = findViewById(R.id.ele_translate_label)
        eleText.text = 5.0.toString()

        calcButton = findViewById(R.id.Calculate)
        refreshButton = findViewById(R.id.Refresh)

        calcButton.setOnClickListener{
            computeAZ()
            computeEle()
        }

        refreshButton.setOnClickListener{
            getLastLocation()
        }
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

    }

    override fun onStart() {
        super.onStart()

        if (!checkPermissions()) {
            requestPermissions()
        } else {
            getLastLocation()
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLastLocation() {
        fusedLocationClient.lastLocation
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful && task.result != null) {
                    latitudeText.text = String.format(resources
                        .getString(R.string.latitude_label), task.result.latitude)
                    longitudeText.text = resources
                        .getString(R.string.longitude_label, task.result.longitude)
                    altText.text = resources
                        .getString(R.string.altitude_label, task.result.altitude)
                    print("altitude: $task.result.altitude")
                    lng = task.result.longitude
                    lat = task.result.latitude
                    alt = (task.result.altitude/1000)
                    getMagneticInclination()
                } else {
                    Log.w(TAG, "getLastLocation:exception", task.exception)
                    showSnackbar(R.string.no_location_detected)
                }
            }
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
        val df = SimpleDateFormat("yyyy-MM-dd")
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

// Add the request to the RequestQueue.
        queue.add(stringRequest)
    }

    private fun computeAZ(){
        az_transText.text = resources.getString(R.string.az_translate_label).
            format(if(magInc < 0){ "W "} else{"E "},
                getOppositSideInInch(Math.abs(magInc),
                    azText.text.toString().toDouble()
                    ))
    }

    private fun computeEle(){
        ele_transText.text =resources
            .getString(R.string.Ele_translate_label, getOppositSideInInch(lat,
                eleText.text.toString().toDouble()
                ))
    }

    private  fun  getOppositSideInInch(angle: Double, adjacent: Double):Double{
        return tan(angle)*adjacent/39.3701
    }
}
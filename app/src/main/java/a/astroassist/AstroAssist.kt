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
import kotlinx.android.synthetic.main.activity_astro_assist.*
import kotlin.math.tan


class AstroAssist : AppCompatActivity() {

    private val TAG = "AstroAssist"
    private val REQUEST_PERMISSIONS_REQUEST_CODE = 34


    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var latitudeText: TextView
    private lateinit var longitudeText: TextView
    private lateinit var alt: TextView
    private lateinit var magInclination: TextView

    private  lateinit var lngStr: String
    private  lateinit var latStr: String
    private  lateinit var altStr: String

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
        alt = findViewById(R.id.alt_text)

        magInclination = findViewById(R.id.MagInc_label)

        azText= findViewById(R.id.az_len_label)
        az_transText = findViewById(R.id.az_translate_label)
        azText.text = "5"

        eleText= findViewById(R.id.ele_len_label)
        ele_transText = findViewById(R.id.ele_translate_label)
        eleText.text = "1"

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
                    latitudeText.text = resources
                        .getString(R.string.latitude_label, task.result.latitude)
                    longitudeText.text = resources
                        .getString(R.string.longitude_label, task.result.longitude)
                    alt.text = resources
                        .getString(R.string.altitude_label, task.result.altitude)
                    lngStr = task.result.longitude.toString()
                    latStr = task.result.latitude.toString()
                    altStr = (task.result.altitude/1000).toString()
                    getMagneticInclination()
                } else {
                    Log.w(TAG, "getLastLocation:exception", task.exception)
                    showSnackbar(R.string.no_location_detected)
                }
            }
    }

    /**
     * Shows a [Snackbar].
     *
     * @param snackStrId The id for the string resource for the Snackbar text.
     * @param actionStrId The text of the action item.
     * @param listener The listener associated with the Snackbar action.
     */
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

    /**
     * Return the current state of the permissions needed.
     */
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
                  // Request permission
                startLocationPermissionRequest()
            })

        } else {
            Log.i(TAG, "Requesting permission")
            startLocationPermissionRequest()
        }
    }

    /**
     * Callback received when a permissions request has been completed.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        Log.i(TAG, "onRequestPermissionResult")
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            when {
                // If user interaction was interrupted, the permission request is cancelled and you
                // receive empty arrays.
                grantResults.isEmpty() -> Log.i(TAG, "User interaction was cancelled.")

                // Permission granted.
                (grantResults[0] == PackageManager.PERMISSION_GRANTED) -> getLastLocation()
            else -> {
                    showSnackbar(R.string.permission_denied_explanation, R.string.settings,
                        View.OnClickListener {
                            // Build intent that displays the App settings screen.
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

    fun getMagneticInclination(){

        val c = Calendar.getInstance().getTime()
        val df = SimpleDateFormat("yyyy-MM-dd")
        val formattedDate = df.format(c)

        val queue = Volley.newRequestQueue(this)
        val url = "http://geomag.bgs.ac.uk/web_service/GMModels/wmm/2015v2/?latitude="+latStr+
                "&longitude="+lngStr +
                "&altitude="+altStr+
                "&date="+formattedDate+
                "&format=json"
// Request a string response from the provided URL.
        val stringRequest = StringRequest(
            Request.Method.GET, url,
            Response.Listener<String> { response ->
                var temp = response.split("\"inclination\": {")[1]
                    .split('}')[0]
                    .split(":")

                println("Response => $temp")
                var dir = temp[1].contains("down")
                var value = temp[2].toDouble()

                if(dir){
                    magInc = value*-1
                }
                else{
                    magInc = value
                }

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
        var value = magInc
        if(value < 0)
            value *=-1

        var delta = tan(value)*(az_len_label.text.toString().toDouble())/39.3701 //inch
        if(value < 0){
            az_transText.text = "West:$delta"
        }
        else{
            az_transText.text = "East:$delta"
        }
    }

    private fun computeEle(){
        var delta = tan(latStr.toDouble())*(eleText.text.toString().toDouble())
        ele_transText.text = delta.toString()

    }
}

package com.tuempresa.rutaapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.gms.tasks.CancellationTokenSource
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val client = OkHttpClient()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) showUserLocation()
            else Toast.makeText(this, "Permiso denegado", Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        if (permission == PackageManager.PERMISSION_GRANTED) {
            showUserLocation()
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @SuppressLint("MissingPermission")
    private fun showUserLocation() {
        val token = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, token.token)
            .addOnSuccessListener { location ->
                if (location != null) {
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    mMap.isMyLocationEnabled = true
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))

                    // destino ejemplo (puedes reemplazarlo)
                    val destino = LatLng(-12.0500, -77.0300)
                    mMap.addMarker(MarkerOptions().position(destino).title("Destino"))

                    drawRoute(currentLatLng, destino)
                } else {
                    Toast.makeText(this, "No se pudo obtener la ubicación", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun drawRoute(origin: LatLng, destination: LatLng) {
        val originStr = "${origin.latitude},${origin.longitude}"
        val destStr = "${destination.latitude},${destination.longitude}"
        val apiKey = getString(R.string.google_maps_key)
        val url = "https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=${URLEncoder.encode(originStr, "UTF-8")}&" +
                "destination=${URLEncoder.encode(destStr, "UTF-8")}&" +
                "mode=walking&key=$apiKey"

        Thread {
            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val json = JSONObject(response.body!!.string())
                val routes = json.getJSONArray("routes")
                if (routes.length() > 0) {
                    val points = routes.getJSONObject(0)
                        .getJSONObject("overview_polyline")
                        .getString("points")
                    val polyline = decodePolyline(points)

                    runOnUiThread {
                        mMap.addPolyline(
                            PolylineOptions()
                                .addAll(polyline)
                                .width(10f)
                                .color(ContextCompat.getColor(this, android.R.color.holo_blue_dark))
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Error obteniendo ruta", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val latLng = LatLng(lat / 1E5, lng / 1E5)
            poly.add(latLng)
        }
        return poly
    }
}

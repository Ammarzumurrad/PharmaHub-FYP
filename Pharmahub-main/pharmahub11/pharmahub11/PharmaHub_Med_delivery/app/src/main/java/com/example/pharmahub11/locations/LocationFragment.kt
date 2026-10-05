package com.example.pharmahub11.locations

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.pharmahub11.R
import com.example.pharmahub11.databinding.FragmentLocationBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import dagger.hilt.android.AndroidEntryPoint
import java.io.IOException
import java.util.Locale

@AndroidEntryPoint
class LocationFragment : Fragment() {
    private lateinit var binding: FragmentLocationBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLatitude: Double = 0.0
    private var currentLongitude: Double = 0.0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentLocationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnConfirmLocation.setOnClickListener {
            // Return the current location coordinates
            val result = Bundle().apply {
                putDouble("latitude", currentLatitude)
                putDouble("longitude", currentLongitude)
            }
            parentFragmentManager.setFragmentResult("location_result", result)
            findNavController().popBackStack()
        }

        if (hasLocationPermission()) {
            fetchDeviceLocation()
        } else {
            requestLocationPermission()
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    @SuppressLint("MissingPermission")
    private fun fetchDeviceLocation() {
        try {
            binding.progressBar.visibility = View.VISIBLE
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    currentLatitude = location.latitude
                    currentLongitude = location.longitude

                    // Update map view
                    setupMapView(location.latitude, location.longitude)

                    // Reverse geocode to get address
                    reverseGeocode(location.latitude, location.longitude)
                } ?: showError("Unable to get location")
                binding.progressBar.visibility = View.GONE
            }.addOnFailureListener { e ->
                showError("Failed to get location: ${e.message}")
                binding.progressBar.visibility = View.GONE
            }
        } catch (e: SecurityException) {
            showError("Location permission required")
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun setupMapView(latitude: Double, longitude: Double) {
        binding.mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS) {
            // Set camera position
            val cameraOptions = CameraOptions.Builder()
                .center(com.mapbox.geojson.Point.fromLngLat(longitude, latitude))
                .zoom(14.0)
                .build()
            binding.mapView.getMapboxMap().setCamera(cameraOptions)

            // Add marker at current location
            val annotationApi = binding.mapView.annotations
            val pointAnnotationManager = annotationApi.createPointAnnotationManager()
            val pointAnnotationOptions = PointAnnotationOptions()
                .withPoint(com.mapbox.geojson.Point.fromLngLat(longitude, latitude))
                .withIconImage("marker-icon")
            pointAnnotationManager.create(pointAnnotationOptions)
        }
    }

    private fun reverseGeocode(latitude: Double, longitude: Double) {
        val geocoder = Geocoder(requireContext(), Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            addresses?.firstOrNull()?.let { address ->
                val addressText = buildString {
                    address.thoroughfare?.let { append("$it, ") }
                    address.locality?.let { append(it) }
                }
                binding.tvLocationAddress.text = addressText
            } ?: showError("No address found")
        } catch (e: IOException) {
            showError("Could not get address")
        }
    }

    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    fetchDeviceLocation()
                } else {
                    showError("Location permission required")
                }
            }
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }
}
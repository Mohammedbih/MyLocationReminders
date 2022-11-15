package com.udacity.project4.locationreminders.savereminder.selectreminderlocation


import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.snackbar.Snackbar
import com.udacity.project4.R
import com.udacity.project4.base.BaseFragment
import com.udacity.project4.base.NavigationCommand
import com.udacity.project4.databinding.FragmentSelectLocationBinding
import com.udacity.project4.locationreminders.savereminder.SaveReminderViewModel
import com.udacity.project4.utils.setDisplayHomeAsUpEnabled
import org.koin.android.ext.android.inject
import java.util.Locale


class SelectLocationFragment : BaseFragment(), OnMapReadyCallback {

    //Use Koin to get the view model of the SaveReminder
    override val _viewModel: SaveReminderViewModel by inject()
    private lateinit var binding: FragmentSelectLocationBinding
    private lateinit var map: GoogleMap
    private lateinit var supportMapFragment: SupportMapFragment
    private lateinit var pointOfInterest: PointOfInterest
    private val client by lazy { LocationServices.getFusedLocationProviderClient(requireActivity()) }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_select_location, container, false)

        binding.viewModel = _viewModel
        binding.lifecycleOwner = this

        setHasOptionsMenu(true)
        setDisplayHomeAsUpEnabled(true)

        supportMapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment

        supportMapFragment.getMapAsync(this)




        binding.saveButton.setOnClickListener {
            if (::pointOfInterest.isInitialized) onLocationSelected()
            else _viewModel.showSnackBar.value = getString(R.string.select_poi)
        }
        return binding.root
    }


    private fun onLocationSelected() {

        _viewModel.selectedPOI.value = pointOfInterest
        _viewModel.reminderSelectedLocationStr.value = pointOfInterest.name
        _viewModel.latitude.value = pointOfInterest.latLng.latitude
        _viewModel.longitude.value = pointOfInterest.latLng.longitude
        _viewModel.navigationCommand.value = NavigationCommand.Back

    }


    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.map_options, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {

        R.id.normal_map -> {
            map.mapType = GoogleMap.MAP_TYPE_NORMAL
            true
        }

        R.id.hybrid_map -> {
            map.mapType = GoogleMap.MAP_TYPE_HYBRID
            true
        }

        R.id.satellite_map -> {
            map.mapType = GoogleMap.MAP_TYPE_SATELLITE
            true
        }

        R.id.terrain_map -> {
            map.mapType = GoogleMap.MAP_TYPE_TERRAIN
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    override fun onMapReady(p0: GoogleMap) {
        map = p0
        when (ContextCompat.checkSelfPermission(
            requireActivity(),
            Manifest.permission.ACCESS_FINE_LOCATION
        )) {
            PackageManager.PERMISSION_GRANTED -> {
                map.isMyLocationEnabled = true
            }

            else -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        map.setOnMyLocationButtonClickListener {
            checkDeviceLocationSettingsAndEnableMyLocation()
            true
        }

        map.setMapStyle(
            MapStyleOptions.loadRawResourceStyle(
                requireActivity(),
                R.raw.map_style
            )
        )

        map.setOnPoiClickListener {
            pointOfInterest = it
            map.clear()

            val snippet = String.format(
                Locale.getDefault(),
                "Lat: %1$.5f, Long: %2$.5f",
                it.latLng.latitude,
                it.latLng.longitude
            )

            map.addMarker(
                MarkerOptions()
                    .position(
                        LatLng(
                            it.latLng.latitude,
                            it.latLng.longitude
                        )
                    )
                    .title(it.name)
                    .snippet(snippet)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN))
            )

        }

        map.setOnMapClickListener {

            val snippet = String.format(
                Locale.getDefault(),
                "Lat: %1$.5f, Long: %2$.5f",
                it.latitude,
                it.longitude
            )

            map.clear()
            val marker =
                MarkerOptions().position(it).title(getString(R.string.dropped_pin)).snippet(snippet)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN))
            map.addMarker(marker)

            pointOfInterest = PointOfInterest(it, marker.title.toString(), marker.title.toString())
        }

    }


    private fun enableMyLocation() {
        when (ContextCompat.checkSelfPermission(
            requireActivity(),
            Manifest.permission.ACCESS_FINE_LOCATION
        )) {
            PackageManager.PERMISSION_GRANTED -> {

                client.lastLocation.addOnSuccessListener {
                    it?.let {
                        val latLang = LatLng(it.latitude, it.longitude)
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLang, 15f))
                    }
                }
            }

            else -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }


    @SuppressLint("MissingPermission")
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                _viewModel.showSnackBar.value = "Permission granted"
                map.isMyLocationEnabled = true
            } else {
                _viewModel.showSnackBar.value = "Permission denied"
            }

        }

    private fun checkDeviceLocationSettingsAndEnableMyLocation() {
        val locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_LOW_POWER
        }
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)

        val settingsClient = LocationServices.getSettingsClient(requireActivity())
        val locationSettingsResponseTask =
            settingsClient.checkLocationSettings(builder.build())




        locationSettingsResponseTask.addOnCompleteListener {
            if (it.isSuccessful) {
                enableMyLocation()
            }
        }

        locationSettingsResponseTask.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    startIntentSenderForResult(
                        exception.resolution.intentSender,
                        29,
                        null,
                        0,
                        0,
                        0,
                        null
                    )
                } catch (sendEx: IntentSender.SendIntentException) {
                    Log.d(
                        ContentValues.TAG,
                        "Error getting location settings resolution: " + sendEx.message
                    )
                }
            } else {
                Snackbar.make(
                    binding.mapContainer,
                    R.string.location_required_error, Snackbar.LENGTH_INDEFINITE
                ).setAction(android.R.string.ok) {
                    checkDeviceLocationSettingsAndEnableMyLocation()
                }.show()
            }
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 29) {
            if (resultCode == Activity.RESULT_OK) {
                checkDeviceLocationSettingsAndEnableMyLocation()
            } else {
                Snackbar.make(
                    binding.mapContainer,
                    R.string.location_required_error, Snackbar.LENGTH_INDEFINITE
                ).setAction(android.R.string.ok) {
                }.show()
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }
}

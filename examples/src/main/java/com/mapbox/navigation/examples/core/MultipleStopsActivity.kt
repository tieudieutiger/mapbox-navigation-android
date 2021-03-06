package com.mapbox.navigation.examples.core

import android.annotation.SuppressLint
import android.location.Location
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
import com.mapbox.android.core.location.LocationEngineCallback
import com.mapbox.android.core.location.LocationEngineProvider
import com.mapbox.android.core.location.LocationEngineResult
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.navigation.base.internal.extensions.applyDefaultParams
import com.mapbox.navigation.base.trip.model.RouteLegProgress
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.directions.session.RoutesRequestCallback
import com.mapbox.navigation.core.replay.route.ReplayRouteLocationEngine
import com.mapbox.navigation.core.stops.ArrivalController
import com.mapbox.navigation.core.stops.ArrivalOptions
import com.mapbox.navigation.core.trip.session.TripSessionState
import com.mapbox.navigation.core.trip.session.TripSessionStateObserver
import com.mapbox.navigation.examples.R
import com.mapbox.navigation.examples.utils.Utils
import com.mapbox.navigation.examples.utils.extensions.toPoint
import com.mapbox.navigation.ui.camera.NavigationCamera
import com.mapbox.navigation.ui.map.NavigationMapboxMap
import com.mapbox.navigation.ui.map.NavigationMapboxMapInstanceState
import java.lang.ref.WeakReference
import kotlinx.android.synthetic.main.replay_engine_example_activity_layout.*
import timber.log.Timber

/**
 * To ensure proper functioning of this example make sure your Location is turned on.
 */
class MultipleStopsActivity : AppCompatActivity(), OnMapReadyCallback {

    private var mapboxMap: MapboxMap? = null
    private var mapboxNavigation: MapboxNavigation? = null
    private var navigationMapboxMap: NavigationMapboxMap? = null
    private var mapInstanceState: NavigationMapboxMapInstanceState? = null
    private val replayRouteLocationEngine = ReplayRouteLocationEngine()
    private val stopsController = StopsController()

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.replay_engine_example_activity_layout)
        mapView.onCreate(savedInstanceState)

        val mapboxNavigationOptions = MapboxNavigation.defaultNavigationOptions(
            this,
            Utils.getMapboxAccessToken(this)
        )

        mapboxNavigation = MapboxNavigation(
            applicationContext,
            Utils.getMapboxAccessToken(this),
            mapboxNavigationOptions,
            locationEngine = replayRouteLocationEngine
        ).apply {
            registerTripSessionStateObserver(tripSessionStateObserver)
        }
        initListeners()
        mapView.getMapAsync(this)
        Snackbar.make(container, R.string.msg_long_press_map_to_place_waypoint, LENGTH_SHORT)
            .show()
    }

    override fun onMapReady(mapboxMap: MapboxMap) {
        this.mapboxMap = mapboxMap
        mapboxMap.setStyle(Style.MAPBOX_STREETS) { style ->
            mapboxMap.moveCamera(CameraUpdateFactory.zoomTo(15.0))
            navigationMapboxMap = NavigationMapboxMap(mapView, mapboxMap, true)
            mapInstanceState?.let { state ->
                navigationMapboxMap?.restoreFrom(state)
            }

            // Center the map at current location. Using LocationEngineProvider because the
            // replay engine won't have your last location.
            LocationEngineProvider.getBestLocationEngine(this).getLastLocation(locationListenerCallback)
        }
        mapboxMap.addOnMapLongClickListener { latLng ->
            stopsController.add(latLng)

            mapboxMap.locationComponent.lastKnownLocation?.let { originLocation ->
                requestRoute(originLocation)
            }
            true
        }
    }

    private fun requestRoute(originLocation: Location) {
        mapboxNavigation?.requestRoutes(
            RouteOptions.builder().applyDefaultParams()
                .accessToken(Utils.getMapboxAccessToken(applicationContext))
                .coordinates(stopsController.coordinates(originLocation))
                .alternatives(true)
                .profile(DirectionsCriteria.PROFILE_DRIVING)
                .build(),
            routesReqCallback
        )
    }

    private val routesReqCallback = object : RoutesRequestCallback {
        override fun onRoutesReady(routes: List<DirectionsRoute>) {
            Timber.d("route request success %s", routes.toString())
            if (routes.isNotEmpty()) {
                navigationMapboxMap?.drawRoute(routes[0])
                replayRouteLocationEngine.assign(routes[0])
                startNavigation.visibility = View.VISIBLE
            } else {
                startNavigation.visibility = View.GONE
            }
        }

        override fun onRoutesRequestFailure(throwable: Throwable, routeOptions: RouteOptions) {
            Timber.e("route request failure %s", throwable.toString())
        }

        override fun onRoutesRequestCanceled(routeOptions: RouteOptions) {
            Timber.d("route request canceled")
        }
    }

    @SuppressLint("MissingPermission")
    fun initListeners() {
        mapboxNavigation?.attachArrivalController(arrivalObserver)
        startNavigation.setOnClickListener {
            updateCameraOnNavigationStateChange(true)
            navigationMapboxMap?.addProgressChangeListener(mapboxNavigation!!)
            if (mapboxNavigation?.getRoutes()?.isNotEmpty() == true) {
                navigationMapboxMap?.startCamera(mapboxNavigation?.getRoutes()!![0])
            }
            mapboxNavigation?.startTripSession()
            startNavigation.visibility = View.GONE
        }
    }

    private val arrivalObserver = object : ArrivalController {
        val arrivalOptions = ArrivalOptions.Builder()
            .arriveInSeconds(60.0)
            .build()
        override fun arrivalOptions(): ArrivalOptions = arrivalOptions

        override fun navigateNextRouteLeg(routeLegProgress: RouteLegProgress): Boolean {
            // This example shows you can use both time and distance.
            // Move to the next step when the distance is small
            Timber.i("arrival_debug legIndex=${routeLegProgress.legIndex()} distanceRemaining=${routeLegProgress.distanceRemaining()}")
            return routeLegProgress.distanceRemaining() < 5.0
        }
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
        navigationMapboxMap?.onStart()
    }

    public override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    public override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        navigationMapboxMap?.onStop()
        mapView.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapboxNavigation?.unregisterTripSessionStateObserver(tripSessionStateObserver)
        mapboxNavigation?.stopTripSession()
        mapboxNavigation?.onDestroy()
        mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    private val locationListenerCallback = MyLocationEngineCallback(this)

    private class MyLocationEngineCallback(activity: MultipleStopsActivity) :
        LocationEngineCallback<LocationEngineResult> {

        private val activityRef = WeakReference(activity)

        override fun onSuccess(result: LocationEngineResult?) {
            result?.locations?.firstOrNull()?.let {
                activityRef.get()?.mapboxMap?.locationComponent?.forceLocationUpdate(it)
            }
        }

        override fun onFailure(exception: Exception) {
        }
    }

    private val tripSessionStateObserver = object : TripSessionStateObserver {
        override fun onSessionStateChanged(tripSessionState: TripSessionState) {
            if (tripSessionState == TripSessionState.STOPPED) {
                stopsController.clear()
                navigationMapboxMap?.removeRoute()
                updateCameraOnNavigationStateChange(false)
            }
        }
    }

    private fun updateCameraOnNavigationStateChange(
        navigationStarted: Boolean
    ) {
        navigationMapboxMap?.apply {
            if (navigationStarted) {
                updateCameraTrackingMode(NavigationCamera.NAVIGATION_TRACKING_MODE_GPS)
                updateLocationLayerRenderMode(RenderMode.GPS)
            } else {
                updateCameraTrackingMode(NavigationCamera.NAVIGATION_TRACKING_MODE_NONE)
                updateLocationLayerRenderMode(RenderMode.COMPASS)
            }
        }
    }
}

private class StopsController {
    private val stops = mutableListOf<Point>()

    fun add(latLng: LatLng) {
        stops.add(latLng.toPoint())
    }

    fun clear() {
        stops.clear()
    }

    fun coordinates(originLocation: Location): List<Point> {
        val coordinates = mutableListOf<Point>()
        coordinates.add(originLocation.toPoint())
        coordinates.addAll(stops)
        return coordinates
    }
}

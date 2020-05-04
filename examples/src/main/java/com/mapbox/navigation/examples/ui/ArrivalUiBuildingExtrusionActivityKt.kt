package com.mapbox.navigation.examples.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Looper
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.mapbox.android.core.location.LocationEngine
import com.mapbox.android.core.location.LocationEngineCallback
import com.mapbox.android.core.location.LocationEngineProvider
import com.mapbox.android.core.location.LocationEngineRequest
import com.mapbox.android.core.location.LocationEngineResult
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.mapboxsdk.annotations.IconFactory
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.location.LocationComponent
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.plugins.annotation.SymbolManager
import com.mapbox.mapboxsdk.plugins.annotation.SymbolOptions
import com.mapbox.navigation.base.internal.extensions.applyDefaultParams
import com.mapbox.navigation.base.internal.extensions.coordinates
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.directions.session.RoutesRequestCallback
import com.mapbox.navigation.core.fasterroute.FasterRouteObserver
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.core.trip.session.TripSessionState
import com.mapbox.navigation.core.trip.session.TripSessionStateObserver
import com.mapbox.navigation.examples.R
import com.mapbox.navigation.examples.utils.Utils
import com.mapbox.navigation.examples.utils.extensions.toPoint
import com.mapbox.navigation.ui.arrival.BuildingExtrusionLayer
import com.mapbox.navigation.ui.camera.NavigationCamera
import com.mapbox.navigation.ui.map.NavigationMapboxMap
import java.io.File
import java.lang.ref.WeakReference
import java.net.URI
import kotlinx.android.synthetic.main.activity_arrival_building_extrusion.*
import kotlinx.android.synthetic.main.activity_trip_service.mapView
import kotlinx.android.synthetic.main.bottom_sheet_faster_route.*
import kotlinx.android.synthetic.main.content_simple_mapbox_navigation.*
import kotlinx.coroutines.channels.Channel
import timber.log.Timber

class ArrivalUiBuildingExtrusionActivityKt : AppCompatActivity(), OnMapReadyCallback {

    private val startTimeInMillis = 5000L
    private val countdownInterval = 10L
    private val maxProgress = startTimeInMillis / countdownInterval
    private val locationEngineCallback = MyLocationEngineCallback(this)
    private val restartSessionEventChannel = Channel<RestartTripSessionAction>(1)

    private var mapboxMap: MapboxMap? = null
    private var navigationMapboxMap: NavigationMapboxMap? = null
    private var locationComponent: LocationComponent? = null
    private var symbolManager: SymbolManager? = null
    private var fasterRoute: DirectionsRoute? = null
    private var colorList = listOf(Color.BLUE, Color.MAGENTA, Color.parseColor("#32a88f"))
    private var opacityList = listOf(.5f, .2f, .8f)

    private var adjustExtrusionsStyleButtonIndex = 0
    private lateinit var mapboxNavigation: MapboxNavigation
    private lateinit var localLocationEngine: LocationEngine
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var buildingExtrusionLayer: BuildingExtrusionLayer

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_arrival_building_extrusion)

        initViews()
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)
        localLocationEngine = LocationEngineProvider.getBestLocationEngine(applicationContext)

        val options =
            MapboxNavigation.defaultNavigationOptions(this, Utils.getMapboxAccessToken(this))

        val tilesUri = URI("https://api-routing-tiles-staging.tilestream.net")
        val tilesVersion = "2020_02_02-03_00_00"

        val endpoint = options.onboardRouterConfig?.endpoint?.toBuilder()
            ?.host(tilesUri.host)
            ?.version(tilesVersion)
            ?.build()

        val onboardRouterConfig = options.onboardRouterConfig?.toBuilder()
            ?.tilePath(
                File(
                    filesDir,
                    "Offline/${tilesUri.host}/$tilesVersion"
                ).absolutePath
            )
            ?.endpoint(endpoint)
            ?.build()

        val newOptions =
            options.toBuilder()
                .onboardRouterConfig(onboardRouterConfig)
                .build()

        mapboxNavigation = MapboxNavigation(
            applicationContext,
            Utils.getMapboxAccessToken(this),
            navigationOptions = newOptions
        )
    }

    override fun onMapReady(mapboxMap: MapboxMap) {
        this.mapboxMap = mapboxMap
        mapboxMap.moveCamera(CameraUpdateFactory.zoomTo(15.0))

        mapboxMap.addOnMapLongClickListener { click ->
            locationComponent?.lastKnownLocation?.let { location ->
                mapboxNavigation.requestRoutes(
                    RouteOptions.builder().applyDefaultParams()
                        .accessToken(Utils.getMapboxAccessToken(applicationContext))
                        .coordinates(location.toPoint(), null, click.toPoint())
                        .alternatives(true)
                        .profile(DirectionsCriteria.PROFILE_DRIVING_TRAFFIC)
                        .build(),
                    routesReqCallback
                )

                symbolManager?.deleteAll()
                symbolManager?.create(
                    SymbolOptions()
                        .withIconImage("marker")
                        .withGeometry(click.toPoint())
                )

                // Show and move the destination building highlighted footprint
                buildingExtrusionLayer.updateVisibility(true)
            }
            false
        }

        mapboxMap.setStyle(Style.MAPBOX_STREETS) { style ->
            locationComponent = mapboxMap.locationComponent.apply {
                activateLocationComponent(
                    LocationComponentActivationOptions.builder(this@ArrivalUiBuildingExtrusionActivityKt, style)
                        .useDefaultLocationEngine(false)
                        .build()
                )
                cameraMode = CameraMode.TRACKING
                isLocationComponentEnabled = true
            }

            symbolManager = SymbolManager(mapView, mapboxMap, style)
            style.addImage("marker", IconFactory.getInstance(this).defaultMarker().bitmap)

            navigationMapboxMap = NavigationMapboxMap(mapView, mapboxMap)
            navigationMapboxMap?.setOnRouteSelectionChangeListener { route ->
                mapboxNavigation.setRoutes(mapboxNavigation.getRoutes().toMutableList().apply {
                    remove(route)
                    add(0, route)
                })
            }

            // Initialize the Nav UI SDK's DestinationBuildingFootprintLayer class.
            buildingExtrusionLayer = BuildingExtrusionLayer(mapboxMap, mapView)
            adjust_building_extrusion_color_and_opacity_fab.show()
            adjust_building_extrusion_visibility_fab.show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun initViews() {
        startNavigation.setOnClickListener {
            updateCameraOnNavigationStateChange(true)
            mapboxNavigation.startTripSession()
        }
        adjust_building_extrusion_visibility_fab.setOnClickListener {
            buildingExtrusionLayer.updateVisibility(!buildingExtrusionLayer.visibility)
        }

        adjust_building_extrusion_color_and_opacity_fab.setOnClickListener {
            if (adjustExtrusionsStyleButtonIndex == opacityList.size) {
                adjustExtrusionsStyleButtonIndex = 0
            }
            buildingExtrusionLayer.opacity = opacityList[adjustExtrusionsStyleButtonIndex]
            buildingExtrusionLayer.color = colorList[adjustExtrusionsStyleButtonIndex]
            adjustExtrusionsStyleButtonIndex++
        }
    }

    private val locationObserver = object : LocationObserver {
        override fun onRawLocationChanged(rawLocation: Location) {
            Timber.d("raw location %s", rawLocation.toString())
        }

        override fun onEnhancedLocationChanged(
            enhancedLocation: Location,
            keyPoints: List<Location>
        ) {
            if (keyPoints.isNotEmpty()) {
                locationComponent?.forceLocationUpdate(keyPoints, true)
            } else {
                locationComponent?.forceLocationUpdate(enhancedLocation)
            }
            Timber.d("enhanced location %s", enhancedLocation)
            Timber.d("enhanced keyPoints %s", keyPoints)
        }
    }

    private fun startLocationUpdates() {
        val request = LocationEngineRequest.Builder(1000L)
            .setFastestInterval(500L)
            .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
            .build()
        try {
            localLocationEngine.requestLocationUpdates(
                request,
                locationEngineCallback,
                Looper.getMainLooper()
            )
            localLocationEngine.getLastLocation(locationEngineCallback)
        } catch (exception: SecurityException) {
            Timber.e(exception)
        }
    }

    private fun stopLocationUpdates() {
        localLocationEngine.removeLocationUpdates(locationEngineCallback)
    }

    private val routeProgressObserver = object : RouteProgressObserver {
        override fun onRouteProgressChanged(routeProgress: RouteProgress) {
            Timber.d("route progress %s", routeProgress.toString())
        }
    }

    private val routesObserver = object : RoutesObserver {
        override fun onRoutesChanged(routes: List<DirectionsRoute>) {
            navigationMapboxMap?.drawRoutes(routes)
            if (routes.isEmpty()) {
                Toast.makeText(this@ArrivalUiBuildingExtrusionActivityKt, "Empty routes", Toast.LENGTH_SHORT)
                    .show()
            }
            Timber.d("route changed %s", routes.toString())
        }
    }

    private val fasterRouteSelectionTimer: CountDownTimer =
        object : CountDownTimer(startTimeInMillis, countdownInterval) {
            override fun onTick(millisUntilFinished: Long) {
                Timber.d("FASTER_ROUTE: millisUntilFinished $millisUntilFinished")
                fasterRouteAcceptProgress.progress =
                    (maxProgress - millisUntilFinished / countdownInterval).toInt()
            }

            override fun onFinish() {
                Timber.d("FASTER_ROUTE: finished")
                this@ArrivalUiBuildingExtrusionActivityKt.fasterRoute = null
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }

    private val fasterRouteObserver = object : FasterRouteObserver {
        override fun onFasterRoute(currentRoute: DirectionsRoute, alternativeRoute: DirectionsRoute, isAlternativeFaster: Boolean) {
            if (isAlternativeFaster) {
                this@ArrivalUiBuildingExtrusionActivityKt.fasterRoute = alternativeRoute
                fasterRouteSelectionTimer.start()
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    private val routesReqCallback = object : RoutesRequestCallback {
        override fun onRoutesReady(routes: List<DirectionsRoute>) {
            Timber.d("route request success %s", routes.toString())
        }

        override fun onRoutesRequestFailure(throwable: Throwable, routeOptions: RouteOptions) {
            symbolManager?.deleteAll()
            Timber.e("route request failure %s", throwable.toString())
        }

        override fun onRoutesRequestCanceled(routeOptions: RouteOptions) {
            symbolManager?.deleteAll()
            Timber.d("route request canceled")
        }
    }

    private val tripSessionStateObserver = object : TripSessionStateObserver {
        override fun onSessionStateChanged(tripSessionState: TripSessionState) {
            when (tripSessionState) {
                TripSessionState.STARTED -> {
                    stopLocationUpdates()
                    startNavigation.visibility = GONE
                }
                TripSessionState.STOPPED -> {
                    startLocationUpdates()
                    startNavigation.visibility = VISIBLE
                    navigationMapboxMap?.removeRoute()
                    symbolManager?.deleteAll()
                    updateCameraOnNavigationStateChange(false)
                }
            }
        }
    }

    public override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    public override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    @SuppressLint("MissingPermission")
    override fun onStart() {
        super.onStart()
        mapView.onStart()

        restartSessionEventChannel.poll()?.also {
            mapboxNavigation.startTripSession()
        }

        mapboxNavigation.registerLocationObserver(locationObserver)
        mapboxNavigation.registerRouteProgressObserver(routeProgressObserver)
        mapboxNavigation.registerRoutesObserver(routesObserver)
        mapboxNavigation.registerTripSessionStateObserver(tripSessionStateObserver)
        mapboxNavigation.attachFasterRouteObserver(fasterRouteObserver)
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()

        mapboxNavigation.unregisterLocationObserver(locationObserver)
        mapboxNavigation.unregisterRouteProgressObserver(routeProgressObserver)
        mapboxNavigation.unregisterRoutesObserver(routesObserver)
        mapboxNavigation.unregisterTripSessionStateObserver(tripSessionStateObserver)
        mapboxNavigation.detachFasterRouteObserver()
        stopLocationUpdates()

        if (mapboxNavigation.getRoutes().isEmpty() && mapboxNavigation.getTripSessionState() == TripSessionState.STARTED) {
            // use this to kill the service and hide the notification when going into the background in the Free Drive state,
            // but also ensure to restart Free Drive when coming back from background by using the channel
            mapboxNavigation.stopTripSession()
            restartSessionEventChannel.offer(RestartTripSessionAction)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
        mapboxNavigation.stopTripSession()
        mapboxNavigation.onDestroy()
        restartSessionEventChannel.cancel()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
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

    private class MyLocationEngineCallback(activity: ArrivalUiBuildingExtrusionActivityKt) :
        LocationEngineCallback<LocationEngineResult> {

        private val activityRef = WeakReference(activity)

        override fun onSuccess(result: LocationEngineResult?) {
            result?.locations?.firstOrNull()?.let {
                activityRef.get()?.locationComponent?.forceLocationUpdate(it)
            }
        }

        override fun onFailure(exception: Exception) {
        }
    }

    private object RestartTripSessionAction
}

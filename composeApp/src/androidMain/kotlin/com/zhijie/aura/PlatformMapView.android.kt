package com.zhijie.aura

import android.Manifest
import android.app.Activity
import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.maps.MapView

private enum class LocationPermissionState {
    Granted,
    Denied,
    PermanentlyDenied,
}

@Composable
actual fun PlatformMapView(
    modifier: Modifier,
    config: MapConfig,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    var hasLocationPermission by remember { mutableStateOf(context.hasLocationPermission()) }
    var hasRequestedPermission by remember { mutableStateOf(false) }
    var permissionState by remember {
        mutableStateOf(
            if (hasLocationPermission) LocationPermissionState.Granted else LocationPermissionState.Denied,
        )
    }
    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var hasCenteredOnUserLocation by remember { mutableStateOf(false) }
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var userLocationMarker by remember { mutableStateOf<Marker?>(null) }
    var loadedStyleUrl by remember { mutableStateOf<String?>(null) }
    var isLocationServiceEnabled by remember { mutableStateOf(context.isLocationServiceEnabled()) }
    var locateStatusText by remember { mutableStateOf("等待定位") }
    val userLocationIcon = remember { context.createUserLocationBlueDotIcon() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        hasRequestedPermission = true
        hasLocationPermission = context.hasLocationPermission()
        permissionState = when {
            hasLocationPermission -> LocationPermissionState.Granted
            context.isLocationPermissionPermanentlyDenied() -> LocationPermissionState.PermanentlyDenied
            else -> LocationPermissionState.Denied
        }
    }

    val locateUser: (Boolean) -> Unit = { shouldFocus ->
        if (!context.hasLocationPermission()) {
            locateStatusText = "未授予定位权限，正在请求权限"
            hasRequestedPermission = true
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        } else if (!context.isLocationServiceEnabled()) {
            isLocationServiceEnabled = false
            locateStatusText = "定位服务未开启，请先开启 GPS"
        } else {
            isLocationServiceEnabled = true
            locateStatusText = "正在获取当前位置..."
            context.requestCurrentLatLng { latLng, fromLastKnown ->
                userLocation = latLng
                if (latLng != null) {
                    userLocationMarker = mapLibreMap?.updateUserMarker(userLocationMarker, latLng, userLocationIcon)
                    if (shouldFocus) {
                        mapLibreMap?.let { map ->
                            map.focusOnUser(latLng, config.camera.zoom)
                            hasCenteredOnUserLocation = true
                        }
                    }
                    locateStatusText = if (fromLastKnown) {
                        "已获取位置（来自上次记录）"
                    } else {
                        "定位成功"
                    }
                } else {
                    locateStatusText = "未获取到当前位置，请在空旷处稍后重试"
                }
            }
        }
    }


    LaunchedEffect(Unit) {
        isLocationServiceEnabled = context.isLocationServiceEnabled()
        if (!hasLocationPermission) {
            locateStatusText = "未授予定位权限，正在请求权限"
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        } else if (!isLocationServiceEnabled) {
            locateStatusText = "定位服务未开启，请先开启 GPS"
        }
    }

    LaunchedEffect(permissionState) {
        if (permissionState == LocationPermissionState.Granted) {
            locateUser(!hasCenteredOnUserLocation)
        } else if (permissionState == LocationPermissionState.PermanentlyDenied) {
            locateStatusText = "定位权限被永久拒绝，请到设置开启"
        } else {
            locateStatusText = "未授予定位权限"
        }
    }

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> {
                    mapView.onResume()
                    hasLocationPermission = context.hasLocationPermission()
                    isLocationServiceEnabled = context.isLocationServiceEnabled()
                    permissionState = if (hasLocationPermission) {
                        LocationPermissionState.Granted
                    } else if (hasRequestedPermission && context.isLocationPermissionPermanentlyDenied()) {
                        LocationPermissionState.PermanentlyDenied
                    } else {
                        LocationPermissionState.Denied
                    }
                    if (hasLocationPermission) {
                        locateUser(!hasCenteredOnUserLocation)
                    }
                }
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }

        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { mapView },
            update = { view ->
                view.getMapAsync { map ->
                    mapLibreMap = map

                    if (loadedStyleUrl != config.styleUrl) {
                        loadedStyleUrl = config.styleUrl
                        map.setStyle(Style.Builder().fromUri(config.styleUrl)) {
                            val fallbackTarget = LatLng(config.camera.latitude, config.camera.longitude)
                            val target = userLocation ?: fallbackTarget
                            val zoom = if (userLocation != null) maxOf(config.camera.zoom, 15.0) else config.camera.zoom
                            map.cameraPosition = CameraPosition.Builder().target(target).zoom(zoom).build()
                            userLocationMarker = map.updateUserMarker(userLocationMarker, userLocation, userLocationIcon)
                            if (userLocation != null && !hasCenteredOnUserLocation) {
                                map.focusOnUser(userLocation!!, config.camera.zoom)
                                hasCenteredOnUserLocation = true
                            }
                        }
                    } else {
                        userLocationMarker = map.updateUserMarker(userLocationMarker, userLocation, userLocationIcon)
                        val currentLocation = userLocation
                        if (currentLocation != null && !hasCenteredOnUserLocation) {
                            map.focusOnUser(currentLocation, config.camera.zoom)
                            hasCenteredOnUserLocation = true
                        }
                    }
                }
            },
        )

        if (permissionState != LocationPermissionState.Granted) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (permissionState == LocationPermissionState.PermanentlyDenied) {
                            "定位权限已被拒绝，请前往设置开启。"
                        } else {
                            "请允许定位权限，才能自动展示你当前的位置。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (permissionState == LocationPermissionState.PermanentlyDenied) {
                        TextButton(onClick = { context.openAppSettings() }) {
                            Text("去设置")
                        }
                    } else {
                        TextButton(
                            onClick = {
                                hasRequestedPermission = true
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ),
                                )
                            },
                        ) {
                            Text("重试授权")
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            onClick = {
                locateUser(true)
            },
        ) {
            Text("定位")
        }

        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 88.dp),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val currentLocation = userLocation
                Text("定位状态: $locateStatusText", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = if (currentLocation != null) {
                        "当前位置: ${currentLocation.latitude}, ${currentLocation.longitude}"
                    } else {
                        "当前位置: 未获取到"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!isLocationServiceEnabled) {
                    TextButton(onClick = { context.openLocationSettings() }) {
                        Text("去开启 GPS")
                    }
                }
            }
        }
    }
}

private fun MapLibreMap.focusOnUser(userLatLng: LatLng, baseZoom: Double) {
    val camera = CameraPosition.Builder()
        .target(userLatLng)
        .zoom(maxOf(baseZoom, 15.0))
        .build()
    animateCamera(CameraUpdateFactory.newCameraPosition(camera))
}

private fun MapLibreMap.updateUserMarker(currentMarker: Marker?, userLatLng: LatLng?, icon: Icon): Marker? {
    if (userLatLng == null) {
        currentMarker?.let { removeMarker(it) }
        return null
    }

    currentMarker?.let { removeMarker(it) }
    return addMarker(
        MarkerOptions()
            .position(userLatLng)
            .icon(icon)
            .title("My location"),
    )
}

private fun Context.createUserLocationBlueDotIcon(): Icon {
    val sizePx = 36
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = sizePx / 2f

    val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66389BFF
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center, 16f, outerPaint)

    val whiteStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center, 9f, whiteStrokePaint)

    val blueCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1A73E8.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center, 6f, blueCorePaint)

    return IconFactory.getInstance(this).fromBitmap(bitmap)
}

private fun Context.hasLocationPermission(): Boolean {
    val coarseGranted = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    val fineGranted = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    return coarseGranted || fineGranted
}

private fun Context.isLocationPermissionPermanentlyDenied(): Boolean {
    val activity = findActivity() ?: return false
    val fineRationale = ActivityCompat.shouldShowRequestPermissionRationale(
        activity,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )
    val coarseRationale = ActivityCompat.shouldShowRequestPermissionRationale(
        activity,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )
    return !fineRationale && !coarseRationale && !hasLocationPermission()
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Context.openAppSettings() {
    val intent = Intent(
        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}

private fun Context.isLocationServiceEnabled(): Boolean {
    val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
    return runCatching {
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }.getOrDefault(false)
}

private fun Context.openLocationSettings() {
    val intent = Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}

@SuppressLint("MissingPermission")
private fun Context.requestCurrentLatLng(
    timeoutMs: Long = 7000L,
    onResult: (LatLng?, Boolean) -> Unit,
) {
    val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    if (locationManager == null) {
        onResult(null, false)
        return
    }

    val provider = when {
        runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) -> {
            LocationManager.GPS_PROVIDER
        }
        runCatching { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) -> {
            LocationManager.NETWORK_PROVIDER
        }
        else -> null
    }

    if (provider == null) {
        val fallback = getLastKnownLatLng()
        onResult(fallback, fallback != null)
        return
    }

    var completed = false
    val mainHandler = Handler(Looper.getMainLooper())

    fun complete(latLng: LatLng?, fromLastKnown: Boolean) {
        if (completed) return
        completed = true
        onResult(latLng, fromLastKnown)
    }

    val timeoutTask = Runnable {
        val fallback = getLastKnownLatLng()
        complete(fallback, fallback != null)
    }

    mainHandler.postDelayed(timeoutTask, timeoutMs)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        runCatching {
            locationManager.getCurrentLocation(provider, null, mainExecutor) { location ->
                mainHandler.removeCallbacks(timeoutTask)
                if (location != null) {
                    complete(LatLng(location.latitude, location.longitude), false)
                } else {
                    val fallback = getLastKnownLatLng()
                    complete(fallback, fallback != null)
                }
            }
        }.onFailure {
            mainHandler.removeCallbacks(timeoutTask)
            val fallback = getLastKnownLatLng()
            complete(fallback, fallback != null)
        }
        return
    }

    val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            locationManager.removeUpdates(this)
            mainHandler.removeCallbacks(timeoutTask)
            complete(LatLng(location.latitude, location.longitude), false)
        }
    }

    runCatching {
        locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
    }.onFailure {
        locationManager.removeUpdates(listener)
        mainHandler.removeCallbacks(timeoutTask)
        val fallback = getLastKnownLatLng()
        complete(fallback, fallback != null)
    }
}

@SuppressLint("MissingPermission")
private fun Context.getLastKnownLatLng(): LatLng? {
    val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

    val locations = locationManager.getProviders(true)
        .mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }

    val latestLocation = locations.maxByOrNull { it.time } ?: return null
    return LatLng(latestLocation.latitude, latestLocation.longitude)
}

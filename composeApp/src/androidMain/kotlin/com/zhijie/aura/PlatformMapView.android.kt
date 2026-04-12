package com.zhijie.aura

import android.Manifest
import android.app.Activity
import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.content.SharedPreferences
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.ExperimentalFoundationApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private enum class LocationPermissionState {
    Granted,
    Denied,
    PermanentlyDenied,
}

private data class PhotonSearchResult(
    val title: String,
    val subtitle: String?,
    val latLng: LatLng,
)

@Composable
@OptIn(ExperimentalFoundationApi::class)
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
    var searchedLocationMarker by remember { mutableStateOf<Marker?>(null) }
    var loadedStyleUrl by remember { mutableStateOf<String?>(null) }
    var isLocationServiceEnabled by remember { mutableStateOf(context.isLocationServiceEnabled()) }
    var showPermissionCard by remember { mutableStateOf(false) }
    var showGpsCard by remember { mutableStateOf(false) }
    var permissionReminderTrigger by remember { mutableStateOf(0) }
    var gpsReminderTrigger by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchPageOpen by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var searchResults by remember { mutableStateOf<List<PhotonSearchResult>>(emptyList()) }
    var recentSearches by remember { mutableStateOf(context.loadRecentSearches()) }
    var selectedSearchResult by remember { mutableStateOf<PhotonSearchResult?>(null) }
    var immediateSearchToken by remember { mutableStateOf(0) }
    var pendingDeleteRecentSearch by remember { mutableStateOf<PhotonSearchResult?>(null) }
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

        if (permissionState == LocationPermissionState.Granted) {
            showPermissionCard = false
        } else {
            showPermissionCard = true
            permissionReminderTrigger += 1
        }
    }

    val locateUser: (Boolean) -> Unit = { shouldFocus ->
        if (!context.hasLocationPermission()) {
            hasRequestedPermission = true
            showPermissionCard = true
            permissionReminderTrigger += 1
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        } else if (!context.isLocationServiceEnabled()) {
            isLocationServiceEnabled = false
            showGpsCard = true
            gpsReminderTrigger += 1
        } else {
            isLocationServiceEnabled = true
            showPermissionCard = false
            showGpsCard = false
            context.requestCurrentLatLng { latLng, _ ->
                userLocation = latLng
                if (latLng != null) {
                    userLocationMarker = mapLibreMap?.updateUserMarker(userLocationMarker, latLng, userLocationIcon)
                    if (shouldFocus) {
                        mapLibreMap?.let { map ->
                            map.focusOnUser(latLng, config.camera.zoom)
                            hasCenteredOnUserLocation = true
                        }
                    }
                }
            }
        }
    }


    LaunchedEffect(Unit) {
        isLocationServiceEnabled = context.isLocationServiceEnabled()
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    LaunchedEffect(permissionState) {
        if (permissionState == LocationPermissionState.Granted) {
            locateUser(!hasCenteredOnUserLocation)
        }
    }

    LaunchedEffect(permissionReminderTrigger) {
        if (permissionReminderTrigger > 0) {
            delay(3500)
            showPermissionCard = false
        }
    }

    LaunchedEffect(gpsReminderTrigger) {
        if (gpsReminderTrigger > 0) {
            delay(3500)
            showGpsCard = false
        }
    }

    suspend fun runSearch(query: String) {
        val biasCenter = userLocation ?: mapLibreMap?.cameraPosition?.target
        isSearching = true
        searchError = null
        runCatching {
            searchPhoton(query = query, biasCenter = biasCenter)
        }.onSuccess {
            searchResults = it
        }.onFailure {
            searchResults = emptyList()
            searchError = "搜索失败，请稍后重试"
        }
        isSearching = false
    }

    LaunchedEffect(searchQuery, isSearchPageOpen) {
        if (!isSearchPageOpen) return@LaunchedEffect
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            isSearching = false
            searchError = null
            searchResults = emptyList()
            return@LaunchedEffect
        }

        delay(350)
        runSearch(query)
    }

    LaunchedEffect(immediateSearchToken) {
        if (!isSearchPageOpen || immediateSearchToken == 0) return@LaunchedEffect
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            isSearching = false
            searchError = null
            searchResults = emptyList()
            return@LaunchedEffect
        }
        runSearch(query)
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
                        showPermissionCard = false
                        if (!isLocationServiceEnabled) {
                            showGpsCard = true
                            gpsReminderTrigger += 1
                        }
                        locateUser(!hasCenteredOnUserLocation)
                    } else {
                        showPermissionCard = true
                        permissionReminderTrigger += 1
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
        val selectSearchResult: (PhotonSearchResult) -> Unit = { result ->
            selectedSearchResult = result
            searchQuery = result.title
            isSearchPageOpen = false
            searchResults = emptyList()
            searchError = null
            recentSearches = context.saveRecentSearch(result, recentSearches)

            mapLibreMap?.let { map ->
                searchedLocationMarker = map.updateSearchMarker(searchedLocationMarker, result)
                map.focusOnSearchResult(result.latLng)
            }
        }

        val reminderCardModifier = Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 40.dp)

        val searchCardModifier = Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
            .zIndex(2f)

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
                            searchedLocationMarker = map.updateSearchMarker(searchedLocationMarker, selectedSearchResult)
                            if (userLocation != null && !hasCenteredOnUserLocation) {
                                map.focusOnUser(userLocation!!, config.camera.zoom)
                                hasCenteredOnUserLocation = true
                            }
                        }
                    } else {
                        userLocationMarker = map.updateUserMarker(userLocationMarker, userLocation, userLocationIcon)
                        searchedLocationMarker = map.updateSearchMarker(searchedLocationMarker, selectedSearchResult)
                        val currentLocation = userLocation
                        if (currentLocation != null && !hasCenteredOnUserLocation) {
                            map.focusOnUser(currentLocation, config.camera.zoom)
                            hasCenteredOnUserLocation = true
                        }
                    }
                }
            },
        )

        Card(modifier = searchCardModifier.clickable { isSearchPageOpen = true }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (searchQuery.isBlank()) "搜索地点" else searchQuery,
                    style = MaterialTheme.typography.bodyLarge,
                )

                if (searchQuery.isNotBlank()) {
                    IconButton(
                        onClick = {
                            searchQuery = ""
                            searchError = null
                            searchResults = emptyList()
                            selectedSearchResult = null
                            searchedLocationMarker = mapLibreMap?.updateSearchMarker(
                                searchedLocationMarker,
                                null,
                            )
                        },
                    ) {
                        androidx.compose.material3.Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_close_clear_cancel),
                            contentDescription = "清空搜索",
                        )
                    }
                }
            }
        }

        if (permissionState != LocationPermissionState.Granted && showPermissionCard) {
            Card(
                modifier = reminderCardModifier,
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
        } else if (!isLocationServiceEnabled && showGpsCard) {
            Card(
                modifier = reminderCardModifier,
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "定位服务未开启，请先开启 GPS。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { context.openLocationSettings() }) {
                        Text("去开启 GPS")
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

        if (isSearchPageOpen) {
            BackHandler { isSearchPageOpen = false }

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .zIndex(3f),
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "搜索",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            if (it.isBlank()) {
                                searchError = null
                                searchResults = emptyList()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("搜索地点") },
                        leadingIcon = {
                            IconButton(onClick = { isSearchPageOpen = false }) {
                                androidx.compose.material3.Icon(
                                    painter = painterResource(id = android.R.drawable.ic_media_previous),
                                    contentDescription = "返回",
                                )
                            }
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        searchQuery = ""
                                        searchError = null
                                        searchResults = emptyList()
                                    },
                                ) {
                                    androidx.compose.material3.Icon(
                                        painter = painterResource(id = android.R.drawable.ic_menu_close_clear_cancel),
                                        contentDescription = "清空搜索",
                                    )
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                immediateSearchToken += 1
                            },
                        ),
                    )

                    val panelModifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState())

                    when {
                        searchQuery.isBlank() -> {
                            Column(modifier = panelModifier) {
                                if (recentSearches.isEmpty()) {
                                    Text(
                                        text = "暂无最近搜索",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                } else {
                                    recentSearches.forEachIndexed { index, result ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .combinedClickable(
                                                    onClick = { selectSearchResult(result) },
                                                    onLongClick = { pendingDeleteRecentSearch = result },
                                                )
                                                .padding(vertical = 10.dp),
                                        ) {
                                            Text(result.title, style = MaterialTheme.typography.bodyLarge)
                                            result.subtitle?.let {
                                                Text(it, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                        if (index != recentSearches.lastIndex) {
                                            Divider(color = Color.LightGray)
                                        }
                                    }
                                }
                            }
                        }

                        isSearching -> {
                            Text(
                                text = "搜索中...",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }

                        searchError != null -> {
                            Text(
                                text = searchError!!,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }

                        searchResults.isEmpty() -> {
                            Text(
                                text = "未找到匹配地点",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }

                        else -> {
                            Column(modifier = panelModifier) {
                                searchResults.forEachIndexed { index, result ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectSearchResult(result) }
                                            .padding(vertical = 10.dp),
                                    ) {
                                        Text(result.title, style = MaterialTheme.typography.bodyLarge)
                                        result.subtitle?.let {
                                            Text(it, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    if (index != searchResults.lastIndex) {
                                        Divider(color = Color.LightGray)
                                    }
                                }
                            }
                        }
                    }
                }

                pendingDeleteRecentSearch?.let { target ->
                    AlertDialog(
                        onDismissRequest = { pendingDeleteRecentSearch = null },
                        title = { Text("删除历史记录") },
                        text = { Text("确定删除“${target.title}”吗？") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    recentSearches = context.deleteRecentSearch(target, recentSearches)
                                    pendingDeleteRecentSearch = null
                                },
                            ) {
                                Text("删除")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingDeleteRecentSearch = null }) {
                                Text("取消")
                            }
                        },
                    )
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

private fun MapLibreMap.focusOnSearchResult(searchLatLng: LatLng) {
    val camera = CameraPosition.Builder()
        .target(searchLatLng)
        .zoom(16.0)
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

private fun MapLibreMap.updateSearchMarker(
    currentMarker: Marker?,
    searchResult: PhotonSearchResult?,
): Marker? {
    if (searchResult == null) {
        currentMarker?.let { removeMarker(it) }
        return null
    }

    currentMarker?.let { removeMarker(it) }
    return addMarker(
        MarkerOptions()
            .position(searchResult.latLng)
            .title(searchResult.title)
            .snippet(searchResult.subtitle ?: ""),
    )
}

private suspend fun searchPhoton(
    query: String,
    limit: Int = 8,
    biasCenter: LatLng? = null,
): List<PhotonSearchResult> = withContext(Dispatchers.IO) {
    val encodedQuery = URLEncoder.encode(query, "UTF-8")
    val biasQuery = biasCenter?.let { "&lat=${it.latitude}&lon=${it.longitude}" }.orEmpty()
    val requestUrl = URL("https://osm.zhijie.win/api/?q=$encodedQuery&limit=$limit$biasQuery")
    val connection = (requestUrl.openConnection() as HttpURLConnection).apply {
        connectTimeout = 8000
        readTimeout = 8000
        requestMethod = "GET"
        setRequestProperty("Accept", "application/json")
    }

    try {
        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            throw IllegalStateException("HTTP $responseCode")
        }

        val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
        parsePhotonResults(responseBody)
            .prioritizeByDistance(biasCenter)
    } finally {
        connection.disconnect()
    }
}

private fun List<PhotonSearchResult>.prioritizeByDistance(
    biasCenter: LatLng?,
): List<PhotonSearchResult> {
    if (biasCenter == null || isEmpty()) return this

    // Keep Photon text relevance while nudging nearby places to the top.
    return withIndex()
        .sortedWith(
            compareBy<IndexedValue<PhotonSearchResult>> {
                distanceBucketMeters(biasCenter, it.value.latLng)
            }.thenBy { it.index },
        )
        .map { it.value }
}

private fun distanceBucketMeters(center: LatLng, point: LatLng): Int {
    val distanceResults = FloatArray(1)
    Location.distanceBetween(
        center.latitude,
        center.longitude,
        point.latitude,
        point.longitude,
        distanceResults,
    )
    val distance = distanceResults[0]
    return when {
        distance <= 1_500f -> 0
        distance <= 5_000f -> 1
        distance <= 15_000f -> 2
        distance <= 40_000f -> 3
        distance <= 120_000f -> 4
        else -> 5
    }
}

private fun parsePhotonResults(responseBody: String): List<PhotonSearchResult> {
    val jsonObject = JSONObject(responseBody)
    val features = jsonObject.optJSONArray("features") ?: return emptyList()
    val results = mutableListOf<PhotonSearchResult>()

    for (index in 0 until features.length()) {
        val feature = features.optJSONObject(index) ?: continue
        val geometry = feature.optJSONObject("geometry") ?: continue
        val coordinates = geometry.optJSONArray("coordinates") ?: continue
        val longitude = coordinates.optDouble(0, Double.NaN)
        val latitude = coordinates.optDouble(1, Double.NaN)
        if (!latitude.isFinite() || !longitude.isFinite()) continue

        val properties = feature.optJSONObject("properties")
        val name = properties?.optString("name").orEmpty()
        val city = properties?.optString("city").orEmpty()
        val state = properties?.optString("state").orEmpty()
        val country = properties?.optString("country").orEmpty()
        val street = properties?.optString("street").orEmpty()
        val houseNumber = properties?.optString("housenumber").orEmpty()

        val title = name
            .ifBlank { listOfNotNull(street.takeIf { it.isNotBlank() }, houseNumber.takeIf { it.isNotBlank() }).joinToString(" ") }
            .ifBlank { city }
            .ifBlank { country }
            .ifBlank { "未命名地点" }

        val subtitle = listOf(street, city, state, country)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")
            .ifBlank { null }

        results += PhotonSearchResult(
            title = title,
            subtitle = subtitle,
            latLng = LatLng(latitude, longitude),
        )
    }

    return results
}

private fun Context.loadRecentSearches(): List<PhotonSearchResult> {
    val sharedPreferences = getSearchSharedPreferences()
    val raw = sharedPreferences.getString(RECENT_SEARCHES_KEY, null) ?: return emptyList()
    return runCatching {
        val jsonArray = JSONArray(raw)
        buildList {
            for (index in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONObject(index) ?: continue
                val title = item.optString("title")
                val subtitle = item.optString("subtitle").ifBlank { null }
                val latitude = item.optDouble("latitude", Double.NaN)
                val longitude = item.optDouble("longitude", Double.NaN)
                if (!latitude.isFinite() || !longitude.isFinite() || title.isBlank()) continue

                add(
                    PhotonSearchResult(
                        title = title,
                        subtitle = subtitle,
                        latLng = LatLng(latitude, longitude),
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())
}

private fun Context.saveRecentSearch(
    latest: PhotonSearchResult,
    current: List<PhotonSearchResult>,
): List<PhotonSearchResult> {
    val updated = (listOf(latest) + current)
        .distinctBy { "${it.title}:${it.latLng.latitude}:${it.latLng.longitude}" }
        .take(MAX_RECENT_SEARCHES)

    val jsonArray = JSONArray()
    updated.forEach { item ->
        jsonArray.put(
            JSONObject().apply {
                put("title", item.title)
                put("subtitle", item.subtitle ?: "")
                put("latitude", item.latLng.latitude)
                put("longitude", item.latLng.longitude)
            },
        )
    }

    getSearchSharedPreferences().edit().putString(RECENT_SEARCHES_KEY, jsonArray.toString()).apply()
    return updated
}

private fun Context.deleteRecentSearch(
    target: PhotonSearchResult,
    current: List<PhotonSearchResult>,
): List<PhotonSearchResult> {
    val updated = current.filterNot {
        it.title == target.title &&
            it.latLng.latitude == target.latLng.latitude &&
            it.latLng.longitude == target.latLng.longitude
    }

    val jsonArray = JSONArray()
    updated.forEach { item ->
        jsonArray.put(
            JSONObject().apply {
                put("title", item.title)
                put("subtitle", item.subtitle ?: "")
                put("latitude", item.latLng.latitude)
                put("longitude", item.latLng.longitude)
            },
        )
    }

    getSearchSharedPreferences().edit().putString(RECENT_SEARCHES_KEY, jsonArray.toString()).apply()
    return updated
}

private fun Context.getSearchSharedPreferences(): SharedPreferences {
    return getSharedPreferences(SEARCH_PREFS_NAME, Context.MODE_PRIVATE)
}

private const val SEARCH_PREFS_NAME = "map_search"
private const val RECENT_SEARCHES_KEY = "recent_searches"
private const val MAX_RECENT_SEARCHES = 8

private fun Context.createUserLocationBlueDotIcon(): Icon {
    val density = resources.displayMetrics.density
    val sizePx = (28f * density).toInt().coerceAtLeast(36)
    val scale = sizePx / 36f
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = sizePx / 2f

    val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66389BFF
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center, 16f * scale, outerPaint)

    val whiteStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center, 9f * scale, whiteStrokePaint)

    val blueCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1A73E8.toInt()
        style = Paint.Style.FILL
    }
    canvas.drawCircle(center, center, 6f * scale, blueCorePaint)

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

package com.zhijie.aura

import android.Manifest
import android.app.Activity
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Icon as MapLibreIcon
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.maps.MapView
import org.maplibre.geojson.Feature
import com.zhijie.aura.search.SemanticQueryParser
import com.zhijie.aura.search.SemanticTag
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.roundToLong

private enum class LocationPermissionState {
    Granted,
    Denied,
    PermanentlyDenied,
}

private enum class BottomNavTab {
    Map,
    List,
}

private enum class ListPeekMode {
    Full,
    Half,
    Tiny,
}

private enum class HintTone {
    Neutral,
    Success,
}

private data class PhotonSearchResult(
    val title: String,
    val subtitle: String?,
    val latLng: LatLng,
)

private data class FavoriteFolder(
    val id: String,
    val name: String,
    val places: List<PhotonSearchResult>,
)

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
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
    var favoriteMarkers by remember { mutableStateOf<List<Marker>>(emptyList()) }
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
    var favoriteFolders by remember { mutableStateOf(context.loadFavoriteFolders()) }
    var selectedFavoriteFolderId by remember { mutableStateOf<String?>(favoriteFolders.firstOrNull()?.id) }
    var pendingAddToFavoriteFolderId by remember { mutableStateOf<String?>(null) }
    var pendingPreferredCollectionFolderId by remember { mutableStateOf<String?>(null) }
    var showPlaceDetailPanel by remember { mutableStateOf(false) }
    var showPlaceActionExtras by remember { mutableStateOf(false) }
    var showPlaceCollectionsDialog by remember { mutableStateOf(false) }
    var placeFolderDraftIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var placeActionHint by remember { mutableStateOf<String?>(null) }
    var placeActionHintTone by remember { mutableStateOf(HintTone.Neutral) }
    var showCreateFavoriteDialog by remember { mutableStateOf(false) }
    var newFavoriteName by remember { mutableStateOf("") }
    var createFavoriteError by remember { mutableStateOf<String?>(null) }
    var pendingDeleteFavoriteFolder by remember { mutableStateOf<FavoriteFolder?>(null) }
    var selectedSearchResult by remember { mutableStateOf<PhotonSearchResult?>(null) }
    var immediateSearchToken by remember { mutableStateOf(0) }
    var activeTab by remember { mutableStateOf(BottomNavTab.Map) }
    var listPeekMode by remember { mutableStateOf(ListPeekMode.Full) }
    val bottomBarHeight = 80.dp
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val density = LocalDensity.current
    val halfPeekHeight = (screenHeight * 0.42f).coerceAtLeast(220.dp).coerceAtMost(360.dp)
    val tinyPeekHeight = 88.dp
    val fastDownFlingThreshold = with(density) { 2200.dp.toPx() }
    val listSheetPeekHeight = if (listPeekMode == ListPeekMode.Tiny) tinyPeekHeight else halfPeekHeight
    val userLocationIcon = remember { context.createUserLocationBlueDotIcon() }
    val listSheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.Hidden,
        skipHiddenState = false,
        confirmValueChange = { targetValue ->
            if (activeTab == BottomNavTab.List && targetValue == SheetValue.Hidden) {
                // When user drags down to the bottom in list mode, settle on tiny instead of Hidden.
                listPeekMode = ListPeekMode.Tiny
                false
            } else {
                true
            }
        },
    )
    val listSheetScaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = listSheetState,
    )
    var partialSheetOffsetPx by remember { mutableStateOf<Float?>(null) }
    var expandedSheetOffsetPx by remember { mutableStateOf<Float?>(null) }
    val listExpandProgress by remember(activeTab, isSearchPageOpen, listSheetState) {
        derivedStateOf {
            if (isSearchPageOpen || activeTab != BottomNavTab.List) {
                return@derivedStateOf 0f
            }

            val fallbackProgress = if (listSheetState.currentValue == SheetValue.Expanded) 1f else 0f
            val currentOffset = runCatching { listSheetState.requireOffset() }.getOrNull()
                ?: return@derivedStateOf fallbackProgress

            val partialOffset = partialSheetOffsetPx
            val expandedOffset = expandedSheetOffsetPx
            if (partialOffset == null || expandedOffset == null || partialOffset <= expandedOffset) {
                return@derivedStateOf fallbackProgress
            }

            ((partialOffset - currentOffset) / (partialOffset - expandedOffset)).coerceIn(0f, 1f)
        }
    }
    val dimTarget = 0.18f * listExpandProgress
    val mapDimAlpha by animateFloatAsState(
        targetValue = dimTarget,
        animationSpec = tween(durationMillis = 220),
        label = "mapDimAlpha",
    )
    val listSheetGestureConnection = remember(activeTab, listPeekMode, fastDownFlingThreshold) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (
                    activeTab == BottomNavTab.List &&
                    listPeekMode == ListPeekMode.Tiny &&
                    available.y < -1f
                ) {
                    // Promote tiny -> half on upward drag so the middle anchor is reachable again.
                    listPeekMode = ListPeekMode.Half
                } else if (
                    activeTab == BottomNavTab.List &&
                    source == NestedScrollSource.UserInput &&
                    listPeekMode == ListPeekMode.Half &&
                    listSheetState.currentValue == SheetValue.PartiallyExpanded &&
                    available.y > 1f
                ) {
                    // When dragging down from half, snap to tiny (header-only) instead of rebounding to half.
                    listPeekMode = ListPeekMode.Tiny
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val shouldSnapTiny =
                    activeTab == BottomNavTab.List &&
                        available.y > fastDownFlingThreshold &&
                        listSheetState.currentValue != SheetValue.Hidden
                if (shouldSnapTiny) {
                    listPeekMode = ListPeekMode.Tiny
                    listSheetState.partialExpand()
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(activeTab, isSearchPageOpen, listSheetState.currentValue) {
        if (activeTab != BottomNavTab.List || isSearchPageOpen) return@LaunchedEffect
        val offset = runCatching { listSheetState.requireOffset() }.getOrNull() ?: return@LaunchedEffect
        when (listSheetState.currentValue) {
            SheetValue.PartiallyExpanded -> partialSheetOffsetPx = offset
            SheetValue.Expanded -> expandedSheetOffsetPx = offset
            SheetValue.Hidden -> Unit
        }
    }

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

    LaunchedEffect(placeActionHint) {
        if (placeActionHint != null) {
            delay(1800)
            placeActionHint = null
            placeActionHintTone = HintTone.Neutral
        }
    }

    suspend fun runSearch(query: String) {
        val biasCenter = userLocation ?: mapLibreMap?.cameraPosition?.target
        val parsed = SemanticQueryParser.parse(
            query = query,
            languageCode = Locale.getDefault().language,
            countryCode = Locale.getDefault().country,
        )
        isSearching = true
        searchError = null
        runCatching {
            searchPhoton(
                query = parsed.effectiveQuery,
                biasCenter = biasCenter,
                semanticTags = parsed.semanticTags,
            )
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

    LaunchedEffect(activeTab) {
        if (activeTab == BottomNavTab.List) {
            listPeekMode = ListPeekMode.Full
            listSheetState.expand()
        } else if (listSheetState.currentValue != SheetValue.Hidden) {
            listSheetState.hide()
        }
    }

    LaunchedEffect(listSheetState.currentValue, activeTab, listPeekMode) {
        if (activeTab != BottomNavTab.List) return@LaunchedEffect
        when (listSheetState.currentValue) {
            SheetValue.Expanded -> listPeekMode = ListPeekMode.Full
            SheetValue.PartiallyExpanded -> {
                if (listPeekMode == ListPeekMode.Full) {
                    listPeekMode = ListPeekMode.Half
                }
            }
            SheetValue.Hidden -> Unit
        }
    }

    LaunchedEffect(listSheetState.targetValue, activeTab) {
        if (activeTab != BottomNavTab.List) return@LaunchedEffect
        if (listSheetState.targetValue == SheetValue.PartiallyExpanded && listPeekMode == ListPeekMode.Full) {
            // Drag-release near the middle should settle on half mode.
            listPeekMode = ListPeekMode.Half
        }
    }

    LaunchedEffect(isSearchPageOpen) {
        if (isSearchPageOpen && activeTab == BottomNavTab.List) {
            activeTab = BottomNavTab.Map
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

    val selectedFavoritePlaces = remember(favoriteFolders, selectedFavoriteFolderId) {
        favoriteFolders.firstOrNull { it.id == selectedFavoriteFolderId }?.places.orEmpty()
    }

    Box(modifier = modifier.fillMaxSize()) {
        val showHint: (String, HintTone) -> Unit = { message, tone ->
            placeActionHint = message
            placeActionHintTone = tone
        }

        val refreshVisibleFavoriteMarkers: () -> Unit = {
            val focusedPlaces = favoriteFolders
                .firstOrNull { it.id == selectedFavoriteFolderId }
                ?.places
                .orEmpty()
            mapLibreMap?.let { map ->
                favoriteMarkers = map.updateFavoriteMarkers(favoriteMarkers, focusedPlaces)
            }
        }

        val openPlaceCollectionsDialog: (PhotonSearchResult, String?) -> Unit = { place, preselectedFolderId ->
            val existingFolderIds = favoriteFolders.folderIdsContainingPlace(place)
            selectedSearchResult = place
            searchQuery = place.title
            placeFolderDraftIds = existingFolderIds + listOfNotNull(preselectedFolderId)
            showPlaceCollectionsDialog = true
        }

        val selectSearchResult: (PhotonSearchResult) -> Unit = { result ->
            val pendingFolderId = pendingAddToFavoriteFolderId
            pendingAddToFavoriteFolderId = null
            pendingPreferredCollectionFolderId = pendingFolderId
            showPlaceDetailPanel = false
            showPlaceActionExtras = false
            isSearchPageOpen = false
            searchResults = emptyList()
            searchError = null
            selectedSearchResult = result

            mapLibreMap?.let { map ->
                searchedLocationMarker = map.updateSearchMarker(searchedLocationMarker, result)
                map.focusOnSearchResult(result.latLng)
            }
            activeTab = BottomNavTab.Map
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

        val syncMapOverlays: (MapLibreMap) -> Unit = { map ->
            userLocationMarker = map.updateUserMarker(userLocationMarker, userLocation, userLocationIcon)
            searchedLocationMarker = map.updateSearchMarker(searchedLocationMarker, selectedSearchResult)
            favoriteMarkers = map.updateFavoriteMarkers(favoriteMarkers, selectedFavoritePlaces)

            val currentLocation = userLocation
            if (currentLocation != null && !hasCenteredOnUserLocation) {
                map.focusOnUser(currentLocation, config.camera.zoom)
                hasCenteredOnUserLocation = true
            }
        }

        val applyStyleAndSync: (MapLibreMap) -> Unit = { map ->
            loadedStyleUrl = config.styleUrl
            map.setStyle(Style.Builder().fromUri(config.styleUrl)) {
                val fallbackTarget = LatLng(config.camera.latitude, config.camera.longitude)
                val target = userLocation ?: fallbackTarget
                val zoom = if (userLocation != null) maxOf(config.camera.zoom, 15.0) else config.camera.zoom
                map.cameraPosition = CameraPosition.Builder().target(target).zoom(zoom).build()
                syncMapOverlays(map)
            }
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { mapView },
            update = { view ->
                val currentMap = mapLibreMap
                if (currentMap == null) {
                    view.getMapAsync { asyncMap ->
                        mapLibreMap = asyncMap
                        asyncMap.setOnMarkerClickListener { marker ->
                            val place = marker.toPhotonSearchResultOrNull() ?: return@setOnMarkerClickListener false
                            selectedSearchResult = place
                            pendingPreferredCollectionFolderId = null
                            showPlaceDetailPanel = true
                            showPlaceActionExtras = false
                            searchedLocationMarker = asyncMap.updateSearchMarker(searchedLocationMarker, place)
                            asyncMap.focusOnSearchResult(place.latLng)
                            activeTab = BottomNavTab.Map
                            true
                        }
                        asyncMap.addOnMapClickListener { clickedLatLng ->
                            if (isSearchPageOpen) {
                                return@addOnMapClickListener false
                            }

                            val place = asyncMap.pickBaseMapPlace(clickedLatLng) ?: return@addOnMapClickListener false
                            selectedSearchResult = place
                            pendingPreferredCollectionFolderId = null
                            showPlaceDetailPanel = true
                            showPlaceActionExtras = false
                            searchedLocationMarker = asyncMap.updateSearchMarker(searchedLocationMarker, place)
                            asyncMap.focusOnSearchResult(place.latLng)
                            activeTab = BottomNavTab.Map
                            true
                        }
                        if (loadedStyleUrl != config.styleUrl) {
                            applyStyleAndSync(asyncMap)
                        } else {
                            syncMapOverlays(asyncMap)
                        }
                    }
                } else if (loadedStyleUrl != config.styleUrl) {
                    applyStyleAndSync(currentMap)
                } else {
                    syncMapOverlays(currentMap)
                }
            },
        )

        if (mapDimAlpha > 0.001f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = mapDimAlpha))
                    .zIndex(1f),
            )
        }

        if (!isSearchPageOpen && activeTab == BottomNavTab.Map && showPlaceDetailPanel) {
            BackHandler {
                showPlaceDetailPanel = false
                showPlaceActionExtras = false
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f))
                    .clickable {
                        showPlaceDetailPanel = false
                        showPlaceActionExtras = false
                    }
                    .zIndex(3f),
            )
        }

        if (!isSearchPageOpen && activeTab == BottomNavTab.Map) {
            Card(modifier = searchCardModifier.clickable {
                pendingAddToFavoriteFolderId = null
                pendingPreferredCollectionFolderId = null
                showPlaceDetailPanel = false
                showPlaceActionExtras = false
                isSearchPageOpen = true
            }) {
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
                            pendingPreferredCollectionFolderId = null
                            showPlaceDetailPanel = false
                            showPlaceActionExtras = false
                            showPlaceCollectionsDialog = false
                            placeFolderDraftIds = emptySet()
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

        if (!isSearchPageOpen) {
            if (!showPlaceDetailPanel) {
                FloatingActionButton(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .zIndex(3f)
                        .padding(end = 16.dp, bottom = bottomBarHeight + 16.dp),
                    onClick = {
                        locateUser(true)
                    },
                ) {
                    Text("定位")
                }
            }

            BottomSheetScaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = bottomBarHeight)
                    .nestedScroll(listSheetGestureConnection)
                    .zIndex(2f),
                scaffoldState = listSheetScaffoldState,
                sheetPeekHeight = listSheetPeekHeight,
                containerColor = Color.Transparent,
                sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                sheetContainerColor = MaterialTheme.colorScheme.surface,
                sheetTonalElevation = 0.dp,
                sheetShadowElevation = 0.dp,
                sheetDragHandle = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(50),
                                ),
                        )
                    }
                },
                sheetContent = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.92f)
                            .navigationBarsPadding()
                            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("收藏夹", style = MaterialTheme.typography.titleMedium)
                            TextButton(
                                onClick = {
                                    createFavoriteError = null
                                    newFavoriteName = ""
                                    showCreateFavoriteDialog = true
                                },
                            ) {
                                Text("新建")
                            }
                        }

                        if (favoriteFolders.isEmpty()) {
                            Text(
                                text = "暂无收藏夹，点击右上角新建",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                favoriteFolders.forEachIndexed { index, folder ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = {
                                                    selectedFavoriteFolderId = folder.id
                                                    pendingAddToFavoriteFolderId = null
                                                    pendingPreferredCollectionFolderId = null
                                                    selectedSearchResult = null
                                                    showPlaceDetailPanel = false
                                                    showPlaceActionExtras = false
                                                    showPlaceCollectionsDialog = false
                                                    placeFolderDraftIds = emptySet()
                                                    searchedLocationMarker = mapLibreMap?.updateSearchMarker(
                                                        searchedLocationMarker,
                                                        null,
                                                    )
                                                    mapLibreMap?.let { map ->
                                                        favoriteMarkers = map.updateFavoriteMarkers(favoriteMarkers, folder.places)
                                                        map.focusOnPlaces(folder.places)
                                                    }
                                                    activeTab = BottomNavTab.Map
                                                },
                                                onLongClick = { pendingDeleteFavoriteFolder = folder },
                                            )
                                            .padding(vertical = 10.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(folder.name, style = MaterialTheme.typography.bodyLarge)
                                                Text(
                                                    text = "${folder.places.size} 个地点",
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            }
                                            TextButton(
                                                onClick = {
                                                    selectedFavoriteFolderId = folder.id
                                                    pendingAddToFavoriteFolderId = folder.id
                                                    searchQuery = ""
                                                    searchError = null
                                                    searchResults = emptyList()
                                                    isSearchPageOpen = true
                                                },
                                            ) {
                                                Text("添加地点")
                                            }
                                        }
                                    }
                                    if (index != favoriteFolders.lastIndex) {
                                        HorizontalDivider(color = Color.LightGray)
                                    }
                                }
                            }
                        }
                    }
                },
            ) {
                // Intentionally empty: map and overlays are rendered in sibling layers.
            }

            NavigationBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .zIndex(4f),
                windowInsets = WindowInsets(0, 0, 0, 0),
            ) {
                NavigationBarItem(
                    selected = activeTab == BottomNavTab.Map,
                    onClick = { activeTab = BottomNavTab.Map },
                    icon = {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_mapmode),
                            contentDescription = "地图",
                        )
                    },
                    label = { Text("地图") },
                )
                NavigationBarItem(
                    selected = activeTab == BottomNavTab.List,
                    onClick = { activeTab = BottomNavTab.List },
                    icon = {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_agenda),
                            contentDescription = "列表",
                        )
                    },
                    label = { Text("列表") },
                )
            }
        }

        if (!isSearchPageOpen && activeTab == BottomNavTab.Map && showPlaceDetailPanel) {
            selectedSearchResult?.let { place ->
                val collectedFolderCount = favoriteFolders.folderIdsContainingPlace(place).size
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .zIndex(4f)
                        .padding(start = 12.dp, end = 12.dp, bottom = bottomBarHeight + 12.dp),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "地点详情",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = place.title,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                place.subtitle?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                            IconButton(onClick = {
                                showPlaceDetailPanel = false
                                showPlaceActionExtras = false
                            }) {
                                Icon(
                                    painter = painterResource(id = android.R.drawable.ic_menu_close_clear_cancel),
                                    contentDescription = "关闭地点面板",
                                )
                            }
                        }

                        Text(
                            text = "${"%.6f".format(Locale.US, place.latLng.latitude)}, ${"%.6f".format(Locale.US, place.latLng.longitude)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Text(
                            text = if (collectedFolderCount > 0) {
                                "已加入 $collectedFolderCount 个收藏夹"
                            } else {
                                "尚未加入收藏夹"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    showPlaceDetailPanel = false
                                    showPlaceActionExtras = false
                                    openPlaceCollectionsDialog(place, pendingPreferredCollectionFolderId)
                                    pendingPreferredCollectionFolderId = null
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("收藏")
                            }
                            OutlinedButton(
                                onClick = { showHint("路径功能即将上线", HintTone.Neutral) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("路径")
                            }
                        }

                        TextButton(
                            onClick = { showPlaceActionExtras = !showPlaceActionExtras },
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text(if (showPlaceActionExtras) "收起更多功能" else "更多功能")
                        }

                        if (showPlaceActionExtras) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { showHint("AI 查询功能即将上线", HintTone.Neutral) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("AI 查询")
                                }
                                OutlinedButton(
                                    onClick = { showHint("分享功能即将上线", HintTone.Neutral) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("分享")
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val copied = context.copyToClipboard(
                                            label = "坐标",
                                            text = "${"%.6f".format(Locale.US, place.latLng.latitude)}, ${"%.6f".format(Locale.US, place.latLng.longitude)}",
                                        )
                                        if (copied) {
                                            showHint("坐标已复制", HintTone.Success)
                                        } else {
                                            showHint("暂时无法复制坐标", HintTone.Neutral)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("复制坐标")
                                }
                                OutlinedButton(
                                    onClick = { showHint("反馈功能即将上线", HintTone.Neutral) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("反馈")
                                }
                            }
                        }
                    }
                }
            }
        }

        val quickHint = placeActionHint
        if (!isSearchPageOpen && quickHint != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 82.dp, start = 16.dp, end = 16.dp)
                    .zIndex(5f),
            ) {
                Text(
                    text = quickHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (placeActionHintTone == HintTone.Success) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
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
                            text = pendingAddToFavoriteFolderId?.let { folderId ->
                                favoriteFolders.firstOrNull { it.id == folderId }?.let { "添加到：${it.name}" }
                            } ?: "搜索",
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
                                if (pendingAddToFavoriteFolderId != null) {
                                    Text(
                                        text = "输入关键词后，点击结果即可加入收藏夹",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                } else {
                                    Text(
                                        text = "输入关键词开始搜索地点",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
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
                                        HorizontalDivider(color = Color.LightGray)
                                    }
                                }
                            }
                        }
                    }
                }

            }
        }

        if (showCreateFavoriteDialog) {
            AlertDialog(
                onDismissRequest = {
                    showCreateFavoriteDialog = false
                    createFavoriteError = null
                },
                title = { Text("新建收藏夹") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newFavoriteName,
                            onValueChange = {
                                newFavoriteName = it
                                createFavoriteError = null
                            },
                            singleLine = true,
                            placeholder = { Text("例如：周末去处") },
                        )
                        createFavoriteError?.let {
                            Text(text = it, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val trimmed = newFavoriteName.trim()
                            when {
                                trimmed.isBlank() -> {
                                    createFavoriteError = "收藏夹名称不能为空"
                                }
                                favoriteFolders.any { it.name.equals(trimmed, ignoreCase = true) } -> {
                                    createFavoriteError = "收藏夹名称已存在"
                                }
                                else -> {
                                    val created = FavoriteFolder(
                                        id = "fav_${System.currentTimeMillis()}_${trimmed.hashCode()}",
                                        name = trimmed,
                                        places = emptyList(),
                                    )
                                    favoriteFolders = context.saveFavoriteFolders(favoriteFolders + created)
                                    selectedFavoriteFolderId = created.id
                                    showCreateFavoriteDialog = false
                                    createFavoriteError = null
                                    newFavoriteName = ""
                                }
                            }
                        },
                    ) {
                        Text("创建")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showCreateFavoriteDialog = false
                            createFavoriteError = null
                        },
                    ) {
                        Text("取消")
                    }
                },
            )
        }

        val editablePlace = selectedSearchResult
        if (showPlaceCollectionsDialog && editablePlace != null) {
            BackHandler { showPlaceCollectionsDialog = false }
            AlertDialog(
                onDismissRequest = { showPlaceCollectionsDialog = false },
                title = { Text("地点收藏") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = editablePlace.title,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        editablePlace.subtitle?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

                        Text(
                            text = "已选择 ${placeFolderDraftIds.size} 个收藏夹",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        if (favoriteFolders.isEmpty()) {
                            Text(
                                text = "暂无收藏夹，请先在列表页新建收藏夹",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(onClick = { placeFolderDraftIds = favoriteFolders.map { it.id }.toSet() }) {
                                    Text("全选")
                                }
                                TextButton(onClick = { placeFolderDraftIds = emptySet() }) {
                                    Text("清空")
                                }
                            }

                            favoriteFolders.forEach { folder ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            color = if (folder.id in placeFolderDraftIds) {
                                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                                            } else {
                                                Color.Transparent
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                        )
                                        .clickable {
                                            placeFolderDraftIds = if (folder.id in placeFolderDraftIds) {
                                                placeFolderDraftIds - folder.id
                                            } else {
                                                placeFolderDraftIds + folder.id
                                            }
                                        }
                                        .padding(vertical = 4.dp, horizontal = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = folder.id in placeFolderDraftIds,
                                        onCheckedChange = null,
                                    )
                                    Column(modifier = Modifier.padding(start = 2.dp)) {
                                        Text(folder.name, style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            text = "${folder.places.size} 个地点",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            favoriteFolders = context.setPlaceFolderMembership(
                                place = editablePlace,
                                targetFolderIds = placeFolderDraftIds,
                                current = favoriteFolders,
                            )

                            if (selectedFavoriteFolderId == null && placeFolderDraftIds.isNotEmpty()) {
                                selectedFavoriteFolderId = favoriteFolders
                                    .firstOrNull { it.id in placeFolderDraftIds }
                                    ?.id
                            }

                            refreshVisibleFavoriteMarkers()
                            showHint("收藏夹已更新", HintTone.Success)
                            showPlaceCollectionsDialog = false
                        },
                    ) {
                        Text("保存")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPlaceCollectionsDialog = false }) {
                        Text("取消")
                    }
                },
            )
        }

        pendingDeleteFavoriteFolder?.let { target ->
            AlertDialog(
                onDismissRequest = { pendingDeleteFavoriteFolder = null },
                title = { Text("删除收藏夹") },
                text = { Text("确定删除“${target.name}”吗？") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            favoriteFolders = context.deleteFavoriteFolder(target.id, favoriteFolders)
                            if (selectedFavoriteFolderId == target.id) {
                                selectedFavoriteFolderId = favoriteFolders.firstOrNull()?.id
                                val nextPlaces = favoriteFolders
                                    .firstOrNull { it.id == selectedFavoriteFolderId }
                                    ?.places
                                    .orEmpty()
                                mapLibreMap?.let { map ->
                                    favoriteMarkers = map.updateFavoriteMarkers(favoriteMarkers, nextPlaces)
                                }
                            }
                            pendingDeleteFavoriteFolder = null
                        },
                    ) {
                        Text("删除")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteFavoriteFolder = null }) {
                        Text("取消")
                    }
                },
            )
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

private fun MapLibreMap.focusOnPlaces(places: List<PhotonSearchResult>) {
    if (places.isEmpty()) return
    if (places.size == 1) {
        focusOnSearchResult(places.first().latLng)
        return
    }

    val boundsBuilder = LatLngBounds.Builder()
    places.forEach { boundsBuilder.include(it.latLng) }
    val bounds = boundsBuilder.build()
    animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120))
}

private fun MapLibreMap.updateUserMarker(currentMarker: Marker?, userLatLng: LatLng?, icon: MapLibreIcon): Marker? {
    if (userLatLng == null) {
        currentMarker?.let { removeMarker(it) }
        return null
    }

    if (
        currentMarker != null &&
            hasSameLatLng(currentMarker.position, userLatLng) &&
            currentMarker.title == "My location"
    ) {
        return currentMarker
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

    if (currentMarker != null && currentMarker.matches(searchResult)) {
        return currentMarker
    }

    currentMarker?.let { removeMarker(it) }
    return addMarker(
        MarkerOptions()
            .position(searchResult.latLng)
            .title(searchResult.title)
            .snippet(searchResult.subtitle ?: ""),
    )
}

private fun MapLibreMap.updateFavoriteMarkers(
    currentMarkers: List<Marker>,
    places: List<PhotonSearchResult>,
): List<Marker> {
    if (
        currentMarkers.size == places.size &&
            currentMarkers.zip(places).all { (marker, place) -> marker.matches(place) }
    ) {
        return currentMarkers
    }

    val reusableMarkersByKey = mutableMapOf<MarkerStableKey, ArrayDeque<Marker>>()
    currentMarkers.forEach { marker ->
        reusableMarkersByKey
            .getOrPut(marker.toStableKey()) { ArrayDeque() }
            .addLast(marker)
    }

    val nextMarkers = mutableListOf<Marker>()
    places.forEach { place ->
        val key = place.toStableKey()
        val reusedMarker = reusableMarkersByKey[key]?.removeFirstOrNull()
        if (reusedMarker != null) {
            nextMarkers += reusedMarker
        } else {
            nextMarkers += addMarker(
                MarkerOptions()
                    .position(place.latLng)
                    .title(place.title)
                    .snippet(place.subtitle ?: ""),
            )
        }
    }

    reusableMarkersByKey.values.forEach { staleMarkers ->
        staleMarkers.forEach { removeMarker(it) }
    }

    return nextMarkers
}

private data class MarkerStableKey(
    val latE6: Long,
    val lonE6: Long,
    val title: String,
    val subtitle: String,
)

private fun PhotonSearchResult.toStableKey(): MarkerStableKey {
    return MarkerStableKey(
        latE6 = (latLng.latitude * 1_000_000.0).roundToLong(),
        lonE6 = (latLng.longitude * 1_000_000.0).roundToLong(),
        title = title,
        subtitle = subtitle ?: "",
    )
}

private fun Marker.toStableKey(): MarkerStableKey {
    return MarkerStableKey(
        latE6 = (position.latitude * 1_000_000.0).roundToLong(),
        lonE6 = (position.longitude * 1_000_000.0).roundToLong(),
        title = title ?: "",
        subtitle = snippet ?: "",
    )
}

private fun Marker.matches(place: PhotonSearchResult): Boolean {
    return hasSameLatLng(position, place.latLng) &&
        title == place.title &&
        snippet.orEmpty() == (place.subtitle ?: "")
}

private fun Marker.toPhotonSearchResultOrNull(): PhotonSearchResult? {
    val markerTitle = title?.takeIf { it.isNotBlank() } ?: return null
    if (markerTitle == "My location") return null
    return PhotonSearchResult(
        title = markerTitle,
        subtitle = snippet?.takeIf { it.isNotBlank() },
        latLng = position,
    )
}

private fun hasSameLatLng(first: LatLng, second: LatLng, epsilon: Double = 1e-6): Boolean {
    return kotlin.math.abs(first.latitude - second.latitude) <= epsilon &&
        kotlin.math.abs(first.longitude - second.longitude) <= epsilon
}

private fun MapLibreMap.pickBaseMapPlace(clickedLatLng: LatLng): PhotonSearchResult? {
    // Limit click-to-select to mid/high zoom to avoid selecting broad region labels.
    if (cameraPosition.zoom < BASE_MAP_PICK_MIN_ZOOM) return null

    val clickPoint: PointF = projection.toScreenLocation(clickedLatLng)
    val pickRadiusPx = BASE_MAP_PICK_RADIUS_PX
    val clickRect = RectF(
        clickPoint.x - pickRadiusPx,
        clickPoint.y - pickRadiusPx,
        clickPoint.x + pickRadiusPx,
        clickPoint.y + pickRadiusPx,
    )
    val features = runCatching { queryRenderedFeatures(clickRect) }
        .getOrElse { runCatching { queryRenderedFeatures(clickPoint) }.getOrDefault(emptyList()) }

    return features
        .asSequence()
        .mapNotNull { feature ->
            val place = feature.toBaseMapPlaceOrNull(clickedLatLng) ?: return@mapNotNull null
            val score = feature.poiPickScore()
            place to score
        }
        .maxByOrNull { it.second }
        ?.first
}

private fun Feature.toBaseMapPlaceOrNull(fallbackLatLng: LatLng): PhotonSearchResult? {
    val name = bestDisplayName()

    val placeClass = getStringPropertySafely("class")?.trim().orEmpty()
    val placeType = getStringPropertySafely("type")?.trim().orEmpty()

    val normalizedClass = placeClass.lowercase(Locale.ROOT)
    val normalizedType = placeType.lowercase(Locale.ROOT)

    val hasNamedPlace = !name.isNullOrBlank()
    if (!hasNamedPlace) {
        return null
    }

    if (normalizedClass in NON_PLACE_FEATURE_CLASSES || normalizedType in NON_PLACE_FEATURE_TYPES) {
        return null
    }

    val title = name

    val subtitle = listOf(
        placeClass.takeIf { it.isNotBlank() }?.toPlaceTypeLabel(),
        placeType.takeIf { it.isNotBlank() }?.toPlaceTypeLabel(),
        getStringPropertySafely("street"),
        getStringPropertySafely("city"),
        getStringPropertySafely("state"),
        getStringPropertySafely("country"),
    ).filterNotNull()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(" · ")
        .ifBlank { null }

    return PhotonSearchResult(
        title = title,
        subtitle = subtitle,
        latLng = fallbackLatLng,
    )
}

private fun Feature.getStringPropertySafely(key: String): String? {
    if (!hasProperty(key)) return null
    return runCatching { getStringProperty(key) }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
}

private fun Feature.bestDisplayName(): String? {
    val directNameKeys = listOf(
        "name",
        "name:zh",
        "name:en",
        "name:es",
        "name_es",
        "int_name",
        "official_name",
        "short_name",
        "loc_name",
    )

    val directMatch = directNameKeys
        .asSequence()
        .mapNotNull { key -> getStringPropertySafely(key) }
        .firstOrNull { it.isNotBlank() }
    if (!directMatch.isNullOrBlank()) return directMatch

    // Some vector tiles expose only localized keys like name:xx.
    val dynamicLocalizedName = runCatching {
        properties()
            ?.entrySet()
            ?.asSequence()
            ?.firstOrNull { (key, value) ->
                key.startsWith("name:") && value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString
            }
            ?.value
            ?.asString
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()

    return dynamicLocalizedName
}

private fun Feature.hasAnyProperty(vararg keys: String): Boolean {
    return keys.any { key ->
        getStringPropertySafely(key)?.isNotBlank() == true
    }
}

private fun Feature.poiPickScore(): Int {
    val placeClass = getStringPropertySafely("class")?.trim()?.lowercase(Locale.ROOT).orEmpty()
    val placeType = getStringPropertySafely("type")?.trim()?.lowercase(Locale.ROOT).orEmpty()
    val hasName = !bestDisplayName().isNullOrBlank()

    var score = 0
    if (hasName) score += 120
    if (hasAnyProperty("maki", "category")) score += 120
    if (placeClass in POI_BUILDING_CLASSES) score += 70
    if (placeType in POI_BUILDING_TYPES) score += 50
    if (hasAnyProperty("street", "city", "state", "country")) score += 12
    return score
}

private fun String.toPlaceTypeLabel(): String {
    return replace('_', ' ')
        .replace('-', ' ')
        .trim()
        .replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
        }
}

private val NON_PLACE_FEATURE_CLASSES = setOf(
    "boundary",
    "landcover",
    "landuse",
    "mountain_peak",
    "path",
    "rail",
    "road",
    "transit",
    "water",
    "water_name",
)

private val NON_PLACE_FEATURE_TYPES = setOf(
    "archipelago",
    "city",
    "country",
    "county",
    "district",
    "hamlet",
    "locality",
    "neighbourhood",
    "province",
    "region",
    "state",
    "suburb",
    "town",
    "village",
)

private val POI_BUILDING_CLASSES = setOf(
    "aeroway",
    "amenity",
    "building",
    "historic",
    "leisure",
    "man_made",
    "office",
    "shop",
    "tourism",
)

private val POI_BUILDING_TYPES = setOf(
    "apartments",
    "attraction",
    "castle",
    "church",
    "commercial",
    "construction",
    "gallery",
    "government",
    "hospital",
    "hotel",
    "mall",
    "memorial",
    "monument",
    "museum",
    "office",
    "public",
    "residential",
    "retail",
    "school",
    "stadium",
    "station",
    "temple",
    "theatre",
    "tower",
    "university",
)

private const val BASE_MAP_PICK_MIN_ZOOM = 12.8
private const val BASE_MAP_PICK_RADIUS_PX = 20f

private suspend fun searchPhoton(
    query: String,
    limit: Int = 8,
    biasCenter: LatLng? = null,
    semanticTags: Set<SemanticTag> = emptySet(),
): List<PhotonSearchResult> = withContext(Dispatchers.IO) {
    val encodedQuery = URLEncoder.encode(query.ifBlank { "*" }, "UTF-8")
    val baseParams = mutableListOf(
        "q=$encodedQuery",
        "limit=$limit",
    )
    biasCenter?.let {
        baseParams += "lat=${it.latitude}"
        baseParams += "lon=${it.longitude}"
    }
    semanticTags.forEach { tag ->
        val encodedTag = URLEncoder.encode("${tag.key}:${tag.value}", "UTF-8")
        baseParams += "osm_tag=$encodedTag"
    }
    val requestUrl = URL("https://osm.zhijie.win/api/?${baseParams.joinToString("&")}")
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

private fun Context.loadFavoriteFolders(): List<FavoriteFolder> {
    val sharedPreferences = getSearchSharedPreferences()
    val raw = sharedPreferences.getString(FAVORITE_FOLDERS_KEY, null) ?: return emptyList()
    return runCatching {
        val jsonArray = JSONArray(raw)
        buildList {
            for (index in 0 until jsonArray.length()) {
                val folderObject = jsonArray.optJSONObject(index) ?: continue
                val id = folderObject.optString("id").ifBlank { continue }
                val name = folderObject.optString("name").ifBlank { continue }
                val placesArray = folderObject.optJSONArray("places") ?: JSONArray()
                val places = mutableListOf<PhotonSearchResult>()

                for (placeIndex in 0 until placesArray.length()) {
                    val item = placesArray.optJSONObject(placeIndex) ?: continue
                    val title = item.optString("title")
                    val subtitle = item.optString("subtitle").ifBlank { null }
                    val latitude = item.optDouble("latitude", Double.NaN)
                    val longitude = item.optDouble("longitude", Double.NaN)
                    if (!latitude.isFinite() || !longitude.isFinite() || title.isBlank()) continue

                    places += PhotonSearchResult(
                        title = title,
                        subtitle = subtitle,
                        latLng = LatLng(latitude, longitude),
                    )
                }

                add(
                    FavoriteFolder(
                        id = id,
                        name = name,
                        places = places,
                    ),
                )
            }
        }
    }.getOrDefault(emptyList())
}

private fun Context.saveFavoriteFolders(folders: List<FavoriteFolder>): List<FavoriteFolder> {
    val updated = folders.take(MAX_FAVORITE_FOLDERS)

    val jsonArray = JSONArray()
    updated.forEach { folder ->
        val placesArray = JSONArray()
        folder.places.take(MAX_FAVORITE_PLACES_PER_FOLDER).forEach { item ->
            placesArray.put(
                JSONObject().apply {
                    put("title", item.title)
                    put("subtitle", item.subtitle ?: "")
                    put("latitude", item.latLng.latitude)
                    put("longitude", item.latLng.longitude)
                },
            )
        }

        jsonArray.put(
            JSONObject().apply {
                put("id", folder.id)
                put("name", folder.name)
                put("places", placesArray)
            },
        )
    }

    getSearchSharedPreferences().edit().putString(FAVORITE_FOLDERS_KEY, jsonArray.toString()).apply()
    return updated
}


private fun List<FavoriteFolder>.folderIdsContainingPlace(place: PhotonSearchResult): Set<String> {
    val placeKey = place.toStableKey()
    return asSequence()
        .filter { folder -> folder.places.any { it.toStableKey() == placeKey } }
        .map { it.id }
        .toSet()
}

private fun Context.setPlaceFolderMembership(
    place: PhotonSearchResult,
    targetFolderIds: Set<String>,
    current: List<FavoriteFolder>,
): List<FavoriteFolder> {
    val placeKey = place.toStableKey()
    val updated = current.map { folder ->
        val containsPlace = folder.places.any { it.toStableKey() == placeKey }
        val shouldContainPlace = folder.id in targetFolderIds
        when {
            shouldContainPlace && !containsPlace -> {
                folder.copy(places = (folder.places + place).take(MAX_FAVORITE_PLACES_PER_FOLDER))
            }
            !shouldContainPlace && containsPlace -> {
                folder.copy(places = folder.places.filterNot { it.toStableKey() == placeKey })
            }
            else -> folder
        }
    }
    return saveFavoriteFolders(updated)
}

private fun Context.deleteFavoriteFolder(
    folderId: String,
    current: List<FavoriteFolder>,
): List<FavoriteFolder> {
    val updated = current.filterNot { it.id == folderId }
    return saveFavoriteFolders(updated)
}

private fun Context.getSearchSharedPreferences(): SharedPreferences {
    return getSharedPreferences(SEARCH_PREFS_NAME, Context.MODE_PRIVATE)
}

private const val SEARCH_PREFS_NAME = "map_search"
private const val FAVORITE_FOLDERS_KEY = "favorite_folders"
private const val MAX_FAVORITE_FOLDERS = 12
private const val MAX_FAVORITE_PLACES_PER_FOLDER = 100

private fun Context.createUserLocationBlueDotIcon(): MapLibreIcon {
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

private fun Context.copyToClipboard(label: String, text: String): Boolean {
    val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
    clipboardManager.setPrimaryClip(ClipData.newPlainText(label, text))
    return true
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

package com.zhijie.aura

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

const val DEFAULT_STYLE_URL = "https://osm.zhijie.win/styles/bright.json"

data class MapCamera(
    val latitude: Double = 39.9042,
    val longitude: Double = 116.4074,
    val zoom: Double = 11.0,
)

data class MapConfig(
    val styleUrl: String = DEFAULT_STYLE_URL,
    val camera: MapCamera = MapCamera(),
)

@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    config: MapConfig = MapConfig(),
) {
    PlatformMapView(
        modifier = modifier,
        config = config,
    )
}

@Composable
expect fun PlatformMapView(
    modifier: Modifier = Modifier,
    config: MapConfig,
)


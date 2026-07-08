package com.runconnect.app.ui.screens.activitydetail

import android.view.Gravity
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapInitOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.style
import com.mapbox.maps.extension.style.terrain.generated.terrain
import com.mapbox.maps.extension.style.sources.generated.rasterDemSource
import com.mapbox.maps.plugin.animation.camera
import com.runconnect.app.domain.model.RoutePoint
import com.runconnect.app.ui.theme.CardDark
import com.runconnect.app.ui.theme.TealPrimary
import com.runconnect.app.ui.theme.TextSecondary

@Composable
fun MapboxRouteView(
    routePoints: List<RoutePoint>,
    isLoading: Boolean = false,
    consentRequired: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    if (routePoints.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(CardDark),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isLoading -> CircularProgressIndicator(color = TealPrimary, modifier = Modifier.size(28.dp))
                consentRequired -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(4.dp))
                    Text("GPS route access needed", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent("android.health.connect.action.HEALTH_CONNECT_SETTINGS")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }) {
                        Text("Open Health Connect", style = MaterialTheme.typography.labelSmall, color = TealPrimary)
                    }
                }
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.LocationOff, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(6.dp))
                    Text("GPS route not in Health Connect", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Spacer(Modifier.height(2.dp))
                    Text("Garmin may not sync routes to HC", style = MaterialTheme.typography.labelSmall, color = TextSecondary.copy(alpha = 0.6f))
                }
            }
        }
        return
    }

    val coordinates = remember(routePoints) {
        routePoints.map { Point.fromLngLat(it.longitude, it.latitude, it.altitudeMeters ?: 0.0) }
    }
    val centerLat = routePoints.map { it.latitude }.average()
    val centerLon = routePoints.map { it.longitude }.average()

    val mapView = remember { MapView(context) }

    DisposableEffect(Unit) {
        onDispose {
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(16.dp)),
        update = { mv ->
            mv.mapboxMap.loadStyle(
                style(Style.DARK) {
                    // 3D terrain
                    +rasterDemSource("terrain-source") {
                        url("mapbox://mapbox.mapbox-terrain-dem-v1")
                        tileSize(512)
                        maxzoom(14L)
                    }
                    +terrain("terrain-source") {
                        exaggeration(1.5)
                    }
                    // Route source
                    +geoJsonSource("route-source") {
                        geometry(LineString.fromLngLats(coordinates))
                    }
                    // Route outline
                    +lineLayer("route-outline", "route-source") {
                        lineColor("#1A1E24")
                        lineWidth(6.0)
                        lineCap(LineCap.ROUND)
                        lineJoin(LineJoin.ROUND)
                    }
                    // Route line (teal)
                    +lineLayer("route-layer", "route-source") {
                        lineColor("#3DD9C5")
                        lineWidth(3.5)
                        lineCap(LineCap.ROUND)
                        lineJoin(LineJoin.ROUND)
                    }
                    // Start/end markers could be added as symbol layers here
                }
            ) {
                mv.mapboxMap.setCamera(
                    CameraOptions.Builder()
                        .center(Point.fromLngLat(centerLon, centerLat))
                        .zoom(13.5)
                        .pitch(50.0)
                        .bearing(0.0)
                        .build()
                )
            }
        }
    )
}

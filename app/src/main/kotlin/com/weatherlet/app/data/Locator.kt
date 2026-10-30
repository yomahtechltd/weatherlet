package com.weatherlet.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Approximate device location → a [Place] flagged `isCurrent`. Only runs while the app is in use. */
object Locator {
    fun hasPermission(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasBackgroundPermission(ctx: Context) =
        hasPermission(ctx) &&
            ctx.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Null if there's no permission, location is off, or no fix arrived in time. */
    suspend fun locate(ctx: Context): Place? {
        if (!hasPermission(ctx)) return null
        val loc = rawLocation(ctx) ?: return null
        return describe(ctx, loc.latitude, loc.longitude)
    }

    @SuppressLint("MissingPermission")
    private suspend fun rawLocation(ctx: Context): Location? {
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        val provider = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) } ?: return null
        val fresh = withTimeoutOrNull(8_000) {
            suspendCancellableCoroutine<Location?> { cont ->
                val cancel = CancellationSignal()
                cont.invokeOnCancellation { cancel.cancel() }
                try {
                    lm.getCurrentLocation(provider, cancel, ctx.mainExecutor) { cont.resume(it) }
                } catch (e: SecurityException) {
                    cont.resume(null)
                }
            }
        }
        return fresh ?: runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
    }

    /** Name the spot using the system geocoder; fall back to a generic label if it isn't available. */
    private suspend fun describe(ctx: Context, lat: Double, lon: Double): Place {
        val addr = runCatching { reverse(ctx, lat, lon) }.getOrNull()
        return Place(
            name = addr?.locality ?: addr?.subLocality ?: addr?.subAdminArea ?: addr?.adminArea ?: "Current location",
            admin = addr?.adminArea,
            country = addr?.countryName.orEmpty(),
            lat = lat,
            lon = lon,
            countryCode = addr?.countryCode,
            isCurrent = true,
            admin2 = addr?.subAdminArea,
        )
    }

    private suspend fun reverse(ctx: Context, lat: Double, lon: Double): Address? {
        if (!Geocoder.isPresent()) return null
        val g = Geocoder(ctx, Locale.getDefault())
        return withTimeoutOrNull(6_000) {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine<Address?> { cont ->
                    g.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) { cont.resume(addresses.firstOrNull()) }
                        override fun onError(errorMessage: String?) { cont.resume(null) }
                    })
                }
            } else withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION") g.getFromLocation(lat, lon, 1)?.firstOrNull()
            }
        }
    }

    fun distanceKm(a: Place, b: Place): Double {
        val r = 6371.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).let { it * it }
        return 2 * r * asin(sqrt(h))
    }
}

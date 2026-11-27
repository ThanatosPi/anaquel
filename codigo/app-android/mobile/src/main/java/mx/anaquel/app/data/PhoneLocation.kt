package mx.anaquel.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class Coordinates(val latitude: Double, val longitude: Double)

object PhoneLocation {
    fun permitted(context: Context): Boolean = ContextCompat.checkSelfPermission(context,
        Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun current(context: Context): Coordinates? {
        if (!permitted(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        if (!LocationManagerCompat.isLocationEnabled(manager)) return null
        // Sin permiso preciso ni seguimiento en segundo plano.
        val provider = listOf("fused", LocationManager.NETWORK_PROVIDER).firstOrNull {
            it in manager.allProviders && manager.isProviderEnabled(it)
        } ?: return null
        return withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { continuation ->
                val cancellation = CancellationSignal()
                continuation.invokeOnCancellation { cancellation.cancel() }
                try {
                    LocationManagerCompat.getCurrentLocation(manager, provider, cancellation,
                        ContextCompat.getMainExecutor(context)) { location ->
                        if (continuation.isActive) continuation.resume(location?.let { Coordinates(it.latitude, it.longitude) })
                    }
                } catch (_: SecurityException) { if (continuation.isActive) continuation.resume(null) }
                catch (_: IllegalArgumentException) { if (continuation.isActive) continuation.resume(null) }
            }
        }
    }
}

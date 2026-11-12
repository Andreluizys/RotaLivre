package br.com.andreluizs.rotalivre

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Um ponto no mapa e, quando o Android informa, a cidade dele. */
class Geo(val lat: Double, val lon: Double, val city: String?)

/**
 * Localização, busca de endereço (Geocoder do Android) e piso da rua (OpenStreetMap).
 * Tudo aqui pode demorar: chame sempre fora da thread principal.
 */
object GeoLookup {

    fun hasLocationPermission(c: Context) =
        ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Com o app em segundo plano (lendo a Uber/99), o Android só dá a localização com "Permitir o tempo todo". */
    fun hasBackgroundPermission(c: Context) =
        Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Posição mais recente que o celular conhece, ou nulo (sem permissão ou sem sinal). */
    @SuppressLint("MissingPermission")
    fun lastLocation(c: Context): Location? {
        if (!hasLocationPermission(c)) return null
        val lm = c.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { p -> try { lm.getLastKnownLocation(p) } catch (_: Exception) { null } }
            .maxByOrNull { it.time }
    }

    /**
     * Coordenadas e cidade de um endereço. Com [near], procura só numa caixa de ~35 km em volta do motorista
     * (evita pegar uma rua de mesmo nome em outro estado).
     */
    @Suppress("DEPRECATION")
    fun geocode(c: Context, address: String, near: Location?): Geo? {
        if (!Geocoder.isPresent()) return null
        return try {
            val g = Geocoder(c, Locale("pt", "BR"))
            val list = if (near != null)
                g.getFromLocationName(address, 1, near.latitude - 0.3, near.longitude - 0.3, near.latitude + 0.3, near.longitude + 0.3)
            else g.getFromLocationName(address, 1)
            val a = list?.firstOrNull() ?: return null
            Geo(a.latitude, a.longitude, a.locality ?: a.subAdminArea)
        } catch (_: Exception) {
            null
        }
    }

    /** Centro da cidade escolhida (guardado depois da 1ª busca para não repetir). */
    fun cityCenter(c: Context, city: String): Geo? {
        val sp = c.getSharedPreferences("geo", Context.MODE_PRIVATE)
        if (sp.getString("center_city", "") == city && sp.contains("center_lat"))
            return Geo(sp.getFloat("center_lat", 0f).toDouble(), sp.getFloat("center_lon", 0f).toDouble(), city)
        val g = geocode(c, "$city, Brasil", null) ?: return null
        sp.edit().putString("center_city", city).putFloat("center_lat", g.lat.toFloat()).putFloat("center_lon", g.lon.toFloat()).apply()
        return g
    }

    private val dirtSurfaces = setOf(
        "unpaved", "dirt", "ground", "earth", "mud", "sand", "grass", "gravel", "fine_gravel", "compacted", "dirt/sand"
    )

    /**
     * A rua [street] perto do ponto é de terra, segundo o OpenStreetMap?
     * Devolve nulo quando o mapa não sabe (rua sem o piso preenchido) ou sem internet.
     */
    fun isDirtRoad(lat: Double, lon: Double, street: String): Boolean? {
        if (street.isBlank()) return null
        return try {
            val query = "[out:json][timeout:5];way(around:40,$lat,$lon)[\"highway\"][\"name\"];out tags;"
            val conn = (URL("https://overpass-api.de/api/interpreter").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 4000
                readTimeout = 6000
                doOutput = true
                setRequestProperty("User-Agent", "RotaLivre-app")
            }
            conn.outputStream.use { it.write(("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray()) }
            if (conn.responseCode != 200) return null
            val elements = JSONObject(conn.inputStream.bufferedReader().readText()).getJSONArray("elements")
            val wanted = AddressGeo.norm(street)
            var known: Boolean? = null
            for (i in 0 until elements.length()) {
                val tags = elements.getJSONObject(i).optJSONObject("tags") ?: continue
                val name = AddressGeo.norm(tags.optString("name"))
                if (name.isEmpty() || !(name == wanted || wanted.endsWith(name) || name.endsWith(wanted))) continue
                val surface = tags.optString("surface")
                val track = tags.optString("highway") == "track"
                if (surface.isNotEmpty() || track) {
                    val dirt = track || surface in dirtSurfaces
                    if (dirt) return true
                    known = false
                }
            }
            known
        } catch (_: Exception) {
            null
        }
    }
}

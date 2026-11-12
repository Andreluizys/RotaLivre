package br.com.andreluizs.rotalivre

import android.content.Context

/** Uma corrida aceita, guardada para o histórico. */
data class RideRecord(
    val time: Long,
    val pkg: String,
    val pickupKm: Double,
    val tripKm: Double
) {
    val totalKm get() = pickupKm + tripKm
    val appName get() = when (pkg) {
        "com.ubercab.driver" -> "Uber"
        "com.app99.driver" -> "99"
        "br.com.ifood.driver.app" -> "iFood"
        "com.loggi.driverapp" -> "Loggi"
        "com.shopee.spx.driver.brazil" -> "Shopee"
        else -> "App"
    }
}

/** Histórico das corridas aceitas (só aceitas), uma linha por corrida: hora|app|até o passageiro|viagem. */
object RideHistory {
    private const val MAX = 1000

    fun encode(r: RideRecord) = "${r.time}|${r.pkg}|${r.pickupKm}|${r.tripKm}"

    fun decode(line: String): RideRecord? {
        val p = line.split('|')
        if (p.size != 4) return null
        return RideRecord(
            p[0].toLongOrNull() ?: return null,
            p[1],
            p[2].toDoubleOrNull() ?: return null,
            p[3].toDoubleOrNull() ?: return null
        )
    }

    private fun sp(c: Context) = c.getSharedPreferences("history", Context.MODE_PRIVATE)

    fun add(c: Context, pkg: String, offer: Offer, now: Long = System.currentTimeMillis()) {
        val p = sp(c)
        val lines = (p.getString("rides", "") ?: "").lines().filter { it.isNotBlank() }
        val novo = lines + encode(RideRecord(now, pkg, offer.pickupKm, offer.tripKm))
        p.edit().putString("rides", novo.takeLast(MAX).joinToString("\n")).apply()
    }

    /** Da mais recente para a mais antiga. */
    fun all(c: Context): List<RideRecord> =
        (sp(c).getString("rides", "") ?: "").lines().mapNotNull(::decode).sortedByDescending { it.time }
}

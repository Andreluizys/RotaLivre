package br.com.andreluizs.rotalivre

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Stats {
    private fun today() = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    private fun sp(c: Context) = c.getSharedPreferences("stats", Context.MODE_PRIVATE)

    fun add(c: Context, perKm: Double) {
        val d = today()
        val p = sp(c)
        p.edit()
            .putInt("count_$d", p.getInt("count_$d", 0) + 1)
            .putFloat("sum_$d", p.getFloat("sum_$d", 0f) + perKm.toFloat())
            .apply()
    }

    /** Corrida aceita: soma os km (até o passageiro + viagem) e o valor do dia. */
    fun addAccepted(c: Context, totalKm: Double, price: Double) {
        val d = today()
        val p = sp(c)
        p.edit()
            .putInt("acc_count_$d", p.getInt("acc_count_$d", 0) + 1)
            .putFloat("acc_km_$d", p.getFloat("acc_km_$d", 0f) + totalKm.toFloat())
            .putFloat("acc_price_$d", p.getFloat("acc_price_$d", 0f) + price.toFloat())
            .apply()
    }

    fun acceptedCount(c: Context): Int = sp(c).getInt("acc_count_${today()}", 0)
    fun acceptedKm(c: Context): Float = sp(c).getFloat("acc_km_${today()}", 0f)

    fun count(c: Context): Int = sp(c).getInt("count_${today()}", 0)

    fun average(c: Context): Float {
        val n = count(c)
        return if (n > 0) sp(c).getFloat("sum_${today()}", 0f) / n else 0f
    }

    fun summary(c: Context): String {
        val d = today()
        val n = sp(c).getInt("count_$d", 0)
        val s = sp(c).getFloat("sum_$d", 0f)
        val avg = if (n > 0) s / n else 0f
        return "Hoje: $n ofertas • média R$ %.2f/km\nAceitas: %d • %.1f km".format(avg, acceptedCount(c), acceptedKm(c))
    }
}

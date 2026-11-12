package br.com.andreluizs.rotalivre

import android.content.Context
import java.util.Calendar

object Prefs {
    val DAYS = listOf("Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb")
    val FUELS = listOf("Gasolina", "Etanol", "GNV")

    private fun sp(c: Context) = c.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    // ---- Mínimo R$/km por dia da semana (0 = Dom ... 6 = Sáb) ----
    // Se ainda não há valor por dia, aproveita o da antiga faixa 0-6h (migração).
    fun getMinDay(c: Context, day: Int): Float =
        sp(c).getFloat("minday_$day", sp(c).getFloat("min_${day}_0", 2.0f))

    fun setMinDay(c: Context, day: Int, v: Float) =
        sp(c).edit().putFloat("minday_$day", v).apply()

    // ---- Pico ----
    fun isPicoOn(c: Context) = sp(c).getBoolean("pico_on", false)
    fun setPicoOn(c: Context, v: Boolean) = sp(c).edit().putBoolean("pico_on", v).apply()

    fun getPicoMin(c: Context) = sp(c).getFloat("pico_min", 3.0f)
    fun setPicoMin(c: Context, v: Float) = sp(c).edit().putFloat("pico_min", v).apply()

    // ---- Distância máxima até o passageiro ----
    fun getMaxPickup(c: Context) = sp(c).getFloat("max_pickup", 4.0f)
    fun setMaxPickup(c: Context, v: Float) = sp(c).edit().putFloat("max_pickup", v).apply()

    // ---- Apps de delivery (iFood, Loggi, Shopee...) ----
    fun isDeliveryOn(c: Context) = sp(c).getBoolean("delivery_on", true)
    fun setDeliveryOn(c: Context, v: Boolean) = sp(c).edit().putBoolean("delivery_on", v).apply()

    fun getDeliveryMin(c: Context) = sp(c).getFloat("delivery_min", 1.5f)
    fun setDeliveryMin(c: Context, v: Float) = sp(c).edit().putFloat("delivery_min", v).apply()

    fun getDeliveryMaxPickup(c: Context) = sp(c).getFloat("delivery_max_pickup", 3.0f)
    fun setDeliveryMaxPickup(c: Context, v: Float) = sp(c).edit().putFloat("delivery_max_pickup", v).apply()

    // ---- Custos (opcionais) ----
    fun getFuelType(c: Context) = sp(c).getInt("fuel_type", 0)
    fun setFuelType(c: Context, v: Int) = sp(c).edit().putInt("fuel_type", v).apply()

    /** Preço do combustível (R$ por litro, ou R$ por m³ no GNV). 0 = não informado. */
    fun getFuelPrice(c: Context) = sp(c).getFloat("fuel_price", 0f)
    fun setFuelPrice(c: Context, v: Float) = sp(c).edit().putFloat("fuel_price", v).apply()

    /** Consumo (km por litro, ou km por m³ no GNV). 0 = não informado. */
    fun getKmPerUnit(c: Context) = sp(c).getFloat("km_per_unit", 0f)
    fun setKmPerUnit(c: Context, v: Float) = sp(c).edit().putFloat("km_per_unit", v).apply()

    /** Manutenção opcional em R$ por km (pneu, óleo etc.). */
    fun getMaintPerKm(c: Context) = sp(c).getFloat("maint_km", 0f)
    fun setMaintPerKm(c: Context, v: Float) = sp(c).edit().putFloat("maint_km", v).apply()

    /** Custo total por km (combustível + manutenção). 0 se nada informado. */
    fun costPerKm(c: Context): Double {
        val price = getFuelPrice(c)
        val kmUnit = getKmPerUnit(c)
        val fuel = if (price > 0f && kmUnit > 0f) price / kmUnit else 0f
        return (fuel + getMaintPerKm(c)).toDouble()
    }

    fun hasCosts(c: Context) = costPerKm(c) > 0.0

    /** Tamanho da janela do quadro (1.0 = normal), escolhido pelo motorista. */
    fun getOverlayScale(c: Context) = sp(c).getFloat("overlay_scale", 1.2f).coerceIn(0.8f, 1.8f)
    fun setOverlayScale(c: Context, v: Float) = sp(c).edit().putFloat("overlay_scale", v).apply()

    // ---- O que mostrar no quadro da corrida ----
    fun showPickup(c: Context) = sp(c).getBoolean("show_pickup", true)
    fun setShowPickup(c: Context, v: Boolean) = sp(c).edit().putBoolean("show_pickup", v).apply()

    fun showPerKm(c: Context) = sp(c).getBoolean("show_perkm", true)
    fun setShowPerKm(c: Context, v: Boolean) = sp(c).edit().putBoolean("show_perkm", v).apply()

    fun showPerHour(c: Context) = sp(c).getBoolean("show_perhour", true)
    fun setShowPerHour(c: Context, v: Boolean) = sp(c).edit().putBoolean("show_perhour", v).apply()

    fun showProfit(c: Context) = sp(c).getBoolean("show_profit", true)
    fun setShowProfit(c: Context, v: Boolean) = sp(c).edit().putBoolean("show_profit", v).apply()

    fun showSummary(c: Context) = sp(c).getBoolean("show_summary", true)
    fun setShowSummary(c: Context, v: Boolean) = sp(c).edit().putBoolean("show_summary", v).apply()

    // ---- Avisos de endereço (tarjas brancas no quadro) ----
    /** Cidade onde o motorista trabalha. Endereço de outra cidade ganha a tarja "fora dos limites". */
    fun getHomeCity(c: Context) = sp(c).getString("home_city", "São José dos Campos")?.ifBlank { "São José dos Campos" } ?: "São José dos Campos"
    fun setHomeCity(c: Context, v: String) = sp(c).edit().putString("home_city", v.trim()).apply()

    fun warnOutOfCity(c: Context) = sp(c).getBoolean("warn_out_of_city", false)
    fun setWarnOutOfCity(c: Context, v: Boolean) = sp(c).edit().putBoolean("warn_out_of_city", v).apply()

    fun warnDirtRoad(c: Context) = sp(c).getBoolean("warn_dirt_road", false)
    fun setWarnDirtRoad(c: Context, v: Boolean) = sp(c).edit().putBoolean("warn_dirt_road", v).apply()

    /** Valor mínimo por km para hoje (dia da semana). */
    fun currentMin(c: Context): Float {
        val day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1   // 0 = Dom ... 6 = Sáb
        return getMinDay(c, day)
    }
}

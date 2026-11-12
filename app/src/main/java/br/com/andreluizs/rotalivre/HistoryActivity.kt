package br.com.andreluizs.rotalivre

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Histórico das corridas aceitas, agrupado por dia, com o total de km de cada dia. */
class HistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Histórico de corridas"

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }
        val root = ScrollView(this).apply {
            setBackgroundColor(ContextCompat.getColor(this@HistoryActivity, R.color.app_bg))
            addView(list)
        }
        setContentView(root)

        list.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "‹ Voltar"
            setOnClickListener { finish() }
        })
        list.addView(text("Histórico de corridas", 24f, R.color.on_surface, bold = true, top = 12))
        list.addView(text("Só as corridas que você aceitou.", 14f, R.color.on_surface_muted, top = 2))

        val rides = RideHistory.all(this)
        if (rides.isEmpty()) {
            list.addView(
                text(
                    "Nenhuma corrida aceita ainda.\nAs corridas aparecem aqui assim que você tocar em Aceitar.",
                    16f, R.color.on_surface_muted, top = 24
                )
            )
            return
        }

        val dayFmt = SimpleDateFormat("EEEE, dd/MM/yyyy", Locale("pt", "BR"))
        val hourFmt = SimpleDateFormat("HH:mm", Locale("pt", "BR"))
        rides.groupBy { dayFmt.format(Date(it.time)) }.forEach { (day, dayRides) ->
            val total = dayRides.sumOf { it.totalKm }
            list.addView(text(day.replaceFirstChar { it.uppercase() }, 17f, R.color.brand, bold = true, top = 22))
            list.addView(
                text("%d corridas  •  %.1f km no dia".format(dayRides.size, total), 14f, R.color.on_surface_muted, top = 2)
            )
            dayRides.forEach { r ->
                list.addView(
                    rideCard(
                        "${hourFmt.format(Date(r.time))}  •  ${r.appName}",
                        "Até o passageiro: %.1f km\nViagem: %.1f km".format(r.pickupKm, r.tripKm),
                        "Total %.1f km".format(r.totalKm)
                    )
                )
            }
        }
    }

    private fun rideCard(head: String, body: String, total: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(ContextCompat.getColor(this@HistoryActivity, R.color.surface))
                setStroke(dp(1), ContextCompat.getColor(this@HistoryActivity, R.color.outline))
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(8) }
            addView(text(head, 16f, R.color.on_surface, bold = true))
            addView(text(body, 15f, R.color.on_surface_muted, top = 4))
            addView(text(total, 18f, R.color.brand, bold = true, top = 6))
        }

    private fun text(s: String, sp: Float, color: Int, bold: Boolean = false, top: Int = 0) =
        TextView(this).apply {
            text = s
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(ContextCompat.getColor(this@HistoryActivity, color))
            if (bold) setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(top) }
        }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

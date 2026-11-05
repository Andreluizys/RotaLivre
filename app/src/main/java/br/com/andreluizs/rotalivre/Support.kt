package br.com.andreluizs.rotalivre

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/** Apoio opcional ao desenvolvedor via Pix. */
object Support {
    const val PIX_KEY = "ab904fa6-ee2b-4009-8965-c87a3696fd38"
    const val PIX_COPIA_E_COLA =
        "00020126580014BR.GOV.BCB.PIX0136ab904fa6-ee2b-4009-8965-c87a3696fd385204000053039865802BR592551147560 ANDRE LUIZ SOARE6015SAO JOSE DOS CA62070503***63043180"

    private const val DAY = 24L * 60 * 60 * 1000
    private const val FIRST_DELAY = 2 * DAY   // aparece após 2 dias de uso
    private const val SNOOZE = 10 * DAY       // "Agora não" → volta em 10 dias

    private fun sp(c: Context) = c.getSharedPreferences("support", Context.MODE_PRIVATE)

    /** Registra o primeiro uso e diz se a janelinha deve aparecer agora. */
    fun shouldPrompt(c: Context): Boolean {
        val p = sp(c)
        if (p.getBoolean("donated", false)) return false
        val now = System.currentTimeMillis()
        if (!p.contains("next_prompt")) {
            p.edit().putLong("next_prompt", now + FIRST_DELAY).apply()
            return false
        }
        return now >= p.getLong("next_prompt", Long.MAX_VALUE)
    }

    private fun snooze(c: Context) =
        sp(c).edit().putLong("next_prompt", System.currentTimeMillis() + SNOOZE).apply()

    private fun markDonated(c: Context) =
        sp(c).edit().putBoolean("donated", true).apply()

    fun copy(c: Context, text: String, msg: String) {
        val cm = c.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Pix", text))
        Toast.makeText(c, msg, Toast.LENGTH_SHORT).show()
    }

    fun copyCode(c: Context) = copy(c, PIX_COPIA_E_COLA, "Pix copia e cola copiado! Cole no app do seu banco.")
    fun copyKey(c: Context) = copy(c, PIX_KEY, "Chave Pix copiada!")

    fun qrView(c: Context) = ImageView(c).apply {
        setImageResource(R.drawable.pix_qr)
        adjustViewBounds = true
        val size = (220 * c.resources.displayMetrics.density).toInt()
        layoutParams = LinearLayout.LayoutParams(size, size).apply { gravity = Gravity.CENTER_HORIZONTAL }
    }

    /** Janelinha de apoio. */
    fun showDialog(c: Context, fromPrompt: Boolean) {
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
            addView(TextView(c).apply {
                text = "O Rota Livre é gratuito e continua gratuito. Se ele está te ajudando a ganhar mais, você pode apoiar o desenvolvedor com um Pix de qualquer valor. É totalmente opcional."
                textSize = 15f
                setPadding(0, 0, 0, 24)
            })
            addView(qrView(c))
            addView(TextView(c).apply {
                text = "Leia o QR Code no app do banco ou toque em \"Copiar Pix\"."
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(0, 16, 0, 0)
            })
        }

        val b = AlertDialog.Builder(c)
            .setTitle("Curtindo o Rota Livre? 💚")
            .setView(box)
            .setNeutralButton("Copiar Pix") { _, _ -> copyCode(c) }

        if (fromPrompt) {
            b.setNegativeButton("Agora não") { _, _ -> snooze(c) }
            b.setPositiveButton("Já apoiei") { _, _ ->
                markDonated(c)
                Toast.makeText(c, "Valeu demais pelo apoio! 🙏", Toast.LENGTH_SHORT).show()
            }
            b.setOnCancelListener { snooze(c) }
        } else {
            b.setPositiveButton("Fechar", null)
        }
        b.show()
    }
}

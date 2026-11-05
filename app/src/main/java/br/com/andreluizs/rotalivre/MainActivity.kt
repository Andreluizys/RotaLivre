package br.com.andreluizs.rotalivre

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val fields = Array(7) { arrayOfNulls<EditText>(4) }
    private lateinit var summary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ctx = this

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        root.addView(Button(this).apply {
            text = "Ativar leitura (Acessibilidade)"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })

        summary = TextView(this).apply { textSize = 16f; setPadding(0, 16, 0, 16) }
        root.addView(summary)

        // ---------- Pico ----------
        root.addView(section("Pico"))
        root.addView(Switch(this).apply {
            text = "Modo PICO (lê dinâmico/pico na oferta)"
            isChecked = Prefs.isPicoOn(ctx)
            setOnCheckedChangeListener { _, c -> Prefs.setPicoOn(ctx, c) }
        })
        val picoMin = numField(Prefs.getPicoMin(this))
        root.addView(label("Mínimo R$/km no pico:"))
        root.addView(picoMin)

        // ---------- Passageiro ----------
        root.addView(section("Passageiro"))
        val maxPickup = numField(Prefs.getMaxPickup(this))
        root.addView(label("Distância máx. até o passageiro (km):"))
        root.addView(maxPickup)

        // ---------- Mínimos por dia/horário ----------
        root.addView(section("Mínimo R$/km por dia e horário"))
        val table = TableLayout(this).apply { isStretchAllColumns = true }
        table.addView(TableRow(this).apply {
            addView(TextView(ctx))
            Prefs.PERIODS.forEach { p ->
                addView(TextView(ctx).apply { text = p; textSize = 12f; gravity = Gravity.CENTER })
            }
        })
        for (d in 0..6) {
            val row = TableRow(this)
            row.addView(TextView(this).apply { text = Prefs.DAYS[d] })
            for (p in 0..3) {
                val f = numField(Prefs.getMin(this, d, p))
                fields[d][p] = f
                row.addView(f)
            }
            table.addView(row)
        }
        root.addView(table)

        // ---------- Custos (opcional) ----------
        root.addView(section("Custos do carro (opcional)"))
        root.addView(TextView(this).apply {
            text = "Preencha para ver o lucro de cada corrida. Deixe 0 para não usar."
            textSize = 12f
        })
        root.addView(label("Combustível:"))
        val fuelSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, Prefs.FUELS)
            setSelection(Prefs.getFuelType(ctx))
        }
        root.addView(fuelSpinner)
        val fuelPrice = numField(Prefs.getFuelPrice(this))
        root.addView(label("Preço (R$ por litro, ou por m³ no GNV):"))
        root.addView(fuelPrice)
        val kmUnit = numField(Prefs.getKmPerUnit(this))
        root.addView(label("Consumo (km por litro, ou por m³ no GNV):"))
        root.addView(kmUnit)
        val maint = numField(Prefs.getMaintPerKm(this))
        root.addView(label("Manutenção (R$ por km — pneu, óleo etc.):"))
        root.addView(maint)

        // ---------- O que mostrar no quadro ----------
        root.addView(section("O que mostrar no quadro da corrida"))
        val cbPickup = check("Distância até o passageiro", Prefs.showPickup(this))
        val cbPerKm = check("Valor por km", Prefs.showPerKm(this))
        val cbPerHour = check("Valor por hora", Prefs.showPerHour(this))
        val cbProfit = check("Lucro (descontando combustível/manutenção)", Prefs.showProfit(this))
        val cbSummary = check("Resumo do dia", Prefs.showSummary(this))
        listOf(cbPickup, cbPerKm, cbPerHour, cbProfit, cbSummary).forEach(root::addView)

        // ---------- Salvar ----------
        root.addView(Button(this).apply {
            text = "Salvar"
            setOnClickListener {
                Prefs.setPicoMin(ctx, picoMin.value())
                Prefs.setMaxPickup(ctx, maxPickup.value())
                for (d in 0..6) for (p in 0..3)
                    Prefs.setMin(ctx, d, p, fields[d][p]!!.value())
                Prefs.setFuelType(ctx, fuelSpinner.selectedItemPosition)
                Prefs.setFuelPrice(ctx, fuelPrice.value())
                Prefs.setKmPerUnit(ctx, kmUnit.value())
                Prefs.setMaintPerKm(ctx, maint.value())
                Prefs.setShowPickup(ctx, cbPickup.isChecked)
                Prefs.setShowPerKm(ctx, cbPerKm.isChecked)
                Prefs.setShowPerHour(ctx, cbPerHour.isChecked)
                Prefs.setShowProfit(ctx, cbProfit.isChecked)
                Prefs.setShowSummary(ctx, cbSummary.isChecked)
                val custo = Prefs.costPerKm(ctx)
                val msg = if (custo > 0) "Salvo! Custo: R$ %.2f/km".format(custo) else "Salvo!"
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
            }
        })

        // ---------- Apoie o desenvolvedor (opcional) ----------
        root.addView(section("Apoie o desenvolvedor (opcional)"))
        root.addView(TextView(this).apply {
            text = "O app é e continua gratuito. Se quiser ajudar, faça um Pix de qualquer valor."
            textSize = 13f
        })
        root.addView(Support.qrView(this).also {
            (it.layoutParams as LinearLayout.LayoutParams).topMargin = 24
        })
        root.addView(Button(this).apply {
            text = "Copiar Pix copia e cola"
            setOnClickListener { Support.copyCode(ctx) }
        })
        root.addView(Button(this).apply {
            text = "Copiar chave Pix"
            setOnClickListener { Support.copyKey(ctx) }
        })

        // ---------- Sobre ----------
        root.addView(TextView(this).apply {
            text = "Rota Livre • App gratuito, distribuição livre.\nCriado por André Luiz — andreluizs.com.br\nNão aceita nem recusa corridas: só mostra as informações."
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 48, 0, 16)
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://andreluizs.com.br")))
            }
        })

        setContentView(ScrollView(this).apply { addView(root) })

        if (Support.shouldPrompt(this)) Support.showDialog(this, fromPrompt = true)
    }

    override fun onResume() {
        super.onResume()
        summary.text = Stats.summary(this)
    }

    private fun section(t: String) = TextView(this).apply {
        text = t; textSize = 17f; setPadding(0, 40, 0, 8)
        setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun label(t: String) = TextView(this).apply { text = t; setPadding(0, 16, 0, 4) }

    private fun check(t: String, v: Boolean) = CheckBox(this).apply { text = t; isChecked = v }

    private fun numField(v: Float) = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        setText(String.format(Locale.US, "%.2f", v))
        gravity = Gravity.CENTER
    }

    private fun EditText.value() = text.toString().replace(",", ".").toFloatOrNull() ?: 0f
}

package br.com.andreluizs.rotalivre

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    /** Mínimos em edição: [dia][faixa]. Só vão para o Prefs ao tocar em Salvar. */
    private val mins = Array(7) { FloatArray(4) }
    private var currentDay = 0
    private lateinit var periodFields: List<EditText>

    private lateinit var dot: View
    private lateinit var tvStatusTitle: TextView
    private lateinit var tvStatusSub: TextView
    private lateinit var btnAccess: MaterialButton
    private lateinit var tvCount: TextView
    private lateinit var tvAvg: TextView
    private lateinit var tvCostKm: TextView
    private lateinit var etFuelPrice: EditText
    private lateinit var etKmUnit: EditText
    private lateinit var etMaint: EditText
    private lateinit var ddFuel: MaterialAutoCompleteTextView
    private var fuelIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindStatus()
        val swPico = find<MaterialSwitch>(R.id.swPicoOn).apply { isChecked = Prefs.isPicoOn(this@MainActivity) }
        val etPicoMin = field(R.id.etPicoMin, Prefs.getPicoMin(this))
        val etMaxPickup = field(R.id.etMaxPickup, Prefs.getMaxPickup(this))
        val swDelivery = find<MaterialSwitch>(R.id.swDelivery).apply { isChecked = Prefs.isDeliveryOn(this@MainActivity) }
        val etDeliveryMin = field(R.id.etDeliveryMin, Prefs.getDeliveryMin(this))
        val etDeliveryMaxPickup = field(R.id.etDeliveryMaxPickup, Prefs.getDeliveryMaxPickup(this))
        bindMinimums()
        bindCosts()

        val swPickup = find<MaterialSwitch>(R.id.swPickup).apply { isChecked = Prefs.showPickup(this@MainActivity) }
        val swPerKm = find<MaterialSwitch>(R.id.swPerKm).apply { isChecked = Prefs.showPerKm(this@MainActivity) }
        val swPerHour = find<MaterialSwitch>(R.id.swPerHour).apply { isChecked = Prefs.showPerHour(this@MainActivity) }
        val swProfit = find<MaterialSwitch>(R.id.swProfit).apply { isChecked = Prefs.showProfit(this@MainActivity) }
        val swSummary = find<MaterialSwitch>(R.id.swSummary).apply { isChecked = Prefs.showSummary(this@MainActivity) }

        val sizeChips = listOf(R.id.chipSize0, R.id.chipSize1, R.id.chipSize2).map { find<Chip>(it) }
        sizeChips[Prefs.getOverlaySize(this).coerceIn(0, 2)].isChecked = true

        bindSupport()

        find<ExtendedFloatingActionButton>(R.id.fabSave).setOnClickListener {
            storeDay()
            Prefs.setPicoOn(this, swPico.isChecked)
            Prefs.setPicoMin(this, etPicoMin.value())
            Prefs.setMaxPickup(this, etMaxPickup.value())
            Prefs.setDeliveryOn(this, swDelivery.isChecked)
            Prefs.setDeliveryMin(this, etDeliveryMin.value())
            Prefs.setDeliveryMaxPickup(this, etDeliveryMaxPickup.value())
            for (d in 0..6) for (p in 0..3) Prefs.setMin(this, d, p, mins[d][p])
            Prefs.setFuelType(this, fuelIndex)
            Prefs.setFuelPrice(this, etFuelPrice.value())
            Prefs.setKmPerUnit(this, etKmUnit.value())
            Prefs.setMaintPerKm(this, etMaint.value())
            Prefs.setOverlaySize(this, sizeChips.indexOfFirst { it.isChecked }.coerceAtLeast(0))
            Prefs.setShowPickup(this, swPickup.isChecked)
            Prefs.setShowPerKm(this, swPerKm.isChecked)
            Prefs.setShowPerHour(this, swPerHour.isChecked)
            Prefs.setShowProfit(this, swProfit.isChecked)
            Prefs.setShowSummary(this, swSummary.isChecked)
            val custo = Prefs.costPerKm(this)
            Toast.makeText(
                this,
                if (custo > 0) "Salvo! Custo: R$ %.2f/km".format(custo) else "Salvo!",
                Toast.LENGTH_SHORT
            ).show()
        }

        if (Support.shouldPrompt(this)) Support.showDialog(this, fromPrompt = true)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    // ---------- Status da leitura + resumo do dia ----------

    private fun bindStatus() {
        dot = find(R.id.dot)
        tvStatusTitle = find(R.id.tvStatusTitle)
        tvStatusSub = find(R.id.tvStatusSub)
        btnAccess = find(R.id.btnAccess)
        tvCount = find(R.id.tvCount)
        tvAvg = find(R.id.tvAvg)
        btnAccess.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        find<MaterialButton>(R.id.btnTest).setOnClickListener {
            val svc = RideService.instance
            if (svc == null) {
                Toast.makeText(this, "Leitura inativa: ligue o Rota Livre na Acessibilidade (desligue e ligue de novo)", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } else svc.demo()
        }
    }

    private fun refreshStatus() {
        val on = isServiceEnabled()
        dot.background.mutate().setTint(ContextCompat.getColor(this, if (on) R.color.good else R.color.bad))
        tvStatusTitle.text = if (on) "Leitura ativa" else "Leitura desativada"
        tvStatusSub.text = if (on) "Abra a Uber ou a 99 e fique online" else "Ligue o Rota Livre na Acessibilidade"
        btnAccess.text = if (on) "Ajustes" else "Ativar"
        tvCount.text = Stats.count(this).toString()
        tvAvg.text = "R$ %.2f".format(Stats.average(this))
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val me = "$packageName/${RideService::class.java.name}"
        return enabled.split(':').any { it.equals(me, ignoreCase = true) }
    }

    // ---------- Mínimo por dia e faixa ----------

    private fun bindMinimums() {
        for (d in 0..6) for (p in 0..3) mins[d][p] = Prefs.getMin(this, d, p)
        periodFields = listOf(R.id.etP0, R.id.etP1, R.id.etP2, R.id.etP3).map { find<EditText>(it) }

        val group = find<ChipGroup>(R.id.chipDays)
        val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1
        val chips = Prefs.DAYS.mapIndexed { i, name ->
            Chip(this, null, com.google.android.material.R.attr.chipStyle).apply {
                id = View.generateViewId()
                text = name
                isCheckable = true
                tag = i
                group.addView(this)
            }
        }
        group.setOnCheckedStateChangeListener { _, ids ->
            val chip = chips.firstOrNull { it.id == ids.firstOrNull() } ?: return@setOnCheckedStateChangeListener
            storeDay()
            currentDay = chip.tag as Int
            loadDay()
        }
        currentDay = today
        chips[today].isChecked = true
        loadDay()

        find<MaterialButton>(R.id.btnApplyAll).setOnClickListener {
            storeDay()
            for (d in 0..6) mins[currentDay].copyInto(mins[d])
            Toast.makeText(this, "Aplicado a todos os dias (toque em Salvar)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun storeDay() {
        periodFields.forEachIndexed { p, f -> mins[currentDay][p] = f.value() }
    }

    private fun loadDay() {
        periodFields.forEachIndexed { p, f -> f.setText(fmt(mins[currentDay][p])) }
    }

    // ---------- Custos ----------

    private fun bindCosts() {
        fuelIndex = Prefs.getFuelType(this)
        ddFuel = find(R.id.ddFuel)
        ddFuel.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, Prefs.FUELS))
        ddFuel.setText(Prefs.FUELS[fuelIndex], false)
        ddFuel.setOnItemClickListener { _, _, pos, _ -> fuelIndex = pos; updateCostLabels() }

        etFuelPrice = field(R.id.etFuelPrice, Prefs.getFuelPrice(this))
        etKmUnit = field(R.id.etKmUnit, Prefs.getKmPerUnit(this))
        etMaint = field(R.id.etMaint, Prefs.getMaintPerKm(this))
        tvCostKm = find(R.id.tvCostKm)

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = updateCostPreview()
        }
        listOf(etFuelPrice, etKmUnit, etMaint).forEach { it.addTextChangedListener(watcher) }
        updateCostLabels()
        updateCostPreview()
    }

    private fun updateCostLabels() {
        val gnv = fuelIndex == 2
        (etFuelPrice.parent.parent as com.google.android.material.textfield.TextInputLayout)
            .suffixText = if (gnv) "R$/m³" else "R$/L"
        (etKmUnit.parent.parent as com.google.android.material.textfield.TextInputLayout)
            .suffixText = if (gnv) "km/m³" else "km/L"
    }

    private fun updateCostPreview() {
        val price = etFuelPrice.value()
        val kmUnit = etKmUnit.value()
        val fuel = if (price > 0f && kmUnit > 0f) price / kmUnit else 0f
        val total = fuel + etMaint.value()
        tvCostKm.text = if (total > 0f) "Custo estimado: R$ %.2f por km".format(total) else ""
    }

    // ---------- Apoio ----------

    private fun bindSupport() {
        find<android.widget.LinearLayout>(R.id.supportBox).addView(Support.qrView(this))
        find<MaterialButton>(R.id.btnCopyCode).setOnClickListener { Support.copyCode(this) }
        find<MaterialButton>(R.id.btnCopyKey).setOnClickListener { Support.copyKey(this) }
        find<TextView>(R.id.tvAbout).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://andreluizs.com.br")))
        }
    }

    // ---------- Helpers ----------

    private fun <T : View> find(id: Int): T = findViewById(id)

    private fun field(id: Int, v: Float): EditText = find<EditText>(id).apply { setText(fmt(v)) }

    private fun fmt(v: Float) = String.format(Locale.US, "%.2f", v)

    private fun EditText.value() = text.toString().replace(",", ".").toFloatOrNull() ?: 0f
}

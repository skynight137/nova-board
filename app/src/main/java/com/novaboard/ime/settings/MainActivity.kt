package com.novaboard.ime.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.novaboard.ime.R
import com.novaboard.ime.theme.ThemeManager
import com.novaboard.ime.theme.ThemeMode
import com.novaboard.ime.util.AppLog

class MainActivity : AppCompatActivity() {
    private val createReport =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/plain")
        ) { destination ->
            if (destination == null) return@registerForActivityResult
            runCatching {
                    AppLog.exportReport(this, destination)
                }
                .onSuccess {
                    Toast.makeText(this, R.string.app_log_exported, Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    AppLog.e("MainActivity", "Failed to export diagnostic report", it)
                    Toast.makeText(this, R.string.app_log_export_failed, Toast.LENGTH_LONG).show()
                }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppLog.init(this)
        AppLog.i("MainActivity", "Settings screen opened")
        ThemeManager.applyStored(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnEnable).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<Button>(R.id.btnSwitch).setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }
        findViewById<Button>(R.id.btnExportLog).setOnClickListener {
            try {
                createReport.launch("NovaBoard-diagnostic-report.txt")
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, R.string.app_log_export_failed, Toast.LENGTH_LONG).show()
            }
        }

        val group = findViewById<RadioGroup>(R.id.themeGroup)
        when (ThemeManager.get(this)) {
            ThemeMode.SYSTEM -> findViewById<RadioButton>(R.id.radioSystem).isChecked = true
            ThemeMode.LIGHT -> findViewById<RadioButton>(R.id.radioLight).isChecked = true
            ThemeMode.DARK -> findViewById<RadioButton>(R.id.radioDark).isChecked = true
        }
        group.setOnCheckedChangeListener { _, checkedId ->
            val mode =
                when (checkedId) {
                    R.id.radioLight -> ThemeMode.LIGHT
                    R.id.radioDark -> ThemeMode.DARK
                    else -> ThemeMode.SYSTEM
                }
            ThemeManager.set(this, mode)
            recreate()
        }
    }
}

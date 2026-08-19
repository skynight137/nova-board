package com.novaboard.ime.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import com.novaboard.ime.R
import com.novaboard.ime.theme.ThemeManager
import com.novaboard.ime.theme.ThemeMode

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
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

package dev.uzair.levitate

import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.materialswitch.MaterialSwitch
import dev.uzair.levitate.databinding.PageSettingsBinding

class PreferencesUi(private val binding: PageSettingsBinding, private val preferences: SharedPreferences,
                    private val onChange: () -> Unit) {
    fun bind() {
        binding.sensitivitySlider.value = preferences.getFloat("sensitivity", 1400f)
        updateSensitivity(binding.sensitivitySlider.value)
        binding.sensitivitySlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                preferences.edit().putFloat("sensitivity", value).apply()
                updateSensitivity(value); onChange()
            }
        }
        bindSwitch(binding.automaticSwitch, "gestures", true)
        bindSwitch(binding.naturalSwitch, "natural", true)
        bindSwitch(binding.hapticsSwitch, "haptics", true)
        bindSwitch(binding.batterySwitch, "battery", false)
        bindSwitch(binding.awakeSwitch, "awake", true)
        bindTheme()
    }

    private fun bindSwitch(widget: MaterialSwitch, key: String, default: Boolean) {
        widget.isChecked = preferences.getBoolean(key, default)
        widget.setOnCheckedChangeListener { _, checked ->
            preferences.edit().putBoolean(key, checked).apply()
            onChange()
        }
    }

    private fun updateSensitivity(value: Float) {
        binding.sensitivityLabel.text = "Pointer sensitivity · ${"%.1f".format(value / 1400)}×"
    }

    private fun bindTheme() {
        val selected = when (preferences.getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)) {
            AppCompatDelegate.MODE_NIGHT_NO -> R.id.themeLight
            AppCompatDelegate.MODE_NIGHT_YES -> R.id.themeDark
            else -> R.id.themeSystem
        }
        binding.themeGroup.check(selected)
        binding.themeGroup.addOnButtonCheckedListener { _, checkedId, checked ->
            if (checked) {
                val theme = when (checkedId) {
                    R.id.themeLight -> AppCompatDelegate.MODE_NIGHT_NO
                    R.id.themeDark -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                preferences.edit().putInt("theme", theme).apply()
                AppCompatDelegate.setDefaultNightMode(theme)
            }
        }
    }
}

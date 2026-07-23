package cvc.dashingdog.vaart

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import cvc.dashingdog.vaart.databinding.ActivitySettingsBinding
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.Preference

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Settings"

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settingsContainer, SettingsFragment())
                .commit()
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.sharedPreferencesName = "vaart_prefs"
            preferenceManager.sharedPreferencesMode = MODE_PRIVATE
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            findPreference<EditTextPreference>("pref_overspeed_grace_value")?.apply {
                setOnBindEditTextListener { it.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED }
                summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
                    val value = pref.text?.toFloatOrNull() ?: 0f
                    val unit = SpeedUnitFormatter.unitLabel(requireContext())
                    when {
                        value == 0f -> "No grace margin — alerts at the exact limit"
                        value > 0f -> "Triggers ${value.toInt()} $unit over the limit"
                        else -> "Triggers ${-value.toInt()} $unit before the limit (early warning)"
                    }
                }
            }
            findPreference<EditTextPreference>("pref_nudge_threshold_value")?.apply {
                setOnBindEditTextListener { it.inputType = InputType.TYPE_CLASS_NUMBER }
                summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
                    val value = pref.text?.toFloatOrNull() ?: 10f
                    val unit = SpeedUnitFormatter.unitLabel(requireContext())
                    "Nudges to start when moving above ${value.toInt()} $unit"
                }
            }
            preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener { _, key ->
                if (key == "pref_speed_unit") {
                    findPreference<EditTextPreference>("pref_overspeed_grace_value")?.let { it.text = it.text }
                    findPreference<EditTextPreference>("pref_nudge_threshold_value")?.let { it.text = it.text }
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
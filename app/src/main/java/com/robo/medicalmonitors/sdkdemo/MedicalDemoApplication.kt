package com.robo.medicalmonitors.sdkdemo

import android.app.Application
import android.content.Context
import com.rhizo.libmedicalmon.MedicalMonitor
import com.rhizo.libmedicalmon.ServerRegion
import com.rhizo.libmedicalmon.Stethoscope

class MedicalDemoApplication : Application() {
    data class EcgSettings(
        val module: Int = 0,
        val speed: Int = 0,
        val lead: Int = 1,
        val gain: Int = 2,
        val filter: Int = 1,
        val frequency: Int = 0,
        val notch: Boolean = false
    )

    private var monitorInitialized = false
    private var stethoscopeInitialized = false

    override fun onCreate() {
        super.onCreate()
        initializeSdk()
    }

    @Synchronized
    fun initializeSdk() {
        runCatching { initializeMonitor() }
        runCatching { initializeStethoscope() }
    }

    @Synchronized
    private fun initializeMonitor() {
        if (monitorInitialized) return
        MedicalMonitor.getInstance().init(this, selectedRegion().toServerRegion())
        monitorInitialized = true
    }

    @Synchronized
    private fun initializeStethoscope() {
        if (stethoscopeInitialized) return
        Stethoscope.getInstance().init(this)
        stethoscopeInitialized = true
    }

    @Synchronized
    fun releaseSdk() {
        if (monitorInitialized) {
            val monitor = MedicalMonitor.getInstance()
            monitor.setMedicalMonitorCallback(null)
            monitor.setMeasurementDataCallback(null)
            monitor.setEcg3LeadsDataCallback(null)
            monitor.setEcg12LeadsDataCallback(null)
            if (monitor.isDeviceConnected()) monitor.stopNIEPMeasure()
            monitor.setFreeze(false)
            monitor.disconnectDevice()
            monitor.release()
            monitorInitialized = false
        }
        if (stethoscopeInitialized) {
            val stethoscope = Stethoscope.getInstance()
            stethoscope.setStethoscopeCallback(null)
            stethoscope.close()
            stethoscope.release()
            stethoscopeInitialized = false
        }
    }

    fun selectedRegion(): Int = getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        .getInt(KEY_REGION, DEFAULT_REGION)
        .coerceIn(0, 1)

    fun saveSelectedRegion(region: Int) {
        getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_REGION, region.coerceIn(0, 1))
            .commit()
    }

    fun loadEcgSettings(): EcgSettings {
        val preferences = getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        return EcgSettings(
            module = preferences.getInt(KEY_ECG_MODULE, 0).coerceIn(0, 1),
            speed = preferences.getInt(KEY_ECG_SPEED, 0).coerceIn(0, 1),
            lead = preferences.getInt(KEY_ECG_LEAD, 1).coerceIn(0, 2),
            gain = preferences.getInt(KEY_ECG_GAIN, 2).coerceIn(0, 3),
            filter = preferences.getInt(KEY_ECG_FILTER, 1).coerceIn(0, 2),
            frequency = preferences.getInt(KEY_ECG_FREQUENCY, 0).coerceIn(0, 1),
            notch = preferences.getBoolean(KEY_ECG_NOTCH, false)
        )
    }

    fun saveEcgSettings(settings: EcgSettings) {
        getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_ECG_MODULE, settings.module.coerceIn(0, 1))
            .putInt(KEY_ECG_SPEED, settings.speed.coerceIn(0, 1))
            .putInt(KEY_ECG_LEAD, settings.lead.coerceIn(0, 2))
            .putInt(KEY_ECG_GAIN, settings.gain.coerceIn(0, 3))
            .putInt(KEY_ECG_FILTER, settings.filter.coerceIn(0, 2))
            .putInt(KEY_ECG_FREQUENCY, settings.frequency.coerceIn(0, 1))
            .putBoolean(KEY_ECG_NOTCH, settings.notch)
            .apply()
    }

    private fun Int.toServerRegion(): ServerRegion =
        if (this == 0) ServerRegion.MAINLAND_CHINA else ServerRegion.HONGKONG_CHINA

    private companion object {
        const val PREFERENCES = "medical_monitor_settings"
        const val KEY_REGION = "region"
        const val KEY_ECG_MODULE = "ecg_module"
        const val KEY_ECG_SPEED = "ecg_speed"
        const val KEY_ECG_LEAD = "ecg_lead"
        const val KEY_ECG_GAIN = "ecg_gain"
        const val KEY_ECG_FILTER = "ecg_filter"
        const val KEY_ECG_FREQUENCY = "ecg_frequency"
        const val KEY_ECG_NOTCH = "ecg_notch"
        const val DEFAULT_REGION = 0
    }
}

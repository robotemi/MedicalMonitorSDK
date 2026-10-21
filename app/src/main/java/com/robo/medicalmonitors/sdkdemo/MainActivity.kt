package com.robo.medicalmonitors.sdkdemo

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.Button
import android.widget.GridLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.rhizo.libmedicalmon.ErrorCode
import com.rhizo.libmedicalmon.MedicalMonitor
import com.rhizo.libmedicalmon.MonitorDataPacket
import com.rhizo.libmedicalmon.Stethoscope
import com.rhizo.libmedicalmon.inface.Ecg12LeadsCallback
import com.rhizo.libmedicalmon.inface.Ecg3LeadsCallback
import com.rhizo.libmedicalmon.inface.MeasurementDataCallback
import com.rhizo.libmedicalmon.inface.MedicalMonitorCallback
import com.rhizo.libmedicalmon.inface.StethoscopeCallback

import com.robo.medicalmonitors.sdkdemo.view.ECGWaveView
import com.robo.medicalmonitors.sdkdemo.view.PulseBarView
import com.robo.medicalmonitors.sdkdemo.view.PulseWaveView
import com.robo.medicalmonitors.sdkdemo.util.DemoLocaleHelper
import com.robo.medicalmonitors.sdkdemo.util.DisplayScaleHelper
import com.robo.medicalmonitors.sdkdemo.view.ECGWaveView.Gain.GAIN_0_25
import com.robo.medicalmonitors.sdkdemo.view.ECGWaveView.Gain.GAIN_0_5
import com.robo.medicalmonitors.sdkdemo.view.ECGWaveView.Gain.GAIN_1
import com.robo.medicalmonitors.sdkdemo.view.ECGWaveView.Gain.GAIN_2
import com.robo.medicalmonitors.sdkdemo.view.PulseWaveView.Speed.SPEED_12_5
import com.robo.medicalmonitors.sdkdemo.view.PulseWaveView.Speed.SPEED_6_25
import com.robo.medicalmonitors.sdkdemo.view.PulseWaveView.Speed.SPEED_25
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

/**
 * SDK Demo 主页面：展示初始化、设备连接、测量回调、波形绘制和听诊器采集。
 *
 */
@SuppressLint("SetTextI18n")
class MainActivity : Activity() {
    private val monitor = MedicalMonitor.getInstance()
    private val stethoscope = Stethoscope.getInstance()
    private val sdkApplication: MedicalDemoApplication
        get() = application as MedicalDemoApplication
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var queryJob: Job? = null
    private var audioCloseJob: Job? = null
    private var resumeAudioOnStart = false
    @Volatile private var pageActive = false
    @Volatile private var generation = 0L
    @Volatile private var ready = false
    private var monitorInitialized = false
    private var audioInitialized = false
    private var connecting = false
    private var frozen = false
    private var permissionPending = false
    private var bpBusy = false
    private var bpAutomatic = false
    private var ignoreBpStartUntil = 0L
    private var lastMetricsAt = 0L
    private var lastDeviceCheckAt = 0L
    private var mode = MODE_THREE_LEADS
    private var selectedRegion = 0
    private var appliedLead: String? = null
    private var savedEcgSettings = MedicalDemoApplication.EcgSettings()
    private var uiBound = false
    private var ecgSettingsAppliedForConnection = false

    /** 当前 ECG 模块是否为 3 导联，避免在示例代码中散落 0/1 判断。 */
    private val isThreeLeadMode: Boolean
        get() = mode == MODE_THREE_LEADS

    private val isTwelveLeadMode: Boolean
        get() = mode == MODE_TWELVE_LEADS

    private val leadCount: Int
        get() = if (isThreeLeadMode) 3 else 12

    private val pcmBytes = AtomicLong()
    @Volatile private var pcmFormat = ""
    private val dataLock = Any()
    private data class Readings(
        var hr3: Int = -100, var hr12: Int = -100,
        var rr3: Int = -100, var rr12: Int = -100,
        var spo2: Int = -100, var pr: Int = -100,
        var temperature: Float = -100f,
        var sys: Int = -100, var dia: Int = -100, var mean: Int = -100,
        var cuff: Int = -100
    )
    private var readings = Readings()
    private val logs = ArrayDeque<String>()
    private val clockFormat = SimpleDateFormat("HH:mm:ss", Locale.CHINA)

    private lateinit var headerStatus: TextView
    private lateinit var snText: TextView
    private lateinit var bpStatus: TextView
    private lateinit var cuffText: TextView
    private lateinit var bpSystolic: TextView
    private lateinit var bpDiastolic: TextView
    private lateinit var bpMean: TextView
    private lateinit var audioStatus: TextView
    private lateinit var audioInfo: TextView
    private lateinit var logText: TextView
    private val metricViews = mutableListOf<TextView>()
    private lateinit var connectButton: Button
    private lateinit var chineseLanguageButton: Button
    private lateinit var englishLanguageButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var snButton: Button
    private lateinit var freezeButton: Button
    private lateinit var startBpButton: Button
    private lateinit var stopBpButton: Button
    private lateinit var audioButton: Button
    private lateinit var applyEcgButton: Button
    private lateinit var sendPaceButton: Button
    private lateinit var regionSpinner: Spinner
    private lateinit var moduleSpinner: Spinner
    private lateinit var ecgSpeedSpinner: Spinner
    private lateinit var periodSpinner: Spinner
    private lateinit var leadSpinner: Spinner
    private lateinit var gainSpinner: Spinner
    private lateinit var filterSpinner: Spinner
    private lateinit var frequencySpinner: Spinner
    private lateinit var leadSettings: View
    private lateinit var frequencySettings: View
    private lateinit var notchSwitch: Switch
    private lateinit var paceSwitch: Switch
    private lateinit var monitoringSwitch: Switch
    private lateinit var loggingSwitch: Switch
    private lateinit var ecgWaveContainer: GridLayout
    private val ecgViews = mutableListOf<ECGWaveView>()
    private lateinit var pulseView: PulseWaveView
    private lateinit var respirationWaveView: PulseWaveView
    private lateinit var oxygenBar: PulseBarView
    private val ecgSettingButtons = mutableListOf<Button>()
    private val TEAL by lazy { getColor(R.color.demo_teal) }
    private val MUTED by lazy { getColor(R.color.demo_muted) }
    private val ORANGE by lazy { getColor(R.color.demo_orange) }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(DemoLocaleHelper.wrap(newBase))
    }

    override fun getResources(): Resources = super.getResources().also(DisplayScaleHelper::applyToResources)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pcmFormat = getString(R.string.pcm_waiting)
        selectedRegion = sdkApplication.selectedRegion()
        savedEcgSettings = sdkApplication.loadEcgSettings()
        mode = savedInstanceState?.getInt(STATE_MODULE) ?: savedEcgSettings.module
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)
        bindUi()
        bindWaveforms()

    }

    override fun onStart() {
        super.onStart()
        pageActive = true

        initializeMonitor()
        initializeAudio()
        resumeAudioIfNeeded()
        handler.post(refresh)
    }

    override fun onStop() {
        pageActive = false
        permissionPending = false
        handler.removeCallbacks(refresh)
        pauseMonitor()
        if (audioInitialized && stethoscope.isOpen()) {
            resumeAudioOnStart = true
            if (audioCloseJob?.isActive != true) {
                audioCloseJob = scope.launch(Dispatchers.IO) { stethoscope.close() }
            }
        }
        super.onStop()
    }

    override fun onDestroy() {
        generation++
        handler.removeCallbacks(refresh)
        if (monitorInitialized) {
            monitor.setMedicalMonitorCallback(null)
            monitor.setMeasurementDataCallback(null)
            monitor.setEcg3LeadsDataCallback(null)
            monitor.setEcg12LeadsDataCallback(null)
        }
        if (audioInitialized) {
            stethoscope.setStethoscopeCallback(null)
        }
        sdkApplication.releaseSdk()
        scope.cancel()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_MODULE, mode)
        super.onSaveInstanceState(outState)
    }

    /**
     * 初始化页面。
     *
     * 这里按职责拆分绑定逻辑，开发者阅读示例时可以快速定位：
     * - [bindViews]：XML 控件引用；
     * - [bindSelectionListeners]：下拉框和开关；
     * - [bindActionListeners]：按钮对应的 SDK 操作；
     * - [bindLogListeners] / [bindNavigationListeners]：日志和页面导航。
     */
    private fun bindUi() {
        bindViews()
        bindLanguageListeners()
        bindSelectionListeners()
        bindActionListeners()
        bindLogListeners()
        bindNavigationListeners()
        updateEcgSettingVisibility()
        updateControls()
        uiBound = true
    }

    /** 只保存 XML 控件引用，不在这里执行 SDK 操作。 */
    private fun bindViews() {
        headerStatus = findViewById(R.id.header_status)
        snText = findViewById(R.id.sn_text)
        bpStatus = findViewById(R.id.bp_status)
        cuffText = findViewById(R.id.cuff_text)
        bpSystolic = findViewById(R.id.bp_systolic)
        bpDiastolic = findViewById(R.id.bp_diastolic)
        bpMean = findViewById(R.id.bp_mean)
        audioStatus = findViewById(R.id.audio_status)
        audioInfo = findViewById(R.id.audio_info)

        logText = findViewById(R.id.log_text)
        connectButton = findViewById(R.id.connect_button)
        chineseLanguageButton = findViewById(R.id.language_zh_button)
        englishLanguageButton = findViewById(R.id.language_en_button)
        disconnectButton = findViewById(R.id.disconnect_button)
        snButton = findViewById(R.id.sn_button)
        freezeButton = findViewById(R.id.freeze_button)
        startBpButton = findViewById(R.id.start_bp_button)
        stopBpButton = findViewById(R.id.stop_bp_button)
        audioButton = findViewById(R.id.audio_button)
        regionSpinner = findViewById(R.id.region_spinner)
        moduleSpinner = findViewById(R.id.module_spinner)
        ecgSpeedSpinner = findViewById(R.id.ecg_speed_spinner)
        periodSpinner = findViewById(R.id.period_spinner)
        leadSpinner = findViewById(R.id.lead_spinner)
        gainSpinner = findViewById(R.id.gain_spinner)
        filterSpinner = findViewById(R.id.filter_spinner)
        frequencySpinner = findViewById(R.id.frequency_spinner)
        leadSettings = findViewById(R.id.lead_settings)
        frequencySettings = findViewById(R.id.frequency_settings)
        notchSwitch = findViewById(R.id.notch_switch)
        notchSwitch.isChecked = savedEcgSettings.notch
        paceSwitch = findViewById(R.id.pace_switch)
        monitoringSwitch = findViewById(R.id.monitoring_switch)
        loggingSwitch = findViewById(R.id.logging_switch)
        ecgWaveContainer = findViewById(R.id.ecg_wave_container)
        respirationWaveView = findViewById(R.id.respiration_wave)
        pulseView = findViewById(R.id.pulse_wave)
        oxygenBar = findViewById(R.id.pulse_bar_view)
        metricViews += listOf(R.id.metric_hr, R.id.metric_spo2, R.id.metric_respiration_rate,
            R.id.metric_temp, R.id.metric_bp, R.id.metric_pr).map { findViewById<TextView>(it) }
        applyEcgButton = findViewById(R.id.apply_ecg_button)
        sendPaceButton = findViewById(R.id.send_pace_button)
        ecgSettingButtons += listOf(applyEcgButton, sendPaceButton)
    }

    /** 语言按钮会重建 Activity，重建后所有控件会使用新的资源语言。 */
    private fun bindLanguageListeners() {
        chineseLanguageButton.setOnClickListener { switchLanguage("zh") }
        englishLanguageButton.setOnClickListener { switchLanguage("en") }
        updateLanguageButtons()
    }

    /** 绑定会影响当前采集配置的下拉框和开关。 */
    private fun bindSelectionListeners() {
        configureSpinner(regionSpinner, selectedRegion) { index ->
            if (selectedRegion != index) {
                confirmRegionChange(index)
            }
        }
        configureSpinner(moduleSpinner, mode) { index ->
            if (mode != index) {
                mode = index
                bindWaveforms()
                respirationWaveView.clearData()
                updateEcgSettingVisibility()
                updateMetrics()
                persistEcgSettings()
                addLog(getString(R.string.module_changed, leadCount))
            }
        }
        configureSpinner(ecgSpeedSpinner, savedEcgSettings.speed) { position ->
            val speed = if (position == 0) SPEED_25 else SPEED_12_5
            ecgViews.forEach { it.speed = speed }
            persistEcgSettings()
        }
        configureSpinner(periodSpinner, 0)
        configureSpinner(leadSpinner, savedEcgSettings.lead) { persistEcgSettings() }
        configureSpinner(gainSpinner, savedEcgSettings.gain) {
            bindWaveforms()
            persistEcgSettings()
        }
        configureSpinner(filterSpinner, savedEcgSettings.filter) { persistEcgSettings() }
        configureSpinner(frequencySpinner, savedEcgSettings.frequency) { persistEcgSettings() }
        notchSwitch.setOnCheckedChangeListener { _, _ -> persistEcgSettings() }
    }

    /** 绑定与 SDK 连接、测量和听诊器有关的按钮。 */
    private fun bindActionListeners() {
        connectButton.setOnClickListener { connect() }
        disconnectButton.setOnClickListener { stopMonitor(getString(R.string.disconnected_by_user)) }
        snButton.setOnClickListener { querySerialNumber() }
        findViewById<Button>(R.id.exit_app_button).setOnClickListener { exitApp() }
        freezeButton.setOnClickListener { toggleFreeze() }
        findViewById<Button>(R.id.clear_waveforms_button).setOnClickListener {
            ecgViews.forEach { it.clearData() }
            respirationWaveView.clearData()
            pulseView.clearData()
            oxygenBar.clearData()
            addLog(getString(R.string.waveforms_cleared))
        }
        startBpButton.setOnClickListener { startBloodPressure() }
        stopBpButton.setOnClickListener { stopBloodPressure() }
        monitoringSwitch.setOnCheckedChangeListener { _, enabled ->
            if (audioInitialized && !stethoscope.setMonitoringEnabled(enabled)) {
                monitoringSwitch.isChecked = stethoscope.isMonitoringEnabled()
            }
        }
        audioButton.setOnClickListener {
            if (stethoscope.isOpen()) {
                stethoscope.close()
                addLog(getString(R.string.stethoscope_closed))
                refreshDeviceState()
            } else requestOpenAudio()
        }
        val advancedContent = findViewById<View>(R.id.ecg_settings_content)
        findViewById<Button>(R.id.expand_ecg_button).setOnClickListener {
            advancedContent.visibility = if (advancedContent.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        applyEcgButton.setOnClickListener { applyEcgSettings() }
        sendPaceButton.setOnClickListener {
            if (requireReady()) {
                val paceEnabled = if (paceSwitch.isChecked) 1 else 0
                if (isThreeLeadMode) monitor.ecgPace3Leads(paceEnabled) else monitor.ecgPace12Leads(paceEnabled)
                addLog(getString(R.string.pace_sent, leadCount, paceEnabled))
            }
        }
    }

    /** 日志开关和复制/清空操作只作用于 Demo 页面日志。 */
    private fun bindLogListeners() {
        loggingSwitch.setOnCheckedChangeListener { _, enabled ->
            if (monitorInitialized) monitor.setLoggingEnabled(enabled)
        }
        findViewById<Button>(R.id.copy_logs_button).setOnClickListener {
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(
                ClipData.newPlainText("MedicalMonitors SDK Demo", logs.joinToString("\n"))
            )
            toast(getString(R.string.logs_copied))
        }
        findViewById<Button>(R.id.clear_logs_button).setOnClickListener {
            logs.clear()
            logText.setText(R.string.no_logs)
        }
    }

    /** 左侧导航滚动到对应的演示区域。 */
    private fun bindNavigationListeners() {
        val scroll = findViewById<ScrollView>(R.id.content_scroll)
        listOf(
            R.id.nav_vitals to R.id.section_ecg_measurement,
            R.id.nav_respiration to R.id.section_respiration_measurement,
            R.id.nav_waveforms to R.id.section_spo2_measurement,
            R.id.nav_blood_pressure to R.id.section_blood_pressure,
            R.id.nav_stethoscope to R.id.section_stethoscope,
            R.id.nav_logs to R.id.section_logs
        ).forEach { (navigationId, sectionId) ->
            findViewById<Button>(navigationId).setOnClickListener {
                scroll.smoothScrollTo(0, findViewById<View>(sectionId).top)
            }
        }
    }

    private fun configureSpinner(spinner: Spinner, initial: Int, changed: (Int) -> Unit = {}) {
        spinner.setSelection(initial)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                changed(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun switchLanguage(language: String) {
        if (DemoLocaleHelper.language(this) == language) return
        DemoLocaleHelper.setLanguage(this, language)
        recreate()
    }

    private fun exitApp() {
        sdkApplication.releaseSdk()
        finishAffinity()
    }

    private fun confirmRegionChange(newRegion: Int) {
        val oldRegion = selectedRegion
        val regionName = resources.getStringArray(R.array.server_regions)
            .getOrNull(newRegion)
            .orEmpty()
        regionSpinner.isEnabled = false
        AlertDialog.Builder(this)
            .setTitle(R.string.region_switch_title)
            .setMessage(getString(R.string.region_switch_message, regionName))
            .setNegativeButton(R.string.cancel) { _, _ ->
                regionSpinner.isEnabled = true
                regionSpinner.setSelection(oldRegion)
            }
            .setPositiveButton(R.string.region_switch_confirm) { _, _ ->
                selectedRegion = newRegion
                sdkApplication.saveSelectedRegion(newRegion)
                pauseMonitor()
                stethoscope.close()
                if (!restartApplication()) {
                    selectedRegion = oldRegion
                    sdkApplication.saveSelectedRegion(oldRegion)
                    regionSpinner.isEnabled = true
                    regionSpinner.setSelection(oldRegion)
                    toast(getString(R.string.region_switch_restart_failed))
                }
            }
            .setOnCancelListener {
                regionSpinner.isEnabled = true
                regionSpinner.setSelection(oldRegion)
            }
            .show()
    }

    private fun restartApplication(): Boolean {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        // 延迟重启，等当前任务退出后由新的 Activity 重新初始化 SDK。
        val restartIntent = PendingIntent.getActivity(
            this,
            RESTART_REQUEST_CODE,
            launchIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(AlarmManager::class.java) ?: return false
        alarmManager.set(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + RESTART_DELAY_MS,
            restartIntent
        )
        sdkApplication.releaseSdk()
        finishAffinity()
        return true
    }

    private fun updateLanguageButtons() {
        val chineseSelected = DemoLocaleHelper.language(this) == "zh"
        chineseLanguageButton.backgroundTintList = ColorStateList.valueOf(
            getColor(if (chineseSelected) R.color.demo_teal else R.color.demo_button_background)
        )
        chineseLanguageButton.setTextColor(getColor(if (chineseSelected) R.color.demo_surface else R.color.demo_teal))
        englishLanguageButton.backgroundTintList = ColorStateList.valueOf(
            getColor(if (chineseSelected) R.color.demo_button_background else R.color.demo_teal)
        )
        englishLanguageButton.setTextColor(getColor(if (chineseSelected) R.color.demo_teal else R.color.demo_surface))
    }

    // region SDK 生命周期与连接
    // SDK 初始化只做一次；Activity 重建或从后台返回时，先确认当前页面仍然有效。
    private fun initializeMonitor() {
        if (monitorInitialized || !pageActive) return
        val token = ++generation
        try {
            sdkApplication.initializeSdk()
            monitorInitialized = true
            monitor.setLoggingEnabled(loggingSwitch.isChecked)
            monitor.setFreeze(false)
            registerCallbacks(token)
            ready = monitor.isDeviceConnected()
            connecting = false
            applyEcgSettingsAfterConnection()
            setStatus(getString(if (ready) R.string.status_connected else R.string.status_waiting), TEAL)
        } catch (e: Exception) {
            setStatus(getString(R.string.status_initialization_failed), ORANGE)
            addLog(getString(R.string.log_sdk_initialization_failed, e.message.orEmpty()))
        }
        updateControls()
    }

    private fun connect() {
        if (connecting || ready) return
        stopMonitor(getString(R.string.status_preparing_connection))
        initializeMonitor()
        if (!monitorInitialized) return
        if (!monitor.hasUsbDevice()) {
            setStatus(getString(R.string.status_no_usb_device), ORANGE)
            addLog(getString(R.string.log_usb_device_hint))
            return
        }
        connecting = true
        setStatus(getString(R.string.status_connecting), TEAL)
        addLog(getString(R.string.log_connect_device))
        updateControls()
        monitor.connectDevice()
    }

    private fun stopMonitor(message: String) {
        val wasReady = ready
        ready = false
        connecting = false
        generation++
        queryJob?.cancel()
        queryJob = null
        if (monitorInitialized) {
            if (wasReady && monitor.isDeviceConnected()) monitor.stopNIEPMeasure()
            monitor.setFreeze(false)
            monitor.disconnectDevice()
            registerCallbacks(generation)
        }
        bpBusy = false
        bpAutomatic = false
        ecgSettingsAppliedForConnection = false
        appliedLead = null
        frozen = false
        clearReadings()
        snText.setText(R.string.serial_number_empty)
        bpStatus.setText(R.string.blood_pressure_idle)
        setStatus(message, MUTED)
        bindWaveforms()
        updateControls()
    }

    private fun pauseMonitor() {
        val wasReady = ready
        ready = false
        connecting = false
        generation++
        queryJob?.cancel()
        queryJob = null
        if (monitorInitialized) {
            if (wasReady && monitor.isDeviceConnected()) monitor.stopNIEPMeasure()
            monitor.setFreeze(false)
            monitor.setMedicalMonitorCallback(null)
            monitor.setMeasurementDataCallback(null)
            monitor.setEcg3LeadsDataCallback(null)
            monitor.setEcg12LeadsDataCallback(null)
            monitor.disconnectDevice()
        }
        monitorInitialized = false
        bpBusy = false
        bpAutomatic = false
        ecgSettingsAppliedForConnection = false
        frozen = false
    }

    private fun live(token: Long) = token == generation
    private fun receiving(token: Long) = live(token) && ready
    private fun onMain(token: Long, action: () -> Unit) {
        handler.post { if (live(token)) action() }
    }

    private fun registerCallbacks(token: Long) {
        // 连接回调：只在主线程更新连接状态和页面控件。
        monitor.setMedicalMonitorCallback(object : MedicalMonitorCallback {
            override fun onDataReceived(data: MonitorDataPacket) = Unit
            override fun onDataReceived12Leads(data: MonitorDataPacket) = Unit

            override fun deviceConnected() = onMain(token) {
                ready = true
                connecting = false
                applyEcgSettingsAfterConnection()
                setStatus(getString(R.string.status_connected), TEAL)
                addLog(getString(R.string.log_connected))
                updateControls()
            }
            override fun deviceConnectedFailed(msg: String) = onMain(token) {
                stopMonitor(getString(R.string.status_connection_failed, errorText(msg)))
                addLog(getString(R.string.log_connection_failed, msg))
            }

        })
        // 通用测量回调：血氧、体温、脉搏、血压等数据与导联模式无关。
        monitor.setMeasurementDataCallback(object : MeasurementDataCallback {
            override fun onBodyTemperatureReceived(celsius: Float, fahrenheit: Float) {
                if (receiving(token)) synchronized(dataLock) { readings.temperature = celsius }
            }
            override fun onBloodOxygenDataReceived(percentage: Int, pulseRate: Int) {
                if (receiving(token)) synchronized(dataLock) { readings.spo2 = percentage; readings.pr = pulseRate }
            }
            override fun onCuffPressureReceived(cuffPressure: Int) {
                if (receiving(token)) synchronized(dataLock) { readings.cuff = cuffPressure }
            }
            override fun onBloodPressureMeasurementComplete() = onMain(token) {
                if (ready) {
                    bpBusy = false
                    bpStatus.setText(if (bpAutomatic) R.string.bp_auto_complete else R.string.bp_complete)
                    addLog("onBloodPressureMeasurementComplete()")
                    updateControls()
                }
            }
            override fun onBloodPressureMeasurementStart() = onMain(token) {
                if (ready && SystemClock.elapsedRealtime() >= ignoreBpStartUntil) {
                    bpBusy = true
                    bpStatus.setText(R.string.bp_measuring)
                    addLog("onBloodPressureMeasurementStart()")
                    updateControls()
                }
            }
            override fun onBloodPressureMeasurementStopped() = onMain(token) {
                if (ready) {
                    bpBusy = false
                    bpAutomatic = false
                    bpStatus.setText(R.string.bp_auto_cancelled)
                    addLog("onBloodPressureMeasurementStopped()")
                    updateControls()
                }
            }
            override fun onBloodPressureMeasurementDataReceived(sys: Int, dia: Int, mean: Int) {
                if (receiving(token)) {
                    synchronized(dataLock) { readings.sys = sys; readings.dia = dia; readings.mean = mean }
                    onMain(token) { addLog(getString(R.string.log_bp_result, sys, dia, mean)) }
                }
            }
            override fun onPulseDataReceived(pulseBar: Int, pulseWave: Int) {
                if (receiving(token)) {
                    pulseView.addDataPoint(pulseWave)
                    oxygenBar.addDataPoint(pulseBar)


                }
            }
            override fun onSpO2SensorIsConnected(isConnected: Boolean) = Unit
        })
        // 3 导联回调：只有当前页面选择 3 导联时才写入对应波形和指标。
        monitor.setEcg3LeadsDataCallback(object : Ecg3LeadsCallback {
            override fun onEcgWavesReceived3Leads(data: Int) {
                if (receiving(token) && isThreeLeadMode) {
                    ecgViews.firstOrNull()?.addECGDataPoint(data)
                }
            }
            override fun onRespWaveReceived3Leads(data: Int) {
                if (receiving(token) && isThreeLeadMode) respirationWaveView.addDataPoint(data)
            }
            override fun onHeartRateReceived3Leads(heartRate: Int) {
                if (receiving(token) && isThreeLeadMode) {
                    synchronized(dataLock) { readings.hr3 = heartRate }
                }
            }
            override fun onRespReceived3Leads(resp: Int) {
                if (receiving(token) && isThreeLeadMode) synchronized(dataLock) { readings.rr3 = resp }
            }
            override fun allLeadsConnected3Leads(isConnected: Boolean) = Unit
        })
        // 12 导联回调：一组数据包含 12 个采样值，按顺序分发给 12 个波形视图。
        monitor.setEcg12LeadsDataCallback(object : Ecg12LeadsCallback {
            override fun onEcgWavesReceived12Leads(data: IntArray) {
                if (receiving(token) && isTwelveLeadMode && data.size == 12) {
                    ecgViews.forEachIndexed { index, view ->
                        view.addECGDataPoint(data[index])
                    }
                }
            }
            override fun onRespWaveReceived12Leads(data: Int) {
                if (receiving(token) && isTwelveLeadMode) respirationWaveView.addDataPoint(data)
            }
            override fun onHeartRateReceived12Leads(heartRate: Int) {
                if (receiving(token) && isTwelveLeadMode) {
                    synchronized(dataLock) { readings.hr12 = heartRate }
                }
            }
            override fun onRespReceived12Leads(resp: Int) {
                if (receiving(token) && isTwelveLeadMode) synchronized(dataLock) { readings.rr12 = resp }
            }
            override fun allLeadsConnected12Leads(isConnected: Boolean) = Unit
        })
    }


    // region 用户触发的 SDK 测试操作
    // 这里的每个方法都对应页面上的一个 Demo 操作，便于开发者复制到自己的业务页面。
    private fun requireReady(): Boolean {
        if (!ready || !monitor.isDeviceConnected()) {
            toast(getString(R.string.prompt_connect_device))
            return false
        }
        return true
    }

    private fun querySerialNumber() {
        if (!requireReady() || queryJob?.isActive == true) return
        val token = generation
        snText.setText(R.string.sn_querying)
        queryJob = scope.launch {
            try {
                val sn = monitor.querySerialNumber()
                if (live(token)) {
                    snText.text = sn?.let { getString(R.string.sn_value, it) } ?: getString(R.string.sn_unsupported)
                    addLog(getString(R.string.log_sn_query, sn ?: "null"))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (live(token)) {
                    snText.setText(R.string.sn_query_failed)
                    addLog(getString(R.string.log_sn_query_failed, e.message.orEmpty()))
                }
            } finally {
                if (live(token)) { queryJob = null; updateControls() }
            }
        }
        updateControls()
    }

    private fun toggleFreeze() {
        if (!requireReady()) return
        frozen = !frozen
        monitor.setFreeze(frozen)
        updateControls()
        addLog(getString(if (frozen) R.string.log_freeze_enabled else R.string.log_freeze_disabled))
    }

    private fun applyEcgSettings() {
        if (!requireReady()) return
        persistEcgSettings()
        applyEcgSettingsToDevice()
        ecgSettingsAppliedForConnection = true
        ecgViews.forEach { it.clearData() }
        bindWaveforms()
        addLog(getString(
            R.string.log_ecg_settings_applied,
            leadCount.toString(),
            gainSpinner.selectedItem,
            filterSpinner.selectedItem,
            if (notchSwitch.isChecked) 1 else 0
        ))
    }

    private fun persistEcgSettings() {
        if (!uiBound) return
        savedEcgSettings = MedicalDemoApplication.EcgSettings(
            module = mode,
            speed = ecgSpeedSpinner.selectedItemPosition,
            lead = leadSpinner.selectedItemPosition,
            gain = gainSpinner.selectedItemPosition,
            filter = filterSpinner.selectedItemPosition,
            frequency = frequencySpinner.selectedItemPosition,
            notch = notchSwitch.isChecked
        )
        sdkApplication.saveEcgSettings(savedEcgSettings)
    }

    private fun applyEcgSettingsToDevice() {
        val gain = gainSpinner.selectedItemPosition
        val filter = filterSpinner.selectedItemPosition
        val notch = if (notchSwitch.isChecked) 1 else 0
        if (isThreeLeadMode) {
            monitor.ecgLeadType3Leads(leadSpinner.selectedItemPosition + 1)
            monitor.ecgWaveGain3Leads(gain)
            monitor.ecgFitterMode3Leads(filter)
            monitor.ecgTrapMode3Leads(notch)
            appliedLead = leadSpinner.selectedItem.toString()
        } else {
            monitor.ecgWaveGain12Leads(gain)
            monitor.ecgFitterMode12Leads(filter)
            monitor.ecgTrapMode12Leads(notch, frequencySpinner.selectedItemPosition + 1)
        }
    }

    private fun applyEcgSettingsAfterConnection() {
        if (!ready || ecgSettingsAppliedForConnection) return
        persistEcgSettings()
        applyEcgSettingsToDevice()
        ecgSettingsAppliedForConnection = true
        addLog(getString(
            R.string.log_ecg_settings_applied,
            leadCount.toString(),
            gainSpinner.selectedItem,
            filterSpinner.selectedItem,
            if (notchSwitch.isChecked) 1 else 0
        ))
    }

    private fun startBloodPressure() {
        if (!requireReady() || bpBusy || bpAutomatic) return
        val period = periodSpinner.selectedItemPosition
        synchronized(dataLock) { readings.sys = -100; readings.dia = -100; readings.mean = -100; readings.cuff = -100 }
        bpBusy = true
        bpAutomatic = period != 0
        ignoreBpStartUntil = 0
        monitor.startNIEPMeasure(period)
        bpStatus.setText(R.string.bp_start_sent)
        addLog(getString(R.string.log_bp_start_command, period, periodSpinner.selectedItem))
        updateControls()
    }

    private fun stopBloodPressure() {
        if (!requireReady()) return
        ignoreBpStartUntil = SystemClock.elapsedRealtime() + 1500
        monitor.stopNIEPMeasure()
        bpStatus.setText(R.string.bp_stop_sent)
        addLog(getString(R.string.log_bp_stop_command))
    }

    private fun initializeAudio() {
        if (audioInitialized) return
        audioInitialized = true
        stethoscope.setMonitoringEnabled(monitoringSwitch.isChecked)
        stethoscope.setStethoscopeCallback(object : StethoscopeCallback {
            override fun onPcmDataReceived(data: ByteArray, sampleRate: Int, channelCount: Int, bitsPerSample: Int) {
                if (!pageActive) return
                pcmBytes.addAndGet(data.size.toLong())
                pcmFormat = getString(R.string.pcm_format, sampleRate, channelCount, bitsPerSample)
            }
            override fun onError(message: String) {
                handler.post {
                    if (pageActive) {
                        addLog(getString(R.string.log_stethoscope_error, errorText(message), message))
                        audioStatus.text = errorText(message)
                        updateControls()
                    }
                }
            }
        })
    }

    private fun resumeAudioIfNeeded() {
        if (!resumeAudioOnStart) return
        scope.launch {
            audioCloseJob?.join()
            audioCloseJob = null
            if (!pageActive || !resumeAudioOnStart) return@launch
            resumeAudioOnStart = false
            requestOpenAudio()
        }
    }

    private fun requestOpenAudio() {
        if (!pageActive) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionPending = true
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_REQUEST)
            return
        }
        if (!stethoscope.hasStethoscope()) { toast(getString(R.string.toast_usb_audio_missing)); return }
        pcmBytes.set(0)
        pcmFormat = getString(R.string.pcm_waiting)
        if (stethoscope.setMonitoringEnabled(monitoringSwitch.isChecked) && stethoscope.open()) {
            addLog(getString(
                R.string.log_stethoscope_opened,
                getString(if (monitoringSwitch.isChecked) R.string.monitoring_enabled else R.string.monitoring_disabled)
            ))
            refreshDeviceState()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != RECORD_REQUEST || !permissionPending || !pageActive) return
        permissionPending = false
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            requestOpenAudio()
        } else {
            addLog(getString(R.string.log_audio_permission_denied))
            if (!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                AlertDialog.Builder(this).setTitle(R.string.record_permission_title)
                    .setMessage(R.string.record_permission_message)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.open_settings) { _, _ ->
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                    }.show()
            } else toast(getString(R.string.toast_record_permission))
        }
    }
    // endregion

    // region 限频刷新与绘图
    // SDK 回调可能高频到达；波形直接追加，指标和设备状态由定时任务限频刷新。
    private val refresh = object : Runnable {
        override fun run() {
            if (!pageActive) return
            val now = SystemClock.elapsedRealtime()
            if (now - lastMetricsAt >= 200) {
                updateMetrics()
                lastMetricsAt = now
            }
            if (now - lastDeviceCheckAt >= 1000) {
                refreshDeviceState()
                lastDeviceCheckAt = now
            }
            handler.postDelayed(this, 33)
        }
    }

    private fun bindWaveforms() {
        if (!::ecgWaveContainer.isInitialized || !::pulseView.isInitialized || !::respirationWaveView.isInitialized) return
        bindEcgWaveforms()
        bindAuxiliaryWaveforms()
    }

    private fun bindEcgWaveforms() {
        ecgWaveContainer.removeAllViews()
        ecgViews.clear()
        val labels = if (isThreeLeadMode) {
            listOf(appliedLead ?: leadSpinner.selectedItem?.toString().orEmpty().ifBlank { "II" })
        } else {
            LEADS
        }
        ecgWaveContainer.columnCount = if (isThreeLeadMode) 1 else 2
        val viewHeight = if (isThreeLeadMode) 180 else 120
        labels.forEach { label ->
            val view = ECGWaveView(this).apply {
                setValueRange(0, 4096)
                setLineColor(getColor(R.color.wave_ecg))
                setBackgroundColor(Color.rgb(9, 28, 38))
                setShowGrid(false)
                setCustomText(getString(R.string.ecg_wave_label, label))
                setShowCustomText(true)
                speed = selectedEcgWaveSpeed()
                gain = selectedEcgGain()
                contentDescription = getString(R.string.ecg_wave_description_format, label)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = dp(viewHeight)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                }
            }
            ecgViews += view
            ecgWaveContainer.addView(view)
        }
    }

    private fun bindAuxiliaryWaveforms() {
        respirationWaveView.setValueRange(0, 255)
        respirationWaveView.setShowGrid(false)
        // 呼吸和脉搏波形使用固定纸速，页面只允许调整 ECG 纸速。
        respirationWaveView.speed = SPEED_6_25
        pulseView.setValueRange(0, 255)
        pulseView.setShowGrid(false)
        pulseView.speed = SPEED_25
        oxygenBar.setValueRange(0, 15)
    }

    private fun selectedEcgGain(): ECGWaveView.Gain = when (gainSpinner.selectedItemPosition.coerceAtLeast(0)) {
        0 -> GAIN_0_25
        1 -> GAIN_0_5
        3 -> GAIN_2
        else -> GAIN_1
    }

    private fun selectedEcgWaveSpeed() =
        if (ecgSpeedSpinner.selectedItemPosition == 0) SPEED_25 else SPEED_12_5

    private fun updateEcgSettingVisibility() {
        leadSettings.visibility = if (isThreeLeadMode) View.VISIBLE else View.GONE
        frequencySettings.visibility = if (isThreeLeadMode) View.GONE else View.VISIBLE
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun updateMetrics() {
        val data = synchronized(dataLock) { readings.copy() }
        metricViews[0].show(valid(if (isThreeLeadMode) data.hr3 else data.hr12))
        metricViews[1].show(if (data.spo2 in 1..100) data.spo2.toString() else getString(R.string.no_reading))
        metricViews[2].show(valid(if (isThreeLeadMode) data.rr3 else data.rr12))
        metricViews[3].show(if (data.temperature > 0 && data.temperature.isFinite()) String.format(Locale.getDefault(), "%.1f", data.temperature) else getString(R.string.no_reading))
        metricViews[4].show(if (data.sys > 0 && data.dia > 0) "${data.sys}/${data.dia}" else getString(R.string.no_reading))
        metricViews[5].show(valid(data.pr))
        bpSystolic.show(valid(data.sys))
        bpDiastolic.show(valid(data.dia))
        bpMean.show(valid(data.mean))
        val cuffPressure = if (data.cuff >= 0) data.cuff.toString() else getString(R.string.no_reading)
        cuffText.show(getString(R.string.cuff_status_format, cuffPressure, valid(data.mean)))
        audioInfo.show(getString(R.string.pcm_info_format, pcmFormat, pcmBytes.get()))

    }

    private fun refreshDeviceState() {
        if (!pageActive) return
        if (ready && !monitor.isDeviceConnected()) {
            stopMonitor(getString(R.string.status_usb_disconnected))
            addLog(getString(R.string.log_device_disconnected))
        }
        val hasAudio = audioInitialized && stethoscope.hasStethoscope()
        if (audioInitialized && stethoscope.isOpen() && !hasAudio) {
            stethoscope.close()
            addLog(getString(R.string.log_stethoscope_removed))
        }
        audioStatus.show(when {
            stethoscope.isOpen() -> getString(R.string.audio_status_collecting)
            hasAudio -> getString(R.string.audio_status_ready)
            else -> getString(R.string.audio_status_missing)
        })
        updateControls()
    }

    private fun updateControls() {
        if (!::connectButton.isInitialized || !::audioButton.isInitialized || !::loggingSwitch.isInitialized) return
        connectButton.isEnabled = !connecting && !ready
        connectButton.show(getString(when {
            connecting -> R.string.connecting_short
            ready -> R.string.connected_button
            else -> R.string.connect_device
        }))
        disconnectButton.isEnabled = monitorInitialized
        snButton.isEnabled = ready && queryJob?.isActive != true
        regionSpinner.isEnabled = !connecting && !ready
        startBpButton.isEnabled = ready && !bpBusy && !bpAutomatic
        stopBpButton.isEnabled = ready
        periodSpinner.isEnabled = !bpBusy && !bpAutomatic
        freezeButton.isEnabled = ready
        freezeButton.show(getString(if (frozen) R.string.freeze_resume else R.string.freeze_data))
        audioButton.isEnabled = audioInitialized
        audioButton.show(getString(if (stethoscope.isOpen()) R.string.close_stethoscope else R.string.open_stethoscope))
        monitoringSwitch.isEnabled = audioInitialized
        ecgSettingButtons.forEach { it.isEnabled = ready }
        (ecgSettingButtons + listOf(connectButton, disconnectButton, snButton, startBpButton, stopBpButton, freezeButton, audioButton)).forEach {
            it.alpha = if (it.isEnabled) 1f else 0.4f
        }
    }

    private fun clearReadings() {
        synchronized(dataLock) { readings = Readings() }
        ecgViews.forEach { it.clearData() }
        if (::respirationWaveView.isInitialized) respirationWaveView.clearData()
        if (::pulseView.isInitialized) pulseView.clearData()
        if (::oxygenBar.isInitialized) oxygenBar.clearData()
        if (metricViews.isNotEmpty() && ::audioInfo.isInitialized) updateMetrics()
    }
    // endregion

    private fun setStatus(message: String, color: Int) { headerStatus.text = message; headerStatus.setTextColor(color) }
    private fun valid(value: Int) = if (value > 0) value.toString() else getString(R.string.no_reading)
    private fun TextView.show(value: String) { if (text.toString() != value) text = value }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun addLog(message: String) {
        if (logs.size >= 100) logs.removeFirst()
        logs.addLast("${clockFormat.format(Date())}  ${message.take(800)}")
        if (::logText.isInitialized) logText.text = logs.takeLast(14).joinToString("\n")
    }

    private fun errorText(message: String): String = when (message) {
        ErrorCode.ERROR_INIT -> getString(R.string.error_sdk_not_initialized)
        ErrorCode.ERROR_USB_PERMISSION -> getString(R.string.error_usb_permission)
        ErrorCode.ERROR_INTERNET_PERMISSION -> getString(R.string.error_internet_permission)
        ErrorCode.ERROR_NETWORK_STATE_PERMISSION -> getString(R.string.error_network_state_permission)
        ErrorCode.ERROR_SERIAL_NUMBER -> getString(R.string.error_serial_number)
        ErrorCode.ERROR_ENCRYPT -> getString(R.string.error_encrypt)
        ErrorCode.ERROR_DECRYPT -> getString(R.string.error_decrypt)
        ErrorCode.ERROR_STETHOSCOPE_INIT -> getString(R.string.error_stethoscope_not_initialized)
        ErrorCode.ERROR_STETHOSCOPE_RECORD_AUDIO_PERMISSION -> getString(R.string.error_microphone_permission)
        ErrorCode.ERROR_STETHOSCOPE_USB_MICROPHONE -> getString(R.string.error_usb_microphone)
        ErrorCode.ERROR_STETHOSCOPE_MONITORING_START -> getString(R.string.error_monitoring_start)
        ErrorCode.ERROR_STETHOSCOPE_CAPTURE_START -> getString(R.string.error_capture_start)
        ErrorCode.ERROR_STETHOSCOPE_CAPTURE -> getString(R.string.error_capture)
        else -> message
    }

    companion object {
        private const val MODE_THREE_LEADS = 0
        private const val MODE_TWELVE_LEADS = 1
        private const val STATE_MODULE = "module"
        private const val RECORD_REQUEST = 41
        private const val RESTART_REQUEST_CODE = 42
        private const val RESTART_DELAY_MS = 200L
        private val LEADS = listOf("I", "II", "III", "aVR", "aVL", "aVF", "V1", "V2", "V3", "V4", "V5", "V6")
    }
}

# Medical Monitor SDK Demo

[中文](README.zh-CN.md) | **English**

Android demo for Medical Monitor SDK `v1.0.0`, supporting temperature, SpO2, pulse, blood pressure, 3/12-lead ECG, respiration, and USB stethoscope audio.

## Requirements

- Android 7.0 (API 24) or later; compile SDK 35
- JDK 17 and a USB host capable Android device
- A supported Medical Box or USB stethoscope

The SDK AAR is included at `app/libs/MedicalMonitors_release_v1.0.0.aar`.

## Run

```bash
git clone git@github.com:robotemi/MedicalMonitorSDK.git
cd MedicalMonitorSDK
./gradlew assembleDebug
```

Open the project in Android Studio and run the `app` configuration. Verify with:

```bash
./gradlew testDebugUnitTest assembleDebug
```

## Integration

### Dependencies

```kotlin
dependencies {
    implementation(files("libs/MedicalMonitors_release_v1.0.0.aar"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("com.robotemi:sdk:1.138.0")
}
```

### Permissions

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
<uses-feature android:name="android.hardware.usb.host" android:required="true" />
```

Request `RECORD_AUDIO` at runtime when using the stethoscope.

### Initialize and connect

```kotlin
class MedicalApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MedicalMonitor.getInstance().init(this, ServerRegion.HONGKONG_CHINA)
        Stethoscope.getInstance().init(this) // Only when needed.
    }
}
```

Register callbacks before connecting. Start operations only after `deviceConnected()`.

```kotlin
val monitor = MedicalMonitor.getInstance()
monitor.setMedicalMonitorCallback(object : MedicalMonitorCallback {
    override fun onDataReceived(data: MonitorDataPacket) = Unit
    override fun onDataReceived12Leads(data: MonitorDataPacket) = Unit
    override fun deviceConnected() = Unit
    override fun deviceConnectedFailed(msg: String) = Unit
})
if (monitor.hasUsbDevice()) monitor.connectDevice()
```

Regions: `MAINLAND_CHINA` (default) and `HONGKONG_CHINA`.

## API Summary

### Medical Box

| API | Description |
| --- | --- |
| `connectDevice()` / `disconnectDevice()` | Connect or disconnect the first supported USB serial device |
| `hasUsbDevice()` / `isDeviceConnected()` | Check device presence or connection state |
| `querySerialNumber()` | Return the box serial number or `null` |
| `setLoggingEnabled()` / `setFreeze()` | Toggle logging or parsed callbacks |
| `release()` | Release resources |

### Measurements

Register `MeasurementDataCallback` for:

- Temperature: `onBodyTemperatureReceived(celsius, fahrenheit)`
- SpO2 and pulse rate: `onBloodOxygenDataReceived(percentage, pulseRate)`
- Blood pressure: cuff pressure, start/stop, result, and completion callbacks
- Pulse waveform and SpO2 sensor state

### ECG

Register `Ecg3LeadsCallback` or `Ecg12LeadsCallback` for ECG, respiration, heart rate, and lead state.

- ECG samples: `0..4096`, baseline `2048`; respiration: `0..256`
- 12-lead order: `I, II, III, aVR, aVL, aVF, V1, V2, V3, V4, V5, V6`
- Gain: `0` x0.25, `1` x0.5, `2` x1, `3` x2
- Filter mode: `0` diagnostic, `1` monitoring, `2` surgery
- Notch frequency: `1` 50 Hz, `2` 60 Hz

`Fitter`, `NIEP`, and `inface` are the actual `v1.0.0` API spellings.

### Blood pressure

Use `startNIEPMeasure(period)` and `stopNIEPMeasure()`. Results arrive through `MeasurementDataCallback`.

Periods: `0` manual; `1..5` = 1, 2, 3, 4, 5 minutes; `6..14` = 10, 15, 30, 60, 90, 120, 180, 240, 480 minutes.

### Stethoscope

```kotlin
val stethoscope = Stethoscope.getInstance()
stethoscope.setMonitoringEnabled(false)
stethoscope.setStethoscopeCallback(callback)
if (stethoscope.hasStethoscope()) stethoscope.open()
```

PCM format: 44.1 kHz, mono, 16-bit, without a WAV header. Do not block the PCM callback. Use `close()` and `release()` when finished.

## Notes

- `hasUsbDevice()` does not confirm USB permission or an active connection.
- Connection errors use `deviceConnectedFailed(msg)`; stethoscope errors use `onError(message)`.
- Raw packets, blood-pressure events, and stethoscope PCM continue while frozen.
- Clear callbacks, stop measurements, disconnect, and release both SDK singletons when finished.

## License

This repository and the included SDK are proprietary. Contact the SDK provider for distribution and usage terms.

# 医疗监护 SDK Demo

**中文** | [English](README.md)

医疗监护 SDK `v1.0.0` Android 示例，支持体温、血氧、脉搏、血压、三/十二导联心电、呼吸和 USB 听诊器音频。

## 环境要求

- Android 7.0（API 24）及以上；compile SDK 35
- JDK 17 和支持 USB Host 的 Android 设备
- 受支持的医疗盒子或 USB 听诊器

SDK AAR 已放在 `app/libs/MedicalMonitors_release_v1.0.0.aar`。

## 运行

```bash
git clone git@github.com:robotemi/MedicalMonitorSDK.git
cd MedicalMonitorSDK
./gradlew assembleDebug
```

用 Android Studio 打开项目并运行 `app` 配置。验证项目：

```bash
./gradlew testDebugUnitTest assembleDebug
```

## 接入

### 依赖

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

### 权限

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
<uses-feature android:name="android.hardware.usb.host" android:required="true" />
```

使用听诊器时还需动态申请 `RECORD_AUDIO`。

### 初始化与连接

```kotlin
class MedicalApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MedicalMonitor.getInstance().init(this, ServerRegion.MAINLAND_CHINA)
        Stethoscope.getInstance().init(this) // 仅在需要时初始化。
    }
}
```

连接前注册回调，收到 `deviceConnected()` 后再执行测量。

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

服务器区域：`MAINLAND_CHINA`（默认）和 `HONGKONG_CHINA`。

## API 概览

### 医疗盒子

| API | 说明 |
| --- | --- |
| `connectDevice()` / `disconnectDevice()` | 连接或断开第一个受支持的 USB 串口设备 |
| `hasUsbDevice()` / `isDeviceConnected()` | 检查设备接入或连接状态 |
| `querySerialNumber()` | 返回盒子序列号或 `null` |
| `setLoggingEnabled()` / `setFreeze()` | 开关日志或解析后回调 |
| `release()` | 释放资源 |

### 测量回调

通过 `MeasurementDataCallback` 接收：

- 体温：`onBodyTemperatureReceived(celsius, fahrenheit)`
- 血氧和脉率：`onBloodOxygenDataReceived(percentage, pulseRate)`
- 血压：袖带压力、开始/停止、结果和完成回调
- 脉搏波和血氧传感器状态

### ECG

通过 `Ecg3LeadsCallback` 或 `Ecg12LeadsCallback` 接收心电、呼吸、心率和导联状态。

- ECG 采样：`0..4096`，基线 `2048`；呼吸：`0..256`
- 12 导联顺序：`I, II, III, aVR, aVL, aVF, V1, V2, V3, V4, V5, V6`
- 增益：`0` x0.25，`1` x0.5，`2` x1，`3` x2
- 滤波：`0` 诊断，`1` 监护，`2` 手术
- 工频：`1` 50 Hz，`2` 60 Hz

`Fitter`、`NIEP` 和 `inface` 是 `v1.0.0` 的实际拼写。

### 血压

使用 `startNIEPMeasure(period)` 和 `stopNIEPMeasure()`，结果通过 `MeasurementDataCallback` 返回。

周期：`0` 手动；`1..5` 为 1、2、3、4、5 分钟；`6..14` 为 10、15、30、60、90、120、180、240、480 分钟。

### 听诊器

```kotlin
val stethoscope = Stethoscope.getInstance()
stethoscope.setMonitoringEnabled(false)
stethoscope.setStethoscopeCallback(callback)
if (stethoscope.hasStethoscope()) stethoscope.open()
```

PCM 格式：44.1 kHz、单声道、16 bit，不含 WAV 头。不要阻塞 PCM 回调，使用完后调用 `close()` 和 `release()`。

## 注意事项

- `hasUsbDevice()` 不代表已获得 USB 权限或已连接。
- 连接错误通过 `deviceConnectedFailed(msg)` 返回；听诊器错误通过 `onError(message)` 返回。
- 冻结时原始包、血压事件和听诊器 PCM 仍会回调。
- 使用完后清空回调、停止测量、断开设备并释放两个 SDK 单例。

## 许可说明

本仓库及其包含的 SDK 为专有软件。分发和使用条款请联系 SDK 提供方。

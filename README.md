# 悬浮便签 Android APP

一款简洁的安卓悬浮桌面便签应用。

## 功能特性

1. **悬浮在所有应用之上** - 使用 `TYPE_APPLICATION_OVERLAY` 实现全局悬浮
2. **上边框显示日期时间** - 左对齐，可自定义显示内容
3. **下边框工具栏** - 设置图标和闹钟图标，右对齐
4. **自动保存** - 编辑内容实时保存到 SharedPreferences
5. **闹钟功能** - 设置提醒时间，闹钟图标闪烁提示
6. **关闭按钮** - 右上角关闭按钮
7. **透明度切换** - 操作时 100% 可见，非操作时 70% 透明

## 项目结构

```
FloatingNote/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/floatingnote/
│       │   ├── MainActivity.kt          # 主界面，启动服务
│       │   ├── FloatingWindowService.kt # 悬浮窗服务
│       │   ├── AlarmReceiver.kt         # 闹钟广播接收器
│       │   └── BootReceiver.kt          # 开机启动接收器
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml
│           │   ├── floating_note_layout.xml
│           │   └── dialog_datetime_picker.xml
│           ├── drawable/
│           │   └── note_background.xml
│           └── values/
│               ├── strings.xml
│               └── themes.xml
├── build.gradle.kts
└── settings.gradle.kts
```

## 编译与安装

### 前置要求

- Android Studio Hedgehog 或更高版本
- JDK 8+
- Android SDK 34

### 编译步骤

1. 用 Android Studio 打开项目目录
2. 等待 Gradle 同步完成
3. Build > Build APK(s) 或 Build > Build Bundle(s) for APK
4. APK 输出路径：`app/build/outputs/apk/debug/app-debug.apk`

### 安装到设备

```bash
adb install app-debug.apk
```

或通过 Android Studio 直接运行。

## 权限说明

- `SYSTEM_ALERT_WINDOW` - 悬浮窗权限（必须手动授予）
- `SET_ALARM` - 设置闹钟
- `RECEIVE_BOOT_COMPLETED` - 开机自启动
- `FOREGROUND_SERVICE` - 前台服务
- `POST_NOTIFICATIONS` - 通知权限（Android 13+）
- `WAKE_LOCK` - 唤醒设备

## 使用方法

1. 打开应用，点击"启动悬浮便签"
2. 首次使用需授予悬浮窗权限
3. 悬浮窗出现后：
   - 拖动标题栏移动位置
   - 点击编辑框输入内容（自动保存）
   - 点击设置图标自定义显示的日期时间
   - 点击闹钟图标设置提醒时间
   - 点击关闭按钮退出

## 透明度说明

- **触摸编辑框时**：100% 不透明
- **失去焦点时**：70% 透明度
- 过渡动画：200ms 淡入淡出

## 闹钟功能

设置闹钟后，闹钟图标会以 500ms 间隔闪烁，直到触发提醒。提醒通过系统通知显示。

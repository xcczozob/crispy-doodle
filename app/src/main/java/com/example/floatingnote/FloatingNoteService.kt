package com.example.floatingnote

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class FloatingNoteService : AppCompatActivity() {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private lateinit var prefs: SharedPreferences
    
    // UI组件
    private lateinit var tvDateTime: TextView
    private lateinit var etNote: EditText
    private lateinit var ivSettings: ImageView
    private lateinit var ivAlarm: ImageView
    private lateinit var ivClose: ImageView
    private lateinit var rootLayout: LinearLayout
    
    // 闹钟相关
    private var alarmTime: Long = 0
    private var isAlarmSet = false
    private var isAlarmBlinking = false
    private var blinkHandler: Handler? = null
    private var blinkRunnable: Runnable? = null
    
    // 拖动相关
    private var lastX = 0
    private var lastY = 0
    private var params: WindowManager.LayoutParams? = null

    companion object {
        const val CHANNEL_ID = "floating_note_channel"
        const val PREFS_NAME = "FloatingNotePrefs"
        const val KEY_NOTE = "note_content"
        const val KEY_ALARM_TIME = "alarm_time"
        const val KEY_DISPLAY_DATE = "display_date"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        
        // 检查悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            requestOverlayPermission()
        } else {
            createNotificationChannel()
            showFloatingNote()
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        intent.data = android.net.Uri.parse("package:$packageName")
        startActivityForResult(intent, 100)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && Settings.canDrawOverlays(this)) {
            createNotificationChannel()
            showFloatingNote()
        } else {
            Toast.makeText(this, "需要悬浮窗权限才能使用", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "便签闹钟提醒",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun showFloatingNote() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_note_layout, null)
        
        // 设置窗口参数
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
            else 
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params?.gravity = Gravity.TOP or Gravity.START
        params?.x = 100
        params?.y = 100
        
        // 初始化UI组件
        initViews()
        
        // 设置透明度（初始70%）
        updateAlpha(false)
        
        // 加载保存的内容
        loadSavedContent()
        
        // 设置事件监听
        setupEventListeners()
        
        // 注册闹钟广播接收器
        registerAlarmReceiver()
        
        // 检查是否有闹钟
        checkAlarm()
        
        // 添加到窗口
        windowManager.addView(floatingView, params)
    }

    private fun initViews() {
        tvDateTime = floatingView!!.findViewById(R.id.tvDateTime)
        etNote = floatingView!!.findViewById(R.id.etNote)
        ivSettings = floatingView!!.findViewById(R.id.ivSettings)
        ivAlarm = floatingView!!.findViewById(R.id.ivAlarm)
        ivClose = floatingView!!.findViewById(R.id.ivClose)
        rootLayout = floatingView!!.findViewById(R.id.rootLayout)
        
        // 显示当前时间
        updateDateTimeDisplay()
    }

    private fun updateDateTimeDisplay() {
        val displayDate = prefs.getString(KEY_DISPLAY_DATE, null)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        tvDateTime.text = displayDate ?: dateFormat.format(Date())
    }

    private fun loadSavedContent() {
        val savedNote = prefs.getString(KEY_NOTE, "")
        etNote.setText(savedNote)
        
        alarmTime = prefs.getLong(KEY_ALARM_TIME, 0)
        isAlarmSet = alarmTime > System.currentTimeMillis()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupEventListeners() {
        // 拖动标题栏移动窗口
        tvDateTime.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX.toInt()
                    lastY = event.rawY.toInt()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX.toInt() - lastX
                    val dy = event.rawY.toInt() - lastY
                    params?.x = params?.x?.plus(dx) ?: dx
                    params?.y = params?.y?.plus(dy) ?: dy
                    windowManager.updateViewLayout(floatingView, params)
                    lastX = event.rawX.toInt()
                    lastY = event.rawY.toInt()
                }
            }
            true
        }
        
        // 编辑框触摸事件 - 获得焦点时100%可见
        etNote.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    params?.flags = params?.flags?.and(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv())!!
                    windowManager.updateViewLayout(floatingView, params)
                    updateAlpha(true)
                    v.onTouchEvent(event)
                }
            }
            true
        }
        
        // 自动保存
        etNote.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                prefs.edit().putString(KEY_NOTE, s?.toString() ?: "").apply()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        
        // 设置按钮 - 设置显示的日期时间
        ivSettings.setOnClickListener {
            showDateTimePickerDialog()
        }
        
        // 闹钟按钮 - 设置闹钟
        ivAlarm.setOnClickListener {
            showAlarmPickerDialog()
        }
        
        // 关闭按钮
        ivClose.setOnClickListener {
            finish()
        }
        
        // 点击空白区域失去焦点
        rootLayout.setOnClickListener {
            params?.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            windowManager.updateViewLayout(floatingView, params)
            updateAlpha(false)
        }
    }

    private fun updateAlpha(isFocused: Boolean) {
        val alpha = if (isFocused) 1.0f else 0.7f
        val animator = ObjectAnimator.ofFloat(rootLayout, "alpha", rootLayout.alpha, alpha)
        animator.duration = 200
        animator.start()
    }

    private fun showDateTimePickerDialog() {
        val calendar = Calendar.getInstance()
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("设置显示日期时间")
            .setView(layoutInflater.inflate(R.layout.dialog_datetime_picker, null))
            .setPositiveButton("确定") { dialog, _ ->
                val datePicker = (dialog as AlertDialog).findViewById<android.widget.DatePicker>(R.id.datePicker)
                val timePicker = (dialog as AlertDialog).findViewById<android.widget.TimePicker>(R.id.timePicker)
                
                if (datePicker != null && timePicker != null) {
                    val year = datePicker.year
                    val month = datePicker.month
                    val day = datePicker.dayOfMonth
                    val hour = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) timePicker.hour else timePicker.currentHour
                    val minute = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) timePicker.minute else timePicker.currentMinute
                    
                    val customDateTime = String.format(Locale.getDefault(), "%04d-%02d-%02d %02d:%02d", 
                        year, month + 1, day, hour, minute)
                    
                    prefs.edit().putString(KEY_DISPLAY_DATE, customDateTime).apply()
                    tvDateTime.text = customDateTime
                }
            }
            .setNegativeButton("取消", null)
            .create()
        
        dialog.show()
    }

    private fun showAlarmPickerDialog() {
        val calendar = Calendar.getInstance()
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_datetime_picker, null)
        val datePicker = dialogView.findViewById<android.widget.DatePicker>(R.id.datePicker)
        val timePicker = dialogView.findViewById<android.widget.TimePicker>(R.id.timePicker)
        
        AlertDialog.Builder(this)
            .setTitle("设置闹钟时间")
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                val year = datePicker.year
                val month = datePicker.month
                val day = datePicker.dayOfMonth
                val hour = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) timePicker.hour else timePicker.currentHour
                val minute = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) timePicker.minute else timePicker.currentMinute
                
                calendar.set(year, month, day, hour, minute, 0)
                alarmTime = calendar.timeInMillis
                
                if (alarmTime > System.currentTimeMillis()) {
                    setAlarm(alarmTime)
                    prefs.edit().putLong(KEY_ALARM_TIME, alarmTime).apply()
                    isAlarmSet = true
                    startAlarmBlinking()
                    Toast.makeText(this, "闹钟已设置", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "请选择未来的时间", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .setNeutralButton("取消闹钟") { _, _ ->
                cancelAlarm()
                Toast.makeText(this, "闹钟已取消", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun setAlarm(timeInMillis: Long) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
        }
    }

    private fun cancelAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        
        prefs.edit().remove(KEY_ALARM_TIME).apply()
        isAlarmSet = false
        stopAlarmBlinking()
    }

    private fun checkAlarm() {
        alarmTime = prefs.getLong(KEY_ALARM_TIME, 0)
        if (alarmTime > System.currentTimeMillis()) {
            isAlarmSet = true
            startAlarmBlinking()
        }
    }

    private fun startAlarmBlinking() {
        if (isAlarmBlinking) return
        
        isAlarmBlinking = true
        blinkHandler = Handler(Looper.getMainLooper())
        blinkRunnable = object : Runnable {
            override fun run() {
                if (isAlarmBlinking) {
                    ivAlarm.alpha = if (ivAlarm.alpha == 1.0f) 0.3f else 1.0f
                    blinkHandler?.postDelayed(this, 500)
                }
            }
        }
        blinkHandler?.post(blinkRunnable!!)
    }

    private fun stopAlarmBlinking() {
        isAlarmBlinking = false
        blinkRunnable?.let { blinkHandler?.removeCallbacks(it) }
        ivAlarm.alpha = 1.0f
    }

    private var alarmReceiver: BroadcastReceiver? = null

    private fun registerAlarmReceiver() {
        alarmReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                showAlarmNotification()
                stopAlarmBlinking()
                prefs.edit().remove(KEY_ALARM_TIME).apply()
            }
        }
        
        val filter = IntentFilter("com.example.floatingnote.ALARM_ACTION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(alarmReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(alarmReceiver, filter)
        }
    }

    private fun showAlarmNotification() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("便签提醒")
                .setContentText(etNote.text.toString())
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(this)
                .setContentTitle("便签提醒")
                .setContentText(etNote.text.toString())
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
        }
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (floatingView != null) {
            windowManager.removeView(floatingView)
        }
        stopAlarmBlinking()
        alarmReceiver?.let { unregisterReceiver(it) }
    }
}

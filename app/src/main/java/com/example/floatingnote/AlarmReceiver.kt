package com.example.floatingnote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 发送内部广播通知服务
        val broadcastIntent = Intent("com.example.floatingnote.ALARM_TRIGGER")
        context.sendBroadcast(broadcastIntent)
    }
}

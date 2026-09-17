package com.zhundian.reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.app.Notification;
import android.app.NotificationManager;

public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!AlarmScheduler.ACTION_REMINDER.equals(intent.getAction())) return;
        String taskId = intent.getStringExtra("taskId");
        String title = intent.getStringExtra("title");
        String body = intent.getStringExtra("body");
        long dueAt = intent.getLongExtra("dueAt", 0L);
        int notificationId = intent.getIntExtra("notificationId", Math.abs(taskId == null ? 1 : taskId.hashCode() % 100000));

        Notification notification = AlarmScheduler.buildNotification(context, title, body, dueAt, notificationId);
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(notificationId, notification);
    }
}

package com.urwah.dhikr

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Calendar

object NotificationHelper {
    private const val CHANNEL_ID = "adhkar_reminders"
    private const val CHANNEL_NAME = "تذكيرات الأذكار"

    const val EXTRA_TYPE = "reminder_type"
    const val TYPE_MORNING = "الصباح"
    const val TYPE_EVENING = "المساء"
    const val TYPE_BEDTIME = "النوم"
    const val TYPE_KAHF = "الكهف"
    const val TYPE_MULK = "الملك"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تذكير بقراءة الأذكار وسور القرآن"
                enableVibration(true)
                setBypassDnd(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    fun showReminder(context: Context, type: String) {
        val (title, text, categoryName) = when (type) {
            TYPE_MORNING -> Triple(
                "حان وقت أذكار $type",
                "أصبحنا وأصبح الملك لله، والحمد لله...",
                "أذكار $type"
            )
            TYPE_EVENING -> Triple(
                "حان وقت أذكار $type",
                "أمسينا وأمسى الملك لله، والحمد لله...",
                "أذكار $type"
            )
            TYPE_BEDTIME -> Triple(
                "حان وقت أذكار $type",
                "اللهم بك أمسينا وبك أصبحنا...",
                "أذكار $type"
            )
            TYPE_KAHF -> Triple(
                "يوم الجمعة - سورة الكهف",
                "اقرأ سورة الكهف فإنها نور ما بين الجمعتين",
                "سورة الكهف"
            )
            TYPE_MULK -> Triple(
                "قبل النوم - سورة الملك",
                "اقرأ سورة الملك تنجيك من عذاب القبر",
                "سورة الملك"
            )
            else -> Triple(
                "حان وقت الأذكار",
                "اذكر الله يذكرك",
                ""
            )
        }

        val intent = Intent(context, DhikrDetailsActivity::class.java).apply {
            putExtra("CATEGORY_NAME", categoryName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, type.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mosque_black_24dp)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(type.hashCode(), notification)
    }

    fun scheduleReminder(context: Context, type: String, hourOfDay: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_TYPE, type)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, type.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (type == TYPE_KAHF) {
                // Schedule for next Friday
                set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY)
                if (before(Calendar.getInstance())) {
                    add(Calendar.WEEK_OF_YEAR, 1)
                }
            } else if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val interval = if (type == TYPE_KAHF) AlarmManager.INTERVAL_DAY * 7 else AlarmManager.INTERVAL_DAY
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            cal.timeInMillis,
            interval,
            pendingIntent
        )
    }

    fun cancelReminder(context: Context, type: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_TYPE, type)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, type.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra(NotificationHelper.EXTRA_TYPE) ?: return
        NotificationHelper.createChannel(context)
        NotificationHelper.showReminder(context, type)
    }
}

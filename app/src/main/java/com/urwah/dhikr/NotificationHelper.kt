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
    const val TYPE_KHATMA = "الختمة"

    val defaultHours = mapOf(
        TYPE_MORNING to 6,
        TYPE_EVENING to 17,
        TYPE_BEDTIME to 22,
        TYPE_KAHF to 6,
        TYPE_MULK to 22,
        TYPE_KHATMA to 8
    )

    val defaultMinutes = mapOf(
        TYPE_MORNING to 0,
        TYPE_EVENING to 0,
        TYPE_BEDTIME to 0,
        TYPE_KAHF to 0,
        TYPE_MULK to 0,
        TYPE_KHATMA to 0
    )

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
        val data = getNotificationData(context, type)

        val intent = buildDeepLink(context, type, data)
        val pendingIntent = PendingIntent.getActivity(
            context, type.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mosque_black_24dp)
            .setContentTitle(data.title)
            .setContentText(data.text)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(0xFF8B6F5E.toInt())
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(NotificationCompat.BigTextStyle().bigText(data.text))
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(type.hashCode(), notification)
    }

    fun scheduleNext(context: Context, type: String, hourOfDay: Int, minute: Int) {
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
                set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY)
                if (before(Calendar.getInstance())) {
                    add(Calendar.WEEK_OF_YEAR, 1)
                }
            } else if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val alarmInfo = AlarmManager.AlarmClockInfo(cal.timeInMillis, pendingIntent)
        try {
            alarmManager.setAlarmClock(alarmInfo, pendingIntent)
        } catch (_: SecurityException) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
        }
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

    fun scheduleAll(context: Context) {
        val prefs = context.getSharedPreferences("urwah_settings", Context.MODE_PRIVATE)
        val allTypes = listOf(TYPE_MORNING, TYPE_EVENING, TYPE_BEDTIME, TYPE_KAHF, TYPE_MULK, TYPE_KHATMA)
        for (type in allTypes) {
            val enabled = prefs.getBoolean("${type}_enabled", false)
            if (enabled) {
                val h = prefs.getInt("${type}_hour", defaultHours[type] ?: 6)
                val m = prefs.getInt("${type}_min", defaultMinutes[type] ?: 0)
                scheduleNext(context, type, h, m)
            }
        }
    }

    private fun getNotificationData(context: Context, type: String): NotificationData {
        return when (type) {
            TYPE_MORNING -> NotificationData(
                "عروة — أذكار الصباح",
                "أصبحنا وأصبح الملك لله، والحمد لله...",
                "أذكار الصباح",
                "dhikr"
            )
            TYPE_EVENING -> NotificationData(
                "عروة — أذكار المساء",
                "أمسينا وأمسى الملك لله، والحمد لله...",
                "أذكار المساء",
                "dhikr"
            )
            TYPE_BEDTIME -> NotificationData(
                "عروة — أذكار النوم",
                "باسمك اللهم أموت وأحيا...",
                "أذكار النوم",
                "dhikr"
            )
            TYPE_KAHF -> NotificationData(
                "عروة — سورة الكهف",
                "اقرأ سورة الكهف فإنها نور ما بين الجمعتين",
                "سورة الكهف",
                "surah"
            )
            TYPE_MULK -> NotificationData(
                "عروة — سورة الملك",
                "اقرأ سورة الملك تنجيك من عذاب القبر",
                "سورة الملك",
                "surah"
            )
            TYPE_KHATMA -> {
                val activeKhatmas = getActiveKhatmaNames(context)
                if (activeKhatmas.isEmpty()) {
                    NotificationData(
                        "عروة — تذكير الختمة",
                        "لديك ختمات مفتوحة، تابع قراءتك اليومية",
                        "الختمة",
                        "khatma"
                    )
                } else {
                    NotificationData(
                        "عروة — تذكير الختمة",
                        "تابع قراءة: ${activeKhatmas.first().name}",
                        activeKhatmas.first().id,
                        "khatma"
                    )
                }
            }
            else -> NotificationData(
                "عروة — تذكير",
                "اذكر الله يذكرك",
                "",
                "dhikr"
            )
        }
    }

    private data class NotificationData(
        val title: String,
        val text: String,
        val targetId: String,
        val targetType: String
    )

    private fun buildDeepLink(context: Context, type: String, data: NotificationData): Intent {
        return when (data.targetType) {
            "surah" -> {
                val surahNumber = when (type) {
                    TYPE_KAHF -> 18
                    TYPE_MULK -> 67
                    else -> 18
                }
                Intent(context, SurahDetailActivity::class.java).apply {
                    putExtra("SURAH_NUMBER", surahNumber)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
            "khatma" -> {
                val khatmas = KhatmaManager.getAll(context).filter { it.isActive }
                val khatma = khatmas.find { it.id == data.targetId } ?: khatmas.firstOrNull()
                if (khatma != null) {
                    Intent(context, KhatmaReadingActivity::class.java).apply {
                        putExtra("KHATMA_ID", khatma.id)
                        putExtra("START_JUZ", khatma.startJuz)
                        putExtra("TOTAL_DAYS", khatma.totalDays)
                        putExtra("CURRENT_DAY", khatma.currentDay)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                } else {
                    Intent(context, DhikrDetailsActivity::class.java).apply {
                        putExtra("CATEGORY_NAME", data.targetId)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                }
            }
            else -> {
                Intent(context, DhikrDetailsActivity::class.java).apply {
                    putExtra("CATEGORY_NAME", data.targetId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
        }
    }

    private fun getActiveKhatmaNames(context: Context): List<com.urwah.dhikr.Khatma> {
        val all = KhatmaManager.getAll(context)
        return all.filter { it.isActive }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra(NotificationHelper.EXTRA_TYPE) ?: return
        NotificationHelper.createChannel(context)
        NotificationHelper.showReminder(context, type)

        if (type != NotificationHelper.TYPE_KHATMA) {
            val prefs = context.getSharedPreferences("urwah_settings", Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean("${type}_enabled", false)
            if (enabled) {
                val h = prefs.getInt("${type}_hour", NotificationHelper.defaultHours[type] ?: 6)
                val m = prefs.getInt("${type}_min", NotificationHelper.defaultMinutes[type] ?: 0)
                NotificationHelper.scheduleNext(context, type, h, m)
            }
        } else {
            NotificationHelper.scheduleNext(context, type,
                NotificationHelper.defaultHours[type] ?: 8,
                NotificationHelper.defaultMinutes[type] ?: 0
            )
        }
    }
}

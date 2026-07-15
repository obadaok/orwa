package com.urwah.dhikr

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View

object HapticUtil {
    private const val PREFS_NAME = "urwah_settings"
    private const val KEY_VIBRATION = "vibration_enabled"

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_VIBRATION, true)
    }

    fun perform(context: Context, view: View? = null) {
        if (!isEnabled(context)) return
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                    return
                } catch (_: IllegalArgumentException) { }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(80, 255))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(80)
            }
        }
        view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}

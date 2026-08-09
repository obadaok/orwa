package com.urwah.dhikr

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast

object UrwahToast {
    private var currentToast: Toast? = null

    fun show(context: Context, message: String) {
        showToast(context, message, false)
    }

    fun showError(context: Context, message: String) {
        showToast(context, message, true)
    }

    private fun showToast(context: Context, message: String, isError: Boolean) {
        currentToast?.cancel()

        val inflater = LayoutInflater.from(context)
        val layout = inflater.inflate(R.layout.toast_urwah, null)

        val tvMessage = layout.findViewById<TextView>(R.id.tvToastMessage)
        tvMessage.text = message

        if (isError) {
            layout.setBackgroundResource(R.drawable.bg_toast_error)
        } else {
            layout.setBackgroundResource(R.drawable.bg_toast_urwah)
        }

        val toast = Toast(context).apply {
            duration = Toast.LENGTH_SHORT
            setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 120)
            view = layout
        }
        currentToast = toast

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            layout.alpha = 0f
            val fadeIn = ObjectAnimator.ofFloat(layout, "alpha", 0f, 1f).apply {
                duration = 250
            }
            fadeIn.start()
        }

        toast.show()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            layout.postDelayed({
                val fadeOut = ObjectAnimator.ofFloat(layout, "alpha", 1f, 0f).apply {
                    duration = 250
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            toast.cancel()
                        }
                    })
                }
                fadeOut.start()
            }, 2000)
        }
    }
}

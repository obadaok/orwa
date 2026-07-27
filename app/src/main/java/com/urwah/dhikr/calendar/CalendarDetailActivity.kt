package com.urwah.dhikr.calendar

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.urwah.dhikr.R

class CalendarDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_CONTENT = "extra_content"
        const val EXTRA_SUBTITLE = "extra_subtitle"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calendar_detail)

        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val content = intent.getStringExtra(EXTRA_CONTENT) ?: ""
        val subtitle = intent.getStringExtra(EXTRA_SUBTITLE) ?: ""

        findViewById<TextView>(R.id.detailTitle).text = title
        findViewById<TextView>(R.id.detailSubtitle).text = subtitle
        findViewById<TextView>(R.id.detailContent).text = content

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }
    }
}

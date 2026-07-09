package com.urwah.dhikr

import android.content.Context
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class DhikrDetailsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dhikr_details)

        FavoritesManager.init(this)

        val categoryName = intent.getStringExtra("CATEGORY_NAME") ?: "أذكار"

        val txtTitle = findViewById<TextView>(R.id.txtCategoryTitle)
        txtTitle.text = categoryName

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener { onBackPressed() }

        val rvDhikrList = findViewById<RecyclerView>(R.id.rvDhikrList)
        rvDhikrList.layoutManager = LinearLayoutManager(this)
        rvDhikrList.adapter = DhikrListAdapter(
            initialItems = DhikrDataProvider.getDhikrs(categoryName),
            onAllCompleted = { showCompletionDialog(categoryName) }
        )

        rvDhikrList.itemAnimator?.apply {
            moveDuration = 450
            changeDuration = 250
        }
    }

    private fun showCompletionDialog(categoryName: String) {
        val prefs = getSharedPreferences("urwah_stats", Context.MODE_PRIVATE)
        val completedToday = prefs.getInt("completed_today", 0)
        prefs.edit().putInt("completed_today", completedToday + 1).apply()

        val message = "ماشاء الله لقد أكملت ${categoryName}\nتقبل الله منك"

        AlertDialog.Builder(this)
            .setTitle("أحسنت 🎉")
            .setMessage(message)
            .setPositiveButton("الحمد لله") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}

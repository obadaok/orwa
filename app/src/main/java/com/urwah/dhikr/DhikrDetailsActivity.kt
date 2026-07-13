package com.urwah.dhikr

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class DhikrDetailsActivity : AppCompatActivity() {

    private var completionDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dhikr_details)

        FavoritesManager.init(this)

        val categoryName = intent.getStringExtra("CATEGORY_NAME") ?: "أذكار"

        val txtTitle = findViewById<TextView>(R.id.txtCategoryTitle)
        txtTitle.text = categoryName

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener { onBackPressed() }

        val btnReset = findViewById<ImageButton>(R.id.btnResetDhikr)
        btnReset.setOnClickListener {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_delete_bookmark, null)
            dialogView.findViewById<TextView>(R.id.tvDeleteMessage).text =
                "هل أنت متأكد من إعادة تعيين التقدم في $categoryName؟\nسيتم مسح جميع الإنجازات."

            val dialog = AlertDialog.Builder(this)
                .setView(dialogView as android.view.View)
                .create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            val confirmBtn = dialogView.findViewById<Button>(R.id.btnDeleteConfirm)
            confirmBtn.text = "نعم، إعادة تعيين"
            confirmBtn.setOnClickListener {
                DhikrListAdapter.resetCategory(this@DhikrDetailsActivity, categoryName)
                dialog.dismiss()
                recreate()
            }
            dialogView.findViewById<Button>(R.id.btnDeleteCancel).setOnClickListener {
                dialog.dismiss()
            }

            dialog.show()
        }

        val rvDhikrList = findViewById<RecyclerView>(R.id.rvDhikrList)
        rvDhikrList.layoutManager = LinearLayoutManager(this)

        val items = DhikrDataProvider.getDhikrs(categoryName)
        val savedDone = DhikrListAdapter.getCompletedIds(this, categoryName)

        val adapter = DhikrListAdapter(
            initialItems = items,
            categoryName = categoryName,
            onAllCompleted = { showCompletionDialog(categoryName) }
        )
        adapter.setSavedProgress(this, savedDone)
        rvDhikrList.adapter = adapter

        rvDhikrList.itemAnimator?.apply {
            moveDuration = 450
            changeDuration = 250
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        completionDialog?.dismiss()
    }

    private fun showCompletionDialog(categoryName: String) {
        val prefs = getSharedPreferences("urwah_stats", Context.MODE_PRIVATE)
        val completedToday = prefs.getInt("completed_today", 0)
        val totalCompleted = prefs.getInt("total_completed", 0)
        prefs.edit()
            .putInt("completed_today", completedToday + 1)
            .putInt("total_completed", totalCompleted + 1)
            .apply()

        val dialogView = layoutInflater.inflate(R.layout.dialog_completion, null)
        dialogView.findViewById<TextView>(R.id.tv_dialog_message).text =
            "ماشاء الله لقد أكملت ${categoryName}\nتقبل الله منك"

        completionDialog = AlertDialog.Builder(this, android.R.style.Theme_Translucent_NoTitleBar)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialogView.findViewById<Button>(R.id.btn_dialog_dismiss).setOnClickListener {
            completionDialog?.dismiss()
        }

        completionDialog?.show()
    }
}

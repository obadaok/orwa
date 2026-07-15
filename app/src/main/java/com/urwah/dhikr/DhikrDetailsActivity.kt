package com.urwah.dhikr

import android.content.Context
import android.graphics.Canvas
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class DhikrDetailsActivity : AppCompatActivity() {

    private var completionDialog: AlertDialog? = null
    private lateinit var rvDhikrList: RecyclerView
    private var adapter: DhikrListAdapter? = null
    private var categoryName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dhikr_details)

        FavoritesManager.init(this)

        categoryName = intent.getStringExtra("CATEGORY_NAME") ?: "أذكار"

        val txtTitle = findViewById<TextView>(R.id.txtCategoryTitle)
        txtTitle.text = categoryName

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener {
            HapticUtil.perform(this, btnBack)
            onBackPressed()
        }

        findViewById<ImageButton>(R.id.btnResetDhikr).setOnClickListener { v ->
            HapticUtil.perform(this, v)
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_delete_bookmark, null)
            dialogView.findViewById<TextView>(R.id.tvDeleteMessage).text =
                "هل أنت متأكد من إعادة تعيين التقدم في $categoryName؟\nسيتم مسح جميع الإنجازات."

            val dialog = AlertDialog.Builder(this)
                .setView(dialogView)
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

        findViewById<ImageButton>(R.id.btnAddDhikr).setOnClickListener { v ->
            HapticUtil.perform(this, v)
            showAddDhikrDialog()
        }

        rvDhikrList = findViewById(R.id.rvDhikrList)
        rvDhikrList.layoutManager = LinearLayoutManager(this)

        setupSwipeToDelete()
        loadItems()
    }

    private fun loadItems() {
        val items = CustomDhikrManager.getAllDhikrItems(this, categoryName)
        val savedDone = DhikrListAdapter.getCompletedIds(this, categoryName)

        adapter = DhikrListAdapter(
            initialItems = items,
            categoryName = categoryName,
            onAllCompleted = { showCompletionDialog(categoryName) }
        )
        adapter?.setSavedProgress(this, savedDone)
        rvDhikrList.adapter = adapter

        rvDhikrList.itemAnimator?.apply {
            moveDuration = 450
            changeDuration = 250
        }
    }

    private fun setupSwipeToDelete() {
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.adapterPosition
                val item = adapter?.getItem(pos)
                if (item == null || item.id < 10000) {
                    Toast.makeText(this@DhikrDetailsActivity, "لا يمكن حذف الأذكار الافتراضية", Toast.LENGTH_SHORT).show()
                    return
                }
                showDeleteConfirmation(item, pos)
            }

            override fun onChildDraw(c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
                val pos = vh.adapterPosition
                if (pos == RecyclerView.NO_POSITION) return
                val item = adapter?.getItem(pos)
                val isDeletable = item != null && item.id >= 10000

                val content = vh.itemView.findViewById<View>(R.id.cardDhikrItem) ?: return
                val bg = vh.itemView.findViewById<View>(R.id.deleteBackground) ?: return

                if (!isDeletable || !isCurrentlyActive) {
                    val dampedDx = dX.coerceIn(-content.width.toFloat(), content.width.toFloat()) * 0.1f
                    content.translationX = dampedDx
                    bg.alpha = 0f
                    return
                }

                val clampedDx = dX.coerceIn(-content.width.toFloat(), content.width.toFloat())
                content.translationX = clampedDx
                val absDx = kotlin.math.abs(clampedDx)
                val fraction = minOf(absDx / content.width.toFloat(), 1f)
                bg.alpha = fraction * 0.6f
            }

            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                val content = vh.itemView.findViewById<View>(R.id.cardDhikrItem)
                content?.translationX = 0f
                content?.animate()?.translationX(0f)?.setDuration(200)?.start()
                val bg = vh.itemView.findViewById<View>(R.id.deleteBackground)
                bg?.alpha = 0f
            }
        })
        touchHelper.attachToRecyclerView(rvDhikrList)
    }

    private fun showDeleteConfirmation(item: DhikrItem, pos: Int) {
        adapter?.notifyItemChanged(pos)

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_delete_bookmark, null)
        dialogView.findViewById<TextView>(R.id.tvDeleteMessage).text =
            "حذف هذا الذكر؟"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<View>(R.id.btnDeleteConfirm).setOnClickListener {
            CustomDhikrManager.delete(this, item.id)
            dialog.dismiss()
            loadItems()
        }
        dialogView.findViewById<View>(R.id.btnDeleteCancel).setOnClickListener {
            dialog.dismiss()
            loadItems()
        }

        dialog.show()
    }

    private fun showAddDhikrDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_dhikr, null)
        val etText = dialogView.findViewById<EditText>(R.id.etDhikrText)
        val pickerRepeats = dialogView.findViewById<NumberPicker>(R.id.pickerRepeats)
        val etVirtue = dialogView.findViewById<EditText>(R.id.etVirtue)

        pickerRepeats.minValue = 1
        pickerRepeats.maxValue = 100
        pickerRepeats.value = 1

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<Button>(R.id.btnSaveDhikr).setOnClickListener {
            val text = etText.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(this, "الرجاء إدخال نص الذكر", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            CustomDhikrManager.add(this, categoryName, text, pickerRepeats.value, etVirtue.text.toString().trim())
            dialog.dismiss()
            loadItems()
            Toast.makeText(this, "تمت إضافة الذكر", Toast.LENGTH_SHORT).show()
        }
        dialogView.findViewById<Button>(R.id.btnCancelDhikr).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
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

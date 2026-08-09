package com.urwah.dhikr.fragments

import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.NumberPicker
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.urwah.dhikr.JuzData
import com.urwah.dhikr.Khatma
import com.urwah.dhikr.KhatmaManager
import com.urwah.dhikr.KhatmaReadingActivity
import com.urwah.dhikr.QuranDataLoader
import com.urwah.dhikr.R

class KhatmaFragment : Fragment() {

    private var _view: View? = null
    private val root get() = _view!!
    private var adapter: KhatmaListAdapter? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _view = inflater.inflate(R.layout.fragment_khatma, container, false)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rv = view.findViewById<RecyclerView>(R.id.rvKhatmas)
        rv.layoutManager = LinearLayoutManager(requireContext())

        view.findViewById<ImageButton>(R.id.btnAddKhatma).setOnClickListener {
            showAddKhatmaDialog()
        }

        refreshList()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        val rv = root.findViewById<RecyclerView>(R.id.rvKhatmas)
        val tvEmpty = root.findViewById<TextView>(R.id.tvEmpty)
        val khatmas = KhatmaManager.getAll(requireContext())
        if (khatmas.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            rv.visibility = View.GONE
            return
        }
        tvEmpty.visibility = View.GONE
        rv.visibility = View.VISIBLE
        adapter = KhatmaListAdapter(khatmas,
            onClick = { k ->
                val intent = Intent(requireContext(), KhatmaReadingActivity::class.java)
                intent.putExtra("KHATMA_ID", k.id)
                intent.putExtra("START_JUZ", k.startJuz)
                intent.putExtra("TOTAL_DAYS", k.totalDays)
                intent.putExtra("CURRENT_DAY", k.currentDay)
                startActivity(intent)
            },
            onDelete = { k ->
                showDeleteConfirmation(k)
            }
        )
        rv.adapter = adapter
    }

    private fun showDeleteConfirmation(khatma: Khatma) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_delete_khatma, null)
        dialogView.findViewById<TextView>(R.id.tvDeleteMessage).text =
            "هل أنت متأكد من حذف ختمة \"${khatma.name}\"؟\nسيتم حذف جميع بيانات التقدم."

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<Button>(R.id.btnDeleteConfirm).setOnClickListener {
            KhatmaManager.delete(requireContext(), khatma.id)
            dialog.dismiss()
            refreshList()
        }
        dialogView.findViewById<Button>(R.id.btnDeleteCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private var selectedRiwaya: String = "hafs"
    private var riwayaChips: MutableList<Triple<com.urwah.dhikr.RiwayatInfo, FrameLayout, TextView>> = mutableListOf()

    private fun dp(value: Int): Int = (resources.displayMetrics.density * value).toInt()

    private fun selectRiwaya(info: com.urwah.dhikr.RiwayatInfo, card: FrameLayout, tv: TextView, isSelected: Boolean) {
        if (isSelected) {
            card.setBackgroundResource(R.drawable.bg_primary_button)
            tv.setTextColor(android.graphics.Color.WHITE)
            tv.text = "${info.arabicName} ✓"
        } else {
            card.setBackgroundResource(R.drawable.bg_segment_unselected)
            tv.setTextColor(android.graphics.Color.parseColor("#5E4B40"))
            tv.text = info.arabicName
        }
    }

    private fun buildRiwayaChips(container: android.widget.LinearLayout) {
        riwayaChips.clear()
        container.removeAllViews()

        val current = selectedRiwaya
        QuranDataLoader.availableRiwayat.forEach { info ->
            val card = FrameLayout(requireContext()).apply {
                val lp = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(44)
                )
                lp.setMargins(0, 0, dp(8), 0)
                layoutParams = lp
                background = resources.getDrawable(
                    if (info.id == current) R.drawable.bg_primary_button else R.drawable.bg_segment_unselected
                )
                isClickable = true
                isFocusable = true
            }
            val tv = TextView(requireContext()).apply {
                text = info.arabicName
                setTextColor(
                    if (info.id == current) android.graphics.Color.WHITE
                    else android.graphics.Color.parseColor("#5E4B40")
                )
                textSize = 14f
                gravity = android.view.Gravity.CENTER
                setPadding(dp(16), 0, dp(16), 0)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            card.addView(tv)
            card.setOnClickListener {
                selectedRiwaya = info.id
                riwayaChips.forEach { (ri, c, t) ->
                    selectRiwaya(ri, c, t, ri.id == info.id)
                }
            }
            container.addView(card)
            riwayaChips.add(Triple(info, card, tv))
        }
    }

    private fun showAddKhatmaDialog() {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_khatma_setup, null)
        val etName = view.findViewById<EditText>(R.id.etKhatmaName)
        val juzPicker = view.findViewById<NumberPicker>(R.id.pickerStartJuz)
        val dayPicker = view.findViewById<NumberPicker>(R.id.pickerDays)
        val tvWird = view.findViewById<TextView>(R.id.tvWirdPreview)
        val chipsContainer = view.findViewById<android.widget.LinearLayout>(R.id.llRiwayaChips)

        selectedRiwaya = "hafs"
        buildRiwayaChips(chipsContainer)

        juzPicker.minValue = 1
        juzPicker.maxValue = 30
        juzPicker.wrapSelectorWheel = false
        juzPicker.displayedValues = (1..30).map { "الجزء $it" }.toTypedArray()

        dayPicker.minValue = 1
        dayPicker.maxValue = 365
        dayPicker.wrapSelectorWheel = false
        dayPicker.value = 30

        fun updateWird() {
            val juz = juzPicker.value
            val days = dayPicker.value
            tvWird.text = JuzData.formatDayRange(juz, days, 0)
        }
        updateWird()
        juzPicker.setOnValueChangedListener { _, _, _ -> updateWird() }
        dayPicker.setOnValueChangedListener { _, _, _ -> updateWird() }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(view)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        view.findViewById<Button>(R.id.btnCreateKhatma).setOnClickListener {
            val juz = juzPicker.value
            val days = dayPicker.value
            val name = etName.text.toString().trim()
            val finalName = if (name.isNotEmpty()) name else {
                val remainingJuz = 31 - juz
                when {
                    days <= remainingJuz -> "ختمة أجزاء من ج$juz"
                    days <= remainingJuz * 2 -> "ختمة أنصاف من ج$juz"
                    else -> "ختمة $days يومًا من ج$juz"
                }
            }
            val khatma = Khatma(name = finalName, startJuz = juz, totalDays = days, color = Khatma.pickColor(juz), riwaya = selectedRiwaya)
            KhatmaManager.add(requireContext(), khatma)
            dialog.dismiss()
            refreshList()
        }
        view.findViewById<Button>(R.id.btnCancelKhatma).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private class KhatmaListAdapter(
        private val items: List<Khatma>,
        private val onClick: (Khatma) -> Unit,
        private val onDelete: (Khatma) -> Unit
    ) : RecyclerView.Adapter<KhatmaListAdapter.VH>() {

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvKhatmaName)
            val tvRange: TextView = view.findViewById(R.id.tvKhatmaRange)
            val tvProgress: TextView = view.findViewById(R.id.tvKhatmaProgress)
            val tvDays: TextView = view.findViewById(R.id.tvKhatmaDays)
            val ivDelete: ImageView = view.findViewById(R.id.ivDeleteKhatma)
        }

        override fun onCreateViewHolder(p: ViewGroup, vt: Int): VH {
            val v = LayoutInflater.from(p.context).inflate(R.layout.item_khatma, p, false)
            return VH(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val k = items[pos]
            h.tvName.text = k.name

            val percentage = if (k.totalDays > 0) (k.currentDay * 100 / k.totalDays) else 0
            h.tvProgress.text = "تم $percentage%"
            h.tvDays.text = "اليوم ${k.currentDay + 1} من ${k.totalDays}"

            h.tvRange.text = JuzData.formatDayRange(k.startJuz, k.totalDays, k.currentDay)

            h.itemView.setOnClickListener { onClick(k) }
            h.ivDelete.setOnClickListener { onDelete(k) }
        }
    }
}

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

    private fun selectRiwaya(card: FrameLayout, tv: TextView, isSelected: Boolean) {
        if (isSelected) {
            card.setBackgroundResource(R.drawable.bg_primary_button)
            tv.setTextColor(android.graphics.Color.WHITE)
            tv.text = "${if (card.id == R.id.cardRiwayaWarsh) "ورش" else "حفص"} ✓"
        } else {
            card.setBackgroundResource(R.drawable.bg_segment_unselected)
            tv.setTextColor(android.graphics.Color.parseColor("#5E4B40"))
            tv.text = if (card.id == R.id.cardRiwayaWarsh) "ورش" else "حفص"
        }
    }

    private fun showAddKhatmaDialog() {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_khatma_setup, null)
        val etName = view.findViewById<EditText>(R.id.etKhatmaName)
        val juzPicker = view.findViewById<NumberPicker>(R.id.pickerStartJuz)
        val dayPicker = view.findViewById<NumberPicker>(R.id.pickerDays)
        val tvWird = view.findViewById<TextView>(R.id.tvWirdPreview)
        val cardHafs = view.findViewById<FrameLayout>(R.id.cardRiwayaHafs)
        val cardWarsh = view.findViewById<FrameLayout>(R.id.cardRiwayaWarsh)
        val tvHafs = view.findViewById<TextView>(R.id.tvRiwayaHafs)
        val tvWarsh = view.findViewById<TextView>(R.id.tvRiwayaWarsh)

        selectedRiwaya = "hafs"
        selectRiwaya(cardHafs, tvHafs, true)
        selectRiwaya(cardWarsh, tvWarsh, false)

        cardHafs.setOnClickListener {
            selectedRiwaya = "hafs"
            selectRiwaya(cardHafs, tvHafs, true)
            selectRiwaya(cardWarsh, tvWarsh, false)
        }
        cardWarsh.setOnClickListener {
            selectedRiwaya = "warsh"
            selectRiwaya(cardWarsh, tvWarsh, true)
            selectRiwaya(cardHafs, tvHafs, false)
        }

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
            h.tvDays.text = "اليوم ${k.currentDay} من ${k.totalDays}"

            h.tvRange.text = JuzData.formatDayRange(k.startJuz, k.totalDays, k.currentDay)

            h.itemView.setOnClickListener { onClick(k) }
            h.ivDelete.setOnClickListener { onDelete(k) }
        }
    }
}

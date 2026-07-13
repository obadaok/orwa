package com.urwah.dhikr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class AyahAdapter(
    private val ayahs: List<AyahData>,
    private val surahInfo: SurahInfo
) : RecyclerView.Adapter<AyahAdapter.ViewHolder>() {

    private val highlightColor = 0xFFE9DFCF.toInt()
    private val normalColor = 0xFFFFFFFF.toInt()
    private val mainHandler = Handler(Looper.getMainLooper())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: com.google.android.material.card.MaterialCardView = view.findViewById(R.id.cardAyah)
        val tvAyahNumber: TextView = view.findViewById(R.id.tvAyahNumber)
        val tvAyahText: TextView = view.findViewById(R.id.tvAyahText)
        val btnTafsir: ImageView = view.findViewById(R.id.btnTafsir)
        val btnShare: ImageView = view.findViewById(R.id.btnShare)
        val btnCopy: ImageView = view.findViewById(R.id.btnCopy)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ayah_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val ayah = ayahs[position]
        val context = holder.itemView.context

        holder.tvAyahNumber.text = "${ayah.number}"
        holder.tvAyahText.text = ayah.text

        holder.btnCopy.setOnClickListener {
            copyAyah(context, ayah)
        }

        holder.btnShare.setOnClickListener {
            shareAyah(context, ayah)
        }

        holder.btnTafsir.setOnClickListener {
            // TODO: فتح شاشة/نافذة التفسير الخاصة بهذه الآية
        }

        // ===== تحديد الآية بالضغط المطوّل: تظليل لحظي + نسخ تلقائي =====
        holder.card.setOnLongClickListener {
            highlightThenCopy(holder, ayah)
            true
        }
    }

    private fun highlightThenCopy(holder: ViewHolder, ayah: AyahData) {
        holder.card.setCardBackgroundColor(highlightColor)
        copyAyah(holder.itemView.context, ayah)

        mainHandler.postDelayed({
            holder.card.setCardBackgroundColor(normalColor)
        }, 250L)
    }

    private fun copyAyah(context: Context, ayah: AyahData) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("ayah", ayah.text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "تم نسخ الآية ${ayah.number}", Toast.LENGTH_SHORT).show()
    }

    private fun shareAyah(context: Context, ayah: AyahData) {
        val shareText = "${ayah.text} (${ayah.number})\n\n${surahInfo.nameArabic} - آية ${ayah.number}"
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(Intent.createChooser(shareIntent, "مشاركة الآية"))
    }

    override fun getItemCount() = ayahs.size
}

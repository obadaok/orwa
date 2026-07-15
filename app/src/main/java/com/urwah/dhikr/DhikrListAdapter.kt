package com.urwah.dhikr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class DhikrListAdapter(
    initialItems: List<DhikrItem>,
    private val categoryName: String = "",
    private val onAllCompleted: (() -> Unit)? = null,
    private val onProgressChanged: ((String, Int, Int) -> Unit)? = null
) : RecyclerView.Adapter<DhikrListAdapter.ViewHolder>() {

    private val currentList = initialItems.toMutableList()
    private val counts = MutableList(currentList.size) { 0 }
    private val completed = MutableList(currentList.size) { false }

    private var uthmanicFont: Typeface? = null
    private var progressPrefs: android.content.SharedPreferences? = null

    fun setSavedProgress(context: android.content.Context, savedCompleted: Set<Int>) {
        progressPrefs = context.getSharedPreferences("urwah_dhikr_progress", Context.MODE_PRIVATE)
        val completedIds = savedCompleted.flatMap { id ->
            currentList.filter { it.id == id }.map { currentList.indexOf(it) }
        }.filter { it >= 0 }
        for (i in completedIds) {
            counts[i] = currentList[i].repeats
            completed[i] = true
        }
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: View = view.findViewById(R.id.cardDhikrItem)
        val deleteBackground: View? = view.findViewById(R.id.deleteBackground)
        val txtText: TextView = view.findViewById(R.id.txtDhikrText)
        val containerVirtue: View = view.findViewById(R.id.containerVirtue)
        val txtVirtue: TextView = view.findViewById(R.id.txtVirtue)
        val txtReference: TextView = view.findViewById(R.id.txtReference)
        val circularCounter: CircularCounterView = view.findViewById(R.id.circularCounter)
        val btnCopy: ImageView = view.findViewById(R.id.btnCopy)
        val btnShare: ImageView = view.findViewById(R.id.btnShare)
        val btnFavorite: ImageView = view.findViewById(R.id.btnFavorite)
        val confettiView: ConfettiView = view.findViewById(R.id.confettiView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dhikr, parent, false)
        val holder = ViewHolder(view)
        uthmanicFont = ResourcesCompat.getFont(parent.context, QuranDataLoader.getUthmanicFontRes(parent.context))
        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = currentList[position]
        val context = holder.itemView.context
        val isDone = completed[position]

        holder.txtText.text = resolveDhikrText(context, item)
        holder.circularCounter.setProgress(counts[position], item.repeats)
        holder.card.alpha = if (isDone) 0.55f else 1f
        holder.card.isEnabled = !isDone

        if (item.arabic.contains("﴿")) {
            holder.txtText.setLineSpacing(14f, 1f)
        } else {
            holder.txtText.setLineSpacing(6f, 1f)
        }

        if (item.virtue.isNotBlank()) {
            holder.txtVirtue.text = item.virtue
            holder.containerVirtue.visibility = View.VISIBLE
        } else {
            holder.containerVirtue.visibility = View.GONE
        }

        if (item.reference.isNotBlank()) {
            holder.txtReference.text = item.reference
            holder.txtReference.visibility = View.VISIBLE
        } else {
            holder.txtReference.visibility = View.GONE
        }

        updateFavoriteIcon(holder.btnFavorite, item.id)

        holder.card.setOnClickListener {
            if (completed[position]) return@setOnClickListener

            val newCount = counts[position] + 1
            counts[position] = newCount
            holder.circularCounter.setProgress(newCount, item.repeats)

            HapticUtil.perform(context, holder.card)

            if (newCount >= item.repeats) {
                completed[position] = true
                saveItemCompleted(item.id)
                celebrate(holder)
                if (completed.all { it }) {
                    holder.itemView.postDelayed({
                        onAllCompleted?.invoke()
                    }, 1500)
                }
            }
        }

        holder.btnCopy.setOnClickListener {
            HapticUtil.perform(context, holder.btnCopy)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("dhikr", item.arabic)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "تم نسخ الذكر", Toast.LENGTH_SHORT).show()
        }

        holder.btnShare.setOnClickListener {
            HapticUtil.perform(context, holder.btnShare)
            val shareText = buildString {
                append(item.arabic)
                if (item.reference.isNotBlank()) {
                    append("\n\n(${item.reference})")
                }
            }
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            context.startActivity(Intent.createChooser(shareIntent, "مشاركة الذكر"))
        }

        holder.btnFavorite.setOnClickListener {
            HapticUtil.perform(context, holder.btnFavorite)
            val isFav = FavoritesManager.toggle(item.id)
            updateFavoriteIcon(holder.btnFavorite, item.id)
            val msg = if (isFav) "تمت الإضافة إلى المفضلة" else "تمت الإزالة من المفضلة"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    fun getItem(position: Int): DhikrItem? = currentList.getOrNull(position)

    override fun getItemCount() = currentList.size

    override fun onViewAttachedToWindow(holder: ViewHolder) {
        super.onViewAttachedToWindow(holder)
        val pos = holder.adapterPosition
        if (pos != RecyclerView.NO_POSITION && completed[pos]) {
            holder.card.alpha = 0.55f
            holder.card.isEnabled = false
        }
    }

    private fun applyDhikrFormatting(text: String): CharSequence {
        val sb = SpannableStringBuilder()
        val cleaned = text.replace(Regex("\\s*\\*\\s*"), "\n")
        var remaining = cleaned

        val basmalaRegex = Regex(
            "^(بِسْمِ اللَّهِ[^\\n]*?الرَّحِيمِ|أَعُوذُ بِاللَّهِ[^\\n]*?الرَّجِيمِ)\\s*"
        )
        basmalaRegex.find(remaining)?.let { match ->
            val basmala = SpannableString(match.value.trim())
            basmala.setSpan(StyleSpan(Typeface.BOLD), 0, basmala.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append(basmala)
            sb.append("\n\n")
            remaining = remaining.removeRange(match.range)
        }

        sb.append(applyQuranFont(remaining))
        return sb
    }

    private fun applyQuranFont(text: String): CharSequence {
        val sb = SpannableStringBuilder()
        val regex = Regex("﴿[^﴾]*﴾")
        var lastEnd = 0

        for (match in regex.findAll(text)) {
            if (match.range.first > lastEnd) {
                sb.append(SpannableString(text.substring(lastEnd, match.range.first)))
            }

            if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append(" ")

            val inner = match.value.removePrefix("﴿").removeSuffix("﴾")
            val verse = SpannableString(inner)
            uthmanicFont?.let {
                verse.setSpan(CustomTypefaceSpan(it), 0, verse.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            verse.setSpan(
                RelativeSizeSpan(VERSE_RELATIVE_SIZE), 0, verse.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            sb.append(verse)

            lastEnd = match.range.last + 1
            if (lastEnd < text.length && text[lastEnd] != '\n') sb.append(" ")
        }

        if (lastEnd < text.length) {
            sb.append(SpannableString(text.substring(lastEnd)))
        }

        return sb
    }

    private fun resolveDhikrText(context: Context, item: DhikrItem): CharSequence {
        val title = item.title
        if (!title.startsWith("سورة")) return applyDhikrFormatting(item.arabic)

        val surahName = title.removePrefix("سورة ").trim()
        val surahNum = SurahDataProvider.allSurahs
            .sortedByDescending { it.name.length }
            .find {
                it.name.contains(surahName, ignoreCase = true) || surahName.contains(it.name, ignoreCase = true)
            }?.number ?: return applyDhikrFormatting(item.arabic)

        val surahData = com.urwah.dhikr.QuranDataLoader.getSurah(context, surahNum) ?: return applyDhikrFormatting(item.arabic)
        val uthmanicFont = ResourcesCompat.getFont(context, QuranDataLoader.getUthmanicFontRes(context)) ?: return applyDhikrFormatting(item.arabic)
        val hindi = arrayOf("٠", "١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩")
        val prefs = context.getSharedPreferences("urwah_quran", Context.MODE_PRIVATE)
        val singleLine = prefs.getBoolean("ayah_single_line", true)

        val sb = SpannableStringBuilder()
        if (surahNum != 9) {
            val bStart = sb.length
            sb.append("بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ")
            sb.setSpan(CustomTypefaceSpan(uthmanicFont), bStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(RelativeSizeSpan(1.22f), bStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append("\n")
        }
        if (singleLine) {
            for (i in surahData.ayahs.indices) {
                val ayah = surahData.ayahs[i]
                val ayahStart = sb.length
                sb.append(ayah.text)
                sb.setSpan(CustomTypefaceSpan(uthmanicFont), ayahStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(RelativeSizeSpan(1.22f), ayahStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                val numStr = ayah.number.toString().map { hindi[it - '0'] }.joinToString("")
                sb.append("  $numStr")
                if (i != surahData.ayahs.lastIndex) {
                    sb.append("\n\n")
                }
            }
        } else {
            for (i in surahData.ayahs.indices) {
                val ayah = surahData.ayahs[i]
                val ayahStart = sb.length
                sb.append(ayah.text)
                sb.setSpan(CustomTypefaceSpan(uthmanicFont), ayahStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(RelativeSizeSpan(1.22f), ayahStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                val numStr = ayah.number.toString().map { hindi[it - '0'] }.joinToString("")
                sb.append("  $numStr  ")
            }
        }
        return sb
    }

    companion object {
        private const val VERSE_RELATIVE_SIZE = 1.22f

        private const val PREFS_NAME = "urwah_dhikr_progress"
        private const val KEY_LAST_DATE = "last_date"

        fun getCompletedIds(context: android.content.Context, category: String): Set<Int> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getStringSet(category, emptySet())?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
        }

        fun saveCompletedIds(context: android.content.Context, category: String, ids: Set<Int>) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putStringSet(category, ids.map { it.toString() }.toSet()).apply()
        }

        fun isCategoryComplete(context: android.content.Context, category: String): Boolean {
            val completed = getCompletedIds(context, category)
            val total = DhikrDataProvider.getDhikrs(category).size
            return total > 0 && completed.size >= total
        }

        fun resetCategory(context: android.content.Context, category: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(category).apply()
        }

        fun checkAndResetDaily(context: android.content.Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            val lastDate = prefs.getString(KEY_LAST_DATE, "")
            if (lastDate != today) {
                prefs.edit().clear().putString(KEY_LAST_DATE, today).apply()
            }
        }

        fun resetAllProgress(context: android.content.Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().clear().putString(KEY_LAST_DATE,
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            ).apply()
        }
    }

    private fun saveItemCompleted(itemId: Int) {
        val prefs = progressPrefs ?: return
        val set = prefs.getStringSet(categoryName, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        set.add(itemId.toString())
        prefs.edit().putStringSet(categoryName, set).apply()
    }

    private fun updateFavoriteIcon(imageView: ImageView, itemId: Int) {
        val isFav = FavoritesManager.isFavorite(itemId)
        imageView.alpha = if (isFav) 1f else 0.65f
        imageView.setColorFilter(
            if (isFav) ContextCompat.getColor(imageView.context, R.color.favorite_active) else ContextCompat.getColor(imageView.context, R.color.favorite_inactive)
        )
    }

    private fun celebrate(holder: ViewHolder) {
        val confetti = holder.confettiView
        confetti.burst(
            confetti.width / 2f,
            confetti.height / 2f
        )
    }


}

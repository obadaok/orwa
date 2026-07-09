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
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class DhikrListAdapter(
    initialItems: List<DhikrItem>,
    private val onAllCompleted: (() -> Unit)? = null
) : RecyclerView.Adapter<DhikrListAdapter.ViewHolder>() {

    private val currentList = initialItems.toMutableList()
    private val counts = MutableList(currentList.size) { 0 }
    private val completed = MutableList(currentList.size) { false }

    private var uthmanicFont: Typeface? = null

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: CardView = view.findViewById(R.id.cardDhikrItem)
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
        uthmanicFont = ResourcesCompat.getFont(parent.context, R.font.uthmanic_hafs)
        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = currentList[position]
        val context = holder.itemView.context
        val isDone = completed[position]

        holder.txtText.text = applyDhikrFormatting(item.arabic)
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

            holder.card.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

            if (newCount >= item.repeats) {
                completed[position] = true
                celebrate(holder)
                animateCompletionThenReorder(holder, position)
                if (completed.all { it }) {
                    holder.itemView.postDelayed({
                        onAllCompleted?.invoke()
                    }, 1500)
                }
            }
        }

        holder.btnCopy.setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("dhikr", item.arabic)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "تم نسخ الذكر", Toast.LENGTH_SHORT).show()
        }

        holder.btnShare.setOnClickListener {
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
            val isFav = FavoritesManager.toggle(item.id)
            updateFavoriteIcon(holder.btnFavorite, item.id)
            val msg = if (isFav) "تمت الإضافة إلى المفضلة" else "تمت الإزالة من المفضلة"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

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
        var matched = false

        for (match in regex.findAll(text)) {
            matched = true
            if (match.range.first > lastEnd) {
                sb.append(SpannableString(text.substring(lastEnd, match.range.first)))
            }

            // فصل الآية في فقرتها الخاصة حتى لا تشترك سطراً مع نص عادي بخط مختلف
            if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append("\n")

            val verse = SpannableString(match.value)
            uthmanicFont?.let {
                verse.setSpan(CustomTypefaceSpan(it), 0, verse.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            // لا تغميق صناعي فوق خط عثماني مخصص، لأنه يشوّه سماكة الحروف
            // ويُظهرها غير متناسقة مع باقي النص. حجم نسبي موحّد بدل النص العادي
            // لأن خط عثماني طه بالتشكيل الكامل يبدو أكبر بصرياً من خط النسخ بنفس الحجم.
            verse.setSpan(
                RelativeSizeSpan(VERSE_RELATIVE_SIZE), 0, verse.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            sb.append(verse)

            lastEnd = match.range.last + 1
            if (lastEnd < text.length && text[lastEnd] != '\n') sb.append("\n")
        }

        if (lastEnd < text.length) {
            sb.append(SpannableString(text.substring(lastEnd)))
        }

        return if (matched) sb else text
    }

    companion object {
        // اضبط هذه القيمة إذا بدا حجم الآيات أكبر أو أصغر من باقي النص بعد التجربة على الجهاز
        private const val VERSE_RELATIVE_SIZE = 0.94f
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

    private fun animateCompletionThenReorder(holder: ViewHolder, position: Int) {
        holder.card.postDelayed({
            holder.card.animate()
                .alpha(0.3f)
                .scaleX(0.92f)
                .scaleY(0.92f)
                .setDuration(350)
                .withEndAction {
                    holder.card.alpha = 0.55f
                    holder.card.scaleX = 1f
                    holder.card.scaleY = 1f
                    moveCompletedItemToEnd(holder, position)
                }
                .start()
        }, 900)
    }

    private fun moveCompletedItemToEnd(holder: ViewHolder, position: Int) {
        if (position < 0 || position >= currentList.size) return

        val layoutManager = (holder.itemView.parent as? RecyclerView)?.layoutManager as? LinearLayoutManager
        val firstVisible = layoutManager?.findFirstVisibleItemPosition() ?: 0
        val offset = layoutManager?.findViewByPosition(firstVisible)?.top ?: 0

        val item = currentList.removeAt(position)
        val count = counts.removeAt(position)
        val done = completed.removeAt(position)

        currentList.add(item)
        counts.add(count)
        completed.add(done)

        notifyItemMoved(position, currentList.size - 1)
        notifyItemRangeChanged(position, currentList.size - position)

        layoutManager?.scrollToPositionWithOffset(firstVisible, offset)
    }
}

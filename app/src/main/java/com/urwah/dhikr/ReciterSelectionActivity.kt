package com.urwah.dhikr

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.urwah.dhikr.audio.Reciter
import com.urwah.dhikr.audio.ReciterCatalog
import kotlin.math.roundToInt

/**
 * شاشة اختيار القارئ — بطاقات أنيقة بهوية عروة مع بحث ومفضلة
 * وفرز وفلترة بالرواية وإبراز آخر قارئ مستخدم.
 * تُرجع القارئ المختار عبر onActivityResult.
 */
class ReciterSelectionActivity : AppCompatActivity() {

    private lateinit var reciterPrefs: android.content.SharedPreferences
    private val favorites = mutableSetOf<Int>()
    private var query = ""
    private var favOnly = false
    private var sortByName = false
    private var currentReciterId = 0
    private var activeRiwaya = "الكل"
    private var adapter: ReciterAdapter? = null
    private var chipViews = mutableListOf<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reciter_selection)

        reciterPrefs = getSharedPreferences("urwah_audio", Context.MODE_PRIVATE)
        favorites.addAll(
            reciterPrefs.getStringSet("favorite_reciters", emptySet())
                ?.mapNotNull { it.toIntOrNull() } ?: emptyList()
        )
        currentReciterId = reciterPrefs.getInt("selected_reciter", 0)

        val rv = findViewById<RecyclerView>(R.id.rvReciterGrid)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = ReciterAdapter(
            currentReciterId = currentReciterId,
            favorites = favorites,
            onFavToggle = ::toggleFavorite,
            onSelect = ::onReciterSelected
        )
        rv.adapter = adapter

        findViewById<View>(R.id.btnReciterBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnReciterFilterFav).setOnClickListener {
            favOnly = !favOnly
            refreshFilterButton()
            applyFilter()
        }
        findViewById<View>(R.id.btnReciterSort).setOnClickListener {
            sortByName = !sortByName
            refreshSortButton()
            applyFilter()
        }
        findViewById<View>(R.id.btnReciterClearSearch).setOnClickListener {
            findViewById<EditText>(R.id.etReciterSearch).setText("")
        }

        val et = findViewById<EditText>(R.id.etReciterSearch)
        et.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                query = s?.toString()?.trim() ?: ""
                findViewById<View>(R.id.btnReciterClearSearch).isVisible = query.isNotEmpty()
                applyFilter()
            }
        })

        setupRiwayaChips()
        applyFilter()
    }

    private fun setupRiwayaChips() {
        val row = findViewById<LinearLayout>(R.id.riwayaChipsRow)
        row.removeAllViews()
        chipViews.clear()
        val riwayas = listOf("الكل") + ReciterCatalog.reciters.map { it.riwaya }.distinct()
        riwayas.forEach { riwaya ->
            val chip = createChip(riwaya)
            row.addView(chip)
            chipViews.add(chip)
        }
    }

    private fun createChip(label: String): View {
        val chip = TextView(this).apply {
            text = label
            isClickable = true
            isFocusable = true
            setTextColor(resources.getColor(if (label == activeRiwaya) R.color.urwah_surface else R.color.urwah_thread_brown))
            setBackgroundResource(if (label == activeRiwaya) R.drawable.bg_primary_button else R.drawable.bg_chip_neo)
            textSize = 12f
            setPadding(dp(16f), dp(7f), dp(16f), dp(7f))
        }
        val lp = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        lp.marginEnd = dp(8f)
        chip.layoutParams = lp
        chip.setOnClickListener {
            activeRiwaya = label
            chipViews.forEach { c ->
                val active = (c as TextView).text.toString() == label
                c.setBackgroundResource(if (active) R.drawable.bg_primary_button else R.drawable.bg_chip_neo)
                c.setTextColor(resources.getColor(if (active) R.color.urwah_surface else R.color.urwah_thread_brown))
            }
            applyFilter()
        }
        return chip
    }

    private fun refreshFilterButton() {
        findViewById<ImageView>(R.id.btnReciterFilterFav).setImageResource(
            if (favOnly) R.drawable.ic_favorite_24dp else R.drawable.ic_favorite_border_24dp
        )
        findViewById<ImageView>(R.id.btnReciterFilterFav).alpha = if (favOnly) 1f else 0.5f
    }

    private fun refreshSortButton() {
        findViewById<ImageView>(R.id.btnReciterSort).alpha = if (sortByName) 1f else 0.5f
    }

    private fun applyFilter() {
        val all = ReciterCatalog.reciters
        var filtered = all.filter { r ->
            val matchQuery = query.isEmpty() ||
                r.nameArabic.contains(query, ignoreCase = true) ||
                r.nameEnglish.contains(query, ignoreCase = true) ||
                r.riwaya.contains(query, ignoreCase = true)
            val matchRiwaya = activeRiwaya == "الكل" || r.riwaya == activeRiwaya
            val matchFav = !favOnly || r.id in favorites
            matchQuery && matchRiwaya && matchFav
        }
        if (sortByName) {
            filtered = filtered.sortedBy { it.nameArabic }
        } else {
            val current = filtered.find { it.id == currentReciterId }
            val rest = filtered.filter { it.id != currentReciterId }
            filtered = if (current != null) listOf(current) + rest else rest
        }
        adapter?.submit(filtered)
        findViewById<TextView>(R.id.tvReciterEmpty).isVisible = filtered.isEmpty()
        findViewById<RecyclerView>(R.id.rvReciterGrid).isVisible = filtered.isNotEmpty()
    }

    private fun toggleFavorite(reciter: Reciter) {
        if (reciter.id in favorites) {
            favorites.remove(reciter.id)
        } else {
            favorites.add(reciter.id)
        }
        reciterPrefs.edit().putStringSet(
            "favorite_reciters", favorites.map { it.toString() }.toSet()
        ).apply()
        applyFilter()
    }

    private fun onReciterSelected(reciter: Reciter) {
        reciterPrefs.edit().putInt("selected_reciter", reciter.id).apply()
        val resultIntent = Intent().putExtra(EXTRA_RECITER_ID, reciter.id)
        setResult(RESULT_OK, resultIntent)
        finish()
        overridePendingTransition(0, android.R.anim.fade_out)
    }

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).roundToInt()

    private class ReciterAdapter(
        private var currentReciterId: Int,
        private val favorites: MutableSet<Int>,
        private val onFavToggle: (Reciter) -> Unit,
        private val onSelect: (Reciter) -> Unit
    ) : RecyclerView.Adapter<ReciterAdapter.VH>() {

        private var items: List<Reciter> = ReciterCatalog.reciters

        fun submit(newItems: List<Reciter>) {
            items = newItems
            notifyDataSetChanged()
        }

        class VH(view: View) : RecyclerView.ViewHolder(view)

        override fun onCreateViewHolder(p: ViewGroup, vt: Int): VH = VH(
            LayoutInflater.from(p.context).inflate(R.layout.item_reciter_card, p, false)
        )

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val r = items[pos]
            val item = h.itemView
            val isCurrent = r.id == currentReciterId
            val isFav = r.id in favorites

            item.alpha = 0f
            item.translationY = item.context.resources.displayMetrics.density * 14f
            item.animate().alpha(1f).translationY(0f)
                .setDuration(220)
                .setStartDelay((pos % 6) * 40L)
                .start()

            item.findViewById<TextView>(R.id.tvReciterName).text = r.nameArabic
            item.findViewById<TextView>(R.id.tvReciterSub).text = "${r.riwaya} • ${r.bitrateDisplay()}"
            item.findViewById<TextView>(R.id.tvReciterAvatar).text =
                r.nameArabic.take(1)
            item.findViewById<TextView>(R.id.tvReciterQuality).text = if (isCurrent) "قيد التشغيل" else "بث مباشر"
            item.findViewById<TextView>(R.id.tvReciterQuality).setTextColor(
                item.context.getColor(
                    if (isCurrent) R.color.urwah_thread_brown else R.color.urwah_thread_light
                )
            )
            item.findViewById<TextView>(R.id.tvReciterLastUsed).isVisible = isCurrent

            val ivFav = item.findViewById<ImageView>(R.id.ivReciterFavorite)
            ivFav.setImageResource(
                if (isFav) R.drawable.ic_favorite_24dp else R.drawable.ic_favorite_border_24dp
            )
            ivFav.setColorFilter(
                item.context.getColor(
                    if (isFav) R.color.favorite_active else R.color.urwah_thread_light
                )
            )
            ivFav.setOnClickListener { onFavToggle(r) }

            item.findViewById<ImageView>(R.id.ivReciterSelected).isVisible = isCurrent

            item.setOnClickListener { onSelect(r) }
        }
    }

    companion object {
        const val EXTRA_RECITER_ID = "extra_reciter_id"
    }
}

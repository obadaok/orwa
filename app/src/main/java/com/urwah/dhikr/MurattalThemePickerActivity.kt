package com.urwah.dhikr

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * صفحة اختيار مظهر المرتّل — بطاقات بمعاينة حقيقية لكل مظهر.
 * اختيار أي مظهر يُحفظ فورًا ويكون جاهزًا لتطبيقه فورًا عند العودة.
 */
class MurattalThemePickerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_murattal_theme_picker)

        findViewById<View>(R.id.btnMurattalThemeBack).setOnClickListener { finish() }

        val rv = findViewById<RecyclerView>(R.id.rvMurattalThemes)
        rv.layoutManager = GridLayoutManager(this, 2)
        rv.adapter = ThemeAdapter { theme ->
            MurattalThemeManager.select(this, theme.id)
            rv.adapter?.notifyDataSetChanged()
            setResult(RESULT_OK, Intent().putExtra(EXTRA_SELECTED_THEME, theme.id))
        }
    }

    companion object {
        const val EXTRA_SELECTED_THEME = "extra_selected_theme"
    }
}

class ThemeAdapter(
    private val onSelect: (MurattalTheme) -> Unit
) : RecyclerView.Adapter<ThemeAdapter.ThemeHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThemeHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_murattal_theme_card, parent, false)
        return ThemeHolder(view)
    }

    override fun getItemCount(): Int = MurattalThemeManager.themes.size

    override fun onBindViewHolder(holder: ThemeHolder, position: Int) {
        val context = holder.itemView.context
        val theme = MurattalThemeManager.themes[position]
        val palette = MurattalThemeManager.palette(context, theme)

        holder.preview.palette = palette
        holder.name.text = theme.name
        holder.check.visibility = if (theme.id == MurattalThemeManager.current(context).id) {
            View.VISIBLE
        } else {
            View.GONE
        }
        holder.itemView.setOnClickListener { onSelect(theme) }
    }

    class ThemeHolder(view: View) : RecyclerView.ViewHolder(view) {
        val preview: MurattalThemePreview = view.findViewById(R.id.themePreview)
        val name: TextView = view.findViewById(R.id.tvThemeName)
        val check: ImageView = view.findViewById(R.id.ivThemeSelected)
    }
}
package me.nomi.urdutyper.presentation.ui.type.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import me.nomi.urdutyper.R

/**
 * Background library tiles (Canva phase 3). One entry per in-app template
 * background style, in the same order as TypeFragment.styleRes: the plan's
 * six first, then ink/dusk, demo tiles and picture styles.
 */
data class BackgroundEntry(
    val styleName: String,
    val drawableRes: Int,
    val label: String
)

val BACKGROUND_ENTRIES = listOf(
    BackgroundEntry("DARK_GLOW", R.drawable.template_bg_dark_glow, "Dark Glow"),
    BackgroundEntry("RAIN_GLASS", R.drawable.template_bg_rain, "Rain Glass"),
    BackgroundEntry("AUTUMN", R.drawable.template_bg_autumn, "Autumn"),
    BackgroundEntry("PAPER", R.drawable.template_bg_paper, "Paper"),
    BackgroundEntry("TRUCK_ART", R.drawable.template_bg_truck, "Truck Art"),
    BackgroundEntry("FRAMED", R.drawable.template_bg_framed, "Framed Arch"),
    BackgroundEntry("INK_DARK", R.drawable.template_bg_ink, "Ink Dark"),
    BackgroundEntry("DUSK", R.drawable.template_bg_dusk, "Dusk"),
    BackgroundEntry("DEMO_GLOW", R.drawable.template_bg_demo_glow, "Demo Glow"),
    BackgroundEntry("DEMO_RAIN", R.drawable.template_bg_demo_rain, "Demo Rain"),
    BackgroundEntry("DEMO_AUTUMN", R.drawable.template_bg_demo_autumn, "Demo Autumn"),
    BackgroundEntry("DEMO_PAPER", R.drawable.template_bg_demo_paper, "Demo Paper"),
    BackgroundEntry("PIC_GLOW", R.drawable.template_bg_pic_glow, "Pic Glow"),
    BackgroundEntry("PIC_RAIN", R.drawable.template_bg_pic_rain, "Pic Rain"),
    BackgroundEntry("PIC_AUTUMN", R.drawable.template_bg_pic_autumn, "Pic Autumn"),
    BackgroundEntry("PIC_PAPER", R.drawable.template_bg_pic_paper, "Pic Paper"),
    BackgroundEntry("PIC_INK", R.drawable.template_bg_pic_ink, "Pic Ink"),
    BackgroundEntry("PIC_TRUCK", R.drawable.template_bg_pic_truck, "Pic Truck"),
    BackgroundEntry("PIC_FRAMED", R.drawable.template_bg_pic_arch, "Pic Arch"),
    BackgroundEntry("PIC_FLORAL", R.drawable.template_bg_pic_floral, "Pic Floral"),
    BackgroundEntry("PIC_VINTAGE", R.drawable.template_bg_pic_vintage, "Pic Vintage"),
    BackgroundEntry("PIC_AZURE", R.drawable.template_bg_pic_azure, "Pic Azure"),
    BackgroundEntry("PIC_VINTAGE2", R.drawable.template_bg_pic_vintage2, "Pic Vintage 2"),
    BackgroundEntry("PIC_VINTAGE3", R.drawable.template_bg_pic_vintage3, "Pic Vintage 3")
)

class BackgroundsAdapter(
    private val onPick: (BackgroundEntry) -> Unit
) : RecyclerView.Adapter<BackgroundsAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val preview: View = view.findViewById(R.id.backgroundPreview)
        val name: TextView = view.findViewById(R.id.backgroundName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_background, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = BACKGROUND_ENTRIES[position]
        holder.preview.setBackgroundResource(entry.drawableRes)
        holder.name.text = entry.label
        holder.itemView.setOnClickListener { onPick(entry) }
    }

    override fun getItemCount(): Int = BACKGROUND_ENTRIES.size
}

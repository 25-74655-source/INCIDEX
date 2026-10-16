package com.example.incidex

import android.graphics.Color
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat

object IncidentIcons {

    // Change this one value to recolor every icon in the app.
    const val ICON_BLUE = "#1E3A8A"

    @DrawableRes
    fun iconFor(type: String): Int {
        val t = type.lowercase()
        return when {
            "accident" in t || "crash" in t -> R.drawable.ic_accident
            "traffic" in t -> R.drawable.ic_traffic
            "flood" in t -> R.drawable.ic_flooding
            "closure" in t || "construction" in t -> R.drawable.ic_closure
            else -> R.drawable.ic_hazard
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun colorFor(type: String): Int = Color.parseColor(ICON_BLUE)
}

/** Puts a small tinted icon at the start of a TextView (replaces emoji prefixes). */
fun TextView.setStartIcon(@DrawableRes res: Int, color: Int, sizeDp: Int = 16, paddingDp: Int = 6) {
    val d = ContextCompat.getDrawable(context, res)?.mutate() ?: return
    val density = resources.displayMetrics.density
    val px = (sizeDp * density).toInt()
    d.setBounds(0, 0, px, px)
    d.setTint(color)
    setCompoundDrawablesRelative(d, null, null, null)
    compoundDrawablePadding = (paddingDp * density).toInt()
}

/** Puts a small tinted icon above the text of a TextView. */
fun TextView.setTopIcon(@DrawableRes res: Int, color: Int, sizeDp: Int = 32, paddingDp: Int = 6) {
    val d = ContextCompat.getDrawable(context, res)?.mutate() ?: return
    val density = resources.displayMetrics.density
    val px = (sizeDp * density).toInt()
    d.setBounds(0, 0, px, px)
    d.setTint(color)
    setCompoundDrawables(null, d, null, null)
    compoundDrawablePadding = (paddingDp * density).toInt()
}
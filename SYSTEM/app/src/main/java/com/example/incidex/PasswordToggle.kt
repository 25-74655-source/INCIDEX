package com.example.incidex

import android.annotation.SuppressLint
import android.graphics.Color
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.MotionEvent
import android.widget.EditText
import androidx.core.content.ContextCompat

@SuppressLint("ClickableViewAccessibility")
fun EditText.enablePasswordToggle(tint: Int = Color.parseColor("#64748B")) {
    val density = resources.displayMetrics.density
    val iconPx = (22 * density).toInt()
    var visible = false

    fun render() {
        val icon = ContextCompat.getDrawable(
            context,
            if (visible) R.drawable.ic_eye else R.drawable.ic_eye_off
        )?.mutate() ?: return
        icon.setBounds(0, 0, iconPx, iconPx)
        icon.setTint(tint)

        val d = compoundDrawablesRelative
        setCompoundDrawablesRelative(d[0], d[1], icon, d[3])
        compoundDrawablePadding = (8 * density).toInt()

        val cursor = selectionStart
        transformationMethod =
            if (visible) HideReturnsTransformationMethod.getInstance()
            else PasswordTransformationMethod.getInstance()
        if (cursor >= 0) setSelection(cursor)
    }

    render() // start hidden, with the "eye off" icon

    setOnTouchListener { v, event ->
        if (event.action == MotionEvent.ACTION_UP) {
            val icon = compoundDrawablesRelative[2]
            if (icon != null) {
                val rtl = layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL
                val touchWidth = icon.bounds.width() + paddingEnd + (8 * density).toInt()
                val hit = if (rtl) event.x <= touchWidth else event.x >= width - touchWidth
                if (hit) {
                    visible = !visible
                    render()
                    v.performClick()
                    return@setOnTouchListener true
                }
            }
        }
        false
    }
}
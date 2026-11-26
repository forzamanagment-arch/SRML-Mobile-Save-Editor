package com.srmlmobile.saveeditor

import android.app.Activity
import android.app.AlertDialog
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.graphics.Color

object ItemPickerDialog {
    fun show(
        activity: Activity,
        currentId: Long,
        currentCount: Long,
        waterOnly: Boolean = false,
        onSelect: (Long, Long) -> Unit
    ) {
        val dark = activity.getSharedPreferences("srml_settings", Activity.MODE_PRIVATE).getBoolean("dark_theme", true)
        val russian = activity.getSharedPreferences("srml_settings", Activity.MODE_PRIVATE).getBoolean("russian", false)
        val surfaceColor = if (dark) Color.rgb(34,39,46) else surfaceColor
        val textColor = if (dark) Color.rgb(244,242,236) else textColor
        val mutedTextColor = if (dark) Color.rgb(177,187,198) else mutedTextColor
        val borderColor = if (dark) Color.rgb(65,73,83) else borderColor
        val accentColor = Color.rgb(49,73,110)
        val fallbackColor = if (dark) Color.rgb(43,49,58) else fallbackColor
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 16), dp(activity, 16), dp(activity, 16), dp(activity, 14))
            background = rounded(activity, surfaceColor, borderColor, 28)
        }
        val headerBar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(activity, Color.rgb(232,76,138), Color.TRANSPARENT, 16)
        }
        val header = TextView(activity).apply {
            text = if (waterOnly) "CHOOSE WATER TYPE" else "CHOOSE AN ITEM"
            textSize = 13f
            typeface = GameAssets.typeface(activity, true)
            letterSpacing = .08f
            setTextColor(Color.WHITE)
            setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 8), dp(activity, 12))
        }
        headerBar.addView(header, LinearLayout.LayoutParams(0, -2, 1f))
        val closeButton = TextView(activity).apply {
            text = "×"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(activity, 10), 0, dp(activity, 12), dp(activity, 2))
        }
        headerBar.addView(closeButton, LinearLayout.LayoutParams(dp(activity, 44), dp(activity, 44)))
        root.addView(headerBar)
        val search = EditText(activity).apply {
            hint = "Search items…"
            setSingleLine(true)
            typeface = GameAssets.typeface(activity)
            textSize = 14f
            setPadding(dp(activity, 12), dp(activity, 7), dp(activity, 12), dp(activity, 7))
            setTextColor(textColor)
            setHintTextColor(mutedTextColor)
            background = rounded(activity, surfaceColor, borderColor, 999)
        }
        val searchParams = LinearLayout.LayoutParams(-1, dp(activity, 44))
        searchParams.topMargin = dp(activity, 9)
        root.addView(search, searchParams)
        val chips = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val chipScroll = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        }
        root.addView(chipScroll, LinearLayout.LayoutParams(-1, dp(activity, 42)))
        root.addView(TextView(activity).apply {
            text = "Some catalog IDs are community references and may not be supported by the mobile build."
            textSize = 10f
            typeface = GameAssets.typeface(activity)
            setTextColor(mutedTextColor)
            setPadding(dp(activity, 3), dp(activity, 2), dp(activity, 3), dp(activity, 4))
        })
        val results = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(activity).apply {
            isVerticalScrollBarEnabled = false
            setBackgroundColor(Color.TRANSPARENT)
            addView(results)
        }
        val scrollParams = LinearLayout.LayoutParams(-1, dp(activity, 280))
        scrollParams.topMargin = dp(activity, 5)
        root.addView(scroll, scrollParams)

        val quantityRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val quantity = EditText(activity).apply {
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText(currentCount.toString())
            gravity = Gravity.CENTER
            textSize = 16f
            typeface = GameAssets.typeface(activity, true)
            setTextColor(textColor)
            setHintTextColor(mutedTextColor)
            background = rounded(activity, surfaceColor, borderColor, 999)
        }
        var selectedId = currentId
        var category = "All"
        fun qtyButton(label: String, action: () -> Unit) = Button(activity).apply {
            text = label
            isAllCaps = false
            textSize = 17f
            typeface = GameAssets.typeface(activity, true)
            setTextColor(textColor)
            background = rounded(activity, surfaceColor, accentColor, 999)
            setOnClickListener { action() }
        }
        quantityRow.addView(qtyButton("−") {
            val n = quantity.text.toString().toLongOrNull() ?: 0L
            quantity.setText((n - 1).coerceAtLeast(0).toString())
        }, LinearLayout.LayoutParams(dp(activity, 44), dp(activity, 40)))
        val qParams = LinearLayout.LayoutParams(dp(activity, 112), dp(activity, 40))
        qParams.setMargins(dp(activity, 8), 0, dp(activity, 8), 0)
        quantityRow.addView(quantity, qParams)
        quantityRow.addView(qtyButton("+") {
            val n = quantity.text.toString().toLongOrNull() ?: 0L
            if (n < 4294967295L) quantity.setText((n + 1).toString())
        }, LinearLayout.LayoutParams(dp(activity, 44), dp(activity, 40)))
        val quantityParams = LinearLayout.LayoutParams(-1, dp(activity, 48))
        quantityParams.topMargin = dp(activity, 7)
        root.addView(quantityRow, quantityParams)

        fun categoryMatches(id: Long, name: String): Boolean {
            if (waterOnly) return id == 0L || id == 48L || id == 113L
            val n = name.lowercase()
            return when (category) {
                "Food" -> id in setOf(8L,9L,16L,17L,18L,19L,20L,26L,27L,28L,29L,49L,50L,68L,69L,70L,71L,88L,100L,110L,111L,148L,149L,165L)
                "Plorts" -> n.contains("plort")
                "Resources" -> id in 11000L..11999L
                "Fashion Pod" -> id in 12000L..12099L
                "Ornament" -> id in 14000L..14999L
                else -> true
            }
        }
        fun updateChipAppearance() {
            for (index in 0 until chips.childCount) {
                val chip = chips.getChildAt(index) as? Button ?: continue
                val active = chip.text.toString() == category
                chip.setTextColor(if (active) Color.WHITE else textColor)
                chip.background = rounded(activity,
                    if (active) Color.rgb(232,76,138) else surfaceColor,
                    if (active) Color.TRANSPARENT else borderColor, 999)
            }
        }
        fun render() {
            results.removeAllViews()
            val query = search.text.toString().trim().lowercase()
            val items = ItemCatalog.names.entries.filter { (id, name) ->
                val localized = ItemCatalog.localizedName(id, russian)
                (if (waterOnly) id == 0L || id == 48L || id == 113L else ItemCatalog.isAllowedInRegularSlot(id)) &&
                    categoryMatches(id, localized) &&
                    (query.isEmpty() || localized.lowercase().contains(query) || id.toString().contains(query))
            }
            items.chunked(2).forEach { pair ->
                val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
                pair.forEach { (id, name) ->
                    val selected = id == selectedId
                    val displayName = ItemCatalog.localizedName(id, false)
                    val tile = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        setPadding(dp(activity, 5), dp(activity, 6), dp(activity, 5), dp(activity, 5))
                        background = rounded(activity, surfaceColor,
                            if (selected) accentColor else borderColor, 14)
                        setOnClickListener { selectedId = id; render() }
                    }
                    val bitmap = GameAssets.icon(activity, *ItemCatalog.iconHints(id).toTypedArray())
                    if (bitmap != null) {
                        tile.addView(ImageView(activity).apply { setImageBitmap(bitmap) },
                            LinearLayout.LayoutParams(dp(activity, 46), dp(activity, 46)))
                    } else {
                        tile.addView(TextView(activity).apply {
                            text = "?"
                            textSize = 27f
                            gravity = Gravity.CENTER
                            setTextColor(accentColor)
                            background = rounded(activity, fallbackColor, borderColor, 12)
                        }, LinearLayout.LayoutParams(dp(activity, 46), dp(activity, 46)))
                    }
                    tile.addView(TextView(activity).apply {
                        text = displayName
                        textSize = 10f
                        typeface = GameAssets.typeface(activity, true)
                        maxLines = 2
                        gravity = Gravity.CENTER
                        setTextColor(textColor)
                    }, LinearLayout.LayoutParams(-1, dp(activity, 31)))
                    tile.addView(TextView(activity).apply {
                        text = "ID: $id"
                        textSize = 9f
                        typeface = GameAssets.typeface(activity)
                        setTextColor(mutedTextColor)
                    })
                    val params = LinearLayout.LayoutParams(0, dp(activity, 102), 1f)
                    params.setMargins(dp(activity, 3), dp(activity, 3), dp(activity, 3), dp(activity, 3))
                    row.addView(tile, params)
                }
                if (pair.size == 1) row.addView(View(activity), LinearLayout.LayoutParams(0, dp(activity, 102), 1f))
                results.addView(row)
            }
        }
        val categories = if (waterOnly) listOf("All") else listOf("All", "Food", "Plorts", "Resources", "Fashion Pod", "Ornament")
        categories.forEach { label ->
            val chip = Button(activity).apply {
                text = label
                isAllCaps = false
                textSize = 10f
                typeface = GameAssets.typeface(activity, true)
                setTextColor(if (category == label) Color.WHITE else textColor)
                background = rounded(activity, if (category == label) Color.rgb(232,76,138) else surfaceColor, if (category == label) Color.TRANSPARENT else borderColor, 999)
                setPadding(dp(activity, 16), 0, dp(activity, 16), 0)
                setOnClickListener { category = label; updateChipAppearance(); render() }
            }
            val params = LinearLayout.LayoutParams(-2, dp(activity, 32))
            params.setMargins(dp(activity, 2), dp(activity, 5), dp(activity, 2), dp(activity, 2))
            chips.addView(chip, params)
        }
        updateChipAppearance()
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { render() }
            override fun afterTextChanged(s: Editable?) {}
        })
        render()
        val footer = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(dp(activity, 4), dp(activity, 12), dp(activity, 4), dp(activity, 2))
            background = rounded(activity, surfaceColor, Color.TRANSPARENT, 20)
        }
        val cancel = Button(activity).apply {
            text = "Cancel"
            isAllCaps = false
            textSize = 13f
            typeface = GameAssets.typeface(activity, true)
            setTextColor(accentColor)
            background = rounded(activity, Color.TRANSPARENT, accentColor, 999)
            setPadding(dp(activity, 18), 0, dp(activity, 18), 0)
        }
        val select = Button(activity).apply {
            text = "Select"
            isAllCaps = false
            textSize = 13f
            typeface = GameAssets.typeface(activity, true)
            setTextColor(Color.WHITE)
            background = rounded(activity, accentColor, Color.TRANSPARENT, 999)
            setPadding(dp(activity, 20), 0, dp(activity, 20), 0)
        }
        val cancelParams = LinearLayout.LayoutParams(-2, dp(activity, 42))
        cancelParams.marginEnd = dp(activity, 9)
        footer.addView(cancel, cancelParams)
        footer.addView(select, LinearLayout.LayoutParams(-2, dp(activity, 42)))
        root.addView(footer)

        val dialog = AlertDialog.Builder(activity).setView(root).create()
        closeButton.setOnClickListener { dialog.dismiss() }
        cancel.setOnClickListener { dialog.dismiss() }
        select.setOnClickListener {
            val count = quantity.text.toString().toLongOrNull()
            if (count == null || count !in 0..4294967295L) {
                quantity.error = "Enter a valid amount"
            } else {
                onSelect(selectedId, count)
                dialog.dismiss()
            }
        }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog.window?.setDimAmount(0.45f)
        dialog.window?.setLayout(
            activity.resources.displayMetrics.widthPixels - dp(activity, 28),
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    private fun dp(activity: Activity, value: Int) =
        (value * activity.resources.displayMetrics.density + .5f).toInt()

    private fun rounded(activity: Activity, fill: Int, stroke: Int, radius: Int) =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(activity, radius).toFloat()
            if (stroke != Color.TRANSPARENT) setStroke(dp(activity, 1), stroke)
        }
}

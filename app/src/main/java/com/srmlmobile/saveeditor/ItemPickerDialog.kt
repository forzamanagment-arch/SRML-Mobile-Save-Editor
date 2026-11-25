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
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 12), dp(activity, 12), dp(activity, 12), dp(activity, 10))
            background = rounded(activity, Color.rgb(230,233,242), Color.rgb(179,157,219), 22)
        }
        val header = TextView(activity).apply {
            text = if (waterOnly) "CHOOSE WATER TYPE" else "CHOOSE AN ITEM"
            textSize = 13f
            letterSpacing = .08f
            setTextColor(Color.WHITE)
            setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12))
            background = rounded(activity, Color.rgb(232,76,138), Color.TRANSPARENT, 16)
        }
        root.addView(header)
        val search = EditText(activity).apply {
            hint = "Search items…"
            setSingleLine(true)
            textSize = 14f
            setPadding(dp(activity, 12), dp(activity, 7), dp(activity, 12), dp(activity, 7))
            background = rounded(activity, Color.rgb(255,253,247), Color.rgb(224,216,204), 14)
        }
        val searchParams = LinearLayout.LayoutParams(-1, dp(activity, 44))
        searchParams.topMargin = dp(activity, 9)
        root.addView(search, searchParams)
        val chips = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(chips)
        val results = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(activity).apply { addView(results) }
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
            background = rounded(activity, Color.rgb(255,253,247), Color.rgb(224,216,204), 14)
        }
        var selectedId = currentId
        var category = "All"
        fun qtyButton(label: String, action: () -> Unit) = Button(activity).apply {
            text = label
            isAllCaps = false
            textSize = 17f
            setTextColor(Color.rgb(45,55,72))
            background = rounded(activity, Color.rgb(255,253,247), Color.rgb(179,157,219), 22)
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
                "Food" -> listOf("куриц", "морков", "фрукт", "манго", "лук", "имбир", "тофу", "груш", "свёкл").any { n.contains(it) }
                "Slimes" -> n.contains("слайм") || n.contains("ларго") || n.contains("гордо")
                "Plorts" -> n.contains("плорт")
                "Resources" -> id in 11000L..11999L
                "Gadgets" -> id >= 12000L || n.contains("ящик")
                else -> true
            }
        }
        fun aliases(id: Long, name: String): List<String> {
            val exact = when (id) {
                1L -> "RadSlime"; 2L -> "RockSlime"; 3L -> "Pink_Slime"; 8L -> "Cuberry"
                9L -> "Mint_mango"; 16L -> "Hen_hen"; 17L -> "Roostro"; 19L -> "Carrot"
                21L -> "Boom_Slime"; 27L -> "Pogo_fruit"; 29L -> "Heart_Beet"
                31L -> "PhosphorSlime"; 37L -> "TabbySlime"; 48L -> "PlortWATER"
                51L -> "SlimeChasseur"; 59L -> "HoneySlime"; 72L -> "SlimeAqueux"
                76L -> "Key"; 79L -> "SlimeDeCristal"; 89L -> "Quantum_Slime"
                100L -> "PhaseLemon"; 102L -> "DervishSlime"; 104L -> "MosaicSlime"
                106L -> "Tangle_Slime"; 108L -> "FireSlime"; 148L -> "GildedGinger"
                150L -> "Saber_Slime"; else -> name
            }
            return listOf(exact, name)
        }
        fun render() {
            results.removeAllViews()
            val query = search.text.toString().trim().lowercase()
            val items = ItemCatalog.names.entries.filter { (id, name) ->
                (waterOnly || ItemCatalog.isAllowedInRegularSlot(id)) &&
                    categoryMatches(id, name) &&
                    (query.isEmpty() || name.lowercase().contains(query) || id.toString().contains(query))
            }.take(240)
            items.chunked(2).forEach { pair ->
                val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
                pair.forEach { (id, name) ->
                    val selected = id == selectedId
                    val tile = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        setPadding(dp(activity, 5), dp(activity, 6), dp(activity, 5), dp(activity, 5))
                        background = rounded(activity, Color.rgb(255,253,247),
                            if (selected) Color.rgb(63,184,173) else Color.rgb(224,216,204), 14)
                        setOnClickListener { selectedId = id; render() }
                    }
                    val bitmap = GameAssets.icon(activity, *aliases(id, name).toTypedArray())
                    if (bitmap != null) {
                        tile.addView(ImageView(activity).apply { setImageBitmap(bitmap) },
                            LinearLayout.LayoutParams(dp(activity, 46), dp(activity, 46)))
                    } else {
                        tile.addView(TextView(activity).apply {
                            text = "?"
                            textSize = 27f
                            gravity = Gravity.CENTER
                            setTextColor(Color.rgb(63,184,173))
                            background = rounded(activity, Color.rgb(232,220,200), Color.rgb(203,191,168), 12)
                        }, LinearLayout.LayoutParams(dp(activity, 46), dp(activity, 46)))
                    }
                    tile.addView(TextView(activity).apply {
                        text = name
                        textSize = 10f
                        maxLines = 2
                        gravity = Gravity.CENTER
                        setTextColor(Color.rgb(45,55,72))
                    }, LinearLayout.LayoutParams(-1, dp(activity, 31)))
                    tile.addView(TextView(activity).apply {
                        text = "ID: $id"
                        textSize = 9f
                        setTextColor(Color.rgb(155,168,181))
                    })
                    val params = LinearLayout.LayoutParams(0, dp(activity, 102), 1f)
                    params.setMargins(dp(activity, 3), dp(activity, 3), dp(activity, 3), dp(activity, 3))
                    row.addView(tile, params)
                }
                if (pair.size == 1) row.addView(View(activity), LinearLayout.LayoutParams(0, dp(activity, 102), 1f))
                results.addView(row)
            }
        }
        val categories = if (waterOnly) listOf("All") else listOf("All", "Food", "Slimes", "Plorts", "Resources", "Gadgets")
        categories.forEach { label ->
            val chip = Button(activity).apply {
                text = label
                isAllCaps = false
                textSize = 10f
                setTextColor(Color.rgb(45,55,72))
                background = rounded(activity, Color.rgb(255,253,247), Color.rgb(179,157,219), 20)
                setPadding(dp(activity, 6), 0, dp(activity, 6), 0)
                setOnClickListener { category = label; render() }
            }
            val params = LinearLayout.LayoutParams(-2, dp(activity, 32))
            params.setMargins(dp(activity, 2), dp(activity, 5), dp(activity, 2), dp(activity, 2))
            chips.addView(chip, params)
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { render() }
            override fun afterTextChanged(s: Editable?) {}
        })
        render()
        val dialog = AlertDialog.Builder(activity)
            .setView(root)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Select", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val count = quantity.text.toString().toLongOrNull()
                if (count == null || count !in 0..4294967295L) {
                    quantity.error = "Enter a valid amount"
                } else {
                    onSelect(selectedId, count)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
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

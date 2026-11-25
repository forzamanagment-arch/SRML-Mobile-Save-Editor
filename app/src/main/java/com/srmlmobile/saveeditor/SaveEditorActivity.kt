package com.srmlmobile.saveeditor

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.ImageView
import android.widget.FrameLayout
import android.view.Gravity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class SaveEditorActivity : Activity() {
    companion object {
        private const val PICK_SAVE = 1001
        private const val CREATE_SAVE = 1002
        private const val EMPTY_SLOT_ID = 0L
        private const val WATER_ID = 48L
        private const val MAGIC_WATER_ID = 113L
        private const val MAX_U32 = 4294967295L
        private val INVENTORY_MARKER = byteArrayOf(
            0x04, 0x53, 0x52, 0x41, 0x44, 0x02, 0x00, 0x00, 0x00, 0x00, 0x30
        )
        private val PLAYER_MARKER = byteArrayOf(
            0x04, 0x53, 0x52, 0x50, 0x4c, 0x0e, 0x00, 0x00, 0x00, 0x00, 0x30
        )
    }

    private data class ArchiveEntry(val name: String, val bytes: ByteArray, val time: Long)
    private data class InventoryField(
        val id: Long,
        val idOffset: Int,
        val countOffset: Int,
        val count: Long
    )
    private data class ParsedSave(
        val entries: List<ArchiveEntry>,
        val gameEntryIndex: Int,
        val payload: ByteArray,
        val newbucksOffset: Int?,
        val keysOffset: Int?,
        val currentHealthOffset: Int?,
        val currentEnergyOffset: Int?,
        val inventorySlots: List<InventoryField>,
        val waterSlot: InventoryField?,
        val sourceName: String
    )
    private data class SlotEditor(
        val idInput: EditText,
        val countInput: EditText,
        val nameLabel: TextView,
        val field: InventoryField,
        val iconView: ImageView,
        val badge: TextView
    )

    private var parsed: ParsedSave? = null
    private var newbucksInput: EditText? = null
    private var keysInput: EditText? = null
    private var currentHealthInput: EditText? = null
    private var currentEnergyInput: EditText? = null
    private val slotEditors = mutableListOf<SlotEditor>()
    private var waterCountInput: EditText? = null
    private var waterTypeSpinner: Spinner? = null
    private var pendingArchive: ByteArray? = null
    private var sourceName = "save"
    private var remoteTargetPath: String? = null
    private var backupPath: String? = null
    private var stageFile: java.io.File? = null
    private var saveCompleted = false
    private lateinit var page: LinearLayout
    private lateinit var status: TextView
    private val russian: Boolean get() = getSharedPreferences("srml_settings", MODE_PRIVATE).getBoolean("russian", false)
    private fun tr(en: String, ru: String): String = if (russian) ru else en

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val stage = intent.getStringExtra("stage_path")
        remoteTargetPath = intent.getStringExtra("target_path")
        backupPath = intent.getStringExtra("backup_path")
        if (stage != null && remoteTargetPath != null) {
            try {
                stageFile = java.io.File(stage)
                sourceName = remoteTargetPath!!.substringAfterLast('/')
                parsed = parseArchive(stageFile!!.readBytes(), sourceName)
                showEditor()
            } catch (e: Exception) {
                buildWelcome()
                status.text = "Не удалось открыть подготовленное сохранение: " + (e.message ?: "ошибка")
            }
        } else buildWelcome()
    }

    private fun buildBase(title: String) {
        val scroll = ScrollView(this)
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(34))
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(245,240,232), Color.rgb(237,232,220))
            )
        }
        scroll.addView(page)
        setContentView(scroll)
        page.addView(TextView(this).apply {
            text = tr("SAVE WORKSHOP", "МАСТЕРСКАЯ СОХРАНЕНИЙ")
            textSize = 11f
            letterSpacing = 0.12f
            typeface = GameAssets.typeface(this@SaveEditorActivity)
            setTextColor(Color.rgb(232, 76, 138))
            setPadding(0, 0, 0, dp(7))
        })
        page.addView(TextView(this).apply {
            text = title
            textSize = 30f
            typeface = GameAssets.typeface(this@SaveEditorActivity)
            setTextColor(Color.rgb(45, 55, 72))
            setPadding(0, dp(3), 0, dp(7))
        })
        page.addView(TextView(this).apply {
            text = tr("World save management", "Управление сохранением мира")
            textSize = 14f
            setTextColor(Color.rgb(107, 123, 141))
            setPadding(0, 0, 0, dp(18))
        })
        status = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(Color.rgb(31, 41, 55), Color.TRANSPARENT, dp(24))
        }
        page.addView(status)
    }

    private fun buildWelcome() {
        buildBase(tr("Save editor", "Редактор сохранений"))
        status.text = tr("Return to the home screen, refresh your worlds and choose a save there. No manual file selection is needed.", "Открой главный экран, обнови список миров и выбери сохранение там. Файл вручную выбирать не требуется.")
        addButton(tr("Back to worlds", "Вернуться к списку миров")) { finish() }
        addHint(tr("A backup of the original save is available in the Backup tab.", "Резервная копия исходного сохранения хранится во вкладке Backup."))
    }

    private fun openSavePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, PICK_SAVE)
    }

    @Deprecated("Using document picker result for compatibility with the prototype")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        val uri = data.data!!
        if (requestCode == PICK_SAVE) {
            try {
                sourceName = queryName(uri) ?: "save"
                val raw = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Не удалось прочитать выбранный файл")
                parsed = parseArchive(raw, sourceName)
                showEditor()
            } catch (e: Exception) {
                buildWelcome()
                status.text = "Не удалось открыть сохранение: ${e.message ?: "неизвестная ошибка"}"
            }
        } else if (requestCode == CREATE_SAVE) {
            try {
                val archive = pendingArchive ?: error("Нет подготовленного файла")
                contentResolver.openOutputStream(uri, "wt")?.use { it.write(archive) }
                    ?: error("Не удалось записать файл")
                buildWelcome()
                status.text = "Готово! Создана изменённая копия: ${queryName(uri) ?: sourceName}. Перенеси её в pt_saves, сохранив исходное имя."
                addButton("Выбрать другое сохранение") { openSavePicker() }
            } catch (e: Exception) {
                status.text = "Ошибка записи: ${e.message ?: "неизвестная ошибка"}"
            } finally {
                pendingArchive = null
            }
        }
    }

    private fun queryName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return null
    }

    private fun parseArchive(raw: ByteArray, name: String): ParsedSave {
        val entries = mutableListOf<ArchiveEntry>()
        var gameIndex = -1
        ZipInputStream(ByteArrayInputStream(raw)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val bytes = zip.readBytes()
                if (entry.name == "gamesave-sav") {
                    if (gameIndex >= 0) error("В архиве найдено несколько gamesave-sav")
                    gameIndex = entries.size
                }
                entries.add(ArchiveEntry(entry.name, bytes, entry.time))
                zip.closeEntry()
            }
        }
        if (gameIndex < 0) error("В архиве нет файла gamesave-sav")
        val payload = entries[gameIndex].bytes
        if (payload.size < 100) error("Файл сохранения слишком маленький или повреждён")

        // Newbucks are read from the observed SRPL-relative +22 field.
        // The old +30 equality check was false: that is a different field.
        val playerMarkers = findAll(payload, PLAYER_MARKER)
        if (playerMarkers.size != 1) error("Не удалось однозначно найти блок игрока SRPL")
        val playerTag = playerMarkers.single() + 1
        val newbucksOffset = if (playerTag + 0x16 + 4 <= payload.size) playerTag + 0x16 else null
        // Key count candidate: SRPL-relative +0x1A, corroborated against labelled samples.
        val keysOffset = if (playerTag + 0x1A + 4 <= payload.size) playerTag + 0x1A else null
        // SRPL-relative current stats: +0x0A current health, +0x0E current energy.
        // The offsets below are relative to playerTag, which points at the 'S' in SRPL.
        val currentHealthOffset = if (playerTag + 0x0A + 4 <= payload.size) playerTag + 0x0A else null
        val currentEnergyOffset = if (playerTag + 0x0E + 4 <= payload.size) playerTag + 0x0E else null

        // Find compact inventory record groups near the player block.
        val markers = findAllInRange(
            payload, INVENTORY_MARKER, playerTag,
            minOf(payload.size, playerTag + 6000)
        )
        val groups = mutableListOf<MutableList<Int>>()
        for (marker in markers) {
            val group = groups.lastOrNull()
            if (group == null || marker - group.last() > 128) groups.add(mutableListOf(marker))
            else group.add(marker)
        }
        val selected = groups.maxByOrNull { it.size } ?: emptyList()
        if (selected.size < 4) {
            error("Найдено меньше четырёх записей инвентаря. Слоты не будут угаданы.")
        }

        val records = selected.mapNotNull { marker ->
            val idOffset = marker + INVENTORY_MARKER.size
            val countOffset = idOffset + 4
            val id = readU32(payload, idOffset)
            val count = readU32(payload, countOffset)
            if (id == null || count == null) null
            else InventoryField(id, idOffset, countOffset, count)
        }
        if (records.size < 4) error("Не удалось прочитать четыре основных слота")
        val regularSlots = records.take(4)
        val fifth = records.getOrNull(4)
        val waterSlot = fifth?.takeIf { it.id == EMPTY_SLOT_ID || it.id == WATER_ID || it.id == MAGIC_WATER_ID }

        return ParsedSave(
            entries, gameIndex, payload, newbucksOffset, keysOffset,
            currentHealthOffset, currentEnergyOffset,
            regularSlots, waterSlot, name
        )
    }

    private fun showEditor() {
        val data = parsed ?: return
        newbucksInput = null
        keysInput = null
        currentHealthInput = null
        currentEnergyInput = null
        buildBase(tr("Save dashboard", "Панель сохранения"))
        addSection(tr("PLAYER HUD", "ПАРАМЕТРЫ ИГРОКА"))
        val hudGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(hudGrid)
        val rowA = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val rowB = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        hudGrid.addView(rowA)
        hudGrid.addView(rowB)
        fun hudCard(row: LinearLayout, label: String, iconName: String, offset: Int?, save: (EditText) -> Unit) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(9), dp(9), dp(9), dp(9))
                background = rounded(Color.rgb(230,233,242), Color.rgb(179,157,219), dp(17))
            }
            val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val icon = ImageView(this)
            val bitmap = GameAssets.icon(this, iconName)
            if (bitmap != null) icon.setImageBitmap(bitmap)
            else {
                icon.setBackground(rounded(Color.rgb(232,220,200), Color.rgb(203,191,168), dp(10)))
                icon.contentDescription = "?"
            }
            head.addView(icon, LinearLayout.LayoutParams(dp(25), dp(25)))
            head.addView(TextView(this).apply {
                text = label
                textSize = 11f
                typeface = GameAssets.typeface(this@SaveEditorActivity)
                setTextColor(Color.rgb(45,55,72))
                setPadding(dp(5), 0, 0, 0)
            })
            card.addView(head)
            val current = offset?.let { readU32(data.payload, it) }
            if (current != null) {
                val input = EditText(this).apply {
                    setSingleLine(true)
                    inputType = InputType.TYPE_CLASS_NUMBER
                    filters = arrayOf(InputFilter.LengthFilter(10))
                    setText(current.toString())
                    textSize = 15f
                    setTextColor(Color.rgb(31,41,55))
                    setPadding(dp(8), dp(5), dp(8), dp(5))
                    background = rounded(Color.rgb(255,253,247), Color.rgb(224,216,204), dp(12))
                }
                card.addView(input, LinearLayout.LayoutParams(-1, dp(40)))
                save(input)
            } else card.addView(TextView(this).apply { text = "?"; setTextColor(Color.rgb(155,168,181)) })
            val params = LinearLayout.LayoutParams(0, -2, 1f)
            params.setMargins(dp(3), dp(3), dp(3), dp(6))
            row.addView(card, params)
        }
        hudCard(rowA, tr("Newbucks", "Ньюбаксы"), "Newbucks", data.newbucksOffset) { newbucksInput = it }
        hudCard(rowA, tr("Health", "Здоровье"), "Heart", data.currentHealthOffset) { currentHealthInput = it }
        hudCard(rowB, tr("Energy", "Энергия"), "Battery", data.currentEnergyOffset) { currentEnergyInput = it }
        hudCard(rowB, tr("Keys", "Ключи"), "Key", data.keysOffset) { keysInput = it }

        addSection(tr("INVENTORY", "ИНВЕНТАРЬ"))
        addHint(tr("Four item slots and one dedicated water slot. Tap a tile to choose an item.", "Четыре слота предметов и отдельный слот воды. Нажми на плитку, чтобы выбрать предмет."))
        slotEditors.clear()
        val inventoryGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(inventoryGrid)
        data.inventorySlots.take(4).chunked(2).forEachIndexed { rowIndex, pair ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { column, field ->
                val editor = addSlotEditor(rowIndex * 2 + column + 1, field, row)
                slotEditors.add(editor)
            }
            if (pair.size == 1) row.addView(android.view.View(this), LinearLayout.LayoutParams(0, dp(112), 1f))
            inventoryGrid.addView(row)
        }

        addSection(tr("Slot 5 • water", "Слот 5 • вода"))
        val water = data.waterSlot
        if (water == null) {
            addHint(tr("The water slot is not available in this save.", "Слот воды недоступен в этом сохранении."))
        } else {
            addHint(tr("Available items: Water and Ancient Water.", "Допустимые предметы: вода и древняя вода."))
            val spinner = Spinner(this)
            val options = listOf("0 — Пусто", "48 — Вода", "113 — Древняя вода")
            spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
            spinner.setSelection(when (water.id) {
                WATER_ID -> 1
                MAGIC_WATER_ID -> 2
                else -> 0
            })
            val waterTile = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = rounded(Color.rgb(230,233,242), Color.rgb(179,157,219), dp(18))
            }
            val waterIcon = ImageView(this)
            val waterBitmap = GameAssets.icon(this, "Water", "AncientWater")
            if (waterBitmap != null) waterIcon.setImageBitmap(waterBitmap)
            else waterIcon.setBackground(rounded(Color.rgb(232,220,200), Color.rgb(203,191,168), dp(12)))
            waterTile.addView(waterIcon, LinearLayout.LayoutParams(dp(48), dp(48)))
            val waterText = TextView(this).apply {
                text = tr("Water slot  •  ", "Слот воды  •  ") + water.count
                textSize = 14f
                setTextColor(Color.rgb(45,55,72))
                typeface = GameAssets.typeface(this@SaveEditorActivity)
                setPadding(dp(12), 0, 0, 0)
            }
            val openWaterPicker = {
                ItemPickerDialog.show(this@SaveEditorActivity, water.id, water.count, true) { selectedId, selectedCount ->
                    spinner.setSelection(when (selectedId) { WATER_ID -> 1; MAGIC_WATER_ID -> 2; else -> 0 })
                    waterCountInput?.setText(selectedCount.toString())
                    waterText.text = tr("Water slot  •  ", "Слот воды  •  ") + selectedCount
                }
            }
            waterTile.setOnClickListener { openWaterPicker() }
            waterTile.addView(waterText, LinearLayout.LayoutParams(0, -2, 1f))
            val choose = Button(this).apply {
                text = "⌄"
                isAllCaps = false
                setTextColor(Color.WHITE)
                background = rounded(Color.rgb(63,184,173), Color.TRANSPARENT, dp(24))
                setOnClickListener { openWaterPicker() }
            }
            waterTile.addView(choose, LinearLayout.LayoutParams(dp(44), dp(40)))
            page.addView(waterTile)
            spinner.visibility = android.view.View.GONE
            page.addView(spinner)
            waterTypeSpinner = spinner
            waterCountInput = addNumberField(tr("Water amount", "Количество воды"), water.count.toString()).apply { visibility = android.view.View.GONE }
        }

        addButton(tr("Save changes", "Сохранить изменения")) { prepareOutput() }
        addHint(tr("After saving, the file returns to pt_saves with its original name. The backup remains in Backup.", "После сохранения файл вернётся в pt_saves под исходным именем. Резервная копия останется в Backup."))
    }

    private fun addSlotEditor(slotNumber: Int, field: InventoryField, parent: LinearLayout): SlotEditor {
        val idInput = EditText(this).apply { setText(field.id.toString()); visibility = android.view.View.GONE }
        val countInput = EditText(this).apply { setText(field.count.toString()); visibility = android.view.View.GONE }
        val nameLabel = TextView(this).apply {
            text = ItemCatalog.displayName(field.id)
            textSize = 11f
            gravity = Gravity.CENTER
            maxLines = 2
            setTextColor(Color.rgb(45,55,72))
            typeface = GameAssets.typeface(this@SaveEditorActivity)
        }
        val iconView = ImageView(this)
        val badge = TextView(this).apply {
            text = field.count.toString()
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(dp(6), 0, dp(6), 0)
            background = rounded(Color.rgb(63,184,173), Color.TRANSPARENT, dp(20))
        }
        fun refreshTile() {
            val id = idInput.text.toString().toLongOrNull() ?: 0L
            val aliases = when (id) {
                1L -> listOf("RadSlime"); 2L -> listOf("RockSlime"); 3L -> listOf("PinkSlime")
                8L -> listOf("Cuberry"); 9L -> listOf("MintMango"); 16L -> listOf("HenHen")
                17L -> listOf("Roostro"); 19L -> listOf("Carrot"); 27L -> listOf("Pogofruit")
                29L -> listOf("HeartBeet"); 31L -> listOf("PhosphorSlime"); 37L -> listOf("TabbySlime")
                48L -> listOf("PlortWATER", "Water"); 51L -> listOf("SlimeChasseur", "HunterSlime"); 59L -> listOf("HoneySlime")
                72L -> listOf("SlimeAqueux", "PuddleSlime"); 76L -> listOf("Key"); 79L -> listOf("SlimeDeCristal", "CrystalSlime")
                89L -> listOf("QuantumSlime"); 102L -> listOf("DervishSlime"); 104L -> listOf("MosaicSlime")
                106L -> listOf("TangleSlime"); 108L -> listOf("FireSlime"); else -> emptyList()
            } + ItemCatalog.displayName(id)
            val bitmap = GameAssets.icon(this, *aliases.toTypedArray())
            if (bitmap != null) iconView.setImageBitmap(bitmap) else {
                iconView.setImageResource(android.R.drawable.ic_menu_help)
                iconView.setBackground(rounded(Color.rgb(232,220,200), Color.rgb(203,191,168), dp(12)))
                iconView.contentDescription = "?"
            }
            badge.text = countInput.text.toString()
            nameLabel.text = if (id == 0L) "?" else ItemCatalog.displayName(id)
        }
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = rounded(Color.rgb(232,220,200), Color.rgb(203,191,168), dp(16))
        }
        val frame = FrameLayout(this)
        frame.addView(iconView, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))
        frame.addView(badge, FrameLayout.LayoutParams(-2, dp(20), Gravity.TOP or Gravity.END))
        tile.addView(frame, LinearLayout.LayoutParams(-1, dp(54)))
        tile.addView(nameLabel, LinearLayout.LayoutParams(-1, dp(34)))
        val params = LinearLayout.LayoutParams(0, dp(108), 1f)
        params.setMargins(dp(4), dp(4), dp(4), dp(8))
        parent.addView(tile, params)
        fun openPicker() {
            ItemPickerDialog.show(
                activity = this@SaveEditorActivity,
                currentId = idInput.text.toString().toLongOrNull() ?: 0L,
                currentCount = countInput.text.toString().toLongOrNull() ?: 0L,
                onSelect = { selectedId: Long, selectedCount: Long ->
                    idInput.setText(selectedId.toString())
                    countInput.setText(selectedCount.toString())
                    refreshTile()
                }
            )
        }
        tile.setOnClickListener { openPicker() }
        refreshTile()
        return SlotEditor(idInput, countInput, nameLabel, field, iconView, badge)
    }

    private fun prepareOutput() {
        try {
            val data = parsed ?: error("Сначала выбери сохранение")
            val payload = data.payload.copyOf()

            val newbucksValue = newbucksInput?.text?.toString()?.let {
                parseUnsigned(it, "Ньюбаки")
            }
            val keysValue = keysInput?.text?.toString()?.let {
                parseUnsigned(it, "Количество ключей")
            }
            val healthValue = currentHealthInput?.text?.toString()?.let {
                parseUnsigned(it, "Текущее здоровье")
            }
            val energyValue = currentEnergyInput?.text?.toString()?.let {
                parseUnsigned(it, "Текущая энергия")
            }
            if (newbucksValue != null) {
                val offset = data.newbucksOffset ?: error("Поле Ньюбаксов не распознано")
                writeU32(payload, offset, newbucksValue)
                if (readU32(payload, offset) != newbucksValue) error("Проверка Ньюбаксов не пройдена")
            }

            if (keysValue != null) {
                val offset = data.keysOffset ?: error("Поле количества ключей не распознано")
                writeU32(payload, offset, keysValue)
                if (readU32(payload, offset) != keysValue) error("Проверка количества ключей не пройдена")
            }

            if (healthValue != null) {
                val offset = data.currentHealthOffset ?: error("Поле текущего здоровья не распознано")
                writeU32(payload, offset, healthValue)
                if (readU32(payload, offset) != healthValue) error("Проверка текущего здоровья не пройдена")
            }
            if (energyValue != null) {
                val offset = data.currentEnergyOffset ?: error("Поле текущей энергии не распознано")
                writeU32(payload, offset, energyValue)
                if (readU32(payload, offset) != energyValue) error("Проверка текущей энергии не пройдена")
            }

            slotEditors.forEachIndexed { index, editor ->
                val id = parseUnsigned(editor.idInput.text.toString(), "ID слота ${index + 1}")
                if (!ItemCatalog.isAllowedInRegularSlot(id)) {
                    error("Слот ${index + 1}: ID $id отсутствует в каталоге или запрещён. Для слотов 1–4 нельзя использовать 48, 113 и 16001.")
                }
                val count = parseUnsigned(editor.countInput.text.toString(), "Количество в слоте ${index + 1}")
                writeU32(payload, editor.field.idOffset, id)
                writeU32(payload, editor.field.countOffset, count)
                if (readU32(payload, editor.field.idOffset) != id ||
                    readU32(payload, editor.field.countOffset) != count) {
                    error("Проверка слота ${index + 1} не пройдена")
                }
            }

            val water = data.waterSlot
            if (water != null) {
                val selectedId = when (waterTypeSpinner?.selectedItemPosition ?: 0) {
                    1 -> WATER_ID
                    2 -> MAGIC_WATER_ID
                    else -> EMPTY_SLOT_ID
                }
                val count = parseUnsigned(waterCountInput?.text?.toString(), "Количество воды")
                writeU32(payload, water.idOffset, selectedId)
                writeU32(payload, water.countOffset, count)
                if (readU32(payload, water.idOffset) != selectedId ||
                    readU32(payload, water.countOffset) != count) {
                    error("Проверка пятого слота не пройдена")
                }
            }

            val output = ByteArrayOutputStream()
            ZipOutputStream(output).use { zipOut ->
                data.entries.forEachIndexed { index, entry ->
                    val zipEntry = ZipEntry(entry.name)
                    if (entry.time >= 0) zipEntry.time = entry.time
                    zipOut.putNextEntry(zipEntry)
                    zipOut.write(if (index == data.gameEntryIndex) payload else entry.bytes)
                    zipOut.closeEntry()
                }
            }
            pendingArchive = output.toByteArray()
            val target = remoteTargetPath
            if (target != null) {
                Thread {
                    try {
                        ShizukuFileAccess.writeAtomic(target, pendingArchive!!)
                        stageFile?.delete()
                        saveCompleted = true
                        runOnUiThread { status.text = "Сохранение возвращено в pt_saves. Резервная копия осталась в Backup." }
                    } catch (e: Exception) {
                        runOnUiThread { status.text = "Ошибка сохранения: " + (e.message ?: "ошибка") + ". Backup сохранён." }
                    }
                }.start()
            } else {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_TITLE, data.sourceName)
                }
                startActivityForResult(intent, CREATE_SAVE)
            }
        } catch (e: Exception) {
            status.text = "Изменения не сохранены: ${e.message ?: "проверь введённые значения"}"
        }
    }

    override fun onBackPressed() {
        val target = remoteTargetPath
        val staged = stageFile
        if (target != null && staged != null && !saveCompleted && staged.exists()) {
            status.text = "Возвращаю исходное сохранение без изменений…"
            Thread {
                try {
                    ShizukuFileAccess.writeAtomic(target, staged.readBytes())
                    staged.delete()
                    runOnUiThread { finish() }
                } catch (e: Exception) {
                    runOnUiThread { status.text = "Не удалось восстановить оригинал: " + (e.message ?: "ошибка") + ". Резервная копия находится в Backup." }
                }
            }.start()
        } else {
            super.onBackPressed()
        }
    }

    private fun parseUnsigned(text: String?, label: String): Long {
        val value = text?.trim()?.toLongOrNull() ?: error("$label: введи целое число")
        if (value < 0 || value > MAX_U32) error("$label: допустимый диапазон 0–4294967295")
        return value
    }

    private fun findAll(data: ByteArray, pattern: ByteArray): List<Int> =
        findAllInRange(data, pattern, 0, data.size)

    private fun findAllInRange(data: ByteArray, pattern: ByteArray, start: Int, end: Int): List<Int> {
        val found = mutableListOf<Int>()
        if (pattern.isEmpty() || end - start < pattern.size) return found
        for (i in start..(end - pattern.size)) {
            var match = true
            for (j in pattern.indices) if (data[i + j] != pattern[j]) {
                match = false
                break
            }
            if (match) found.add(i)
        }
        return found
    }

    private fun readU32(data: ByteArray, offset: Int): Long? {
        if (offset < 0 || offset + 4 > data.size) return null
        return (data[offset].toLong() and 0xff) or
            ((data[offset + 1].toLong() and 0xff) shl 8) or
            ((data[offset + 2].toLong() and 0xff) shl 16) or
            ((data[offset + 3].toLong() and 0xff) shl 24)
    }

    private fun writeU32(data: ByteArray, offset: Int, value: Long) {
        if (offset < 0 || offset + 4 > data.size) error("Поле выходит за границы сохранения")
        for (i in 0..3) data[offset + i] = ((value ushr (8 * i)) and 0xff).toByte()
    }

    private fun addSection(text: String) {
        page.addView(TextView(this).apply {
            this.text = text
            textSize = 18f
            setTextColor(Color.rgb(45, 55, 72))
            typeface = GameAssets.typeface(this@SaveEditorActivity)
            setPadding(0, dp(17), 0, dp(7))
        })
    }

    private fun addNumberField(label: String, value: String): EditText {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(8))
        }
        wrapper.addView(TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(Color.rgb(107, 123, 141))
        })
        val input = EditText(this).apply {
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(10))
            setText(value)
            textSize = 16f
            setTextColor(Color.rgb(57, 75, 105))
            setHintTextColor(Color.rgb(155, 168, 181))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = rounded(Color.rgb(255, 253, 247), Color.rgb(224, 216, 204), dp(14))
        }
        wrapper.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        page.addView(wrapper)
        return input
    }

    private fun addButton(text: String, action: () -> Unit) {
        val button = Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 15f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.create("sans-serif-rounded", android.graphics.Typeface.BOLD)
            background = rounded(Color.rgb(63, 184, 173), Color.TRANSPARENT, dp(28))
            setOnClickListener { action() }
        }
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52))
        params.topMargin = dp(10)
        page.addView(button, params)
    }

    private fun addHint(text: String) {
        page.addView(TextView(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(Color.rgb(107, 123, 141))
            setPadding(0, dp(12), 0, dp(8))
        })
    }

    private fun rounded(fill: Int, stroke: Int, radius: Int): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius.toFloat()
            if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

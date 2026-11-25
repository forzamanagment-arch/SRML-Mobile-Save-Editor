package com.srmlmobile.saveeditor

import android.app.Activity
import android.app.AlertDialog
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
        val testOffset: Int?,
        val sourceName: String
    )
    private data class SlotEditor(
        val idInput: EditText,
        val countInput: EditText,
        val nameLabel: TextView,
        val field: InventoryField
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
    private var testValue: Int? = null
    private var remoteTargetPath: String? = null
    private var backupPath: String? = null
    private var stageFile: java.io.File? = null
    private var saveCompleted = false
    private lateinit var page: LinearLayout
    private lateinit var status: TextView

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
            setPadding(dp(20), dp(22), dp(20), dp(28))
            setBackgroundColor(Color.rgb(10, 10, 12))
        }
        scroll.addView(page)
        setContentView(scroll)
        page.addView(TextView(this).apply {
            text = title
            textSize = 25f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(8))
        })
        page.addView(TextView(this).apply {
            text = "Редактор сохранения • 4 обычных слота + отдельный слот воды"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, dp(18))
        })
        status = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.rgb(190, 210, 190))
            setPadding(0, dp(12), 0, dp(12))
        }
        page.addView(status)
    }

    private fun buildWelcome() {
        buildBase("SRML Save Editor")
        status.text = "Выбери оригинальный файл сохранения. Поддерживаются ZIP-архивы с записью gamesave-sav, в том числе файлы без расширения."
        addButton("Выбрать сохранение") { openSavePicker() }
        addButton("Тест распознавания нового сохранения") {
            startActivity(Intent(this, SaveAnalyzerActivity::class.java))
        }
        addHint("Исходный файл не перезаписывается. Редактор создаёт отдельную копию с тем же именем.")
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
        // Experimental candidate: second literal SRGA occurrence, byte offset +0x4A.
        val srgaOccurrences = findAll(payload, "SRGA".toByteArray(Charsets.US_ASCII))
        val testOffset = srgaOccurrences.getOrNull(1)?.plus(0x4A)?.takeIf { it in payload.indices && (payload[it].toInt() and 0xff) <= 3 }

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
            regularSlots, waterSlot, testOffset, name
        )
    }

    private fun showEditor() {
        val data = parsed ?: return
        buildBase("Редактирование")
        val currencyOffset = data.newbucksOffset
        addSection("Ньюбаксы")
        if (currencyOffset != null) {
            val current = readU32(data.payload, currencyOffset)
            if (current != null) newbucksInput = addNumberField("Количество", current.toString())
            else addHint("Поле Ньюбаксов не распознано. Остальные разделы доступны.")
        } else {
            addHint("Поле Ньюбаксов не распознано. Остальные разделы доступны.")
        }

        addSection("Ключи")
        val keyOffset = data.keysOffset
        if (keyOffset != null) {
            val current = readU32(data.payload, keyOffset)
            if (current != null) keysInput = addNumberField("Количество ключей (SRPL + 0x1A)", current.toString())
            else addHint("Количество ключей не удалось прочитать.")
        } else addHint("Поле количества ключей не распознано.")

        addSection("Здоровье и энергия")
        addHint("Это текущие значения на момент сохранения, не максимальная ёмкость здоровья или энергии.")
        val healthOffset = data.currentHealthOffset
        if (healthOffset != null) {
            val current = readU32(data.payload, healthOffset)
            if (current != null) currentHealthInput = addNumberField("Текущее здоровье (SRPL + 0x0A)", current.toString())
            else addHint("Текущее здоровье не удалось прочитать.")
        } else addHint("Поле текущего здоровья не распознано.")
        val energyOffset = data.currentEnergyOffset
        if (energyOffset != null) {
            val current = readU32(data.payload, energyOffset)
            if (current != null) currentEnergyInput = addNumberField("Текущая энергия (SRPL + 0x0E)", current.toString())
            else addHint("Текущую энергию не удалось прочитать.")
        } else addHint("Поле текущей энергии не распознано. Его соответствие пока предварительное.")

        addSection("Тест")
        val candidate = data.testOffset
        if (candidate != null) {
            val currentTest = data.payload[candidate].toInt() and 0xff
            addHint("Экспериментальное поле SRGA + 0x4A. Текущее значение: $currentTest. Назначение пока не подтверждено.")
            addButton("Изменить") { showTestPicker(currentTest) }
            addHint("Применяется на свой страх и риск.")
        } else {
            addHint("Поле SRGA + 0x4A не найдено или его значение не входит в диапазон 0–3. Функция отключена.")
        }

        addSection("Инвентарь")
        addHint("Изменяются только слоты 1–4. ID 48, 113 и 16001 в них запрещены. Слоты после пятого не отображаются и не изменяются.")
        slotEditors.clear()
        data.inventorySlots.forEachIndexed { index, field ->
            addSlotEditor(index + 1, field)?.let { slotEditors.add(it) }
        }

        addSection("Слот 5 • вода")
        val water = data.waterSlot
        if (water == null) {
            addHint("Слот воды заблокирован или его запись не распознана. Редактирование пятого слота отключено.")
        } else {
            addHint("Слот воды обнаружен. Здесь разрешены только ID 48 (вода) и ID 113 (древняя вода).")
            val spinner = Spinner(this)
            val options = listOf("0 — Пусто", "48 — Вода", "113 — Древняя вода")
            spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
            spinner.setSelection(when (water.id) {
                WATER_ID -> 1
                MAGIC_WATER_ID -> 2
                else -> 0
            })
            page.addView(spinner)
            waterTypeSpinner = spinner
            waterCountInput = addNumberField("Количество", water.count.toString())
        }

        addButton("Сохранить изменения") { prepareOutput() }
        addHint("После сохранения файл вернётся в pt_saves под исходным именем. Резервная копия останется в Backup.")
    }

    private fun addSlotEditor(slotNumber: Int, field: InventoryField): SlotEditor {
        addSection("Слот $slotNumber")
        val idInput = addNumberField("ID предмета", field.id.toString())
        val nameLabel = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.rgb(170, 205, 180))
            setPadding(0, 0, 0, dp(6))
            text = ItemCatalog.displayName(field.id)
        }
        page.addView(nameLabel)
        idInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val id = s?.toString()?.toLongOrNull()
                nameLabel.text = when {
                    id == null -> "Введи числовой ID"
                    !ItemCatalog.isKnown(id) -> "ID отсутствует в каталоге"
                    id in setOf(48L, 113L, 16001L) -> "Запрещено в слотах 1–4"
                    else -> ItemCatalog.displayName(id)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        val countInput = addNumberField("Количество", field.count.toString())
        return SlotEditor(idInput, countInput, nameLabel, field)
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
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_TITLE, data.sourceName)
            }
            startActivityForResult(intent, CREATE_SAVE)
        } catch (e: Exception) {
            status.text = "Изменения не сохранены: ${e.message ?: "проверь введённые значения"}"
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
            setTextColor(Color.WHITE)
            setPadding(0, dp(14), 0, dp(6))
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
            setTextColor(Color.LTGRAY)
        })
        val input = EditText(this).apply {
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(10))
            setText(value)
            textSize = 16f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.rgb(34, 34, 38))
        }
        wrapper.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        page.addView(wrapper)
        return input
    }

    private fun addButton(text: String, action: () -> Unit) {
        val button = Button(this).apply {
            this.text = text
            isAllCaps = false
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
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(12), 0, dp(8))
        })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

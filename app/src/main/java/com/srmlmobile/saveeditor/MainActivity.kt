package com.srmlmobile.saveeditor

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class MainActivity : Activity() {
    companion object {
        private const val PICK_SAVE = 1001
        private const val CREATE_SAVE = 1002
        private const val WATER_ID = 48L
        private val INVENTORY_MARKER = byteArrayOf(
            0x04, 0x53, 0x52, 0x41, 0x44, 0x02, 0x00, 0x00, 0x00, 0x00, 0x30
        )
        private val CURRENCY_SECOND_MARKER = byteArrayOf(
            0x04, 0x53, 0x52, 0x50, 0x4c, 0x0e, 0x00, 0x00, 0x00, 0x00, 0x30
        )
    }

    private data class ArchiveEntry(val name: String, val bytes: ByteArray, val time: Long)
    private data class InventoryField(val id: Long, val countOffset: Int, val count: Long)
    private data class ParsedSave(
        val entries: List<ArchiveEntry>,
        val gameEntryIndex: Int,
        val payload: ByteArray,
        val currencyOffsets: List<Int>,
        val inventoryFields: List<InventoryField>,
        val sourceName: String
    )

    private var parsed: ParsedSave? = null
    private var newbucksInput: EditText? = null
    private var waterInput: EditText? = null
    private val inventoryInputs = mutableListOf<Pair<InventoryField, EditText>>()
    private var pendingArchive: ByteArray? = null
    private var sourceName = "save"
    private lateinit var page: LinearLayout
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildWelcome()
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
            text = "Первый прототип • Ньюбаки, вода и инвентарь"
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
                status.text = "Готово! Создана изменённая копия: ${queryName(uri) ?: sourceName}. Теперь перенеси её в pt_saves, сохранив исходное имя."
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

        val firstCurrencyMarker = byteArrayOf(0x03, 0x00, 0x00, 0x00)
        val firstCandidates = findAll(payload, firstCurrencyMarker).filter { offset ->
            offset + 12 <= payload.size &&
                payload.copyOfRange(offset + 8, offset + 12).contentEquals(byteArrayOf(0x36, 0, 0, 0))
        }
        val secondMarkers = findAll(payload, CURRENCY_SECOND_MARKER)
        val currencyOffsets = mutableListOf<Int>()
        if (firstCandidates.size != 1 || secondMarkers.size != 1) {
            error("Не удалось однозначно определить поля ньюбаксов для этой версии сохранения")
        }
        currencyOffsets.add(firstCandidates.single() + 4)
        currencyOffsets.add(secondMarkers.single() + 23)
        val firstCurrency = readU32(payload, currencyOffsets[0])
        val secondCurrency = readU32(payload, currencyOffsets[1])
        if (firstCurrency != secondCurrency) {
            error("Два поля ньюбаксов не совпадают. Сохранение не будет изменено")
        }

        val inventory = mutableListOf<InventoryField>()
        for (markerOffset in findAll(payload, INVENTORY_MARKER)) {
            val idOffset = markerOffset + INVENTORY_MARKER.size
            val countOffset = idOffset + 4
            if (countOffset + 4 > payload.size) continue
            inventory.add(InventoryField(readU32(payload, idOffset), countOffset, readU32(payload, countOffset)))
        }
        val editableInventory = inventory.filter { it.id == WATER_ID || it.id == 59L || it.id == 16L }
        if (editableInventory.none { it.id == WATER_ID }) {
            error("Не найдено поле воды (ID 48). Эта структура сохранения пока не поддерживается")
        }
        return ParsedSave(entries, gameIndex, payload, currencyOffsets, editableInventory, name)
    }

    private fun showEditor() {
        val data = parsed ?: return
        buildBase("Редактирование")
        val currentCurrency = readU32(data.payload, data.currencyOffsets[0])
        status.text = "Сохранение распознано. Найдено записей инвентаря: ${data.inventoryFields.size}. Исходное имя: ${data.sourceName}"
        addSection("Ньюбаки")
        newbucksInput = addNumberField("Количество", currentCurrency.toString())
        addSection("Вода")
        val water = data.inventoryFields.singleOrNull { it.id == WATER_ID }
            ?: error("Найдено несколько записей воды. Нужна дополнительная проверка формата")
        waterInput = addNumberField("Количество воды", water.count.toString())
        addSection("Инвентарь")
        inventoryInputs.clear()
        data.inventoryFields.filter { it.id != WATER_ID }.forEachIndexed { index, field ->
            val itemName = when (field.id) {
                59L -> "Медовые слаймы"
                16L -> "Кур-курицы"
                else -> "Предмет ID ${field.id}"
            }
            val input = addNumberField("$itemName • запись ${index + 1}", field.count.toString())
            inventoryInputs.add(field to input)
        }
        addButton("Сохранить изменённую копию") { prepareOutput() }
        addHint("Не меняй имя файла при переносе в pt_saves. Игра распознаёт сохранение по исходному имени.")
        addButton("Открыть другое сохранение") { openSavePicker() }
    }

    private fun prepareOutput() {
        try {
            val data = parsed ?: error("Сначала выбери сохранение")
            val payload = data.payload.copyOf()
            val newbucks = parseUnsigned(newbucksInput?.text?.toString(), "Ньюбаки")
            val water = parseUnsigned(waterInput?.text?.toString(), "Вода")
            data.currencyOffsets.forEach { writeU32(payload, it, newbucks) }
            val waterField = data.inventoryFields.single { it.id == WATER_ID }
            writeU32(payload, waterField.countOffset, water)
            inventoryInputs.forEach { (field, input) ->
                writeU32(payload, field.countOffset, parseUnsigned(input.text.toString(), "Предмет ID ${field.id}"))
            }

            if (data.currencyOffsets.any { readU32(payload, it) != newbucks }) error("Проверка ньюбаксов не пройдена")
            if (readU32(payload, waterField.countOffset) != water) error("Проверка воды не пройдена")
            inventoryInputs.forEach { (field, input) ->
                if (readU32(payload, field.countOffset) != parseUnsigned(input.text.toString(), "Предмет")) {
                    error("Проверка инвентаря не пройдена")
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
        if (value < 0 || value > 4294967295L) error("$label: допустимый диапазон 0–4294967295")
        return value
    }

    private fun findAll(data: ByteArray, pattern: ByteArray): List<Int> {
        val found = mutableListOf<Int>()
        if (pattern.isEmpty() || data.size < pattern.size) return found
        for (i in 0..data.size - pattern.size) {
            var match = true
            for (j in pattern.indices) {
                if (data[i + j] != pattern[j]) {
                    match = false
                    break
                }
            }
            if (match) found.add(i)
        }
        return found
    }

    private fun readU32(data: ByteArray, offset: Int): Long {
        if (offset < 0 || offset + 4 > data.size) error("Поле выходит за границы сохранения")
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

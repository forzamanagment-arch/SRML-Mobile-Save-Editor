package com.srmlmobile.saveeditor

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/** Read-only pattern recognizer for the pinned Slime Rancher Mobile save format. */
class SaveAnalyzerActivity : Activity() {
    companion object {
        private const val PICK_SAVE = 2001
        private const val MAX_BYTES = 64 * 1024 * 1024
        private val GAME_SIGNATURE = byteArrayOf(0x06, 0x53, 0x52, 0x47, 0x41, 0x4d, 0x45)
        private val PLAYER_MARKER = byteArrayOf(0x04, 0x53, 0x52, 0x50, 0x4c, 0x0e, 0, 0, 0, 0, 0x30)
        private val INVENTORY_MARKER = byteArrayOf(0x04, 0x53, 0x52, 0x41, 0x44, 0x02, 0, 0, 0, 0, 0x30)
        private val ITEM_NAMES = mapOf(
            0L to "Пустая запись",
            3L to "Pink Slime",
            16L to "Кур-курица (Hen-Hen)",
            18L to "Цыпчик (Chickadoo)",
            37L to "Tabby Slime",
            48L to "Вода (ID 48, мобильное соответствие пока кандидат)"
        )
    }

    private data class Item(val id: Long, val count: Long)
    private data class Result(
        val version: String?,
        val world: String?,
        val newbucks: Long?,
        val currencyConfirmed: Boolean,
        val items: List<Item>,
        val notes: List<String>
    )

    private lateinit var page: LinearLayout
    private lateinit var status: TextView
    private var sourceName = "save"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        welcome()
    }

    private fun base(title: String) {
        val scroll = ScrollView(this)
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(28))
            setBackgroundColor(Color.rgb(8, 9, 12))
        }
        scroll.addView(page)
        setContentView(scroll)
        page.addView(TextView(this).apply {
            text = title
            textSize = 25f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(8))
        })
        status = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(10), 0, dp(16))
        }
        page.addView(status)
    }

    private fun welcome() {
        base("Распознавание сохранения")
        status.text = "Выбери сохранение Slime Rancher Mobile. Приложение прочитает архив и попробует определить версию, Ньюбаксы и инвентарь. Исходный файл не изменяется."
        button("Выбрать файл сохранения") { pick() }
        hint("Тестовый распознаватель. Неизвестные поля будут отмечены как неопределённые, а не угаданы.")
    }

    private fun pick() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, PICK_SAVE)
    }

    @Deprecated("Using document picker result for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_SAVE || resultCode != RESULT_OK || data?.data == null) return
        try {
            val uri = data.data!!
            sourceName = queryName(uri) ?: "save"
            val raw = contentResolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > MAX_BYTES) error("Архив больше 64 МБ")
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            } ?: error("Не удалось прочитать файл")
            show(analyzeArchive(raw))
        } catch (e: Exception) {
            base("Распознавание не удалось")
            status.text = e.message ?: "Неизвестная ошибка"
            button("Выбрать другой файл") { pick() }
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

    private fun analyzeArchive(raw: ByteArray): Result {
        var payload: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(raw)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name.endsWith("-sav", true)) {
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val n = zip.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) error("Сохранение больше 64 МБ")
                        out.write(buffer, 0, n)
                    }
                    val candidate = out.toByteArray()
                    if (startsWith(candidate, GAME_SIGNATURE)) {
                        if (payload != null) error("В архиве несколько подходящих файлов *-sav")
                        payload = candidate
                    }
                }
                zip.closeEntry()
            }
        }
        return analyzePayload(payload ?: error("Не найден файл *-sav с сигнатурой SRGAME"))
    }

    private fun analyzePayload(bytes: ByteArray): Result {
        val header = String(bytes, 0, minOf(bytes.size, 512), Charsets.UTF_8)
        val version = Regex("\\b\\d+\\.\\d+\\.\\d+\\b").find(header)?.value
        val world = Regex("\\d{14}-\\d+\\t([^\\u0000-\\u001f]{1,48})")
            .find(header)?.groupValues?.getOrNull(1)?.trim()

        val playerMarkerAt = findAll(bytes, PLAYER_MARKER).firstOrNull()
            ?: error("Не найден блок игрока SRPL")
        val playerTag = playerMarkerAt + 1

        // The training fixture stores the same Newbucks value twice at these
        // positions relative to the SRPL tag. A mismatch is never guessed.
        val firstCurrency = readU32(bytes, playerTag + 22)
        val secondCurrency = readU32(bytes, playerTag + 30)
        val currencyOk = firstCurrency != null && firstCurrency == secondCurrency
        val currency = if (currencyOk) firstCurrency else null

        // The inventory records form a compact SRAD cluster inside the player
        // section. Other SRAD records elsewhere in the world are excluded.
        val markers = findAllInRange(bytes, INVENTORY_MARKER, playerTag, minOf(bytes.size, playerTag + 6000))
        val groups = mutableListOf<MutableList<Int>>()
        for (marker in markers) {
            val group = groups.lastOrNull()
            if (group == null || marker - group.last() > 128) groups.add(mutableListOf(marker))
            else group.add(marker)
        }
        val selected = groups.maxByOrNull { it.size } ?: emptyList()
        val items = selected.mapNotNull { marker ->
            val id = readU32(bytes, marker + INVENTORY_MARKER.size)
            val count = readU32(bytes, marker + INVENTORY_MARKER.size + 4)
            if (id == null || count == null) null else Item(id, count)
        }

        val notes = mutableListOf<String>()
        notes.add(if (currencyOk) "Два поля Ньюбаксов совпали по обученному шаблону SRPL."
            else "Дублированный шаблон Ньюбаксов не совпал; значение не определено.")
        notes.add(if (items.isNotEmpty()) "Найдена компактная группа из " + items.size + " записей SRAD."
            else "Записи инвентаря SRAD не найдены.")
        if (version != "1.1.1103") notes.add("Версия отличается от обучающей 1.1.1103: результаты предварительные.")
        return Result(version, world, currency, currencyOk, items, notes)
    }

    private fun show(result: Result) {
        base("Результат распознавания")
        status.text = "Файл: " + sourceName + "\nМир: " + (result.world?.takeIf { it.isNotBlank() } ?: sourceName) +
            "\nВерсия: " + (result.version ?: "не определена") + "\nБлок игрока SRPL: найден"
        section("Ньюбаксы")
        line(if (result.currencyConfirmed) "Найдено: " + result.newbucks +
            "\nУверенность: высокая для обученного шаблона 1.1.1103"
        else "Не удалось безопасно определить. Значение не угадано.")
        section("Инвентарь")
        if (result.items.isEmpty()) line("Записи инвентаря не распознаны.")
        result.items.forEachIndexed { index, item ->
            val label = ITEM_NAMES[item.id] ?: "Неизвестный предмет ID " + item.id
            line("Запись " + (index + 1) + ": " + label + " × " + item.count)
        }
        section("Пока не распознано")
        line("Ключи, лопнутые гордо, разблокировка водяного слота, день и время пока не имеют подтверждённых шаблонов.")
        section("Диагностика")
        result.notes.forEach { line("• " + it) }
        hint("Это read-only тест. Файл не изменён. Сверь показанные количества с игрой.")
        button("Проверить другое сохранение") { pick() }
    }

    private fun startsWith(data: ByteArray, pattern: ByteArray): Boolean {
        if (data.size < pattern.size) return false
        for (i in pattern.indices) if (data[i] != pattern[i]) return false
        return true
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

    private fun section(value: String) {
        page.addView(TextView(this).apply {
            text = value
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(0, dp(16), 0, dp(6))
        })
    }

    private fun line(value: String) {
        page.addView(TextView(this).apply {
            text = value
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(dp(4), dp(5), dp(4), dp(5))
        })
    }

    private fun button(value: String, action: () -> Unit) {
        page.addView(Button(this).apply {
            text = value
            isAllCaps = false
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply {
            topMargin = dp(10)
        })
    }

    private fun hint(value: String) {
        page.addView(TextView(this).apply {
            text = value
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, dp(14), 0, dp(8))
        })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

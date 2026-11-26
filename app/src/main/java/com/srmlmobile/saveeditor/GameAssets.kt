package com.srmlmobile.saveeditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface

object GameAssets {
    private const val ICON_ROOT = "game-icons"
    private const val FONT_ROOT = "fonts"
    private val cachedFonts = mutableMapOf<Boolean, Typeface>()
    private var indexedIcons: List<String>? = null

    @JvmStatic fun typeface(context: Context): Typeface = typeface(context, false)

    @JvmStatic fun typeface(context: Context, bold: Boolean): Typeface {
        cachedFonts[bold]?.let { return it }
        val files = allFiles(context, FONT_ROOT)
        val preferred = if (bold) listOf("nunito-bold.", "nunito-extrabold.", "nunito-semibold.")
            else listOf("nunito-regular.", "nunito-medium.")
        for (candidate in preferred) {
            val file = files.firstOrNull {
                val n = it.substringAfterLast('/').lowercase()
                (n.endsWith(".ttf") || n.endsWith(".otf")) && n.startsWith(candidate)
            }
            if (file != null) {
                try {
                    return Typeface.createFromAsset(context.assets, file).also { cachedFonts[bold] = it }
                } catch (_: Exception) {}
            }
        }
        val fallback = files.firstOrNull { it.endsWith(".ttf", true) || it.endsWith(".otf", true) }
        if (fallback != null) try {
            return Typeface.createFromAsset(context.assets, fallback).also { cachedFonts[bold] = it }
        } catch (_: Exception) {}
        return Typeface.create("sans-serif-rounded", if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    @JvmStatic fun exactIcon(context: Context, filename: String): Bitmap? {
        val files = indexedIcons ?: allFiles(context, ICON_ROOT).also { indexedIcons = it }
        val match = files.firstOrNull { it.substringAfterLast('/').equals(filename, true) } ?: return null
        return try { context.assets.open(match).use { BitmapFactory.decodeStream(it) } } catch (_: Exception) { null }
    }

    @JvmStatic fun icon(context: Context, vararg hints: String): Bitmap? {
        val files = indexedIcons ?: allFiles(context, ICON_ROOT).also { indexedIcons = it }
        val keys = hints.map { normalize(it) }.filter { it.length > 1 }
        if (keys.isEmpty()) return null
        val match = files.mapNotNull { path ->
            val raw = path.substringAfterLast('/').substringBeforeLast('.')
            val name = normalize(raw)
            var score = 0
            for (key in keys) {
                score = maxOf(score, when {
                    name == key -> 1000
                    name.startsWith(key) -> 760
                    key.startsWith(name) && name.length >= 5 -> 560
                    name.contains(key) -> 430
                    key.contains(name) && name.length >= 6 -> 260
                    else -> 0
                })
            }
            if (score == 0) null else path to score
        }.maxByOrNull { it.second }?.first ?: return null
        return try { context.assets.open(match).use { BitmapFactory.decodeStream(it) } } catch (_: Exception) { null }
    }

    private fun normalize(value: String): String {
        var name = value.substringBeforeLast('.', value).lowercase()
            .replace(Regex("[^a-z0-9а-я]+"), "")
        name = name.removePrefix("300px")
        name = name.removePrefix("icon")
        name = name.removePrefix("plort")
        name = name.removeSuffix("sp").removeSuffix("mp")
        name = name.removeSuffix("sr")
        if (name.endsWith("0")) name = name.dropLast(1)
        return name
    }

    private fun allFiles(context: Context, root: String): List<String> {
        val result = mutableListOf<String>()
        fun walk(path: String) {
            val children = try { context.assets.list(path)?.toList().orEmpty() } catch (_: Exception) { emptyList() }
            for (child in children) {
                val full = "$path/$child"
                val nested = try { context.assets.list(full)?.isNotEmpty() == true } catch (_: Exception) { false }
                if (nested) walk(full) else result.add(full)
            }
        }
        walk(root)
        return result
    }
}

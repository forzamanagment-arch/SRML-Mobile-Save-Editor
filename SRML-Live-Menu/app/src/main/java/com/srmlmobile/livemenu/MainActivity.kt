package com.srmlmobile.livemenu

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private val blue = Color.rgb(49, 73, 110)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (Settings.canDrawOverlays(this)) render()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 36, 28, 36)
            setBackgroundColor(Color.rgb(12, 14, 19))
        }
        val title = TextView(this).apply {
            text = "SRML LIVE MENU"
            textSize = 25f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "Тест плавающего меню для Slime Rancher Mobile"
            textSize = 15f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 14, 0, 28)
        }
        val status = TextView(this).apply {
            text = if (Settings.canDrawOverlays(this@MainActivity))
                "Разрешение оверлея: выдано"
            else "Разрешение оверлея: не выдано"
            textSize = 14f
            setTextColor(if (Settings.canDrawOverlays(this@MainActivity)) Color.GREEN else Color.YELLOW)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 22)
        }
        val permission = makeButton("Разрешить отображение поверх окон") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
        }
        val start = makeButton("Запустить плавающее меню") {
            if (Settings.canDrawOverlays(this)) {
                val intent = Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_SHOW)
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
            } else {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            }
        }
        val stop = makeButton("Остановить меню") {
            stopService(Intent(this, OverlayService::class.java))
        }
        root.addView(title)
        root.addView(subtitle)
        root.addView(status)
        root.addView(permission)
        root.addView(start)
        root.addView(stop)
        setContentView(root)
    }

    private fun makeButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setTextColor(Color.WHITE)
        setBackgroundColor(blue)
        isAllCaps = false
        setOnClickListener { action() }
        val lp = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 7, 0, 7) }
        layoutParams = lp
    }
}

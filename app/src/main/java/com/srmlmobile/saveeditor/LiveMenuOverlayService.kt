package com.srmlmobile.saveeditor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.text.InputType
import kotlin.math.abs
import kotlin.math.min

class LiveMenuOverlayService : Service() {
    private lateinit var wm: WindowManager
    private var bubble: View? = null
    private var panel: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var dark = true
    private var russian = false

    private val navy get() = if (dark) Color.rgb(17, 20, 25) else Color.rgb(245, 240, 232)
    private val card get() = if (dark) Color.rgb(34, 39, 46) else Color.rgb(255, 253, 247)
    private val alt get() = if (dark) Color.rgb(43, 49, 58) else Color.rgb(235, 230, 220)
    private val ink get() = if (dark) Color.rgb(244, 242, 236) else Color.rgb(45, 55, 72)
    private val muted get() = if (dark) Color.rgb(177, 187, 198) else Color.rgb(107, 123, 141)
    private val blue = Color.rgb(49, 73, 110)
    private val pink = Color.rgb(255, 107, 157)
    private fun tr(en: String, ru: String) = if (russian) ru else en

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("srml_settings", MODE_PRIVATE)
        dark = prefs.getBoolean("dark_theme", true)
        russian = prefs.getBoolean("russian", false)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createChannel()
        startForeground(61, buildNotification())
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, tr("Overlay permission is missing", "Нет разрешения на отображение поверх окон"), Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }
        try {
            showBubble()
        } catch (error: Throwable) {
            AppLog.e(this, "OVERLAY", "Could not create floating bubble: " + error.javaClass.simpleName)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (bubble == null) showBubble()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        hidePanel()
        remove(bubble)
        bubble = null
        super.onDestroy()
    }

    private fun overlayType() = if (Build.VERSION.SDK_INT >= 26)
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun params(width: Int, height: Int, focusable: Boolean = false) =
        WindowManager.LayoutParams(
            width, height, overlayType(),
            (if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(18)
            y = dp(110)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

    private fun showBubble() {
        if (bubble != null || !Settings.canDrawOverlays(this)) return
        val b = TextView(this).apply {
            text = "SR"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = GameAssets.typeface(this@LiveMenuOverlayService, true)
            gravity = Gravity.CENTER
            background = shape(blue, 100, Color.rgb(103, 133, 184), 2)
            elevation = dp(10).toFloat()
        }
        val lp = params(dp(54), dp(54))
        wm.addView(b, lp)
        bubble = b
        attachBubbleDrag(b, lp)
    }

    private fun attachBubbleDrag(view: View, lp: WindowManager.LayoutParams) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    startX = lp.x; startY = lp.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (abs(dx) + abs(dy) > dp(7)) moved = true
                    if (moved) {
                        lp.x = startX + dx; lp.y = startY + dy
                        try { wm.updateViewLayout(view, lp) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) togglePanel()
                    true
                }
                else -> true
            }
        }
    }

    private fun togglePanel() {
        if (panel != null) hidePanel() else showPanel()
    }

    private fun hidePanel() {
        val old = panel ?: return
        try {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(old.windowToken, 0)
        } catch (_: Exception) {}
        remove(old)
        panel = null
        panelParams = null
    }

    private fun showPanel() {
        if (panel != null) return
        val scroll = ScrollView(this).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            setPadding(dp(0), dp(0), dp(0), dp(4))
            background = shape(navy, 24, if (dark) Color.rgb(65, 73, 83) else Color.rgb(224, 216, 204), 1)
            elevation = dp(18).toFloat()
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(14))
            clipChildren = true
            clipToPadding = true
        }
        scroll.addView(body, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        val dragHandle = TextView(this).apply {
            text = "•••   " + tr("DRAG TO MOVE MENU", "ПЕРЕМЕСТИТЬ МЕНЮ") + "   •••"
            textSize = 10f
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
            setTextColor(muted)
            typeface = GameAssets.typeface(this@LiveMenuOverlayService, true)
            background = shape(alt, 14, if (dark) Color.rgb(65, 73, 83) else Color.rgb(224, 216, 204), 1)
        }
        val dragParams = LinearLayout.LayoutParams(-1, dp(28))
        dragParams.bottomMargin = dp(6)
        body.addView(dragHandle, dragParams)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = TextView(this).apply {
            text = "SRML  /  MOD MENU"
            textSize = 16f
            setTextColor(ink)
            typeface = GameAssets.typeface(this@LiveMenuOverlayService, true)
        }
        header.addView(heading, LinearLayout.LayoutParams(0, dp(42), 1f))
        val close = TextView(this).apply {
            text = "×"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(muted)
            typeface = Typeface.DEFAULT
        }
        header.addView(close, LinearLayout.LayoutParams(dp(42), dp(42)))
        body.addView(header)
        close.setOnClickListener { hidePanel() }

        val prototype = TextView(this).apply {
            text = tr("INTERFACE PROTOTYPE · GAME VALUES ARE NOT CONNECTED",
                "ПРОТОТИП ИНТЕРФЕЙСА · ИГРОВЫЕ ЗНАЧЕНИЯ НЕ ПОДКЛЮЧЕНЫ")
            textSize = 9f
            letterSpacing = 0.06f
            setTextColor(pink)
            typeface = GameAssets.typeface(this@LiveMenuOverlayService, true)
            setPadding(dp(2), dp(2), dp(2), dp(9))
        }
        body.addView(prototype)

        addSection(body, tr("PLAYER", "ИГРОК"))
        addNumberRow(body, tr("Newbucks", "Ньюбаксы"), "Newbucks", "9535")
        addNumberRow(body, tr("Max Health", "Макс. здоровье"), "Heart.PNG", "100")
        addNumberRow(body, tr("Max Energy", "Макс. энергия"), "Energy", "100")
        addNumberRow(body, tr("Keys", "Ключи"), "Key", "2")
        addToggleRow(body, tr("Infinity Health", "Бесконечное здоровье"), "Heart.PNG")
        addToggleRow(body, tr("Infinity Energy", "Бесконечная энергия"), "Energy")

        addSection(body, tr("INVENTORY EDITOR", "РЕДАКТОР ИНВЕНТАРЯ"))
        for (slot in 1..4) addInventorySlot(body, slot, false)
        addInventorySlot(body, 5, true)

        val apply = makeButton(tr("Apply (prototype)", "Применить (прототип)"), pink)
        apply.setOnClickListener {
            Toast.makeText(this, tr("Prototype only: nothing was changed.", "Это прототип: данные не изменены."), Toast.LENGTH_SHORT).show()
        }
        val applyParams = LinearLayout.LayoutParams(-1, dp(46))
        applyParams.topMargin = dp(12)
        body.addView(apply, applyParams)

        val availableWidth = (resources.displayMetrics.widthPixels - dp(24)).coerceAtLeast(dp(240))
        val panelWidth = min(dp(336), availableWidth)
        val availableHeight = (resources.displayMetrics.heightPixels - dp(72)).coerceAtLeast(dp(240))
        val lp = params(panelWidth, min(dp(640), availableHeight), true).apply {
            x = dp(12)
            y = dp(54)
        }
        wm.addView(scroll, lp)
        panel = scroll
        panelParams = lp
        attachPanelDrag(dragHandle)
    }

    private fun attachPanelDrag(handle: View) {
        val lp = panelParams ?: return
        val target = panel ?: return
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    startX = lp.x; startY = lp.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (abs(dx) + abs(dy) > dp(7)) moved = true
                    if (moved) {
                        val screenW = resources.displayMetrics.widthPixels
                        val screenH = resources.displayMetrics.heightPixels
                        lp.x = (startX + dx).coerceIn(0, (screenW - lp.width).coerceAtLeast(0))
                        lp.y = (startY + dy).coerceIn(0, (screenH - lp.height).coerceAtLeast(0))
                        try { wm.updateViewLayout(target, lp) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> true
                else -> true
            }
        }
    }

    private fun addSection(parent: LinearLayout, title: String) {
        parent.addView(TextView(this).apply {
            text = title
            textSize = 10f
            letterSpacing = 0.13f
            setTextColor(pink)
            typeface = GameAssets.typeface(this@LiveMenuOverlayService, true)
            setPadding(dp(2), dp(12), 0, dp(6))
        })
    }

    private fun addNumberRow(parent: LinearLayout, label: String, iconHint: String, value: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = shape(card, 15, if (dark) Color.rgb(65, 73, 83) else Color.rgb(224, 216, 204), 1)
        }
        addAssetIcon(row, iconHint, 30)
        val title = TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(ink)
            typeface = GameAssets.typeface(this@LiveMenuOverlayService)
            setPadding(dp(8), 0, dp(6), 0)
        }
        row.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        val input = EditText(this).apply {
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(value)
            textSize = 14f
            setTextColor(ink)
            setHintTextColor(muted)
            gravity = Gravity.CENTER
            typeface = GameAssets.typeface(this@LiveMenuOverlayService, true)
            background = shape(alt, 10, Color.TRANSPARENT, 0)
            setPadding(dp(6), dp(5), dp(6), dp(5))
        }
        row.addView(input, LinearLayout.LayoutParams(dp(94), dp(40)))
        val p = LinearLayout.LayoutParams(-1, dp(54))
        p.bottomMargin = dp(5)
        parent.addView(row, p)
    }

    private fun addToggleRow(parent: LinearLayout, label: String, iconHint: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(4), dp(8), dp(4))
            background = shape(card, 15, if (dark) Color.rgb(65, 73, 83) else Color.rgb(224, 216, 204), 1)
        }
        val title = TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(ink)
            typeface = GameAssets.typeface(this@LiveMenuOverlayService)
        }
        addAssetIcon(row, iconHint, 28)
        title.setPadding(dp(8), 0, dp(6), 0)
        row.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        val toggle = makeButton(tr("Enable", "Включить"), blue)
        toggle.textSize = 11f
        toggle.setOnClickListener {
            val enabled = toggle.tag != true
            toggle.tag = enabled
            toggle.text = if (enabled) tr("Disable", "Выключить") else tr("Enable", "Включить")
            toggle.background = shape(if (enabled) pink else blue, 22, Color.TRANSPARENT, 0)
        }
        row.addView(toggle, LinearLayout.LayoutParams(dp(96), dp(38)))
        val p = LinearLayout.LayoutParams(-1, dp(50))
        p.bottomMargin = dp(5)
        parent.addView(row, p)
    }

    private fun addInventorySlot(parent: LinearLayout, slot: Int, water: Boolean) {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(9), dp(10), dp(9))
            background = shape(card, 16, if (dark) Color.rgb(65, 73, 83) else Color.rgb(224, 216, 204), 1)
        }
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        addAssetIcon(heading, if (water) "Water_icon.webp" else "Pink Slime", 28)
        val slotName = TextView(this).apply {
            text = if (water) tr("Water slot", "Слот воды") else tr("Slot $slot", "Слот $slot")
            textSize = 12f
            setTextColor(ink)
            typeface = GameAssets.typeface(this@LiveMenuOverlayService, true)
            setPadding(dp(8), 0, 0, 0)
        }
        heading.addView(slotName)
        wrapper.addView(heading)
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fields.addView(smallField(tr("Item ID", "ID предмета"), if (water) "48" else ""), LinearLayout.LayoutParams(0, dp(43), 1f))
        val gap = View(this)
        fields.addView(gap, LinearLayout.LayoutParams(dp(7), 1))
        fields.addView(smallField(tr("Quantity", "Количество"), "0"), LinearLayout.LayoutParams(0, dp(43), 1f))
        val fp = LinearLayout.LayoutParams(-1, dp(43))
        fp.topMargin = dp(7)
        wrapper.addView(fields, fp)
        val p = LinearLayout.LayoutParams(-1, -2)
        p.bottomMargin = dp(6)
        parent.addView(wrapper, p)
    }

    private fun smallField(hint: String, value: String): EditText = EditText(this).apply {
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_NUMBER
        this.hint = hint
        setText(value)
        textSize = 11f
        setTextColor(ink)
        setHintTextColor(muted)
        gravity = Gravity.CENTER
        typeface = GameAssets.typeface(this@LiveMenuOverlayService)
        background = shape(alt, 10, Color.TRANSPARENT, 0)
        setPadding(dp(5), dp(4), dp(5), dp(4))
    }

    private fun addAssetIcon(parent: LinearLayout, hint: String, size: Int) {
        val image = ImageView(this)
        val bitmap = GameAssets.icon(this, hint)
        if (bitmap != null) image.setImageBitmap(bitmap)
        image.scaleType = ImageView.ScaleType.FIT_CENTER
        parent.addView(image, LinearLayout.LayoutParams(dp(size), dp(size)))
    }

    private fun makeButton(label: String, color: Int) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 13f
        setTextColor(Color.WHITE)
        typeface = GameAssets.typeface(this@LiveMenuOverlayService, true)
        background = shape(color, 24, Color.TRANSPARENT, 0)
        minHeight = dp(38)
        setPadding(dp(8), 0, dp(8), 0)
    }

    private fun shape(color: Int, radius: Int, stroke: Int, strokeWidth: Int) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (strokeWidth > 0) setStroke(dp(strokeWidth), stroke)
        }

    private fun remove(view: View?) {
        if (view != null) try { wm.removeView(view) } catch (_: Exception) {}
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("srml_menu", "SRML Mod Menu", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "srml_menu")
            else @Suppress("DEPRECATION") Notification.Builder(this)
        return builder.setContentTitle("SRML Game Tools")
            .setContentText(tr("Floating Mod Menu is active", "Плавающее Mod Menu активно"))
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }
}

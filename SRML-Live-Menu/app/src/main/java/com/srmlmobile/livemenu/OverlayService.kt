package com.srmlmobile.livemenu

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
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.max

class OverlayService : Service() {
    companion object {
        const val ACTION_SHOW = "com.srmlmobile.livemenu.SHOW"
        private const val CHANNEL_ID = "srml_live_menu"
        private const val NOTIFICATION_ID = 41
    }

    private lateinit var wm: WindowManager
    private var bubble: View? = null
    private var panel: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private val navy = Color.rgb(15, 19, 28)
    private val blue = Color.rgb(49, 73, 110)
    private val muted = Color.rgb(157, 167, 184)
    private val pink = Color.rgb(235, 112, 166)

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        showBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SHOW && bubble == null) showBubble()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        remove(panel)
        remove(bubble)
        panel = null
        bubble = null
        super.onDestroy()
    }

    private fun overlayType() = if (Build.VERSION.SDK_INT >= 26)
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun params(width: Int, height: Int, gravity: Int = Gravity.TOP or Gravity.START) =
        WindowManager.LayoutParams(width, height, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply {
            this.gravity = gravity
            x = 24
            y = 180
        }

    private fun showBubble() {
        if (bubble != null) return
        val b = TextView(this).apply {
            text = "SR"
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = shape(blue, 100f, Color.rgb(94, 126, 181), 2)
            elevation = 12f
            setOnClickListener { togglePanel() }
        }
        val p = params(dp(54), dp(54))
        wm.addView(b, p)
        bubble = b
        attachDrag(b, p, false)
    }

    private fun togglePanel() {
        if (panel != null) {
            remove(panel)
            panel = null
            return
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(16))
            background = shape(navy, 22f, Color.rgb(58, 69, 89), 2)
            elevation = 18f
        }
        val header = TextView(this).apply {
            text = "SRML  /  LIVE MENU     ×"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(12))
            setOnClickListener { togglePanel() }
        }
        card.addView(header, LinearLayout.LayoutParams(-1, dp(42)))
        addSection(card, "PLAYER")
        addFeature(card, "Infinite Health")
        addFeature(card, "Infinite Energy")
        addFeature(card, "999999 Newbucks")
        addSection(card, "INVENTORY")
        addFeature(card, "Infinite Ammo")
        addFeature(card, "Item quantity")
        addSection(card, "WORLD")
        addFeature(card, "Unlock locations")
        val hint = TextView(this).apply {
            text = "Тестовый интерфейс · игровые функции пока не подключены"
            textSize = 10f
            setTextColor(muted)
            setPadding(0, dp(12), 0, 0)
        }
        card.addView(hint)
        val p = params(dp(292), -2).apply { x = dp(18); y = dp(130) }
        wm.addView(card, p)
        panel = card
        panelParams = p
        attachDrag(header, p, true)
    }

    private fun addSection(parent: LinearLayout, name: String) {
        val label = TextView(this).apply {
            text = name
            textSize = 11f
            letterSpacing = 0.16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(pink)
            setPadding(dp(3), dp(10), 0, dp(4))
        }
        parent.addView(label)
    }

    private fun addFeature(parent: LinearLayout, name: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = shape(Color.rgb(25, 31, 44), 12f, Color.rgb(42, 52, 70), 1)
        }
        val title = TextView(this).apply {
            text = name
            textSize = 13f
            setTextColor(Color.WHITE)
        }
        val state = TextView(this).apply {
            text = "NOT CONNECTED"
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(muted)
            gravity = Gravity.CENTER
        }
        row.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(state, LinearLayout.LayoutParams(dp(104), -2))
        val lp = LinearLayout.LayoutParams(-1, dp(43)).apply { setMargins(0, dp(3), 0, dp(3)) }
        parent.addView(row, lp)
    }

    private fun attachDrag(view: View, lp: WindowManager.LayoutParams, dragWholeView: Boolean) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        val target = if (dragWholeView) panel ?: return else bubble ?: return
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    startX = lp.x; startY = lp.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > dp(5)) {
                        lp.x = startX + dx; lp.y = startY + dy
                        try { wm.updateViewLayout(target, lp) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (kotlin.math.abs(event.rawX - downX) + kotlin.math.abs(event.rawY - downY) < dp(8)) {
                        if (dragWholeView) togglePanel() else togglePanel()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun shape(color: Int, radius: Float, stroke: Int, strokeWidth: Int) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius.toInt()).toFloat()
            setStroke(dp(strokeWidth), stroke)
        }

    private fun remove(view: View?) {
        if (view != null) try { wm.removeView(view) } catch (_: Exception) {}
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "SRML Live Menu", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID)
            else @Suppress("DEPRECATION") Notification.Builder(this)
        return builder.setContentTitle("SRML Live Menu")
            .setContentText("Плавающее меню активно")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }
}

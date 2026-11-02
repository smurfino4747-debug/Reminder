package com.example.promemoria

import android.Manifest
import android.app.*
import android.content.*
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.widget.*

object Scheduler {
    private fun pi(c: Context) = PendingIntent.getBroadcast(
        c, 0, Intent(c, AlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun schedule(c: Context) {
        val m = c.getSharedPreferences("p", 0).getInt("min", 30)
        val am = c.getSystemService(AlarmManager::class.java)
        val t = System.currentTimeMillis() + m * 60_000L
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms())
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi(c))
        else
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi(c))
    }

    fun cancel(c: Context) = c.getSystemService(AlarmManager::class.java).cancel(pi(c))
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val p = c.getSharedPreferences("p", 0)
        if (!p.getBoolean("on", false)) return
        if (i.action != Intent.ACTION_BOOT_COMPLETED) {
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel("r", "Promemoria", NotificationManager.IMPORTANCE_HIGH)
            )
            val n = Notification.Builder(c, "r")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Promemoria")
                .setContentText(p.getString("text", ""))
                .setAutoCancel(true)
                .build()
            nm.notify(System.currentTimeMillis().toInt(), n)
        }
        Scheduler.schedule(c)
    }
}

class MainActivity : Activity() {
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, r: Int) =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(r).toFloat() }
    private fun lp(top: Int = 0) =
        LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val bg = if (dark) 0xFF000000.toInt() else 0xFFF2F2F7.toInt()
        val card = if (dark) 0xFF1C1C1E.toInt() else 0xFFFFFFFF.toInt()
        val fg = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        val gray = 0xFF8E8E93.toInt()
        val blue = if (dark) 0xFF0A84FF.toInt() else 0xFF007AFF.toInt()
        val red = if (dark) 0xFFFF453A.toInt() else 0xFFFF3B30.toInt()

        window.statusBarColor = bg
        window.navigationBarColor = bg
        if (!dark) window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val p = getSharedPreferences("p", Context.MODE_PRIVATE)

        fun tv(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
            text = t; setTextSize(size); setTextColor(color)
            if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        fun header(t: String) = tv(t.uppercase(), 13f, gray).apply { setPadding(dp(16), dp(24), 0, dp(8)) }

        val text = EditText(this).apply {
            hint = "Es. Bevi un bicchiere d'acqua"
            setHintTextColor(gray); setTextColor(fg); setTextSize(17f)
            background = null
            setText(p.getString("text", ""))
        }
        val min = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(blue); setTextSize(17f); gravity = Gravity.END
            background = null; minWidth = dp(60)
            setText(p.getInt("min", 30).toString())
        }
        val status = tv("", 13f, gray).apply { gravity = Gravity.CENTER }
        val btn = Button(this).apply {
            isAllCaps = false; setTextSize(17f); setTextColor(0xFFFFFFFF.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            stateListAnimator = null
        }

        val textCard = LinearLayout(this).apply {
            background = rounded(card, 12); setPadding(dp(16), dp(8), dp(16), dp(8))
            addView(text, LinearLayout.LayoutParams(-1, -2))
        }
        val minRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = rounded(card, 12); setPadding(dp(16), dp(8), dp(16), dp(8))
            addView(tv("Ripeti ogni", 17f, fg), LinearLayout.LayoutParams(0, -2, 1f))
            addView(min); addView(tv(" min", 17f, gray))
        }
        val chips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            for (m in listOf(5, 10, 15, 30, 60)) {
                addView(tv("$m", 15f, blue).apply {
                    background = rounded(card, 18)
                    setPadding(dp(16), dp(8), dp(16), dp(8))
                    setOnClickListener { min.setText(m.toString()) }
                }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8) })
            }
        }

        fun refresh() {
            val on = p.getBoolean("on", false)
            btn.text = if (on) "Ferma" else "Avvia"
            btn.background = rounded(if (on) red else blue, 14)
            status.text = if (on) "Attivo: una notifica ogni ${p.getInt("min", 0)} minuti"
                          else "Nessun promemoria attivo"
        }

        btn.setOnClickListener {
            if (p.getBoolean("on", false)) {
                Scheduler.cancel(this)
                p.edit().putBoolean("on", false).apply()
            } else {
                val m = min.text.toString().toIntOrNull() ?: 0
                if (m < 1) {
                    min.error = "Minimo 1"
                    return@setOnClickListener
                }
                p.edit().putString("text", text.text.toString())
                    .putInt("min", m).putBoolean("on", true).apply()
                Scheduler.schedule(this)
            }
            refresh()
        }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(24))
            addView(tv("Promemoria", 34f, fg, true).apply { setPadding(dp(4), 0, 0, 0) })
            addView(header("Testo della notifica"))
            addView(textCard, lp())
            addView(header("Intervallo"))
            addView(minRow, lp())
            addView(chips, lp(12))
            addView(btn, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(32) })
            addView(status, lp(12))
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(bg); addView(col) })
        refresh()
    }
}

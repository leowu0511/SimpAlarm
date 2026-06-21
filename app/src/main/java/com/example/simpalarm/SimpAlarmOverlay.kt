package com.example.simpalarm

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

object SimpAlarmOverlay {
    private var overlayView: View? = null

    fun canShow(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }

    fun settingsIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
    }

    fun show(
        context: Context,
        sender: String,
        message: String,
        sourceAppLabel: String,
        sourcePackage: String,
        returnToAppOnDismiss: Boolean
    ) {
        if (!canShow(context)) {
            SimpEventLog.record(context, "無法顯示浮動鬧鐘：尚未允許顯示在其他 App 上層。")
            return
        }

        Handler(Looper.getMainLooper()).post {
            dismiss(context)

            val appContext = context.applicationContext
            val windowManager = appContext.getSystemService(WindowManager::class.java)
            val view = buildOverlayView(
                appContext,
                sender,
                message,
                sourceAppLabel,
                sourcePackage,
                returnToAppOnDismiss
            )
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            runCatching {
                windowManager.addView(view, params)
                overlayView = view
                SimpEventLog.record(appContext, "已顯示浮動鬧鐘解除畫面。")
            }.onFailure {
                SimpEventLog.record(appContext, "浮動鬧鐘顯示失敗：${it.javaClass.simpleName}")
            }
        }
    }

    fun dismiss(context: Context) {
        val view = overlayView ?: return
        overlayView = null
        Handler(Looper.getMainLooper()).post {
            runCatching {
                context.applicationContext
                    .getSystemService(WindowManager::class.java)
                    .removeView(view)
            }
        }
    }

    private fun buildOverlayView(
        context: Context,
        sender: String,
        message: String,
        sourceAppLabel: String,
        sourcePackage: String,
        returnToAppOnDismiss: Boolean
    ): View {
        val appLabel = sourceAppLabel.ifBlank { "監聽 App" }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 72, 48, 72)
            setBackgroundColor(Color.rgb(251, 228, 226))
        }

        fun addText(text: String, sizeSp: Float, color: Int, bold: Boolean = false) {
            container.addView(
                TextView(context).apply {
                    this.text = text
                    textSize = sizeSp
                    setTextColor(color)
                    gravity = Gravity.CENTER
                    if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 18 }
            )
        }

        addText("Simp Alarm", 34f, Color.rgb(74, 17, 17), bold = true)
        addText(
            if (sender.isBlank()) "$appLabel 傳訊息了" else "$sender 傳訊息了",
            24f,
            Color.rgb(74, 17, 17),
            bold = true
        )
        addText(message.ifBlank { "你設定的對象剛剛傳來通知。" }, 18f, Color.rgb(123, 81, 81))

        val dismissButton = Button(context).apply {
            text = "我醒了，關閉鬧鐘"
            setOnClickListener {
                context.startService(
                    Intent(context, SimpAlarmService::class.java).apply {
                        action = SimpAlarmService.ACTION_DISMISS_ALARM
                        putExtra(SimpAlarmService.EXTRA_RETURN_TO_APP_ON_DISMISS, returnToAppOnDismiss)
                    }
                )
                dismiss(context)
            }
        }
        container.addView(
            dismissButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 24 }
        )

        val openButton = Button(context).apply {
            text = "打開 $appLabel 回覆"
            setOnClickListener {
                context.startService(
                    Intent(context, SimpAlarmService::class.java).apply {
                        action = SimpAlarmService.ACTION_DISMISS_ALARM
                    }
                )
                dismiss(context)
                context.packageManager
                    .getLaunchIntentForPackage(sourcePackage.ifBlank { SimpNotificationListener.INSTAGRAM_PACKAGE })
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ?.let(context::startActivity)
            }
        }
        container.addView(
            openButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12 }
        )

        return container
    }
}

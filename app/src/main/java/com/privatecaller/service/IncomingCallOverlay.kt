package com.privatecaller.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.privatecaller.R

/**
 * A floating incoming-call banner drawn over whatever app is in the foreground,
 * using a TYPE_APPLICATION_OVERLAY window. This is the full-edition alternative
 * to the system CallStyle notification: because we own the layout, the
 * Answer/Decline buttons keep their icons in BOTH portrait and landscape (the
 * system heads-up degrades to text buttons in landscape).
 *
 * Requires the "Display over other apps" permission (SYSTEM_ALERT_WINDOW),
 * declared only in the full flavor. Callers must check [canDraw] first and fall
 * back to [IncomingCallNotifier] when it isn't granted.
 *
 * Touches outside the banner pass through to the app underneath; only the body
 * and the two buttons are interactive.
 */
object IncomingCallOverlay {

    private var view: View? = null

    /** Whether the overlay permission is granted (and so the overlay usable). */
    fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun show(
        context: Context,
        callerName: String,
        onBody: () -> Unit,
        onAnswer: () -> Unit,
        onDecline: () -> Unit,
    ) {
        // Replace any stale banner first.
        hide(context)

        val wm = context.getSystemService(WindowManager::class.java) ?: return
        val v = LayoutInflater.from(context).inflate(R.layout.overlay_incoming_call, null)

        v.findViewById<TextView>(R.id.overlay_name).text = callerName
        v.findViewById<TextView>(R.id.overlay_subtitle).text =
            context.getString(R.string.notif_incoming_call)
        v.findViewById<View>(R.id.overlay_body).setOnClickListener { onBody() }
        v.findViewById<View>(R.id.overlay_answer).setOnClickListener { onAnswer() }
        v.findViewById<View>(R.id.overlay_decline).setOnClickListener { onDecline() }

        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            // Not focusable / not touch-modal so the app underneath keeps working
            // and taps outside the banner fall through to it.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
        }

        runCatching {
            wm.addView(v, params)
            view = v
        }
    }

    fun hide(context: Context) {
        val v = view ?: return
        view = null
        val wm = context.getSystemService(WindowManager::class.java) ?: return
        runCatching { wm.removeView(v) }
    }
}

package de.klimakompendium.ui.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle

object ClipboardHelper {
    private const val CLEAR_AFTER_MS = 45_000L
    private val handler = Handler(Looper.getMainLooper())
    private var pendingClear: Runnable? = null

    /**
     * Kopiert Text. Bei [sensitive] wird der Inhalt als vertraulich markiert (kein Vorschau-Popup in Android 13+)
     * und nach 45 Sekunden automatisch aus der Zwischenablage entfernt.
     */
    fun copy(context: Context, label: String, text: String, sensitive: Boolean) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        // Eindeutiges Label: so erkennen wir später, ob die Zwischenablage noch UNSER Passwort enthält.
        val ownLabel = if (sensitive) "$label#${System.nanoTime()}" else label
        val clip = ClipData.newPlainText(ownLabel, text)
        if (sensitive) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(
                    if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE",
                    true,
                )
            }
        }
        cm.setPrimaryClip(clip)
        pendingClear?.let(handler::removeCallbacks)
        if (sensitive) {
            val r = Runnable { clearIfStillOurs(cm, ownLabel) }
            pendingClear = r
            handler.postDelayed(r, CLEAR_AFTER_MS)
        }
    }

    /** Leert nur, wenn dort noch unser Eintrag liegt – hat der Nutzer inzwischen etwas anderes kopiert, bleibt das erhalten. */
    private fun clearIfStillOurs(cm: ClipboardManager, ownLabel: String) {
        runCatching {
            if (cm.primaryClipDescription?.label?.toString() != ownLabel) return

            if (Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip() else cm.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }
}

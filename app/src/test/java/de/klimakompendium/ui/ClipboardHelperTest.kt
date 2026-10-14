package de.klimakompendium.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.klimakompendium.ui.util.ClipboardHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClipboardHelperTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val cm get() = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private fun clipText() = cm.primaryClip?.getItemAt(0)?.text?.toString()

    @Test fun sensitiveCopyIsClearedAfterTimeout() {
        ClipboardHelper.copy(ctx, "Passwort", "Geheim#123", sensitive = true)
        assertEquals("Geheim#123", clipText())
        shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.SECONDS)
        assertNull("Passwort muss aus der Zwischenablage verschwinden", clipText()?.takeIf { it.isNotEmpty() })
    }

    @Test fun foreignClipboardContentSurvives() {
        ClipboardHelper.copy(ctx, "Passwort", "Geheim#123", sensitive = true)
        // Nutzer kopiert danach etwas anderes → das darf nicht gelöscht werden
        cm.setPrimaryClip(ClipData.newPlainText("meins", "Telefonnummer 0123"))
        shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.SECONDS)
        assertEquals("Telefonnummer 0123", clipText())
    }

    @Test fun nonSensitiveCopyIsNeverCleared() {
        ClipboardHelper.copy(ctx, "Benutzername", "admin", sensitive = false)
        shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.SECONDS)
        assertEquals("admin", clipText())
    }
}

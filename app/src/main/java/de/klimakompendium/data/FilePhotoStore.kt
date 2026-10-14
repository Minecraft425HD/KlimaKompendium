package de.klimakompendium.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import de.klimakompendium.data.repo.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Legt Anlagenfotos verkleinert (max. 2048 px, JPEG) im privaten App-Speicher ab – nicht in der Galerie. */
class FilePhotoStore(private val context: Context) : PhotoStore {
    private val dir get() = File(context.filesDir, "photos").apply { mkdirs() }

    override suspend fun import(source: String, plantId: Long): String? = withContext(Dispatchers.IO) {
        runCatching {
            val uri = Uri.parse(source)
            val resolver = context.contentResolver

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE * 2) sample *= 2

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return@runCatching null

            val rotation = resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
            val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
            val m = Matrix().apply { postRotate(rotation.toFloat()); postScale(scale, scale) }
            val out = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)

            val file = File(dir, "plant_${plantId}_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            file.absolutePath
        }.getOrNull()
    }

    override fun delete(path: String) {
        runCatching { File(path).takeIf { it.parentFile == dir }?.delete() }
    }

    private companion object { const val MAX_SIDE = 2048 }
}

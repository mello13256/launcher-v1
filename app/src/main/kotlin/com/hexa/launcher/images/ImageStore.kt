package com.hexa.launcher.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.LruCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Custom images (hive backgrounds, home background). Picked images are decoded once, downscaled
 * and re-encoded into the launcher's private storage, so:
 * - no persistent URI permission or storage permission is needed (Photo Picker + copy);
 * - the original can be deleted from the gallery without breaking the hive;
 * - decoding at runtime is cheap (small WebP) and results are kept in an LRU cache.
 */
class ImageStore(context: Context, private val io: CoroutineDispatcher) {

    private val dir = File(context.filesDir, "images").apply { mkdirs() }
    private val resolver = context.contentResolver

    private val cache = object : LruCache<String, Bitmap>(maxBytes()) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    /** Copies [uri] into private storage, longest side limited to [maxSide] px. Returns the stored file name. */
    suspend fun import(uri: Uri, maxSide: Int): String? = withContext(io) {
        try {
            val bitmap = decodeScaled(uri, maxSide) ?: return@withContext null
            val name = "img_" + UUID.randomUUID().toString().replace("-", "").take(16) + ".webp"
            val file = File(dir, name)
            file.outputStream().use { out ->
                @Suppress("DEPRECATION")
                val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
                bitmap.compress(format, 90, out)
            }
            cache.put(name, bitmap)
            name
        } catch (e: Exception) {
            null
        }
    }

    fun peek(name: String): Bitmap? = cache.get(name)

    suspend fun load(name: String): Bitmap? {
        cache.get(name)?.let { return it }
        return withContext(io) {
            val file = File(dir, name)
            if (!file.exists()) return@withContext null
            val bitmap = BitmapFactory.decodeFile(file.path) ?: return@withContext null
            bitmap.prepareToDraw()
            cache.put(name, bitmap)
            bitmap
        }
    }

    suspend fun delete(name: String) = withContext(io) {
        cache.remove(name)
        File(dir, name).delete()
    }

    /** Removes files no longer referenced by the configuration (e.g. replaced images). */
    suspend fun deleteUnreferenced(referenced: Set<String>) = withContext(io) {
        dir.listFiles()?.forEach { f -> if (f.name !in referenced) f.delete() }
    }

    fun trim() = cache.evictAll()

    private fun decodeScaled(uri: Uri, maxSide: Int): Bitmap? =
        if (Build.VERSION.SDK_INT >= 28) decodeWithImageDecoder(uri, maxSide) else decodeWithBitmapFactory(uri, maxSide)

    /** Android 8.x: two-pass BitmapFactory decode with a power-of-two sample size, then exact scale. */
    private fun decodeWithBitmapFactory(uri: Uri, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = max(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null
        var sample = 1
        while (longest / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val scale = min(1f, maxSide.toFloat() / max(decoded.width, decoded.height).toFloat())
        if (scale >= 1f) return decoded
        return Bitmap.createScaledBitmap(decoded, (decoded.width * scale).roundToInt().coerceAtLeast(1), (decoded.height * scale).roundToInt().coerceAtLeast(1), true)
    }

    @androidx.annotation.RequiresApi(28)
    private fun decodeWithImageDecoder(uri: Uri, maxSide: Int): Bitmap? {
        val source = ImageDecoder.createSource(resolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val scale = min(1f, maxSide.toFloat() / max(w, h).toFloat())
            if (scale < 1f) decoder.setTargetSize((w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }
    }

    private companion object {
        fun maxBytes(): Int = min(Runtime.getRuntime().maxMemory() / 10, 24L * 1024 * 1024).toInt()
    }
}

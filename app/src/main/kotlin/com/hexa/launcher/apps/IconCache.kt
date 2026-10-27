package com.hexa.launcher.apps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import com.hexa.core.model.AppKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

/** Two icon sizes: small for lists, large for hives. Separate entries avoid scaling huge bitmaps for list rows. */
enum class IconSize(val dp: Int) { SMALL(44), LARGE(96) }

/**
 * Icons rendered once into bitmaps and kept in a size-bounded LRU cache. Loading happens on a
 * background dispatcher; drawing a cached icon costs nothing but a texture upload.
 */
class IconCache(
    context: Context,
    private val apps: AppRepository,
    private val io: CoroutineDispatcher,
) {
    private val density = context.resources.displayMetrics.density
    private val densityDpi = context.resources.displayMetrics.densityDpi

    private val cache = object : LruCache<String, Bitmap>(maxBytes()) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    init {
        apps.addPackageChangeListener { pkg -> invalidatePackage(pkg) }
    }

    fun peek(key: String, size: IconSize): Bitmap? = cache.get(cacheKey(key, size))

    suspend fun load(key: String, size: IconSize): Bitmap? {
        cache.get(cacheKey(key, size))?.let { return it }
        return withContext(io) {
            cache.get(cacheKey(key, size))?.let { return@withContext it }
            val info = apps.activityInfo(key) ?: return@withContext null
            val drawable = try {
                info.getBadgedIcon(densityDpi)
            } catch (e: Exception) {
                null
            } ?: return@withContext null
            val px = min((size.dp * density).roundToInt(), MAX_PX)
            val bitmap = render(drawable, px)
            cache.put(cacheKey(key, size), bitmap)
            bitmap
        }
    }

    fun invalidatePackage(packageName: String) {
        cache.snapshot().keys.filter { AppKey.packageOf(it) == packageName }.forEach { cache.remove(it) }
    }

    /**
     * Drops list-sized icons under memory pressure. Hive icons (about 16 bitmaps) are kept so the
     * home screen is complete on the very first frame after returning from an app.
     */
    fun trimSmall() {
        cache.snapshot().keys.filter { it.endsWith("@${IconSize.SMALL.name}") }.forEach { cache.remove(it) }
    }

    private fun cacheKey(key: String, size: IconSize) = "$key@${size.name}"

    private fun render(drawable: Drawable, px: Int): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null && drawable.bitmap.width == px && drawable.bitmap.height == px) {
            return drawable.bitmap
        }
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, px, px)
        drawable.draw(canvas)
        bitmap.prepareToDraw()
        return bitmap
    }

    private companion object {
        const val MAX_PX = 384

        fun maxBytes(): Int {
            val max = Runtime.getRuntime().maxMemory()
            return min(max / 10, 32L * 1024 * 1024).toInt()
        }
    }
}

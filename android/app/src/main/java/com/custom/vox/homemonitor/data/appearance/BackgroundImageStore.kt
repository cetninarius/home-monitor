package com.custom.vox.homemonitor.data.appearance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.util.Log
import com.custom.vox.homemonitor.R
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Persistent custom backgrounds under filesDir (survive APK updates).
 * Validates uploads and always stores a downsampled JPEG display copy.
 */
class BackgroundImageStore(context: Context) {
    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "appearance/backgrounds").also { it.mkdirs() }
    private val thumbDir = File(appContext.filesDir, "appearance/thumbs").also { it.mkdirs() }

    private val memory = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 24).toInt().coerceAtLeast(2048),
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun listCustom(): List<BackgroundOption> =
        dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".jpg", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                val id = "custom:${file.nameWithoutExtension}"
                BackgroundOption(id, "Custom ${file.nameWithoutExtension.take(8)}", BackgroundOption.Kind.CUSTOM)
            }
            ?: emptyList()

    fun allOptions(): List<BackgroundOption> = AppearanceStore.BUNDLED + listCustom()

    fun exists(id: String): Boolean = when {
        AppearanceStore.isBundledId(id) -> bundledRes(id) != 0
        AppearanceStore.isCustomId(id) -> fileFor(id)?.exists() == true
        else -> false
    }

    /**
     * Accept raw upload bytes. Returns the new background id (`custom:…`).
     * @throws IllegalArgumentException on validation failure
     */
    fun importUpload(bytes: ByteArray, suggestedName: String? = null): String {
        if (bytes.isEmpty()) throw IllegalArgumentException("empty_file")
        if (bytes.size > MAX_UPLOAD_BYTES) throw IllegalArgumentException("file_too_large")
        if (!isSupportedImage(bytes)) throw IllegalArgumentException("unsupported_format")

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalArgumentException("invalid_image")
        }
        if (bounds.outWidth > MAX_SOURCE_EDGE || bounds.outHeight > MAX_SOURCE_EDGE) {
            throw IllegalArgumentException("dimensions_too_large")
        }

        val sample = sampleSize(bounds.outWidth, bounds.outHeight, DISPLAY_MAX_EDGE, DISPLAY_MAX_EDGE)
        val decode = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decode)
            ?: throw IllegalArgumentException("decode_failed")

        val id = UUID.randomUUID().toString().replace("-", "").take(16)
        val out = File(dir, "$id.jpg")
        try {
            FileOutputStream(out).use { fos ->
                if (!bmp.compress(Bitmap.CompressFormat.JPEG, 88, fos)) {
                    throw IllegalArgumentException("encode_failed")
                }
            }
            writeThumb(id, bmp)
        } catch (e: Exception) {
            out.delete()
            throw e
        } finally {
            if (!bmp.isRecycled) bmp.recycle()
        }
        Log.i(TAG, "imported custom bg id=$id name=$suggestedName bytes=${bytes.size}")
        return "custom:$id"
    }

    fun deleteCustom(id: String): Boolean {
        val fileId = AppearanceStore.customFileId(id) ?: return false
        memory.remove(id)
        thumbDir.resolve("$fileId.jpg").delete()
        return fileFor(id)?.delete() == true
    }

    fun loadDisplayBitmap(id: String, maxW: Int = DISPLAY_MAX_EDGE, maxH: Int = DISPLAY_MAX_EDGE): Bitmap? {
        memory.get(id)?.let { return it }
        val bmp = when {
            AppearanceStore.isBundledId(id) -> loadBundled(id, maxW, maxH)
            AppearanceStore.isCustomId(id) -> loadCustom(id, maxW, maxH)
            else -> null
        } ?: return null
        memory.put(id, bmp)
        return bmp
    }

    fun loadThumbBytes(id: String): ByteArray? {
        if (AppearanceStore.isBundledId(id)) {
            // Cheap: decode small and re-encode, or stream raw resource — prefer small decode.
            val bmp = loadBundled(id, THUMB_EDGE, THUMB_EDGE) ?: return null
            return try {
                java.io.ByteArrayOutputStream().use { bos ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 75, bos)
                    bos.toByteArray()
                }
            } finally {
                if (!bmp.isRecycled) bmp.recycle()
            }
        }
        val fileId = AppearanceStore.customFileId(id) ?: return null
        val thumb = File(thumbDir, "$fileId.jpg")
        if (thumb.exists()) return thumb.readBytes()
        val full = loadCustom(id, THUMB_EDGE, THUMB_EDGE) ?: return null
        return try {
            writeThumb(fileId, full)
            thumb.takeIf { it.exists() }?.readBytes()
        } finally {
            if (!full.isRecycled) full.recycle()
        }
    }

    fun bundledRes(id: String): Int = when (id) {
        "bundled:baghdad" -> R.drawable.bg_baghdad
        "bundled:sunset" -> R.drawable.bg_sunset
        "bundled:jungle" -> R.drawable.bg_jungle
        // Removed gradient stubs — fall back to Baghdad.
        "bundled:dusk", "bundled:ember" -> R.drawable.bg_baghdad
        else -> 0
    }

    fun evict(id: String) {
        memory.remove(id)
    }

    private fun fileFor(id: String): File? {
        val fileId = AppearanceStore.customFileId(id) ?: return null
        return File(dir, "$fileId.jpg")
    }

    private fun loadCustom(id: String, maxW: Int, maxH: Int): Bitmap? {
        val file = fileFor(id) ?: return null
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxW, maxH)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    private fun loadBundled(id: String, maxW: Int, maxH: Int): Bitmap? {
        val res = bundledRes(id)
        if (res == 0) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(appContext.resources, res, bounds)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxW, maxH)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeResource(appContext.resources, res, opts)
    }

    private fun writeThumb(fileId: String, source: Bitmap) {
        val scaled = if (source.width > THUMB_EDGE || source.height > THUMB_EDGE) {
            val ratio = minOf(THUMB_EDGE.toFloat() / source.width, THUMB_EDGE.toFloat() / source.height)
            Bitmap.createScaledBitmap(
                source,
                (source.width * ratio).toInt().coerceAtLeast(1),
                (source.height * ratio).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            source
        }
        try {
            FileOutputStream(File(thumbDir, "$fileId.jpg")).use { fos ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 75, fos)
            }
        } finally {
            if (scaled !== source && !scaled.isRecycled) scaled.recycle()
        }
    }

    companion object {
        private const val TAG = "BackgroundImageStore"
        const val MAX_UPLOAD_BYTES = 8 * 1024 * 1024
        const val MAX_SOURCE_EDGE = 8192
        const val DISPLAY_MAX_EDGE = 1920
        const val THUMB_EDGE = 320

        fun sampleSize(w: Int, h: Int, reqW: Int, reqH: Int): Int {
            var sample = 1
            if (w <= 0 || h <= 0) return 1
            while (w / sample > reqW || h / sample > reqH) {
                sample *= 2
            }
            return sample.coerceAtLeast(1)
        }

        fun isSupportedImage(bytes: ByteArray): Boolean {
            if (bytes.size < 12) return false
            // JPEG
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) return true
            // PNG
            if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
            ) {
                return true
            }
            // WebP: RIFF....WEBP
            if (bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
                bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
                bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
            ) {
                return true
            }
            return false
        }
    }
}

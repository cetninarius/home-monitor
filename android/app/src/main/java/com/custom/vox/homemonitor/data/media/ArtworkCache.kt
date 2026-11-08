package com.custom.vox.homemonitor.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Memory + disk artwork cache keyed by video id / URL.
 */
class ArtworkCache(context: Context) {

    private val dir = File(context.cacheDir, "now_playing_art").also { it.mkdirs() }
    private val memory = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 16).toInt().coerceAtLeast(2048)) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun getCached(key: String): Bitmap? = memory.get(key) ?: loadFromDisk(key)?.also { memory.put(key, it) }

    fun load(key: String, url: String): Bitmap? {
        getCached(key)?.let { return it }
        return try {
            val req = Request.Builder().url(url).get().build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "art HTTP ${resp.code} for $key")
                    return null
                }
                val bytes = resp.body?.bytes() ?: return null
                val opts = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                opts.inSampleSize = sampleSize(opts.outWidth, opts.outHeight, 640, 360)
                opts.inJustDecodeBounds = false
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
                memory.put(key, bmp)
                writeDisk(key, bytes)
                bmp
            }
        } catch (e: Exception) {
            Log.w(TAG, "art load failed: ${e.message}")
            null
        }
    }

    private fun loadFromDisk(key: String): Bitmap? {
        val f = fileFor(key)
        if (!f.exists()) return null
        return try {
            BitmapFactory.decodeFile(f.absolutePath)
        } catch (_: Exception) {
            null
        }
    }

    private fun writeDisk(key: String, bytes: ByteArray) {
        try {
            fileFor(key).writeBytes(bytes)
        } catch (_: Exception) {
        }
    }

    private fun fileFor(key: String): File {
        val digest = MessageDigest.getInstance("SHA-1").digest(key.toByteArray())
        val name = digest.joinToString("") { "%02x".format(it) }
        return File(dir, "$name.jpg")
    }

    private fun sampleSize(w: Int, h: Int, maxW: Int, maxH: Int): Int {
        var sample = 1
        while (w / sample > maxW * 2 || h / sample > maxH * 2) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }

    companion object {
        private const val TAG = "ArtworkCache"
    }
}

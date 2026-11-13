package com.chronie.homemoney.data.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import com.chronie.homemoney.domain.model.chat.ChatAttachment
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

/** Encoded image ready to be embedded in a multimodal API request. */
data class EncodedImage(val mediaType: String, val base64: String)

/**
 * Copies user-selected files into app-private storage and encodes them for
 * the three supported model backends. Copies are required because a
 * content:// URI grant usually expires as soon as the picker Activity closes.
 */
@Singleton
class ChatAttachmentCodec @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    /** Persists [uri] under filesDir/chat_attachments and describes it. */
    fun copyToPrivate(uri: Uri): ChatAttachment? {
        return try {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri) ?: "application/octet-stream"
            var displayName = "attachment_${UUID.randomUUID().toString().take(8)}"
            var declaredSize = 0L
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex >= 0) {
                        cursor.getString(nameIndex)?.let { displayName = it }
                    }
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        declaredSize = cursor.getLong(sizeIndex)
                    }
                }
            }

            val dir = File(context.filesDir, ATTACHMENT_DIR).apply { mkdirs() }
            val safeName = displayName.replace(Regex("[/\\\\:*?\"<>|]"), "_").take(80)
            val file = File(dir, "${UUID.randomUUID()}_$safeName")
            val copied = resolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
                file.length()
            } ?: return null

            if (copied == 0L || copied > MAX_FILE_BYTES) {
                Log.w(TAG, "Rejecting attachment of $copied bytes")
                file.delete()
                return null
            }

            ChatAttachment(
                path = file.absolutePath,
                displayName = displayName,
                mimeType = mimeType,
                sizeBytes = if (declaredSize > 0) declaredSize else copied
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy attachment $uri", e)
            null
        }
    }

    /** Reads a text-like attachment, truncated to keep prompts bounded. */
    fun readText(attachment: ChatAttachment, limit: Int = TEXT_LIMIT): String {
        if (!attachment.isText) return ""
        return try {
            val file = File(attachment.path)
            if (!file.exists()) return ""
            file.readText(Charsets.UTF_8).take(limit)
        } catch (e: Exception) {
            Log.w(TAG, "Could not read text attachment ${attachment.displayName}", e)
            ""
        }
    }

    /**
     * Downscales and JPEG-encodes an image for OpenAI image_url / Anthropic
     * image source blocks. Large photos would otherwise blow up request size.
     */
    fun encodeImage(attachment: ChatAttachment): EncodedImage? {
        if (!attachment.isImage) return null
        return try {
            val file = File(attachment.path)
            if (!file.exists()) return null

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val longest = max(bounds.outWidth, bounds.outHeight)
            if (longest <= 0) return null
            val sample = (longest.toFloat() / MAX_IMAGE_EDGE).roundToInt().coerceAtLeast(1)

            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
            val scaled = if (max(bitmap.width, bitmap.height) > MAX_IMAGE_EDGE) {
                val ratio = MAX_IMAGE_EDGE.toFloat() / max(bitmap.width, bitmap.height)
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * ratio).roundToInt().coerceAtLeast(1),
                    (bitmap.height * ratio).roundToInt().coerceAtLeast(1),
                    true
                )
            } else {
                bitmap
            }

            val bytes = ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                out.toByteArray()
            }
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()

            EncodedImage("image/jpeg", Base64.encodeToString(bytes, Base64.NO_WRAP))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encode image ${attachment.displayName}", e)
            null
        }
    }

    /** Best-effort cleanup when a conversation is deleted. */
    fun deleteFiles(attachments: List<ChatAttachment>) {
        attachments.forEach { runCatching { File(it.path).delete() } }
    }

    companion object {
        private const val TAG = "ChatAttachmentCodec"
        private const val ATTACHMENT_DIR = "chat_attachments"
        const val MAX_FILE_BYTES = 10L * 1024 * 1024
        const val TEXT_LIMIT = 12_000
        const val MAX_IMAGE_EDGE = 1024
        const val JPEG_QUALITY = 80
    }
}

package com.tbr.clipilot

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.media3.common.Effect
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.RgbFilter
import androidx.media3.transformer.ClippingConfiguration
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(UnstableApi::class)
class VideoEditor(private val context: Context) {
    suspend fun export(input: Uri, plan: EditPlan): File {
        val output = withContext(Dispatchers.IO) {
            val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
            if (!outputDir.exists() && !outputDir.mkdirs()) throw IllegalStateException("Não consegui criar a pasta de exportação.")
            File(outputDir, "clipilot_${System.currentTimeMillis()}.mp4")
        }
        try {
            suspendCancellableCoroutine<Unit> { continuation ->
                // Transformer is created and used on the main application thread, as required by Media3.
                val clipping = ClippingConfiguration.Builder()
                    .setStartPositionMs(plan.startMs)
                    .setEndPositionMs(plan.endMs ?: throw IllegalArgumentException("Fim do trecho não definido."))
                    .build()
                val mediaItem = androidx.media3.common.MediaItem.Builder()
                    .setUri(input)
                    .setClippingConfiguration(clipping)
                    .build()
                val videoEffects = mutableListOf<Effect>()
                if (plan.grayscale) videoEffects.add(RgbFilter.createGrayscaleFilter())
                val edited = EditedMediaItem.Builder(mediaItem)
                    .setRemoveAudio(plan.muteAudio)
                    .setEffects(Effects(emptyList(), videoEffects))
                    .build()
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Falha ao exportar: ${exportException.message}", exportException))
                        }
                    }).build()
                continuation.invokeOnCancellation { transformer.cancel() }
                transformer.start(edited, output.absolutePath)
            }
            withContext(Dispatchers.IO) {
                if (!output.exists() || output.length() == 0L) throw IllegalStateException("O arquivo exportado está vazio.")
            }
            return output
        } catch (t: Throwable) {
            withContext(Dispatchers.IO) { if (output.exists()) output.delete() }
            throw t
        }
    }
}

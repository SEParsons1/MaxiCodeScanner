package com.vicarriers.maxicodescanner

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool

/**
 * App-owned success beep. DataWedge decode tone is silent, so this is the
 * only audible cue after a parsed MaxiCode.
 */
class DecodeSound(private val context: Context) {
    private val readyIds = mutableSetOf<Int>()
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(audioAttributes())
        .build()
        .apply {
            setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) readyIds.add(sampleId)
            }
        }
    private val sampleId: Int = pool.load(context, R.raw.ups_decode_success, 1)

    fun play() {
        if (sampleId != 0 && sampleId in readyIds) {
            val streamId = pool.play(sampleId, 1f, 1f, 1, 0, 1f)
            if (streamId != 0) return
        }
        MediaPlayer.create(context, R.raw.ups_decode_success, audioAttributes(), 1)?.apply {
            setOnCompletionListener { player -> player.release() }
            start()
        }
    }

    fun release() {
        pool.release()
    }

    private fun audioAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setLegacyStreamType(AudioManager.STREAM_MUSIC)
            .build()
}

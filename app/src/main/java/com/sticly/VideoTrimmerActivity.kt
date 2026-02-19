package com.sticly

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class VideoTrimmerActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "VideoTrimmer"
        private const val FRAME_COUNT = 10
        const val EXTRA_VIDEO_URI = "video_uri"
        const val EXTRA_OUTPUT_PATH = "output_path"
        const val EXTRA_MIN_DURATION = "min_duration"
        const val EXTRA_MAX_DURATION = "max_duration"
        const val DEFAULT_MIN_DURATION = 1
        const val DEFAULT_MAX_DURATION = 5
    }

    private lateinit var playerView: PlayerView
    private lateinit var frameRecyclerView: RecyclerView
    private lateinit var rangeSelectionView: RangeSelectionView
    private lateinit var tvDuration: TextView
    private lateinit var btnSave: MaterialButton
    private lateinit var btnBack: ImageButton

    private var exoPlayer: ExoPlayer? = null
    private var videoUri: Uri? = null
    private var videoDurationMs = 0L
    private var minDuration = DEFAULT_MIN_DURATION
    private var maxDuration = DEFAULT_MAX_DURATION
    private var isVideoLoaded = false

    private var startTimeMs = 0L
    private var endTimeMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_trimmer)

        initViews()
        loadVideoFromIntent()
    }

    private fun initViews() {
        playerView = findViewById(R.id.playerView)
        frameRecyclerView = findViewById(R.id.frameRecyclerView)
        rangeSelectionView = findViewById(R.id.rangeSelectionView)
        tvDuration = findViewById(R.id.tvDuration)
        btnSave = findViewById(R.id.btnSave)
        btnBack = findViewById(R.id.btnBack)

        btnBack.setOnClickListener { finish() }
        btnSave.setOnClickListener { trimAndSave() }

        // Setup RecyclerView for frames
        frameRecyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        frameRecyclerView.adapter = TrimFrameAdapter(emptyList())
        // Disable scroll on RecyclerView - frames are static
        frameRecyclerView.suppressLayout(true)

        // Setup range selection
        rangeSelectionView.onRangeChanged = { leftNorm, rightNorm ->
            startTimeMs = (leftNorm * videoDurationMs).toLong()
            endTimeMs = (rightNorm * videoDurationMs).toLong()
            updateDurationText()

            // Seek to the handle being dragged
            exoPlayer?.seekTo(startTimeMs)
        }
    }

    private fun loadVideoFromIntent() {
        val uriString = intent.getStringExtra(EXTRA_VIDEO_URI)
        minDuration = intent.getIntExtra(EXTRA_MIN_DURATION, DEFAULT_MIN_DURATION)
        maxDuration = intent.getIntExtra(EXTRA_MAX_DURATION, DEFAULT_MAX_DURATION)

        if (uriString == null) {
            Toast.makeText(this, getString(R.string.error_loading_video), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        videoUri = Uri.parse(uriString)
        setupPlayer()
    }

    private fun setupPlayer() {
        val uri = videoUri ?: return

        exoPlayer = ExoPlayer.Builder(this).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
            prepare()

            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY && !isVideoLoaded) {
                        isVideoLoaded = true
                        videoDurationMs = duration

                        // Set initial range based on max duration
                        val maxDurationMs = maxDuration * 1000L
                        endTimeMs = minOf(videoDurationMs, maxDurationMs)
                        startTimeMs = 0L

                        // Set initial range on the selection view
                        val rightNorm = endTimeMs.toFloat() / videoDurationMs
                        rangeSelectionView.setRange(0f, rightNorm)

                        updateDurationText()
                        extractVideoFrames()
                    }
                }
            })
        }

        playerView.player = exoPlayer
    }

    private fun extractVideoFrames() {
        val uri = videoUri ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            val frames = mutableListOf<Bitmap>()

            try {
                retriever.setDataSource(this@VideoTrimmerActivity, uri)
                val frameInterval = videoDurationMs / FRAME_COUNT

                for (i in 0 until FRAME_COUNT) {
                    val frameTimeUs = (i * frameInterval) * 1000 // microseconds
                    val bitmap = retriever.getFrameAtTime(
                        frameTimeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )
                    bitmap?.let {
                        val scaled = Bitmap.createScaledBitmap(it, 200, 150, false)
                        frames.add(scaled)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting frames: ${e.message}")
            } finally {
                retriever.release()
            }

            withContext(Dispatchers.Main) {
                frameRecyclerView.suppressLayout(false)
                frameRecyclerView.adapter = TrimFrameAdapter(frames)
                frameRecyclerView.suppressLayout(true)
            }
        }
    }

    private fun updateDurationText() {
        val durationSec = (endTimeMs - startTimeMs) / 1000f
        tvDuration.text = String.format("%.1fs", durationSec)

        // Validate duration
        val isValid = durationSec >= minDuration && durationSec <= maxDuration
        btnSave.isEnabled = isValid

        if (!isValid) {
            tvDuration.setTextColor(getColor(R.color.error))
        } else {
            tvDuration.setTextColor(0xFFFFFFFF.toInt())
        }
    }

    private fun trimAndSave() {
        val uri = videoUri ?: return

        Log.d(TAG, "trimAndSave called: uri=$uri, start=$startTimeMs, end=$endTimeMs, duration=$videoDurationMs")

        btnSave.isEnabled = false
        btnSave.text = getString(R.string.processing_sticker)

        lifecycleScope.launch {
            val outputPath = withContext(Dispatchers.IO) {
                trimVideo(uri, startTimeMs, endTimeMs)
            }

            Log.d(TAG, "trimAndSave result: outputPath=$outputPath")

            if (outputPath != null) {
                val resultIntent = Intent().apply {
                    putExtra(EXTRA_OUTPUT_PATH, outputPath)
                }
                Log.d(TAG, "Setting RESULT_OK with path: $outputPath")
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            } else {
                Log.e(TAG, "Trim failed! Showing error toast.")
                Toast.makeText(
                    this@VideoTrimmerActivity,
                    getString(R.string.trim_failed),
                    Toast.LENGTH_SHORT
                ).show()
                btnSave.isEnabled = true
                btnSave.text = getString(R.string.save)
            }
        }
    }

    private fun trimVideo(uri: Uri, startMs: Long, endMs: Long): String? {
        try {
            val inputPath = getInputPath(uri)
            if (inputPath == null) {
                Log.e(TAG, "Failed to get input path for uri: $uri")
                return null
            }
            Log.d(TAG, "Input path: $inputPath")

            val outputFile = File(cacheDir, "trimmed_${System.currentTimeMillis()}.mp4")

            val startSec = String.format("%.3f", startMs / 1000.0)
            val durationSec = String.format("%.3f", (endMs - startMs) / 1000.0)

            val command = "-y -ss $startSec -i \"$inputPath\" -t $durationSec -c copy \"${outputFile.absolutePath}\""
            Log.d(TAG, "FFmpeg command: $command")

            val session = FFmpegKit.execute(command)
            val returnCode = session.returnCode

            Log.d(TAG, "FFmpeg return code: $returnCode")
            if (!ReturnCode.isSuccess(returnCode)) {
                Log.e(TAG, "FFmpeg failed. Output: ${session.output}")
                Log.e(TAG, "FFmpeg logs: ${session.logsAsString}")
                return null
            }

            if (!outputFile.exists() || outputFile.length() == 0L) {
                Log.e(TAG, "Output file doesn't exist or is empty")
                return null
            }

            Log.d(TAG, "Trim success! Output: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
            return outputFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error trimming video: ${e.message}", e)
            return null
        }
    }

    private var tempInputFile: File? = null

    private fun getInputPath(uri: Uri): String? {
        return try {
            when (uri.scheme) {
                "file" -> uri.path
                "content" -> {
                    // Copy content URI to a temp file for reliable FFmpeg access
                    if (tempInputFile?.exists() == true) {
                        return tempInputFile!!.absolutePath
                    }
                    val tempFile = File(cacheDir, "temp_input.mp4")
                    Log.d(TAG, "Copying content URI to temp file...")
                    contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    tempInputFile = tempFile
                    Log.d(TAG, "Temp file created: ${tempFile.absolutePath} (${tempFile.length()} bytes)")
                    tempFile.absolutePath
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting input path: ${e.message}", e)
            null
        }
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
    }
}

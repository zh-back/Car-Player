package com.geek.carplayer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var carService: CarAudioService? = null
    private var isBound = false
    private var trackList: List<MusicTrack> = emptyList()

    private lateinit var tvTitle: TextView
    private lateinit var tvFormat: TextView
    private lateinit var btnPlayPause: Button
    private lateinit var btnPrev: Button
    private lateinit var btnNext: Button
    private lateinit var btnScan: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var lvTracks: ListView

    private lateinit var sbBass: SeekBar
    private lateinit var sbMid: SeekBar
    private lateinit var sbTreble: SeekBar
    private lateinit var sbWidth: SeekBar
    private lateinit var tvBass: TextView
    private lateinit var tvMid: TextView
    private lateinit var tvTreble: TextView
    private lateinit var tvWidth: TextView

    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            val progress = NativeAudioEngine.nativeGetProgressPercent()
            progressBar.progress = progress
            progressHandler.postDelayed(this, 500)
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as CarAudioService.LocalBinder
            carService = binder.getService()
            isBound = true

            carService?.onTrackChangedListener = { track ->
                runOnUiThread {
                    tvTitle.text = track.title
                    tvFormat.text = "HI-RES PCM (${track.format})"
                }
            }

            carService?.onPlayStateChangedListener = { isPlaying ->
                runOnUiThread {
                    btnPlayPause.text = if (isPlaying) "⏸" else "▶"
                }
            }

            refreshUsbTracks()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            carService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        bindAudioService()
        setupListeners()
        progressHandler.post(progressRunnable)
    }

    private fun initViews() {
        tvTitle = findViewById(R.id.tvTitle)
        tvFormat = findViewById(R.id.tvFormat)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)
        btnScan = findViewById(R.id.btnScan)
        progressBar = findViewById(R.id.progressBar)
        lvTracks = findViewById(R.id.lvTracks)

        sbBass = findViewById(R.id.sbBass)
        sbMid = findViewById(R.id.sbMid)
        sbTreble = findViewById(R.id.sbTreble)
        sbWidth = findViewById(R.id.sbWidth)

        tvBass = findViewById(R.id.tvBass)
        tvMid = findViewById(R.id.tvMid)
        tvTreble = findViewById(R.id.tvTreble)
        tvWidth = findViewById(R.id.tvWidth)
    }

    private fun bindAudioService() {
        val intent = Intent(this, CarAudioService::class.java)
        startService(intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    private fun setupListeners() {
        btnPlayPause.setOnClickListener { carService?.togglePlayPause() }
        btnPrev.setOnClickListener { carService?.playPrev() }
        btnNext.setOnClickListener { carService?.playNext() }
        btnScan.setOnClickListener { refreshUsbTracks() }

        lvTracks.setOnItemClickListener { _, _, position, _ ->
            carService?.playTrack(position)
        }

        // DSP 均衡器监听
        val eqListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // progress: 0 ~ 24 映射到 -12dB ~ +12dB
                val bass = sbBass.progress - 12f
                val mid = sbMid.progress - 12f
                val treble = sbTreble.progress - 12f

                tvBass.text = "重低音 (100Hz): ${bass.toInt()} dB"
                tvMid.text = "中音人声 (1kHz): ${mid.toInt()} dB"
                tvTreble.text = "明亮高音 (10kHz): ${treble.toInt()} dB"

                // 立即更新底层 C 语言双二阶滤波器
                NativeAudioEngine.nativeSetEQ(bass, mid, treble)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        sbBass.setOnSeekBarChangeListener(eqListener)
        sbMid.setOnSeekBarChangeListener(eqListener)
        sbTreble.setOnSeekBarChangeListener(eqListener)

        sbWidth.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val width = progress / 100f
                tvWidth.text = "车载声场拓宽: ${progress}%"
                NativeAudioEngine.nativeSetStereoWidth(width)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun refreshUsbTracks() {
        trackList = UsbScanner.scanTracks()
        val names = trackList.map { "${it.title} [${it.format}]" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
        lvTracks.adapter = adapter
        carService?.setPlaylist(trackList)
    }

    override fun onDestroy() {
        progressHandler.removeCallbacks(progressRunnable)
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
        super.onDestroy()
    }
}

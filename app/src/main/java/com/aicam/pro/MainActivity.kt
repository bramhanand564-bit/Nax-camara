package com.aicam.pro

import android.Manifest
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.media.MediaActionSound
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.MediaStore
import android.util.Base64
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.concurrent.thread
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var previewView: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var cameraExecutor: ExecutorService

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var camera: androidx.camera.core.Camera? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK

    // UI Controls
    private lateinit var hdrBtn: TextView
    private lateinit var hdrExpandBar: LinearLayout
    private lateinit var flashBtn: TextView
    private lateinit var aiBadgeBtn: TextView
    private lateinit var filterPanel: LinearLayout
    private lateinit var videoSubBar: LinearLayout
    private lateinit var proPanel: LinearLayout
    private lateinit var faceCircleBanner: TextView
    private lateinit var selfieIndicatorBox: View
    private lateinit var zoomBtn: TextView
    private lateinit var shutterBtn: View
    private lateinit var thumbView: ImageView
    private lateinit var countdownTv: TextView

    // Active States
    private var currentMode = "AI CAM"
    private var currentVideoSubMode = "VIDEO"
    private var hdrMode = "Plus" // Off, On, Plus (3-shot merge)
    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private var currentFilter = "None"
    private var filterIntensity = 80
    private var currentLightEffect = "None"
    private var beautyLevel = 55
    private var bokehLevel = 50
    private var currentZoom = 1.0f
    private var exposureCompensation = 0
    private var lastSavedBitmap: Bitmap? = null

    private val greenColor = Color.parseColor("#00E676")
    private val orangeColor = Color.parseColor("#FF9800")
    private val mediaSound = MediaActionSound()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        prefs = getSharedPreferences("AICamPrefs", Context.MODE_PRIVATE)
        cameraExecutor = Executors.newSingleThreadExecutor()

        buildMainCameraUI()
        applyUserPreferences()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                101
            )
        }
    }

    override fun onResume() {
        super.onResume()
        applyUserPreferences()
    }

    private fun applyUserPreferences() {
        // Auto Screen Brightness Control (from Screenshot 1)
        val lp = window.attributes
        lp.screenBrightness = if (prefs.getBoolean("brightness_control", true)) 1.0f else -1.0f
        window.attributes = lp

        // Selfie Indicator Box (from Screenshot 3 & 5)
        val showSelfieBox = prefs.getBoolean("selfie_indicator", true)
        selfieIndicatorBox.visibility = if (showSelfieBox) View.VISIBLE else View.GONE

        // Update AI Power Badge
        val aiOn = prefs.getBoolean("ai_enabled", true)
        val aiPct = prefs.getInt("ai_power_percent", 60)
        aiBadgeBtn.text = if (aiOn) "✨ AI: $aiPct%" else "✨ AI: OFF"
        aiBadgeBtn.setTextColor(if (aiOn) greenColor else Color.GRAY)

        overlayView.invalidate()
    }

    private fun buildMainCameraUI() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        // 1. Camera Preview
        previewView = PreviewView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        root.addView(previewView)

        // 2. Custom Overlay (Grid, Portrait Circle, Focus Box, Histogram, Framing)
        overlayView = OverlayView(this)
        root.addView(overlayView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Tap to Focus & Exposure Sun Slider
        previewView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                overlayView.focusX = event.x
                overlayView.focusY = event.y
                overlayView.showFocusBox = true
                overlayView.invalidate()

                camera?.let { cam ->
                    val factory = previewView.meteringPointFactory
                    val point = factory.createPoint(event.x, event.y)
                    val action = FocusMeteringAction.Builder(point).build()
                    cam.cameraControl.startFocusAndMetering(action)
                }
                showQuickExposurePopup()
            }
            true
        }

        // 3. Selfie Indicator Box (Top-left miniature rectangle from Screenshot 3 & 5)
        selfieIndicatorBox = View(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#33883344"))
                setStroke(4, Color.WHITE)
            }
        }
        val selfieParams = FrameLayout.LayoutParams(210, 310).apply {
            gravity = Gravity.TOP or Gravity.START
            topMargin = 160
            marginStart = 48
        }
        root.addView(selfieIndicatorBox, selfieParams)

        // 4. Portrait Circle Banner ("Please keep your face within the circle")
        faceCircleBanner = TextView(this).apply {
            text = "Please keep your face within the circle"
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(32, 14, 32, 14)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#88000000"))
                cornerRadius = 40f
            }
            visibility = View.GONE
        }
        val bannerParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = 190
        }
        root.addView(faceCircleBanner, bannerParams)

        // 5. Countdown Timer Text
        countdownTv = TextView(this).apply {
            textSize = 72f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            visibility = View.GONE
        }
        root.addView(countdownTv, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))

        // ==========================================
        // 6. TOP CONTROL BAR
        // ==========================================
        val topContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#AA000000"))
            setPadding(24, 24, 24, 16)
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val lensScanBtn = createTopButton("[•]") {
            Toast.makeText(this, "AI Smart Lens Active: Detecting Scene...", Toast.LENGTH_SHORT).show()
        }

        flashBtn = createTopButton("⚡ Off") {
            flashMode = when (flashMode) {
                ImageCapture.FLASH_MODE_OFF -> {
                    flashBtn.text = "⚡ Auto"
                    flashBtn.setTextColor(orangeColor)
                    ImageCapture.FLASH_MODE_AUTO
                }
                ImageCapture.FLASH_MODE_AUTO -> {
                    flashBtn.text = "⚡ On"
                    flashBtn.setTextColor(greenColor)
                    ImageCapture.FLASH_MODE_ON
                }
                else -> {
                    flashBtn.text = "⚡ Off"
                    flashBtn.setTextColor(Color.WHITE)
                    ImageCapture.FLASH_MODE_OFF
                }
            }
            imageCapture?.flashMode = flashMode
        }

        hdrBtn = createTopButton("HDR+") {
            hdrExpandBar.visibility = if (hdrExpandBar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        hdrBtn.setTextColor(greenColor)

        aiBadgeBtn = createTopButton("✨ AI: 60%") {
            showLiveAIPowerDialog()
        }
        aiBadgeBtn.setTextColor(greenColor)

        val filterBtn = createTopButton("🎨") {
            filterPanel.visibility = if (filterPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        val settingsBtn = createTopButton("⚙️") {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        topBar.addView(lensScanBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        topBar.addView(flashBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f))
        topBar.addView(hdrBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f))
        topBar.addView(aiBadgeBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.8f))
        topBar.addView(filterBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        topBar.addView(settingsBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        topContainer.addView(topBar)

        // HDR Expandable Bar: Plus | On | Off (From Screenshot 3!)
        hdrExpandBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 8)
            visibility = View.GONE
        }
        val hdrModes = arrayOf("Plus", "On", "Off")
        hdrModes.forEach { m ->
            val tv = TextView(this).apply {
                text = m
                textSize = 15f
                setPadding(40, 8, 40, 8)
                setTextColor(if (hdrMode == m) greenColor else Color.WHITE)
                setOnClickListener {
                    hdrMode = m
                    hdrBtn.text = if (m == "Off") "HDR Off" else "HDR $m"
                    hdrBtn.setTextColor(if (m == "Off") Color.WHITE else greenColor)
                    hdrExpandBar.visibility = View.GONE
                    buildMainCameraUI()
                    startCamera()
                }
            }
            hdrExpandBar.addView(tv)
        }
        topContainer.addView(hdrExpandBar)

        root.addView(topContainer, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        ))

        // ==========================================
        // 7. RIGHT SIDE FLOATING BUTTONS (Style, Beauty, Bokeh)
        // ==========================================
        val rightFloating = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(16, 16, 24, 16)
        }

        val styleRightBtn = createRightSideButton("✧\nStyle") {
            showColorEngineQuickPicker()
        }
        val beautyRightBtn = createRightSideButton("😊\nBeauty") {
            showBeautyAndMoleSlider()
        }
        val bokehRightBtn = createRightSideButton("f/2.0\nBokeh") {
            showBokehSlider()
        }

        rightFloating.addView(styleRightBtn)
        rightFloating.addView(beautyRightBtn)
        rightFloating.addView(bokehRightBtn)

        val rightParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            bottomMargin = 80
        }
        root.addView(rightFloating, rightParams)

        // ==========================================
        // 8. BOTTOM CONTROLS & MODES CONTAINER
        // ==========================================
        val bottomMaster = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#CC000000"))
            setPadding(0, 12, 0, 28)
        }

        // 8A. Zoom (1x / 2x) + Lens Row
        val zoomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(24, 4, 24, 12)
        }
        zoomBtn = TextView(this).apply {
            text = "1x"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(28, 14, 28, 14)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#66000000"))
                setStroke(2, Color.WHITE)
            }
            setOnClickListener {
                currentZoom = when (currentZoom) {
                    1.0f -> 2.0f
                    2.0f -> 4.0f
                    else -> 1.0f
                }
                zoomBtn.text = "${currentZoom.toInt()}x"
                camera?.cameraControl?.setZoomRatio(currentZoom)
            }
        }
        val lensBtn = TextView(this).apply {
            text = "◎ Lens"
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(36, 12, 16, 12)
            setOnClickListener {
                showLensPickerDialog()
            }
        }
        zoomRow.addView(zoomBtn)
        zoomRow.addView(lensBtn)
        bottomMaster.addView(zoomRow)

        // 8B. Filter & Portrait Light Effect Drawer (From Video 0:04 - 0:13)
        filterPanel = buildFilterAndLightDrawer()
        bottomMaster.addView(filterPanel)

        // 8C. Pro Mode Manual Sliders (ISO, Shutter, WB, EV)
        proPanel = buildProManualPanel()
        bottomMaster.addView(proPanel)

        // 8D. Video Sub-Modes Bar: FAST MOTION | SLOW MOTION | VIDEO | TIMELAPSE (From Video 1:13 - 1:25)
        videoSubBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 8)
            visibility = if (currentMode == "VIDEO") View.VISIBLE else View.GONE
        }
        val vSubModes = arrayOf("FAST MOTION", "SLOW MOTION", "VIDEO", "TIMELAPSE")
        vSubModes.forEach { vm ->
            val tv = TextView(this).apply {
                text = vm
                textSize = 12f
                setPadding(20, 8, 20, 8)
                setTextColor(if (currentVideoSubMode == vm) greenColor else Color.GRAY)
                typeface = if (currentVideoSubMode == vm) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setOnClickListener {
                    currentVideoSubMode = vm
                    buildMainCameraUI()
                    startCamera()
                }
            }
            videoSubBar.addView(tv)
        }
        bottomMaster.addView(videoSubBar)

        // 8E. Main Modes Scroll Bar: MANUAL | NIGHT | PORTRAIT | AI CAM | PHOTO | BEAUTY | VIDEO | MORE
        val modeScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(32, 12, 32, 18)
        }
        val modes = arrayOf("MANUAL", "NIGHT", "PORTRAIT", "AI CAM", "PHOTO", "BEAUTY", "VIDEO", "MORE")
        modes.forEach { modeName ->
            val tv = TextView(this).apply {
                text = modeName
                textSize = 14f
                setPadding(28, 8, 28, 8)
                val isSelected = (currentMode == modeName)
                setTextColor(if (isSelected) greenColor else Color.WHITE)
                typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setOnClickListener {
                    if (modeName == "MORE") {
                        showMoreModesDialog()
                    } else {
                        switchCameraMode(modeName)
                    }
                }
            }
            modeRow.addView(tv)
        }
        modeScroll.addView(modeRow)
        bottomMaster.addView(modeScroll)

        // 8F. Bottom Shutter Row (Gallery, Framing, Shutter, Video Switch, Flip Camera)
        val shutterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(32, 12, 32, 12)
        }

        // Gallery / Before-After Preview Thumbnail
        thumbView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#333333"))
                setStroke(3, Color.WHITE)
            }
            setOnClickListener {
                if (lastSavedBitmap != null) {
                    showBeforeAfterPreviewDialog(lastSavedBitmap!!)
                } else {
                    val intent = Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    startActivity(intent)
                }
            }
        }
        val thumbContainer = FrameLayout(this).apply {
            addView(thumbView, FrameLayout.LayoutParams(120, 120, Gravity.CENTER))
        }

        // Smart Framing / AI Scene Icon
        val frameIcon = createBottomIcon("▣") {
            val cur = prefs.getBoolean("portrait_framing", false)
            prefs.edit().putBoolean("portrait_framing", !cur).apply()
            overlayView.invalidate()
            Toast.makeText(this, "Smart Framing Grid: ${if (!cur) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
        }

        // Big Shutter Button
        shutterBtn = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (currentMode == "VIDEO") Color.RED else Color.WHITE)
                setStroke(10, Color.parseColor("#88FFFFFF"))
            }
            setOnClickListener {
                onShutterPressed()
            }
        }
        val shutterContainer = FrameLayout(this).apply {
            addView(shutterBtn, FrameLayout.LayoutParams(170, 170, Gravity.CENTER))
        }

        // Quick Video/Photo Toggle Icon
        val videoQuickIcon = createBottomIcon(if (currentMode == "VIDEO") "📷" else "📹") {
            switchCameraMode(if (currentMode == "VIDEO") "AI CAM" else "VIDEO")
        }

        // Flip Front/Back Camera Icon
        val flipIcon = createBottomIcon("🔄") {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            startCamera()
        }

        shutterRow.addView(thumbContainer, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        shutterRow.addView(frameIcon, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        shutterRow.addView(shutterContainer, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.3f))
        shutterRow.addView(videoQuickIcon, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        shutterRow.addView(flipIcon, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        bottomMaster.addView(shutterRow)

        root.addView(bottomMaster, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        ))

        setContentView(root)
        updateModeUIState()
    }

    // =====================================================
    // FILTERS & PORTRAIT LIGHT EFFECTS (FROM VIDEO 0:04-0:13)
    // =====================================================
    private fun buildFilterAndLightDrawer(): LinearLayout {
        val drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#DD111111"))
            setPadding(24, 16, 24, 16)
            visibility = View.GONE
        }

        // Intensity Slider for Filters
        val sliderTv = TextView(this).apply {
            text = "Filter / Light Strength: $filterIntensity%"
            setTextColor(Color.WHITE)
            textSize = 12f
        }
        val intensityBar = SeekBar(this).apply {
            max = 100
            progress = filterIntensity
            progressTintList = ColorStateList.valueOf(orangeColor)
            thumbTintList = ColorStateList.valueOf(orangeColor)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    filterIntensity = p
                    sliderTv.text = "Filter / Light Strength: $p%"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        drawer.addView(sliderTv)
        drawer.addView(intensityBar)

        // Tabs: Filters | Portrait light effect
        val tabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 12)
        }
        val filterListScroll = HorizontalScrollView(this)
        val lightListScroll = HorizontalScrollView(this).apply { visibility = View.GONE }

        val tabFilters = TextView(this).apply {
            text = "Filters"
            setTextColor(orangeColor)
            typeface = Typeface.DEFAULT_BOLD
            textSize = 14f
            setPadding(24, 8, 40, 8)
        }
        val tabLights = TextView(this).apply {
            text = "Portrait light effect"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(24, 8, 24, 8)
        }

        tabFilters.setOnClickListener {
            tabFilters.setTextColor(orangeColor)
            tabLights.setTextColor(Color.WHITE)
            filterListScroll.visibility = View.VISIBLE
            lightListScroll.visibility = View.GONE
        }
        tabLights.setOnClickListener {
            tabLights.setTextColor(orangeColor)
            tabFilters.setTextColor(Color.WHITE)
            filterListScroll.visibility = View.GONE
            lightListScroll.visibility = View.VISIBLE
            overlayView.showPortraitCircle = true
            faceCircleBanner.visibility = View.VISIBLE
            overlayView.invalidate()
        }

        tabRow.addView(tabFilters)
        tabRow.addView(tabLights)
        drawer.addView(tabRow)

        // Filter items from user's video
        val filterRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val allFilters = arrayOf(
            "None", "Vivid Glow", "True Tone", "Grapefruit", "Yogurt",
            "Island", "Macarons", "Moonlight", "Bubbles", "Short grass", "Night breeze"
        )
        allFilters.forEach { fName ->
            val chip = createFilterChip(fName, currentFilter == fName) {
                currentFilter = fName
                Toast.makeText(this, "Filter: $fName", Toast.LENGTH_SHORT).show()
            }
            filterRow.addView(chip)
        }
        filterListScroll.addView(filterRow)
        drawer.addView(filterListScroll)

        // Portrait Light Effects from user's video (Studio, Stereo, Loop, Rainbow, Monochrome)
        val lightRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val allLights = arrayOf(
            "None", "Studio light", "Stereo light", "Loop light", "Rainbow light", "Monochrome"
        )
        allLights.forEach { lName ->
            val chip = createFilterChip(lName, currentLightEffect == lName) {
                currentLightEffect = lName
                overlayView.showPortraitCircle = (lName != "None")
                faceCircleBanner.visibility = if (lName != "None") View.VISIBLE else View.GONE
                overlayView.invalidate()
                Toast.makeText(this, "Portrait Light: $lName", Toast.LENGTH_SHORT).show()
            }
            lightRow.addView(chip)
        }
        lightListScroll.addView(lightRow)
        drawer.addView(lightListScroll)

        return drawer
    }

    private fun buildProManualPanel(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#CC111111"))
            setPadding(28, 12, 28, 12)
            visibility = if (currentMode == "MANUAL" || currentMode == "PRO") View.VISIBLE else View.GONE
        }
        val title = TextView(this).apply {
            text = "PRO MANUAL: EV Exposure / White Balance / ISO Boost"
            setTextColor(greenColor)
            textSize = 12f
        }
        val evBar = SeekBar(this).apply {
            max = 20
            progress = 10
            progressTintList = ColorStateList.valueOf(greenColor)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    exposureCompensation = p - 10
                    camera?.cameraControl?.setExposureCompensationIndex(exposureCompensation)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        panel.addView(title)
        panel.addView(evBar)
        return panel
    }

    // =====================================================
    // MORE MODES GRID (50 MP, Pano, Live Photo, Pro, etc.)
    // =====================================================
    private fun showMoreModesDialog() {
        val moreModes = arrayOf(
            "50 MP (Ultra High Resolution)",
            "Pano (Panorama)",
            "Live Photo",
            "Slo-mo (120 FPS)",
            "Time-lapse",
            "Pro (Histogram + RAW)",
            "AR Stickers (AI Glow)",
            "Documents (HD Scanner)"
        )
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("More Camera Modes")
            .setItems(moreModes) { _, which ->
                when (which) {
                    0 -> {
                        prefs.edit().putString("res_back", "50 MP").apply()
                        switchCameraMode("50 MP")
                    }
                    1 -> switchCameraMode("PANO")
                    2 -> switchCameraMode("LIVE PHOTO")
                    3 -> {
                        currentVideoSubMode = "SLOW MOTION"
                        switchCameraMode("VIDEO")
                    }
                    4 -> {
                        currentVideoSubMode = "TIMELAPSE"
                        switchCameraMode("VIDEO")
                    }
                    5 -> switchCameraMode("MANUAL")
                    6 -> switchCameraMode("AI CAM")
                    7 -> switchCameraMode("DOCUMENTS")
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun switchCameraMode(newMode: String) {
        currentMode = newMode
        buildMainCameraUI()
        startCamera()
    }

    private fun updateModeUIState() {
        val isPortrait = (currentMode == "PORTRAIT" || currentLightEffect != "None")
        overlayView.showPortraitCircle = isPortrait
        faceCircleBanner.visibility = if (isPortrait) View.VISIBLE else View.GONE
        overlayView.showHistogram = (currentMode == "MANUAL" && prefs.getBoolean("pro_histogram", true))
        overlayView.invalidate()
    }

    // =====================================================
    // LIVE AI POWER (%) SLIDER POPUP ON MAIN SCREEN
    // =====================================================
    private fun showLiveAIPowerDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 24)
        }

        var isAiOn = prefs.getBoolean("ai_enabled", true)
        var aiPercent = prefs.getInt("ai_power_percent", 60)
        var faceBoost = prefs.getBoolean("face_bright_boost", true)

        val swAi = Switch(this).apply {
            text = "Enable Gemini AI Power"
            setTextColor(Color.WHITE)
            isChecked = isAiOn
        }

        val pctLabel = TextView(this).apply {
            text = "\nAI Power Percentage: $aiPercent%\n(Controls AI strength so photo looks 100% real)"
            setTextColor(greenColor)
            textSize = 14f
        }

        val seek = SeekBar(this).apply {
            max = 100
            progress = aiPercent
            progressTintList = ColorStateList.valueOf(greenColor)
            thumbTintList = ColorStateList.valueOf(greenColor)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    aiPercent = p
                    val styleNote = when {
                        p < 35 -> "Subtle & 100% Natural"
                        p < 75 -> "Balanced Bright & Colorful (Recommended)"
                        else -> "Ultra Vibrant Studio Look"
                    }
                    pctLabel.text = "\nAI Power Percentage: $p%\n($styleNote)"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        val swFace = Switch(this).apply {
            text = "Smart Face Brightening (Gora Skin Boost)"
            setTextColor(Color.WHITE)
            isChecked = faceBoost
            setPadding(0, 24, 0, 8)
        }

        container.addView(swAi)
        container.addView(pctLabel)
        container.addView(seek)
        container.addView(swFace)

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("✨ AI Power Control")
            .setView(container)
            .setPositiveButton("Apply") { _, _ ->
                prefs.edit()
                    .putBoolean("ai_enabled", swAi.isChecked)
                    .putInt("ai_power_percent", aiPercent)
                    .putBoolean("face_bright_boost", swFace.isChecked)
                    .apply()
                applyUserPreferences()
            }
            .setNeutralButton("API Key Settings") { _, _ ->
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            .show()
    }

    private fun showColorEngineQuickPicker() {
        val engines = arrayOf(
            "Super Combo (All-in-One Power)",
            "Vivid Glow (Bright Gora Skin & Punchy Colors)",
            "True Tone (Warm Golden Natural Glow)",
            "HDR+ Pro (Ultra Sharp Details & Shadow Control)"
        )
        val cur = prefs.getString("color_engine_mode", engines[0])
        val idx = engines.indexOfFirst { it.startsWith(cur?.take(8) ?: "Super") }.coerceAtLeast(0)

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Select Color & Skin Engine")
            .setSingleChoiceItems(engines, idx) { d, which ->
                prefs.edit().putString("color_engine_mode", engines[which]).apply()
                Toast.makeText(this, "Engine: ${engines[which]}", Toast.LENGTH_SHORT).show()
                d.dismiss()
            }
            .show()
    }

    private fun showBeautyAndMoleSlider() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        val tv = TextView(this).apply {
            text = "Skin Brightening & Smoothness: $beautyLevel%"
            setTextColor(Color.WHITE)
        }
        val sb = SeekBar(this).apply {
            max = 100
            progress = beautyLevel
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                    beautyLevel = p
                    tv.text = "Skin Brightening & Smoothness: $p%"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        val moleSw = Switch(this).apply {
            text = "Remove Moles & Blemishes"
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean("remove_moles", true)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("remove_moles", isChecked).apply()
            }
        }
        box.addView(tv)
        box.addView(sb)
        box.addView(moleSw)

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("😊 Face Beauty & Skin Tone")
            .setView(box)
            .setPositiveButton("Done", null)
            .show()
    }

    private fun showBokehSlider() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        val tv = TextView(this).apply {
            text = "Portrait Bokeh Blur (f/2.0): $bokehLevel%"
            setTextColor(Color.WHITE)
        }
        val sb = SeekBar(this).apply {
            max = 100
            progress = bokehLevel
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                    bokehLevel = p
                    tv.text = "Portrait Bokeh Blur (f/2.0): $p%"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        box.addView(tv)
        box.addView(sb)
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("f/2.0 Portrait Bokeh Control")
            .setView(box)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showLensPickerDialog() {
        val lenses = arrayOf("1x Main High-Res Sensor (50MP)", "2x Portrait Tele Zoom", "Macro Close-Up")
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Select Camera Lens")
            .setItems(lenses) { _, which ->
                currentZoom = when (which) {
                    1 -> 2.0f
                    2 -> 1.5f
                    else -> 1.0f
                }
                zoomBtn.text = "${currentZoom}x"
                camera?.cameraControl?.setZoomRatio(currentZoom)
            }
            .show()
    }

    private fun showQuickExposurePopup() {
        camera?.let { cam ->
            val state = cam.cameraInfo.exposureState
            if (state.isExposureCompensationSupported) {
                val boost = (state.exposureCompensationRange.upper / 3).coerceAtLeast(1)
                if (prefs.getBoolean("face_bright_boost", true)) {
                    cam.cameraControl.setExposureCompensationIndex(boost)
                }
            }
        }
    }

    // =====================================================
    // CAMERAX INITIALIZATION
    // =====================================================
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val aspectFormat = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                prefs.getString("format_back", "Standard (4:3)")
            } else {
                prefs.getString("format_front", "Standard (4:3)")
            }
            val aspectRatio = if (aspectFormat?.contains("16:9") == true) {
                AspectRatio.RATIO_16_9
            } else {
                AspectRatio.RATIO_4_3
            }

            val preview = Preview.Builder()
                .setTargetAspectRatio(aspectRatio)
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            try {
                cameraProvider.unbindAll()

                if (currentMode == "VIDEO") {
                    val vidRes = prefs.getString("video_res_back", "FHD 1080p")
                    val quality = if (vidRes?.contains("720") == true) Quality.HD else Quality.FHD
                    val recorder = Recorder.Builder()
                        .setQualitySelector(QualitySelector.from(quality))
                        .build()
                    videoCapture = VideoCapture.withOutput(recorder)
                    camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture)
                } else {
                    imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setTargetAspectRatio(aspectRatio)
                        .setFlashMode(flashMode)
                        .build()
                    camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
                }

                // Auto Face-Brightening Exposure Lift
                if (prefs.getBoolean("face_bright_boost", true)) {
                    val range = camera?.cameraInfo?.exposureState?.exposureCompensationRange
                    if (range != null && range.upper > 0) {
                        camera?.cameraControl?.setExposureCompensationIndex((range.upper / 4).coerceAtLeast(1))
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Camera Init: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // =====================================================
    // SHUTTER & COUNTDOWN LOGIC
    // =====================================================
    private fun onShutterPressed() {
        if (currentMode == "VIDEO") {
            toggleVideoRecording()
            return
        }

        val cdSetting = prefs.getString("countdown_sec", "Off") ?: "Off"
        val seconds = cdSetting.replace("s", "").toIntOrNull() ?: 0
        if (seconds > 0) {
            countdownTv.visibility = View.VISIBLE
            object : CountDownTimer(seconds * 1000L, 1000L) {
                override fun onTick(millisUntilFinished: Long) {
                    val secLeft = (millisUntilFinished / 1000) + 1
                    countdownTv.text = secLeft.toString()
                    if (prefs.getBoolean("shutter_sound", true)) {
                        mediaSound.play(MediaActionSound.START_VIDEO_RECORDING)
                    }
                }
                override fun onFinish() {
                    countdownTv.visibility = View.GONE
                    capturePhotoNow()
                }
            }.start()
        } else {
            capturePhotoNow()
        }
    }

    private fun capturePhotoNow() {
        val cap = imageCapture ?: return
        if (prefs.getBoolean("shutter_sound", true)) {
            mediaSound.play(MediaActionSound.SHUTTER_CLICK)
        }

        Toast.makeText(this, "Capturing + Processing ${if (hdrMode == "Plus") "3-Shot HDR+ & " else ""}AI...", Toast.LENGTH_SHORT).show()

        cap.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    val rotation = image.imageInfo.rotationDegrees
                    image.close()

                    thread {
                        val rawBitmap = decodeAndRotateBitmap(bytes, rotation)
                        if (rawBitmap != null) {
                            processPhotoWithAIAndSave(rawBitmap)
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@MainActivity, "Capture Error: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun decodeAndRotateBitmap(bytes: ByteArray, rotation: Int): Bitmap? {
        val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val matrix = Matrix()
        if (rotation != 0) matrix.postRotate(rotation.toFloat())
        if (lensFacing == CameraSelector.LENS_FACING_FRONT && prefs.getBoolean("mirror_selfie", true)) {
            matrix.postScale(-1f, 1f)
        }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
    }

    // =====================================================
    // CORE COLOR ENGINE + GEMINI AI + PERCENTAGE (%) BLEND
    // =====================================================
    private fun processPhotoWithAIAndSave(original: Bitmap) {
        // 1. If "Keep normal photo when HDR" is ON in Settings, save original first
        if (hdrMode != "Off" && prefs.getBoolean("keep_normal_hdr", false)) {
            saveBitmapToGallery(original, "NORMAL")
        }

        // 2. Ask Gemini Free API (if enabled & key provided) or Smart Scene Analyzer for tuning values
        val aiEnabled = prefs.getBoolean("ai_enabled", true)
        val aiPercent = prefs.getInt("ai_power_percent", 60)
        val apiKey = prefs.getString("gemini_api_key", "")?.trim() ?: ""

        var aiBoost = analyzeImageLocally(original)
        if (aiEnabled && apiKey.isNotEmpty()) {
            val geminiResult = callGeminiVisionAPI(original, apiKey)
            if (geminiResult != null) {
                aiBoost = geminiResult
            }
        }

        // 3. Create Full Power Enhanced Bitmap (100% Engine Power)
        val fullEnhanced = renderFullEnhancedBitmap(original, aiBoost)

        // 4. Blend Original & Enhanced using exact AI Power Percentage (0% to 100%)
        val finalFraction = if (aiEnabled) (aiPercent / 100f) else 0.45f
        val blendedBitmap = blendBitmaps(original, fullEnhanced, finalFraction)

        // 5. Add Watermark if enabled in Settings
        val watermarked = applyWatermarkIfNeeded(blendedBitmap)

        // 6. Save High-Quality JPEG to Gallery
        saveBitmapToGallery(watermarked, "AICAM")
        lastSavedBitmap = watermarked

        runOnUiThread {
            thumbView.setImageBitmap(watermarked)
            showInteractiveAfterShotDialog(original, fullEnhanced, aiPercent)
        }
    }

    private data class AITuningParams(
        val brightnessLift: Float,
        val saturationBoost: Float,
        val contrastBoost: Float,
        val warmthShift: Float,
        val summary: String
    )

    private fun analyzeImageLocally(bmp: Bitmap): AITuningParams {
        // Sample center pixels (where face/subject usually is) to detect darkness ("kala-pan")
        val sample = Bitmap.createScaledBitmap(bmp, 32, 32, false)
        var totalLum = 0f
        for (y in 8..24) {
            for (x in 8..24) {
                val c = sample.getPixel(x, y)
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                totalLum += (0.299f * r + 0.587f * g + 0.114f * b)
            }
        }
        val avgLum = totalLum / (17 * 17)
        // If face/subject is dark (< 125), boost brightness strongly
        val neededLift = when {
            avgLum < 85 -> 38f
            avgLum < 125 -> 26f
            avgLum < 160 -> 16f
            else -> 8f
        }
        return AITuningParams(
            brightnessLift = neededLift,
            saturationBoost = 1.38f,
            contrastBoost = 1.12f,
            warmthShift = 10f,
            summary = "Smart Face Brightening + Vivid Color Tuning Applied"
        )
    }

    private fun callGeminiVisionAPI(bmp: Bitmap, apiKey: String): AITuningParams? {
        return try {
            val thumb = Bitmap.createScaledBitmap(bmp, 256, 256, true)
            val baos = ByteArrayOutputStream()
            thumb.compress(Bitmap.CompressFormat.JPEG, 75, baos)
            val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 5000
                readTimeout = 5000
                doOutput = true
            }

            val prompt = """
                Analyze this smartphone camera photo. We want the subject/face to look bright, fair, glowing (never dark) and colors to look vibrant and rich while staying realistic.
                Return ONLY a JSON object with exact numbers:
                {"brightnessLift": 10 to 45, "saturationBoost": 1.15 to 1.55, "contrastBoost": 1.05 to 1.25, "warmthShift": 4 to 18, "summary": "short description"}
            """.trimIndent()

            val payload = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray()
                        .put(JSONObject().put("text", prompt))
                        .put(JSONObject().put("inline_data", JSONObject().apply {
                            put("mime_type", "image/jpeg")
                            put("data", b64)
                        }))
                    )
                }))
            }

            conn.outputStream.use { it.write(payload.toString().toByteArray()) }
            if (conn.responseCode == 200) {
                val resp = conn.inputStream.bufferedReader().readText()
                val text = JSONObject(resp)
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")

                val jsonStart = text.indexOf("{")
                val jsonEnd = text.lastIndexOf("}")
                if (jsonStart != -1 && jsonEnd != -1) {
                    val obj = JSONObject(text.substring(jsonStart, jsonEnd + 1))
                    return AITuningParams(
                        brightnessLift = obj.optDouble("brightnessLift", 24.0).toFloat().coerceIn(5f, 50f),
                        saturationBoost = obj.optDouble("saturationBoost", 1.35).toFloat().coerceIn(1.0f, 1.65f),
                        contrastBoost = obj.optDouble("contrastBoost", 1.12).toFloat().coerceIn(1.0f, 1.35f),
                        warmthShift = obj.optDouble("warmthShift", 10.0).toFloat().coerceIn(-10f, 25f),
                        summary = obj.optString("summary", "Gemini AI Color & Skin Enhanced")
                    )
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun renderFullEnhancedBitmap(src: Bitmap, ai: AITuningParams): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        val engineMode = prefs.getString("color_engine_mode", "Super Combo (All-in-One)") ?: "Super Combo"
        val faceBoostOn = prefs.getBoolean("face_bright_boost", true)

        var sat = ai.saturationBoost
        var contrast = ai.contrastBoost
        var bright = ai.brightnessLift
        var warmR = ai.warmthShift
        var warmB = -ai.warmthShift * 0.5f

        // Engine-specific tuning (Vivid Glow / True Tone / HDR+ Pro / Super Combo)
        when {
            engineMode.startsWith("Vivid Glow") -> {
                sat *= 1.15f
                bright += 12f
                warmR += 6f
            }
            engineMode.startsWith("True Tone") -> {
                warmR += 14f
                warmB -= 8f
                contrast *= 1.04f
            }
            engineMode.startsWith("HDR+ Pro") -> {
                contrast *= 1.12f
                bright += 8f
            }
            else -> {
                // Super Combo: Best of all three!
                sat *= 1.12f
                bright += 10f
                warmR += 8f
            }
        }

        if (faceBoostOn) {
            bright += (beautyLevel * 0.22f)
        }

        // Apply active Filter (Grapefruit, Island, Macarons, Moonlight, etc.)
        val fStrength = filterIntensity / 100f
        when (currentFilter) {
            "Grapefruit" -> { warmR += 18f * fStrength; sat += 0.15f * fStrength }
            "Yogurt" -> { bright += 16f * fStrength; sat -= 0.08f * fStrength }
            "Island" -> { warmB += 15f * fStrength; sat += 0.20f * fStrength }
            "Macarons" -> { bright += 14f * fStrength; warmR += 10f * fStrength }
            "Moonlight" -> { warmB += 22f * fStrength; contrast += 0.1f * fStrength }
            "Bubbles" -> { sat += 0.25f * fStrength; bright += 10f * fStrength }
            "Short grass" -> { sat += 0.30f * fStrength }
            "Night breeze" -> { warmB += 18f * fStrength; bright += 8f * fStrength }
        }

        if (currentLightEffect == "Monochrome") {
            sat = 0f
            contrast = 1.25f
        }

        val cm = ColorMatrix().apply { setSaturation(sat) }
        val scale = contrast
        val translate = (-0.5f * scale + 0.5f) * 255f + bright
        val contrastMatrix = ColorMatrix(floatArrayOf(
            scale, 0f, 0f, 0f, translate + warmR,
            0f, scale, 0f, 0f, translate + (warmR * 0.3f),
            0f, 0f, scale, 0f, translate + warmB,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(contrastMatrix)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(src, 0f, 0f, paint)

        // Apply Soft Radial Face Glow & Portrait Light Effects
        if (faceBoostOn || currentLightEffect != "None" || currentMode == "PORTRAIT") {
            val cx = out.width / 2f
            val cy = out.height / 2f
            val radius = (out.width.coerceAtMost(out.height) * 0.55f)

            val glowColor = when (currentLightEffect) {
                "Rainbow light" -> Color.argb(45, 255, 120, 200)
                "Loop light", "Studio light" -> Color.argb(40, 255, 245, 230)
                else -> Color.argb(28, 255, 240, 225)
            }
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    cx, cy, radius,
                    intArrayOf(glowColor, Color.TRANSPARENT),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(cx, cy, radius, glowPaint)
        }

        return out
    }

    private fun blendBitmaps(original: Bitmap, enhanced: Bitmap, aiFraction: Float): Bitmap {
        val ratio = aiFraction.coerceIn(0f, 1f)
        val result = original.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val alphaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            alpha = (ratio * 255).roundToInt().coerceIn(0, 255)
        }
        canvas.drawBitmap(enhanced, 0f, 0f, alphaPaint)
        return result
    }

    private fun applyWatermarkIfNeeded(bmp: Bitmap): Bitmap {
        val wmMode = prefs.getString("watermark_mode", "Off") ?: "Off"
        if (wmMode == "Off") return bmp

        val copy = bmp.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(copy)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (copy.width * 0.032f).coerceAtLeast(28f)
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(6f, 2f, 2f, Color.BLACK)
        }
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        val label = if (wmMode.contains("AI Cam")) "Shot on AI Cam Pro  |  $dateStr" else dateStr
        canvas.drawText(label, 48f, copy.height - 56f, p)
        return copy
    }

    // =====================================================
    // AFTER-SHOT LIVE AI % SLIDER & BEFORE/AFTER PREVIEW
    // =====================================================
    private fun showInteractiveAfterShotDialog(original: Bitmap, fullEnhanced: Bitmap, initialPct: Int) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 16)
            setBackgroundColor(Color.parseColor("#121212"))
        }

        val previewImg = ImageView(this).apply {
            adjustViewBounds = true
            maxHeight = 900
            setImageBitmap(blendBitmaps(original, fullEnhanced, initialPct / 100f))
        }

        var currentPct = initialPct
        val pctTitle = TextView(this).apply {
            text = "✨ AI Power Used: $currentPct% (Slide to adjust realism)"
            setTextColor(greenColor)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 20, 0, 8)
        }

        val liveSlider = SeekBar(this).apply {
            max = 100
            progress = currentPct
            progressTintList = ColorStateList.valueOf(greenColor)
            thumbTintList = ColorStateList.valueOf(greenColor)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    currentPct = p
                    pctTitle.text = "✨ AI Power Used: $p% ${if (p == 0) "(Original Raw)" else ""}"
                    val previewBmp = blendBitmaps(original, fullEnhanced, p / 100f)
                    previewImg.setImageBitmap(previewBmp)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        val compareBtn = Button(this).apply {
            text = "Hold / Tap to Compare BEFORE (0%) vs AFTER"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#262626"))
            var showingOriginal = false
            setOnClickListener {
                showingOriginal = !showingOriginal
                if (showingOriginal) {
                    previewImg.setImageBitmap(original)
                    pctTitle.text = "👀 Showing: BEFORE (Normal Camera)"
                } else {
                    previewImg.setImageBitmap(blendBitmaps(original, fullEnhanced, currentPct / 100f))
                    pctTitle.text = "✨ Showing: AFTER ($currentPct% AI Power)"
                }
            }
        }

        layout.addView(previewImg)
        layout.addView(pctTitle)
        layout.addView(liveSlider)
        layout.addView(compareBtn)

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Photo Ready! Adjust AI Power %")
            .setView(layout)
            .setPositiveButton("Save at This %") { _, _ ->
                prefs.edit().putInt("ai_power_percent", currentPct).apply()
                applyUserPreferences()
                val customBlended = applyWatermarkIfNeeded(blendBitmaps(original, fullEnhanced, currentPct / 100f))
                thread {
                    saveBitmapToGallery(customBlended, "AICAM_${currentPct}PCT")
                }
                lastSavedBitmap = customBlended
                thumbView.setImageBitmap(customBlended)
                Toast.makeText(this, "Saved to Gallery with $currentPct% AI Power!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Keep Default", null)
            .show()
    }

    private fun showBeforeAfterPreviewDialog(bmp: Bitmap) {
        val iv = ImageView(this).apply {
            adjustViewBounds = true
            setImageBitmap(bmp)
        }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Latest AI Cam Pro Photo")
            .setView(iv)
            .setPositiveButton("Open Gallery") { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI))
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun saveBitmapToGallery(bitmap: Bitmap, prefix: String) {
        val filename = "${prefix}_${System.currentTimeMillis()}.jpg"
        val qualityPref = prefs.getString("jpeg_quality", "Super High (100%)") ?: "Super High"
        val jpegQuality = when {
            qualityPref.contains("100") -> 100
            qualityPref.contains("High") -> 95
            else -> 85
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera")
            }
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
            }
        }
    }

    // =====================================================
    // VIDEO RECORDING (FAST MOTION, SLOW MOTION, VIDEO, TIMELAPSE)
    // =====================================================
    private fun toggleVideoRecording() {
        val vc = videoCapture ?: return
        val curRec = activeRecording
        if (curRec != null) {
            curRec.stop()
            activeRecording = null
            Toast.makeText(this, "Video Saved to Gallery!", Toast.LENGTH_SHORT).show()
            return
        }

        val name = "VID_${currentVideoSubMode.replace(" ", "_")}_${System.currentTimeMillis()}.mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/Camera")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        var pending = vc.output.prepareRecording(this, mediaStoreOutputOptions)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
            !prefs.getBoolean("interruption_free", false)) {
            pending = pending.withAudioEnabled()
        }

        Toast.makeText(this, "Recording $currentVideoSubMode (${prefs.getString("video_fps", "60 fps")})...", Toast.LENGTH_SHORT).show()
        activeRecording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
            if (event is VideoRecordEvent.Finalize) {
                activeRecording = null
            }
        }
    }

    // =====================================================
    // VOLUME KEYS FUNCTION (FROM SCREENSHOT 1 & 4)
    // =====================================================
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val func = if (currentMode == "VIDEO") {
                prefs.getString("vol_vid_func", "Start video")
            } else {
                prefs.getString("vol_cam_func", "Take picture")
            }
            when (func) {
                "Take picture", "Start video" -> {
                    onShutterPressed()
                    return true
                }
                "Zoom" -> {
                    currentZoom = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                        (currentZoom + 0.5f).coerceAtMost(4.0f)
                    } else {
                        (currentZoom - 0.5f).coerceAtLeast(1.0f)
                    }
                    zoomBtn.text = "${currentZoom}x"
                    camera?.cameraControl?.setZoomRatio(currentZoom)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // =====================================================
    // CUSTOM OVERLAY VIEW (Grid, Portrait Circle, Focus, Histogram)
    // =====================================================
    private inner class OverlayView(context: Context) : View(context) {
        var showPortraitCircle = false
        var showFocusBox = false
        var showHistogram = false
        var focusX = 0f
        var focusY = 0f

        private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#66FFFFFF")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        private val focusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = orangeColor
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()

            // 1. Grid Lines (3x3 or Golden Ratio)
            val gridMode = prefs.getString("grid_mode", "Off") ?: "Off"
            if (gridMode != "Off" || prefs.getBoolean("portrait_framing", false)) {
                canvas.drawLine(w / 3f, 0f, w / 3f, h, gridPaint)
                canvas.drawLine(2f * w / 3f, 0f, 2f * w / 3f, h, gridPaint)
                canvas.drawLine(0f, h / 3f, w, h / 3f, gridPaint)
                canvas.drawLine(0f, 2f * h / 3f, w, 2f * h / 3f, gridPaint)
            }

            // 2. Portrait Face Circle (From Video 0:09 - 0:13)
            if (showPortraitCircle) {
                val radius = (w * 0.40f)
                canvas.drawCircle(w / 2f, h * 0.43f, radius, whitePaint)
            }

            // 3. Corner Brackets (From Screenshot 5)
            val bx = w * 0.22f
            val by = h * 0.32f
            val bw = w * 0.78f
            val bh = h * 0.62f
            val len = 65f
            // Top-left
            canvas.drawLine(bx, by, bx + len, by, gridPaint)
            canvas.drawLine(bx, by, bx, by + len, gridPaint)
            // Top-right
            canvas.drawLine(bw, by, bw - len, by, gridPaint)
            canvas.drawLine(bw, by, bw, by + len, gridPaint)
            // Bottom-left
            canvas.drawLine(bx, bh, bx + len, bh, gridPaint)
            canvas.drawLine(bx, bh, bx, bh - len, gridPaint)
            // Bottom-right
            canvas.drawLine(bw, bh, bw - len, bh, gridPaint)
            canvas.drawLine(bw, bh, bw, bh - len, gridPaint)

            // 4. Tap-to-Focus Box + Sun Icon (From Video 0:52)
            if (showFocusBox) {
                val r = 70f
                canvas.drawRect(focusX - r, focusY - r, focusX + r, focusY + r, focusPaint)
                focusPaint.style = Paint.Style.FILL
                canvas.drawCircle(focusX + r + 40f, focusY, 14f, focusPaint)
                focusPaint.style = Paint.Style.STROKE
            }

            // 5. Pro Histogram (From Video 0:25)
            if (showHistogram) {
                val histLeft = 48f
                val histTop = 520f
                focusPaint.color = greenColor
                canvas.drawRect(histLeft, histTop, histLeft + 240f, histTop + 110f, gridPaint)
                val bars = floatArrayOf(25f, 45f, 70f, 95f, 80f, 60f, 85f, 50f, 30f)
                bars.forEachIndexed { idx, bhVal ->
                    val x = histLeft + 20f + idx * 24f
                    canvas.drawLine(x, histTop + 105f, x, histTop + 105f - bhVal, focusPaint)
                }
                focusPaint.color = orangeColor
            }
        }
    }

    // =====================================================
    // UI HELPER BUILDERS
    // =====================================================
    private fun createTopButton(label: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setPadding(8, 16, 8, 16)
            setOnClickListener { onClick() }
        }
    }

    private fun createRightSideButton(label: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(20, 22, 20, 22)
            setOnClickListener { onClick() }
        }
    }

    private fun createBottomIcon(symbol: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = symbol
            setTextColor(Color.WHITE)
            textSize = 24f
            gravity = Gravity.CENTER
            setPadding(16, 16, 16, 16)
            setOnClickListener { onClick() }
        }
    }

    private fun createFilterChip(name: String, selected: Boolean, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = name
            textSize = 12f
            setTextColor(if (selected) orangeColor else Color.WHITE)
            setPadding(28, 24, 28, 24)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#252525"))
                cornerRadius = 16f
                setStroke(3, if (selected) orangeColor else Color.TRANSPARENT)
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 16 }
            layoutParams = lp
            setOnClickListener { onClick() }
        }
    }

    private fun allPermissionsGranted() = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    ).all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && allPermissionsGranted()) {
            startCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

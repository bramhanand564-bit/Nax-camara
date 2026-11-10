package com.aicam.pro

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var rootLayout: LinearLayout
    private val greenColor = Color.parseColor("#00E676")
    private val subTextColor = Color.parseColor("#9E9E9E")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("AICamPrefs", Context.MODE_PRIVATE)

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 32, 36, 80)
        }
        scrollView.addView(rootLayout)
        setContentView(scrollView)

        buildSettingsUI()
    }

    private fun buildSettingsUI() {
        rootLayout.removeAllViews()

        // Top Bar (< Settings)
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 10, 0, 36)
        }
        val backBtn = TextView(this).apply {
            text = "←   Settings"
            setTextColor(Color.WHITE)
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener { finish() }
        }
        topBar.addView(backBtn)
        rootLayout.addView(topBar)

        // ==========================================
        // 1. GEMINI AI & POWER ENGINE (MAIN FEATURE)
        // ==========================================
        addSectionHeader("AI Power & Color Engine (Gemini AI)")

        addSwitchItem(
            "AI Power (Auto Color & Details)",
            "Use Gemini AI to fill vibrant colors, details & natural skin tone",
            "ai_enabled",
            true
        )

        addSliderItem(
            "AI Power Percentage (%)",
            "Control how much AI power is used so photo looks 100% real",
            "ai_power_percent",
            60
        )

        addSwitchItem(
            "Smart Face Brightening (Gora Boost)",
            "Remove darkness & shadows from face automatically",
            "face_bright_boost",
            true
        )

        addChoiceItem(
            "Color Engine Mode",
            "color_engine_mode",
            "Super Combo (All-in-One)",
            arrayOf(
                "Super Combo (All-in-One)",
                "Vivid Glow (Bright Skin & Punchy Colors)",
                "True Tone (Warm Natural Glow)",
                "HDR+ Pro (Ultra Sharp Details)"
            )
        )

        addTextInputItem(
            "Gemini Free API Key",
            "gemini_api_key",
            "Tap to paste your free Gemini API Key"
        )

        // ==========================================
        // 2. FORMAT AND RESOLUTION OF PHOTO
        // ==========================================
        addSectionHeader("Format and resolution of photo")

        addChoiceItem(
            "Format for back camera",
            "format_back",
            "Standard (4:3)",
            arrayOf("Standard (4:3)", "Full HD (16:9)", "Full HD plus (18:9)")
        )

        addChoiceItem(
            "Format for front camera",
            "format_front",
            "Standard (4:3)",
            arrayOf("Standard (4:3)", "Full HD (16:9)", "Full HD plus (18:9)")
        )

        addChoiceItem(
            "Resolution for back camera",
            "res_back",
            "50 MP",
            arrayOf("50 MP", "12 MP")
        )

        addChoiceItem(
            "Resolution for front camera",
            "res_front",
            "16 MP",
            arrayOf("16 MP", "12 MP", "4 MP")
        )

        // ==========================================
        // 3. SMART AI & PRO FEATURES (FROM VIDEO)
        // ==========================================
        addSectionHeader("Smart Photo & Pro features")

        addSwitchItem(
            "AI scene recognition",
            "Applies to camera in Photo and AI Cam modes",
            "ai_scene",
            true
        )

        addChoiceItem(
            "AI Eye Autofocus",
            "ai_eye_af",
            "Auto",
            arrayOf("Auto", "Left eye", "Right eye", "Off")
        )

        addChoiceItem(
            "Eye Autofocus animation",
            "eye_af_anim",
            "Default",
            arrayOf("Default", "Circle Glow", "Off")
        )

        addSwitchItem(
            "Histogram (Pro features)",
            "Show live brightness histogram in Pro mode",
            "pro_histogram",
            true
        )

        addSwitchItem(
            "RAW (Pro features)",
            "In Pro mode, photos can be taken in both RAW and JPEG formats",
            "pro_raw",
            true
        )

        addSwitchItem(
            "Remove moles",
            "Front & Portrait mole and blemish removal effect in Beauty mode",
            "remove_moles",
            true
        )

        addSwitchItem(
            "Portrait framing",
            "Applies to rear camera in Photo and Portrait modes",
            "portrait_framing",
            false
        )

        addChoiceItem(
            "Watermark",
            "watermark_mode",
            "Off",
            arrayOf("Off", "Time & Date Watermark", "AI Cam Pro Watermark")
        )

        addChoiceItem(
            "Countdown",
            "countdown_sec",
            "Off",
            arrayOf("Off", "3s", "5s", "10s")
        )

        // ==========================================
        // 4. RESOLUTION OF VIDEO & CODEC
        // ==========================================
        addSectionHeader("Resolution of video")

        addChoiceItem(
            "Resolution for back camera",
            "video_res_back",
            "FHD 1080p",
            arrayOf("FHD 1080p", "HD 720p")
        )

        addChoiceItem(
            "Resolution for front camera",
            "video_res_front",
            "FHD 1080p",
            arrayOf("FHD 1080p", "HD 720p")
        )

        addChoiceItem(
            "Frame rate",
            "video_fps",
            "60 fps",
            arrayOf("30 fps", "60 fps")
        )

        addChoiceItem(
            "Codec",
            "video_codec",
            "H.265 (high efficiency)",
            arrayOf("H.264 (high compatibility)", "H.265 (high efficiency)")
        )

        // ==========================================
        // 5. FILE AND SAVE
        // ==========================================
        addSectionHeader("File and save")

        addChoiceItem(
            "Save photos to...",
            "save_location",
            "Internal storage (Phone)",
            arrayOf("Internal storage (Phone)", "DCIM/Camera")
        )

        addChoiceItem(
            "File format",
            "file_format",
            "JPEG",
            arrayOf("JPEG", "RAW + JPEG")
        )

        addChoiceItem(
            "Quality of the JPEG",
            "jpeg_quality",
            "Super High (100%)",
            arrayOf("Super High (100%)", "High", "Standard")
        )

        addSwitchItem(
            "Geolocation / Location",
            "Add location tags to photo and video details",
            "geolocation",
            false
        )

        // ==========================================
        // 6. FUNCTION OF THE VOLUME KEYS
        // ==========================================
        addSectionHeader("Function of the volume keys")

        addChoiceItem(
            "Function in camera mode",
            "vol_cam_func",
            "Take picture",
            arrayOf("Take picture", "Zoom", "Volume")
        )

        addChoiceItem(
            "Function in video mode",
            "vol_vid_func",
            "Start video",
            arrayOf("Start video", "Zoom", "Volume")
        )

        // ==========================================
        // 7. SCREEN SETTINGS
        // ==========================================
        addSectionHeader("Screen settings")

        addSwitchItem(
            "Brightness control",
            "Automatically increase brightness of the screen on opening the camera",
            "brightness_control",
            true
        )

        addSwitchItem(
            "Selfie indicator",
            "Display miniature indicating where to look on taking a selfie",
            "selfie_indicator",
            true
        )

        addChoiceItem(
            "Grid lines",
            "grid_mode",
            "Off",
            arrayOf("Off", "3x3 Grid", "Golden Ratio")
        )

        // ==========================================
        // 8. OTHER SETTINGS
        // ==========================================
        addSectionHeader("Other settings")

        addSwitchItem(
            "Camera sounds / Shutter sound",
            "Reproduce shutter release sound and countdown",
            "shutter_sound",
            true
        )

        addSwitchItem(
            "Interruption-free recording",
            "Do not produce sounds or vibrations during video recording",
            "interruption_free",
            false
        )

        addSwitchItem(
            "Keep normal photo when HDR",
            "Save the normally exposed photo from the set of 3 shots merged to create the HDR image",
            "keep_normal_hdr",
            true
        )

        addSwitchItem(
            "Mirror effect / Mirrored selfie",
            "Save photos taken with the front camera exactly as they appear in the preview",
            "mirror_selfie",
            true
        )

        // ==========================================
        // 9. RESET BUTTON
        // ==========================================
        val resetBtn = Button(this).apply {
            text = "Reset All Settings"
            setTextColor(Color.parseColor("#448AFF"))
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            setPadding(24, 24, 24, 24)
            setOnClickListener {
                prefs.edit().clear().apply()
                Toast.makeText(this@SettingsActivity, "Settings Reset to Default", Toast.LENGTH_SHORT).show()
                buildSettingsUI()
            }
        }
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 48 }
        rootLayout.addView(resetBtn, params)
    }

    private fun addSectionHeader(title: String) {
        val tv = TextView(this).apply {
            text = title
            setTextColor(greenColor)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 36, 0, 12)
        }
        rootLayout.addView(tv)
    }

    private fun addDivider() {
        val div = View(this).apply {
            setBackgroundColor(Color.parseColor("#1F1F1F"))
        }
        rootLayout.addView(div, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2))
    }

    private fun addSwitchItem(title: String, subtitle: String, key: String, defaultVal: Boolean) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 26, 0, 26)
        }

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleTv = TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 16f
        }
        val subTv = TextView(this).apply {
            text = subtitle
            setTextColor(subTextColor)
            textSize = 13f
        }
        textCol.addView(titleTv)
        textCol.addView(subTv)

        val sw = Switch(this).apply {
            isChecked = prefs.getBoolean(key, defaultVal)
            thumbTintList = ColorStateList.valueOf(greenColor)
            trackTintList = ColorStateList.valueOf(Color.parseColor("#00703A"))
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean(key, isChecked).apply()
            }
        }

        row.addView(textCol)
        row.addView(sw)
        rootLayout.addView(row)
        addDivider()
    }

    private fun addSliderItem(title: String, subtitle: String, key: String, defaultVal: Int) {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 26, 0, 26)
        }

        val currentVal = prefs.getInt(key, defaultVal)
        val titleTv = TextView(this).apply {
            text = "$title  —  [ $currentVal% ]"
            setTextColor(greenColor)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
        }
        val subTv = TextView(this).apply {
            text = subtitle
            setTextColor(subTextColor)
            textSize = 13f
        }

        val seekBar = SeekBar(this).apply {
            max = 100
            progress = currentVal
            progressTintList = ColorStateList.valueOf(greenColor)
            thumbTintList = ColorStateList.valueOf(greenColor)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    titleTv.text = "$title  —  [ $progress% ]"
                    prefs.edit().putInt(key, progress).apply()
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }

        col.addView(titleTv)
        col.addView(subTv)
        col.addView(seekBar)
        rootLayout.addView(col)
        addDivider()
    }

    private fun addChoiceItem(title: String, key: String, defaultVal: String, options: Array<String>) {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 26, 0, 26)
        }

        val currentVal = prefs.getString(key, defaultVal) ?: defaultVal
        val titleTv = TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 16f
        }
        val valTv = TextView(this).apply {
            text = currentVal
            setTextColor(subTextColor)
            textSize = 14f
        }

        col.setOnClickListener {
            val selectedIdx = options.indexOf(prefs.getString(key, defaultVal)).coerceAtLeast(0)
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setSingleChoiceItems(options, selectedIdx) { dialog, which ->
                    val chosen = options[which]
                    prefs.edit().putString(key, chosen).apply()
                    valTv.text = chosen
                    dialog.dismiss()
                }
                .setNegativeButton("CANCEL", null)
                .show()
        }

        col.addView(titleTv)
        col.addView(valTv)
        rootLayout.addView(col)
        addDivider()
    }

    private fun addTextInputItem(title: String, key: String, hintText: String) {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 26, 0, 26)
        }

        val savedKey = prefs.getString(key, "") ?: ""
        val titleTv = TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 16f
        }
        val valTv = TextView(this).apply {
            text = if (savedKey.isEmpty()) hintText else "API Key Saved (Tap to change)"
            setTextColor(if (savedKey.isEmpty()) subTextColor else greenColor)
            textSize = 13f
        }

        col.setOnClickListener {
            val input = EditText(this).apply {
                hint = "Paste Gemini API Key here"
                setText(prefs.getString(key, ""))
                setTextColor(Color.WHITE)
                setHintTextColor(Color.GRAY)
            }
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setView(input)
                .setPositiveButton("SAVE") { _, _ ->
                    val k = input.text.toString().trim()
                    prefs.edit().putString(key, k).apply()
                    valTv.text = if (k.isEmpty()) hintText else "API Key Saved (Tap to change)"
                    valTv.setTextColor(if (k.isEmpty()) subTextColor else greenColor)
                }
                .setNegativeButton("CANCEL", null)
                .show()
        }

        col.addView(titleTv)
        col.addView(valTv)
        rootLayout.addView(col)
        addDivider()
    }
}

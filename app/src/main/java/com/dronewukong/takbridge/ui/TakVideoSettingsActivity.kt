package com.dronewukong.takbridge.ui

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.dronewukong.takbridge.R
import com.dronewukong.takbridge.bridge.BridgeApplication
import com.dronewukong.takbridge.cot.SharedVideoAddress
import com.dronewukong.takbridge.transport.ConfigStore

/** Explicitly opt in to broadcasting an address; opening settings never connects to a source. */
class TakVideoSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tak_video_settings)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.takVideoRoot)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        val enabled = findViewById<CheckBox>(R.id.takVideoEnabled)
        val address = findViewById<EditText>(R.id.takVideoAddress)
        val status = findViewById<TextView>(R.id.takVideoStatus)
        val config = ConfigStore.loadTakConfig(this)
        enabled.isChecked = config.videoEnabled
        address.setText(config.videoUrl)
        if (BridgeApplication.session(this).running) status.setText(R.string.tak_video_stop_first)
        findViewById<Button>(R.id.takVideoSave).setOnClickListener {
            if (BridgeApplication.session(this).running) {
                status.setText(R.string.tak_video_stop_first)
                return@setOnClickListener
            }
            try {
                val value = if (enabled.isChecked) SharedVideoAddress.validate(address.text.toString()) else ""
                val current = ConfigStore.loadTakConfig(this)
                ConfigStore.saveTakConfig(this, current.copy(videoEnabled = enabled.isChecked, videoUrl = value))
                address.setText(value)
                status.setText(if (enabled.isChecked) R.string.tak_video_saved else R.string.tak_video_disabled)
            } catch (e: IllegalArgumentException) { status.text = e.message }
        }
        findViewById<Button>(R.id.takVideoBack).setOnClickListener { finish() }
    }
}

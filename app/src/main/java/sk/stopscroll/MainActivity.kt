package sk.stopscroll

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var prefs: Prefs

    private lateinit var subtitle: TextView
    private lateinit var status: TextView
    private lateinit var btnAccessibility: Button
    private lateinit var limitLabel: TextView
    private lateinit var limitBar: SeekBar
    private lateinit var stats: TextView
    private lateinit var swDebug: Switch
    private lateinit var debugText: TextView
    private lateinit var btnCopy: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        subtitle = findViewById(R.id.subtitle)
        status = findViewById(R.id.status)
        btnAccessibility = findViewById(R.id.btnAccessibility)
        limitLabel = findViewById(R.id.limitLabel)
        limitBar = findViewById(R.id.limitBar)
        stats = findViewById(R.id.stats)
        swDebug = findViewById(R.id.swDebug)
        debugText = findViewById(R.id.debugText)
        btnCopy = findViewById(R.id.btnCopy)

        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(
                this,
                "Nájdi „StopScroll“ (často v časti Stiahnuté aplikácie) a zapni ho",
                Toast.LENGTH_LONG
            ).show()
        }

        findViewById<Button>(R.id.btnAppInfo).setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        }

        findViewById<Switch>(R.id.swInstagram).apply {
            isChecked = prefs.instagramOn
            setOnCheckedChangeListener { _, on -> prefs.instagramOn = on }
        }
        findViewById<Switch>(R.id.swYoutube).apply {
            isChecked = prefs.youtubeOn
            setOnCheckedChangeListener { _, on -> prefs.youtubeOn = on }
        }
        findViewById<Switch>(R.id.swDmOnly).apply {
            isChecked = prefs.dmOnly
            setOnCheckedChangeListener { _, on -> prefs.dmOnly = on }
        }

        // Vlastné ID
        val edReel = findViewById<EditText>(R.id.edReel)
        val edShorts = findViewById<EditText>(R.id.edShorts)
        val edDm = findViewById<EditText>(R.id.edDm)
        val edInboxBtn = findViewById<EditText>(R.id.edInboxBtn)
        edReel.setText(prefs.extraReelIds)
        edShorts.setText(prefs.extraShortsIds)
        edDm.setText(prefs.extraDmIds)
        edInboxBtn.setText(prefs.extraInboxBtnIds)
        findViewById<Button>(R.id.btnSaveIds).setOnClickListener {
            prefs.extraReelIds = edReel.text.toString().trim()
            prefs.extraShortsIds = edShorts.text.toString().trim()
            prefs.extraDmIds = edDm.text.toString().trim()
            prefs.extraInboxBtnIds = edInboxBtn.text.toString().trim()
            Toast.makeText(this, "ID uložené – platia hneď", Toast.LENGTH_SHORT).show()
        }

        // Posuvník: 1 až 30 sekúnd
        limitBar.progress = prefs.limitSeconds - 1
        limitBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                prefs.limitSeconds = progress + 1
                updateLimitTexts()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        swDebug.isChecked = prefs.debugOn
        swDebug.setOnCheckedChangeListener { _, on ->
            prefs.debugOn = on
            if (!on) prefs.debugDump = ""
            refresh()
        }

        btnCopy.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("StopScroll ladenie", prefs.debugDump))
            Toast.makeText(this, "Skopírované", Toast.LENGTH_SHORT).show()
        }

        updateLimitTexts()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun updateLimitTexts() {
        val s = prefs.limitSeconds
        limitLabel.text = "Limit: $s s"
        subtitle.text = "Keď pozeráš Instagram Reels alebo YouTube Shorts dlhšie ako $s s, " +
            "appka ťa z nich vyhodí na domovskú obrazovku."
    }

    private fun refresh() {
        if (isServiceEnabled()) {
            status.text = "✅ Služba je zapnutá"
            status.setTextColor(getColor(R.color.ok))
            btnAccessibility.text = "Nastavenia prístupnosti"
        } else {
            status.text = "⚠️ Služba je vypnutá – zapni ju v nastaveniach prístupnosti"
            status.setTextColor(getColor(R.color.accent))
            btnAccessibility.text = "Zapnúť v nastaveniach prístupnosti"
        }

        stats.text = "Zablokované Reels/Shorts: ${prefs.blockCount}×\n" +
            "Vrátené do správ: ${prefs.redirectCount}×\n" +
            "Posledná udalosť: ${prefs.lastEvent}"

        val dbgVisible = if (prefs.debugOn) View.VISIBLE else View.GONE
        debugText.visibility = dbgVisible
        btnCopy.visibility = dbgVisible
        debugText.text = prefs.debugDump.ifEmpty { "Zatiaľ žiadne údaje – otvor Instagram alebo YouTube." }
    }

    private fun isServiceEnabled(): Boolean {
        val me = ComponentName(this, BlockerService::class.java)
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}

package com.capylabs.vrlauncher

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

/**
 * VR Store catalog. The launcher never distributes pirated game builds: each
 * card opens the game's official Android/VR distribution page. This keeps the
 * catalog limited to legitimate releases and lets the developer keep signing
 * and updating their own APKs.
 */
class VrStoreActivity : ComponentActivity() {
    data class Game(val title: String, val description: String, val url: String)

    private val games = listOf(
        Game("VRidge", "Stream PC VR games to a Cardboard-style Android headset.", "https://play.google.com/store/apps/details?id=com.riftcat.vridge2"),
        Game("Google Cardboard", "Discover and launch smartphone VR experiences.", "https://play.google.com/store/apps/details?id=com.google.samples.apps.cardboarddemo")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(48, 32, 48, 32)
            setBackgroundColor(Color.rgb(7, 10, 18))
        }
        val title = TextView(this).apply {
            text = "VR STORE"
            textSize = 30f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 28)
        }
        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        games.forEach { game ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(28, 22, 28, 22)
                setBackgroundColor(Color.rgb(24, 29, 42))
                isClickable = true
                setOnClickListener { openOfficialPage(game.url) }
            }
            val name = TextView(this).apply {
                text = game.title
                textSize = 22f
                setTextColor(Color.WHITE)
            }
            val desc = TextView(this).apply {
                text = game.description + "\n\nOPEN OFFICIAL STORE →"
                textSize = 14f
                setTextColor(Color.LTGRAY)
                setPadding(0, 8, 0, 0)
            }
            card.addView(name)
            card.addView(desc)
            root.addView(card, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, 18) })
        }
        setContentView(root)
    }

    private fun openOfficialPage(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

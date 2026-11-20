package dev.hydrogen.tieba

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class OfficialActivity : ComponentActivity() {
    private lateinit var browser: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("url").orEmpty()
        val target = Uri.parse(url)
        if (target.scheme != "https" || !(target.host == "baidu.com" || target.host.orEmpty().endsWith(".baidu.com"))) {
            finish()
            return
        }
        enableEdgeToEdge()
        val dark = intent.getBooleanExtra("dark", false)
        val background = Color.parseColor(if (dark) "#131A16" else "#F7F8F4")
        val foreground = Color.parseColor(if (dark) "#E3E9E0" else "#202923")
        val accent = Color.parseColor(if (dark) "#A6D3B6" else "#326A54")
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(background) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        val toolbar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), 0, dp(4), 0) }
        fun action(label: String, description: String, callback: () -> Unit): TextView = TextView(this).apply {
            text = label
            contentDescription = description
            textSize = 15f
            setTextColor(accent)
            gravity = Gravity.CENTER
            minWidth = dp(52)
            minHeight = dp(52)
            isClickable = true
            isFocusable = true
            setOnClickListener { callback() }
        }
        toolbar.addView(action("返回", "返回上一页") { goBack() })
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), 0, 0, 0) }
        heading.addView(TextView(this).apply { text = if (target.path.orEmpty().startsWith("/im/pcmsg")) "私信 · 百度官方页面" else "百度官方页面"; textSize = 16f; setTextColor(foreground) })
        val host = TextView(this).apply { text = target.host; textSize = 12f; setTextColor(foreground); alpha = .65f }
        heading.addView(host)
        toolbar.addView(heading, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        toolbar.addView(action("刷新", "刷新页面") { browser.reload() })
        toolbar.addView(action("完成", "返回氢贴吧") { finish() })
        root.addView(toolbar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(60)))
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = ColorStateList.valueOf(accent)
        }
        root.addView(progress, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(3)))
        val error = TextView(this).apply {
            text = "页面暂时无法连接，点此重试"
            textSize = 14f
            setTextColor(accent)
            minHeight = dp(48)
            gravity = Gravity.CENTER
            visibility = View.GONE
            setOnClickListener { visibility = View.GONE; browser.reload() }
        }
        root.addView(error)
        browser = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.safeBrowsingEnabled = true
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, percentage: Int) {
                    progress.progress = percentage
                    progress.visibility = if (percentage == 100) View.GONE else View.VISIBLE
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, address: String, favicon: android.graphics.Bitmap?) {
                    host.text = Uri.parse(address).host
                    error.visibility = View.GONE
                }
                override fun onPageFinished(view: WebView, address: String) { CookieManager.getInstance().flush() }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: WebResourceError) {
                    if (request.isForMainFrame) error.visibility = View.VISIBLE
                }
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val address = request.url
                    val domain = address.host.orEmpty().lowercase()
                    if (address.scheme == "https" && (domain == "baidu.com" || domain.endsWith(".baidu.com"))) return false
                    if (address.scheme == "http" && (domain == "baidu.com" || domain.endsWith(".baidu.com"))) {
                        view.loadUrl(address.buildUpon().scheme("https").build().toString())
                    } else if (request.hasGesture() && address.scheme in listOf("https", "http")) {
                        try { startActivity(Intent(Intent.ACTION_VIEW, address)) } catch (_: ActivityNotFoundException) { error.visibility = View.VISIBLE }
                    }
                    return true
                }
            }
        }
        root.addView(browser, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { goBack() }
        })
        if (savedInstanceState == null) browser.loadUrl(url) else browser.restoreState(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::browser.isInitialized) browser.saveState(outState)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        if (::browser.isInitialized) { browser.stopLoading(); browser.destroy(); CookieManager.getInstance().flush() }
        super.onDestroy()
    }
    private fun goBack() { if (browser.canGoBack()) browser.goBack() else finish() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

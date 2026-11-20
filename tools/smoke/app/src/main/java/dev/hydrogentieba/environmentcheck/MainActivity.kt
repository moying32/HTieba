package dev.hydrogentieba.environmentcheck

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "氢贴吧：Android 开发环境检查通过"
            textSize = 22f
            setPadding(48, 96, 48, 48)
        })
    }
}

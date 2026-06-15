package com.invent.ontocook.multiple_connection

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.invent.ontocook.BuildConfig
import com.invent.ontocook.R
import com.invent.ontocook.databinding.ActivitySplashBinding
import com.invent.ontocook.utils.Constants


class SplashActivity : AppCompatActivity() {
    lateinit var handler: Handler
    private lateinit var binding: ActivitySplashBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        val tabletSize = resources.getBoolean(com.invent.ontocook.R.bool.isTablet)
        if (tabletSize)
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
//        if (BuildConfig.DEBUG) {
//            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(false)
//        }
        binding = DataBindingUtil.setContentView(this, com.invent.ontocook.R.layout.activity_splash)
        binding.tvVersion.text = BuildConfig.VERSION_NAME

        if (this::handler.isInitialized)
            handler.removeCallbacksAndMessages(null)
        handler = Handler(Looper.getMainLooper())

        handler.postDelayed({
//            startActivity(Intent(this@SplashActivity, HomeActivity::class.java))
            if (tabletSize) {
                Constants.IS_TABLET = true
                startActivity(Intent(this@SplashActivity, HomeTvActivity::class.java))
            } else {
                startActivity(Intent(this@SplashActivity, HomeActivity::class.java))
            }
            finish()
        }, 1500)
    }
}
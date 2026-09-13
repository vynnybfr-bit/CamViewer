package com.example.cameraviewer

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

class CamViewerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                activity.window.decorView.post {
                    installWatermark(activity)
                }
            }

            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun installWatermark(activity: Activity) {
        val decor = activity.window.decorView as? ViewGroup ?: return
        if (decor.findViewWithTag<View>(WATERMARK_TAG) != null) return

        // The app content itself is a black LinearLayout. If the watermark is
        // placed behind that opaque layout, it disappears. Keep the actual
        // window background black and make the app root transparent so the
        // watermark can sit between the black background and the text/buttons.
        activity.window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        val content = decor.findViewById<ViewGroup>(android.R.id.content) ?: return
        findAppRoot(content)?.setBackgroundColor(Color.TRANSPARENT)

        val watermark = ImageView(activity).apply {
            tag = WATERMARK_TAG
            setImageResource(com.example.cameraviewer.R.drawable.botafogo_watermark)
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = 0.6f
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            visibility = View.GONE
        }

        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ).apply {
            gravity = Gravity.CENTER
        }

        // Content order: black window background -> watermark -> transparent
        // app root -> all app text/buttons. Exactly the requested layering.
        content.addView(watermark, 0, params)

        decor.viewTreeObserver.addOnGlobalLayoutListener {
            if (watermark.isAttachedToWindow) {
                findAppRoot(content)?.setBackgroundColor(Color.TRANSPARENT)
                watermark.visibility = if (isHomeScreen(decor)) View.VISIBLE else View.GONE
            }
        }
    }

    private fun findAppRoot(content: ViewGroup): View? {
        for (i in 0 until content.childCount) {
            val child = content.getChildAt(i)
            if (child !is ImageView || child.tag != WATERMARK_TAG) return child
        }
        return null
    }

    private fun isHomeScreen(root: View): Boolean {
        var hasCamViewerTitle = false
        var hasPlayer = false

        fun inspect(view: View) {
            if (view is TextView && view.text?.toString() == "CamViewer") {
                hasCamViewerTitle = true
            }
            if (view.javaClass.simpleName == "PlayerView") {
                hasPlayer = true
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) inspect(view.getChildAt(i))
            }
        }

        inspect(root)
        return hasCamViewerTitle && !hasPlayer
    }

    companion object {
        private const val WATERMARK_TAG = "camviewer_botafogo_watermark"
    }
}

package com.dronewukong.takbridge.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.dronewukong.takbridge.R

/** Reserve natural action heights before giving remaining space to scrollable settings. */
class ConnectionPanel @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val settings = findViewById<View>(R.id.connectionSettings)
        if (settings?.visibility == View.VISIBLE && MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            var actionsHeight = paddingTop + paddingBottom
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (child === settings || child.visibility == View.GONE) continue
                val lp = child.layoutParams as ViewGroup.MarginLayoutParams
                val width = getChildMeasureSpec(widthMeasureSpec,
                    paddingLeft + paddingRight + lp.leftMargin + lp.rightMargin, lp.width)
                val height = if (lp.height >= 0) MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY)
                    else MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                child.measure(width, height)
                actionsHeight += child.measuredHeight + lp.topMargin + lp.bottomMargin
            }
            val gap = (16 * resources.displayMetrics.density).toInt()
            val maximum = (340 * resources.displayMetrics.density).toInt()
            val available = (MeasureSpec.getSize(heightMeasureSpec) - actionsHeight - gap).coerceIn(1, maximum)
            // Inside measurement: avoid requesting another layout for the same calculation.
            settings.layoutParams.height = available
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}

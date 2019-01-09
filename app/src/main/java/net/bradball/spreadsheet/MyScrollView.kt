package net.bradball.spreadsheet

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.HorizontalScrollView
import androidx.appcompat.widget.AppCompatTextView

class MyScrollView(context: Context, attrSet: AttributeSet? = null, defStyleAttr: Int = android.R.attr.horizontalScrollViewStyle, defStyleRes: Int = 0): HorizontalScrollView(context, attrSet, defStyleAttr, defStyleRes) {


    private var listener: View.OnScrollChangeListener? = null

    fun setScrollListener(newListener: View.OnScrollChangeListener?) {
        listener = newListener
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)



    }


}


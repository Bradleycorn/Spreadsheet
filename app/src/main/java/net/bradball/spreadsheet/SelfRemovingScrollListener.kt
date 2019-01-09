package net.bradball.spreadsheet

import android.view.View
import android.widget.HorizontalScrollView
import androidx.recyclerview.widget.RecyclerView

open class SelfRemovingRecyclerScrollListener : RecyclerView.OnScrollListener() {

    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
        super.onScrollStateChanged(recyclerView, newState)
        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
            recyclerView.removeOnScrollListener(this)
        }
    }
}

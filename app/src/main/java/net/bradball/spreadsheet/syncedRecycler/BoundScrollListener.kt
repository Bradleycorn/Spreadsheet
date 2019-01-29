package net.bradball.spreadsheet.syncedRecycler

import androidx.recyclerview.widget.RecyclerView

class BoundScrollListener(private val to: SyncedRecyclerView, private val orientation: Int): RecyclerView.OnScrollListener() {
    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        super.onScrolled(recyclerView, dx, dy)
        to.scrollBy(if (orientation == SyncedRecyclerView.ALIGN_ORIENTATION_VERTICAL) 0 else dx, if (orientation == SyncedRecyclerView.ALIGN_ORIENTATION_HORIZONTAL) 0 else dy)
    }

    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
        super.onScrollStateChanged(recyclerView, newState)
        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
            recyclerView.removeOnScrollListener(this)
        }
    }
}
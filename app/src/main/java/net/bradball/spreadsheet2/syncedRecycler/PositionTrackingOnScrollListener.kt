package net.bradball.spreadsheet2.syncedRecycler

import androidx.recyclerview.widget.RecyclerView


internal class PositionTrackingOnScrollListener : RecyclerView.OnScrollListener() {

    var scrolledX: Int = 0
        private set
    var scrolledY: Int = 0
        private set

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        scrolledX += dx
        scrolledY += dy
        super.onScrolled(recyclerView, dx, dy)
    }
}

package net.bradball.spreadsheet.syncedRecycler

import android.util.Log
import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView
import net.bradball.spreadsheet.R


class SyncedRecyclerViewTouchListener : RecyclerView.OnItemTouchListener {
    private val TAG = "SyncedTouchListener"

    private val bindings: MutableList<SyncedRecyclerViewBinding> = mutableListOf()
    private var lastX: Int = 0
    private var lastY: Int = 0

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        var touchHandled = false

        if (rv.scrollState == RecyclerView.SCROLL_STATE_IDLE) {
            onTouchEvent(rv, e)
        }

        for (binding in bindings) {
            if (binding.from === rv && binding.to.scrollState != RecyclerView.SCROLL_STATE_IDLE) {
                touchHandled = true
                break
            }
        }

        return touchHandled
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
        for (binding in bindings) {
            handleTouchEvent(rv as SyncedRecyclerView, e, binding.to, binding.orientation)
        }
    }

    private fun handleTouchEvent(from: SyncedRecyclerView, e: MotionEvent, to: SyncedRecyclerView, orientation: Int) {

        val action: Int = e.action
        val thisScrollListener = from.scrollListener

        if (action == MotionEvent.ACTION_DOWN && to.scrollState == RecyclerView.SCROLL_STATE_IDLE) {
            lastX = thisScrollListener.scrolledX
            lastY = thisScrollListener.scrolledY
            from.addOnScrollListener(BoundScrollListener(to, orientation))
        } else {
            val scrolledX = thisScrollListener.scrolledX
            val scrolledY = thisScrollListener.scrolledY
            if (action == MotionEvent.ACTION_UP &&
                    (orientation == SyncedRecyclerView.ALIGN_ORIENTATION_VERTICAL && lastY == scrolledY ||
                     orientation == SyncedRecyclerView.ALIGN_ORIENTATION_HORIZONTAL && lastX == scrolledX ||
                     lastY == scrolledY && lastX == scrolledX)) {
                from.clearOnScrollListeners()
            }
        }
    }

    fun flingBoundViews(rv: SyncedRecyclerView, x: Int, y: Int) {
        for (binding in bindings) {
            if (binding.from == rv) {
                binding.to.fling(x, y)
            }
        }
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) { /* noop */ }

    internal fun createBinding(binding: SyncedRecyclerViewBinding): Boolean {
        return bindings.add(binding)
    }

    internal fun destroyBinding(binding: SyncedRecyclerViewBinding): Boolean {
        return bindings.remove(binding)
    }

    internal fun bindingExists(binding: SyncedRecyclerViewBinding): Boolean {
        return bindings.contains(binding)
    }
}
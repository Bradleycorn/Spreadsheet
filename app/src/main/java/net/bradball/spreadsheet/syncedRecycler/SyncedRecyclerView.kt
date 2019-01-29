package net.bradball.spreadsheet.syncedRecycler

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView


class SyncedRecyclerView(context: Context, attrs: AttributeSet? = null, defStyle: Int) : RecyclerView(context, attrs, defStyle) {
    constructor(context: Context, attrs: AttributeSet? = null): this(context, attrs, 0)
    constructor(context: Context): this(context, null)

    private var touchListener: SyncedRecyclerViewTouchListener = SyncedRecyclerViewTouchListener()
    internal var scrollListener: PositionTrackingOnScrollListener = PositionTrackingOnScrollListener()
        private set

    init {
        addOnItemTouchListener(touchListener)
        addOnScrollListener(scrollListener)
    }

    /**
     * Binds this SyncedRecyclerView to another SyncedRecyclerView. This is an unidirectional
     * binding, meaning that calling this method implies that scrolling this SyncedRecyclerView will cause `target` to scroll.
     * Calling this method does not modify the behavior of the symmetric binding, if any.
     *
     * @param target        SyncedRecyclerView The target to bind to.
     * @param alignmentMode int The alignment mode to use for the binding.
     * @return The success of the operation. Usually the operation would fail if `target` is the
     * own object or the binding already exists.
     */
    fun bindTo(target: SyncedRecyclerView, alignmentMode: Int): Boolean {
        return !isBoundTo(target, alignmentMode) && touchListener.createBinding(SyncedRecyclerViewBinding(this,
                target, alignmentMode))
    }

    /**
     * Unbinds this SyncedRecyclerView from another SyncedRecyclerView. This is an
     * unidirectional unbinding, meaning that calling this method implies that scrolling `target` will no longer cause this SyncedRecyclerView to scroll.
     * Calling this method does not modify the behavior of the symmetric binding, if any.
     *
     * @param target        SyncedRecyclerView The target to unbind from.
     * @param alignmentMode int The alignment mode for the binging to be removed.
     * @return The success of the operation. Usually the operation would fail if `target` is the
     * own object or the binding does not already exists.
     */
    fun unbindFrom(target: SyncedRecyclerView, alignmentMode: Int): Boolean {
        return isBoundTo(target, alignmentMode) && touchListener.destroyBinding(SyncedRecyclerViewBinding(this,
                target, alignmentMode))
    }

    /**
     * Verifies is this SyncedRecyclerView is bound to the given SyncedRecyclerView.
     *
     * @param target        SyncedRecyclerView The target towards which the existence of the binding shall be verified.
     * @param alignmentMode int The alignment mode to check for.
     * @return `true` if there is a binding from this object towards `target`;
     * `false` otherwise.
     */
    fun isBoundTo(target: SyncedRecyclerView, alignmentMode: Int): Boolean {
        return touchListener.bindingExists(SyncedRecyclerViewBinding(this, target, alignmentMode))
    }

    companion object {

        val ALIGN_ORIENTATION_VERTICAL = 1
        val ALIGN_ORIENTATION_HORIZONTAL = 2
    }
}
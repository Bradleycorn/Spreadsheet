package net.bradball.spreadsheet

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import android.view.ViewGroup
import android.graphics.PointF
import android.util.AttributeSet
import android.util.Log
import androidx.recyclerview.widget.LinearSmoothScroller
import android.util.SparseArray
import android.util.SparseIntArray
import android.view.View

class FixedGridLayoutManager: RecyclerView.LayoutManager(), RecyclerView.SmoothScroller.ScrollVectorProvider {

    companion object {
        private val TAG = FixedGridLayoutManager::class.java.simpleName

        private const val DEFAULT_COUNT = 1

        /* View Removal Constants */
        private const val REMOVE_VISIBLE = 0
        private const val REMOVE_INVISIBLE = 1

        /* Fill Direction Constants */
        private const val DIRECTION_NONE = -1
        private const val DIRECTION_START = 0
        private const val DIRECTION_END = 1
        private const val DIRECTION_UP = 2
        private const val DIRECTION_DOWN = 3
    }

    private val layoutState: GridLayoutState by lazy {
        GridLayoutState()
    }

    /**
     * Stashed to avoid allocation.
     * Only used in fillColumns() and fillRows()
     */
    private val layoutChunkResult = LayoutChunkResult()

    /* First (top-left) position visible at any point */
    private var mFirstVisiblePosition: Int = 0
    /* Consistent size applied to all child views */
    private var mDecoratedChildWidth: Int = 0
    private var mDecoratedChildHeight: Int = 0

    /* Metrics for the visible window of our data */
    private var mVisibleColumnCount: Int = 0
    private var mVisibleRowCount: Int = 0

    /* Used for tracking off-screen change events */
    private var mFirstChangedPosition: Int = 0
    private var mChangedPositionCount: Int = 0


    /** Private Helpers and Metrics Accessors  */


    private val firstVisibleColumn: Int
        get() = mFirstVisiblePosition % totalColumnCount

    private val lastVisibleColumn: Int
        get() = firstVisibleColumn + mVisibleColumnCount - 1


    private val firstVisibleRow: Int
        get() = mFirstVisiblePosition / totalColumnCount


    private val lastVisibleRow: Int
        get() = firstVisibleRow + mVisibleRowCount - 1


    private val visibleChildCount: Int
        get() = mVisibleColumnCount * mVisibleRowCount

    var totalColumnCount: Int = DEFAULT_COUNT
        get() = if (itemCount < field) {
            itemCount
        } else field
        set(count) {
            field = count
            requestLayout()
        }


    private val totalRowCount: Int
        get() {
            if (itemCount == 0 || totalColumnCount == 0) {
                return 0
            }
            var maxRow = itemCount / totalColumnCount
            //Bump the row count if it's not exactly even
            if (itemCount % totalColumnCount != 0) {
                maxRow++
            }

            return maxRow
        }

    private val horizontalSpace: Int
        get() = width - paddingRight - paddingLeft

    private val verticalSpace: Int
        get() = height - paddingBottom - paddingTop


    /* Return the overall column index of this position in the global layout */
    private fun getGlobalColumnOfPosition(position: Int): Int {
        return position % totalColumnCount
    }

    /* Return the overall row index of this position in the global layout */
    private fun getGlobalRowOfPosition(position: Int): Int {
        return position / totalColumnCount
    }

    /*
     * Mapping between child view indices and adapter data
     * positions helps fill the proper views during scrolling.
     */
    private fun positionOfIndex(childIndex: Int): Int {
        val row = childIndex / mVisibleColumnCount
        val column = childIndex % mVisibleColumnCount

        return mFirstVisiblePosition + row * totalColumnCount + column
    }


    /*
     * You must return true from this method if you want your
     * LayoutManager to support anything beyond "simple" item
     * animations. Enabling this causes onLayoutChildren() to
     * be called twice on each animated change; once for a
     * pre-layout, and again for the real layout.
     */
    override fun supportsPredictiveItemAnimations(): Boolean {
        return true
    }

    /*
     * Called by RecyclerView when a view removal is triggered. This is called
     * before onLayoutChildren() in pre-layout if the views removed are not visible. We
     * use it in this case to inform pre-layout that a removal took place.
     *
     * This method is still called if the views removed were visible, but it will
     * happen AFTER pre-layout.
     */
    override fun onItemsRemoved(recyclerView: RecyclerView, positionStart: Int, itemCount: Int) {
        mFirstChangedPosition = positionStart
        mChangedPositionCount = itemCount
    }

    /*
     * This method is your initial call from the framework. You will receive it when you
     * need to start laying out the initial set of views. This method will not be called
     * repeatedly, so don't rely on it to continually process changes during user
     * interaction.
     *
     * This method will be called when the data set in the adapter changes, so it can be
     * used to update a layout based on a new item count.
     *
     * If predictive animations are enabled, you will see this called twice. First, with
     * state.isPreLayout() returning true to lay out children in their initial conditions.
     * Then again to lay out children in their final locations.
     */
    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        //We have nothing to show for an empty data set but clear any existing views
        if (itemCount == 0) {
            detachAndScrapAttachedViews(recycler)
            return
        }
        if (childCount == 0 && state.isPreLayout) {
            //Nothing to do during prelayout when empty
            return
        }

        //Clear change tracking state when a real layout occurs
        if (!state.isPreLayout) {
            mChangedPositionCount = 0
            mFirstChangedPosition = mChangedPositionCount
        }

        if (childCount == 0) { //First or empty layout
            //Scrap measure one child
            val scrap = recycler.getViewForPosition(0)
            addView(scrap)
            measureChildWithMargins(scrap, 0, 0)

            /*
             * We make some assumptions in this code based on every child
             * view being the same size (i.e. a uniform grid). This allows
             * us to compute the following values up front because they
             * won't change.
             */
            mDecoratedChildWidth = getDecoratedMeasuredWidth(scrap)
            mDecoratedChildHeight = getDecoratedMeasuredHeight(scrap)

            detachAndScrapView(scrap, recycler)
        }

        //Always update the visible row/column counts
        updateWindowSizing()

        var removedCache: SparseIntArray? = null
        /*
         * During pre-layout, we need to take note of any views that are
         * being removed in order to handle predictive animations
         */
        if (state.isPreLayout) {
            removedCache = SparseIntArray(childCount)
            for (i in 0 until childCount) {
                val view = getChildAt(i)
                val lp = view!!.layoutParams as LayoutParams

                if (lp.isItemRemoved) {
                    //Track these view removals as visible
                    removedCache.put(lp.viewLayoutPosition, REMOVE_VISIBLE)
                }
            }

            //Track view removals that happened out of bounds (i.e. off-screen)
            if (removedCache.size() == 0 && mChangedPositionCount > 0) {
                for (i in mFirstChangedPosition until mFirstChangedPosition + mChangedPositionCount) {
                    removedCache.put(i, REMOVE_INVISIBLE)
                }
            }
        }


        var childLeft: Int
        var childTop: Int
        if (childCount == 0) { //First or empty layout
            //Reset the visible and scroll positions
            mFirstVisiblePosition = 0
            childLeft = paddingLeft
            childTop = paddingTop
        } else if (!state.isPreLayout && visibleChildCount >= state.itemCount) {
            //Data set is too small to scroll fully, just reset position
            mFirstVisiblePosition = 0
            childLeft = paddingLeft
            childTop = paddingTop
        } else { //Adapter data set changes
            /*
             * Keep the existing initial position, and save off
             * the current scrolled offset.
             */
            val topChild = getChildAt(0)
            childLeft = getDecoratedLeft(topChild!!)
            childTop = getDecoratedTop(topChild)

            /*
             * When data set is too small to scroll vertically, adjust vertical offset
             * and shift position to the first row, preserving current column
             */
            if (!state.isPreLayout && verticalSpace > totalRowCount * mDecoratedChildHeight) {
                mFirstVisiblePosition %= totalColumnCount
                childTop = paddingTop

                //If the shift overscrolls the column max, back it off
                if (mFirstVisiblePosition + mVisibleColumnCount > state.itemCount) {
                    mFirstVisiblePosition = Math.max(state.itemCount - mVisibleColumnCount, 0)
                    childLeft = paddingLeft
                }
            }

            /*
             * Adjust the visible position if out of bounds in the
             * new layout. This occurs when the new item count in an adapter
             * is much smaller than it was before, and you are scrolled to
             * a location where no items would exist.
             */
            val maxFirstRow = totalRowCount - (mVisibleRowCount - 1)
            val maxFirstCol = totalColumnCount - (mVisibleColumnCount - 1)
            val isOutOfRowBounds = firstVisibleRow > maxFirstRow
            val isOutOfColBounds = firstVisibleColumn > maxFirstCol
            if (isOutOfRowBounds || isOutOfColBounds) {
                val firstRow = when (isOutOfRowBounds) {
                    true -> maxFirstRow
                    else -> firstVisibleRow
                }
                val firstCol = when (isOutOfColBounds) {
                    true -> maxFirstCol
                    else -> firstVisibleColumn
                }
                mFirstVisiblePosition = firstRow * totalColumnCount + firstCol

                childLeft = horizontalSpace - mDecoratedChildWidth * mVisibleColumnCount
                childTop = verticalSpace - mDecoratedChildHeight * mVisibleRowCount

                //Correct cases where shifting to the bottom-right over-scrolls the top-left
                // This happens on data sets too small to scroll in a direction.
                if (firstVisibleRow == 0) {
                    childTop = Math.min(childTop, paddingTop)
                }
                if (firstVisibleColumn == 0) {
                    childLeft = Math.min(childLeft, paddingLeft)
                }
            }
        }

        //Clear all attached views into the recycle bin
        detachAndScrapAttachedViews(recycler)

        //Fill the grid for the initial layout of views
        fillGrid(childLeft, childTop, recycler, state, removedCache)

        //Evaluate any disappearing views that may exist
        if (!state.isPreLayout && !recycler.scrapList.isEmpty()) {
            val scrapList = recycler.scrapList
            val disappearingViews = HashSet<View>(scrapList.size)

            for (holder in scrapList) {
                val child = holder.itemView
                val lp = child.layoutParams as LayoutParams
                if (!lp.isItemRemoved) {
                    disappearingViews.add(child)
                }
            }

            for (child in disappearingViews) {
                layoutDisappearingView(child)
            }
        }
    }

    override fun onAdapterChanged(oldAdapter: RecyclerView.Adapter<*>?, newAdapter: RecyclerView.Adapter<*>?) {
        //Completely scrap the existing layout
        removeAllViews()
    }

    /*
     * Rather than continuously checking how many views we can fit
     * based on scroll offsets, we simplify the math by computing the
     * visible grid as what will initially fit on screen, plus one.
     */
    private fun updateWindowSizing() {
        mVisibleColumnCount = horizontalSpace / mDecoratedChildWidth + 1
        if (horizontalSpace % mDecoratedChildWidth > 0) {
            mVisibleColumnCount++
        }

        //Allow minimum value for small data sets
        if (mVisibleColumnCount > totalColumnCount) {
            mVisibleColumnCount = totalColumnCount
        }


        mVisibleRowCount = verticalSpace / mDecoratedChildHeight + 1
        if (verticalSpace % mDecoratedChildHeight > 0) {
            mVisibleRowCount++
        }

        if (mVisibleRowCount > totalRowCount) {
            mVisibleRowCount = totalRowCount
        }
    }

    private fun fillGrid(emptyLeft: Int, emptyTop: Int,
                         recycler: RecyclerView.Recycler,
                         state: RecyclerView.State,
                         removedPositions: SparseIntArray?) {
        if (mFirstVisiblePosition < 0) mFirstVisiblePosition = 0
        if (mFirstVisiblePosition >= itemCount) mFirstVisiblePosition = itemCount - 1


        /*
         * First, we will detach all existing views from the layout.
         * detachView() is a lightweight operation that we can use to
         * quickly reorder views without a full add/remove.
         */
        val viewCache = SparseArray<View>(childCount)
        var startLeftOffset = emptyLeft
        var startTopOffset = emptyTop
        if (childCount != 0) {
            val topView = getChildAt(0)
            startLeftOffset = getDecoratedLeft(topView!!)
            startTopOffset = getDecoratedTop(topView)

            //Cache all views by their existing position, before updating counts
            for (i in 0 until childCount) {
                val position = positionOfIndex(i)
                val child = getChildAt(i)
                viewCache.put(position, child)
            }

            //Temporarily detach all views.
            // Views we still need will be added back at the proper index.
            for (i in 0 until viewCache.size()) {
                detachView(viewCache.valueAt(i))
            }
        }

        /*
         * Next, we supply the grid of items that are deemed visible.
         * If these items were previously there, they will simply be
         * re-attached. New views that must be created are obtained
         * from the Recycler and added.
         */
        var leftOffset = startLeftOffset
        var topOffset = startTopOffset

        for (i in 0 until visibleChildCount) {
            var nextPosition = positionOfIndex(i)

            /*
             * When a removal happens out of bounds, the pre-layout positions of items
             * after the removal are shifted to their final positions ahead of schedule.
             * We have to track off-screen removals and shift those positions back
             * so we can properly lay out all current (and appearing) views in their
             * initial locations.
             */
            var offsetPositionDelta = 0
            if (state.isPreLayout) {
                var offsetPosition = nextPosition

                for (offset in 0 until (removedPositions?.size() ?: 0)) {
                    //Look for off-screen removals that are less-than this
                    if (removedPositions?.valueAt(offset) == REMOVE_INVISIBLE
                            && removedPositions.keyAt(offset) < nextPosition) {
                        //Offset position to match
                        offsetPosition--
                    }
                }
                offsetPositionDelta = nextPosition - offsetPosition
                nextPosition = offsetPosition
            }

            if (nextPosition < 0 || nextPosition >= state.itemCount) {
                //Item space beyond the data set, don't attempt to add a view
                continue
            }

            //Layout this position
            var view = viewCache.get(nextPosition)

            if (view == null) {
                /*
                 * The Recycler will give us either a newly constructed view,
                 * or a recycled view it has on-hand. In either case, the
                 * view will already be fully bound to the data by the
                 * adapter for us.
                 */
                view = recycler.getViewForPosition(nextPosition)
                addView(view)

                /*
                 * Update the new view's metadata, but only when this is a real
                 * layout pass.
                 */
                if (!state.isPreLayout) {
                    val lp = view.layoutParams as LayoutParams
                    lp.row = getGlobalRowOfPosition(nextPosition)
                    lp.column = getGlobalColumnOfPosition(nextPosition)
                }

                /*
                 * It is prudent to measure/layout each new view we
                 * receive from the Recycler. We don't have to do
                 * this for views we are just re-arranging.
                 */
                measureChildWithMargins(view, 0, 0)
                layoutDecorated(view, leftOffset, topOffset,
                        leftOffset + mDecoratedChildWidth,
                        topOffset + mDecoratedChildHeight)
            } else {
                //Re-attach the cached view at its new index
                attachView(view)
                viewCache.remove(nextPosition)
            }

            if (i % mVisibleColumnCount == (mVisibleColumnCount - 1)) {
                leftOffset = startLeftOffset
                topOffset += mDecoratedChildHeight

                //During pre-layout, on each column end, apply any additional appearing views
                if (state.isPreLayout) {
                    layoutAppearingViews(recycler, view, nextPosition, removedPositions?.size() ?: 0, offsetPositionDelta)
                }
            } else {
                leftOffset += mDecoratedChildWidth
            }
        }

        /*
         * Finally, we ask the Recycler to scrap and store any views
         * that we did not re-attach. These are views that are not currently
         * necessary because they are no longer visible.
         */
        for (i in 0 until viewCache.size()) {
            val removingView = viewCache.valueAt(i)
            recycler.recycleView(removingView)
        }

    }


    /*
     * You must override this method if you would like to support external calls
     * to shift the view to a given adapter position. In our implementation, this
     * is the same as doing a fresh layout with the given position as the top-left
     * (or first visible), so we simply set that value and trigger onLayoutChildren()
     */
    override fun scrollToPosition(position: Int) {
        if (position >= itemCount) {
            Log.e(TAG, "Cannot scroll to $position, item count is $itemCount")
            return
        }

        //Set requested position as first visible
        mFirstVisiblePosition = position
        //Toss all existing views away
        removeAllViews()
        //Trigger a new view layout
        requestLayout()
    }

    /*
      * You must override this method if you would like to support external calls
      * to animate a change to a new adapter position. The framework provides a
      * helper scroller implementation (LinearSmoothScroller), which we leverage
      * to do the animation calculations.
      */
    override fun smoothScrollToPosition(recyclerView: RecyclerView?, state: RecyclerView.State?, position: Int) {
        if (position >= itemCount) {
            Log.e(TAG, "Cannot scroll to $position, item count is $itemCount")
            return
        }

        /*
         * LinearSmoothScroller's default behavior is to scroll the contents until
         * the child is fully visible. It will snap to the top-left or bottom-right
         * of the parent depending on whether the direction of travel was positive
         * or negative.
         */
        val scroller = LinearSmoothScroller(recyclerView!!.context)
        scroller.targetPosition = position
        startSmoothScroll(scroller)
    }

    override fun computeScrollVectorForPosition(targetPosition: Int): PointF? {
        if (childCount == 0) {
            return null
        }
        val firstChildRow = getGlobalRowOfPosition(0)
        val firstChildColumn = getGlobalColumnOfPosition(0)

        val targetRow = getGlobalRowOfPosition(targetPosition)
        val targetColumn = getGlobalColumnOfPosition(targetPosition)

        val directionX = if (targetColumn < firstChildColumn) -1F else 1F
        val directionY = if (targetRow < firstChildRow) -1F else 1F

        return PointF(directionX, directionY)
    }

    private fun fillColumns(recycler: RecyclerView.Recycler, state: RecyclerView.State): Int {

        val startView = getChildAt(0)
        val endView = getChildAt(mVisibleColumnCount - 1)
        val startLeftOffset: Int
        if (startView == null || endView == null) {
            //What? we can't scroll if there are no views.
            Log.d(TAG, "fillColumns could not find either a start or end view.")
            return 0
        }

        if (layoutState.fillDirection == DIRECTION_END) {
            startLeftOffset = getDecoratedRight(endView)
            layoutState.startPosition = mFirstVisiblePosition + mVisibleColumnCount
            layoutState.startColumn = lastVisibleColumn + 1
        } else { //DIRECTION_START
            startLeftOffset = getDecoratedLeft(startView) + ((layoutState.numberOfColumnsToFill * mDecoratedChildWidth) * -1)
            layoutState.startPosition = Math.max(mFirstVisiblePosition - layoutState.numberOfColumnsToFill, 0)
            layoutState.startColumn = Math.max(firstVisibleColumn - layoutState.numberOfColumnsToFill, 0)
        }

        layoutState.startOffset = startLeftOffset
        layoutState.topOffset = getDecoratedTop(startView)
        layoutState.currentColumn = 0
        layoutState.currentRow = 0

        var consumedSpace = 0

        while ((layoutState.currentRow < layoutState.numberOfRowsToFill
                && layoutState.currentColumn < layoutState.numberOfColumnsToFill)
                && layoutState.canFillMoreCells(state)) {

            layoutChunkResult.reset()
            layoutChunk(recycler, layoutChunkResult)

            if (layoutChunkResult.finished) {
                break
            }

            if (layoutChunkResult.layoutRow == 0) {
                consumedSpace += layoutChunkResult.consumedWidth
            }

            if (layoutState.currentColumn == 0) {
                layoutState.startOffset = startLeftOffset
                layoutState.topOffset += layoutChunkResult.consumedHeight
            } else {
                layoutState.startOffset += layoutChunkResult.consumedWidth
            }

        }

        return consumedSpace
    }

    /*
     * Use this method to tell the RecyclerView if scrolling is even possible
     * in the horizontal direction.
     */
    override fun canScrollHorizontally(): Boolean {
        //We do allow scrolling
        return true
    }

    /*
     * This method describes how far RecyclerView thinks the contents should scroll horizontally.
     * You are responsible for verifying edge boundaries, and determining if this scroll
     * event somehow requires that new views be added or old views get recycled.
     */
    override fun scrollHorizontallyBy(dx: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): Int {
        if (childCount == 0 || dx == 0) {
            return 0
        }

        val direction = if (dx > 0) 1 else -1
        val absDx = Math.abs(dx)

        //Take leftmost measurements from the top-left child
        val startView = getChildAt(0)
        //Take rightmost measurements from the top-right child
        val endView = getChildAt(mVisibleColumnCount - 1)

        //Optimize the case where the entire data set is too small to scroll
        val viewSpan = getDecoratedRight(endView!!) - getDecoratedLeft(startView!!)
        if (viewSpan < horizontalSpace) {
            //We cannot scroll in either direction
            Log.d(TAG, "Can't scroll horizontally")
            return 0
        }

        // There is likely some amount of space that we could already
        // scroll without needing to create/layout any new views.
        // For example, when the view at the end of the screen is only
        // partially in view, we could scroll to the end of that view
        // without having to to do anything.
        // prefilledSpace holds that amount
        val prefilledSpace: Int

        if (dx > 0) {
            layoutState.fillDirection = DIRECTION_END
            prefilledSpace = getDecoratedRight(endView) - width - paddingRight
        } else {
            layoutState.fillDirection = DIRECTION_START
            prefilledSpace = -getDecoratedLeft(startView) + paddingLeft
        }

        val spaceToFill = Math.max(absDx - prefilledSpace, 0)

        // Figure out how many extra columns we'll need,
        // in order to fill out the "spaceToFill".
        // Make sure that we don't create more columns than
        // totalColumnCount
        var columnsToAdd = spaceToFill / mDecoratedChildWidth
        if (spaceToFill > 0 && spaceToFill % mDecoratedChildWidth != 0) {
            columnsToAdd++
        }

        if (dx > 0) {
            if (lastVisibleColumn + columnsToAdd >= totalColumnCount) {
                columnsToAdd = totalColumnCount - lastVisibleColumn - 1
            }
        } else if (dx < 0) {
            if (firstVisibleColumn - columnsToAdd < 0) {
                columnsToAdd = firstVisibleColumn
            }
        }

        val consumed = when (columnsToAdd < 1) {
            true -> prefilledSpace
            else -> {
                layoutState.numberOfColumnsToFill = columnsToAdd
                layoutState.numberOfRowsToFill = mVisibleRowCount

                prefilledSpace + fillColumns(recycler, state)
            }
        }

        val scrolled = if (absDx > consumed) consumed * direction else dx
        offsetChildrenHorizontal(-scrolled)

        if (columnsToAdd > 0) {
            recycleByLayoutState(recycler)
        }

        val child = getChildAt(0)
        mFirstVisiblePosition = when (child) {
            null -> -1
            else -> {
                val lp = child.layoutParams as LayoutParams
                (lp.row * totalColumnCount) + lp.column
            }
        }

        return scrolled
    }

    private fun fillRows(recycler: RecyclerView.Recycler, state: RecyclerView.State): Int {
        val topView = getChildAt(0)
        val bottomView = getChildAt(childCount - 1)
        val startTopOffset: Int

        if (topView == null || bottomView == null) {
            Log.d(TAG, "fillRows could not find either a top or bottom view.")
            return 0
        }

        if (layoutState.fillDirection == DIRECTION_DOWN) {
            startTopOffset = getDecoratedBottom(bottomView)
            layoutState.startPosition = mFirstVisiblePosition + (mVisibleRowCount * totalColumnCount)
            layoutState.startRow = lastVisibleRow + 1
        } else { //DIRECTION_UP
            startTopOffset = getDecoratedTop(topView) - layoutState.numberOfRowsToFill * mDecoratedChildHeight
            layoutState.startPosition = Math.max(mFirstVisiblePosition - (layoutState.numberOfRowsToFill * totalColumnCount) , 0)
            layoutState.startRow= Math.max(firstVisibleRow - (layoutState.numberOfRowsToFill * totalColumnCount), 0)
        }

        layoutState.topOffset = startTopOffset
        layoutState.startOffset = getDecoratedLeft(topView)
        layoutState.currentColumn = 0
        layoutState.currentRow = 0

        var consumedSpace = 0

        while ((layoutState.currentRow < layoutState.numberOfRowsToFill
                        && layoutState.currentColumn < layoutState.numberOfColumnsToFill)
                && layoutState.canFillMoreCells(state)) {

            layoutChunkResult.reset()
            layoutChunk(recycler, layoutChunkResult)

            if (layoutChunkResult.finished) {
                break
            }

            if (layoutChunkResult.layoutColumn == 0) {
                consumedSpace += layoutChunkResult.consumedHeight
            }

            if (layoutState.currentColumn == 0) {
                layoutState.startOffset = getDecoratedLeft(topView)
                layoutState.topOffset += layoutChunkResult.consumedHeight
            } else {
                layoutState.startOffset += layoutChunkResult.consumedWidth
            }

        }

        return consumedSpace
    }

    /*
     * Use this method to tell the RecyclerView if scrolling is even possible
     * in the vertical direction.
     */
    override fun canScrollVertically(): Boolean {
        //We do allow scrolling
        return true
    }

    /*
     * This method describes how far RecyclerView thinks the contents should scroll vertically.
     * You are responsible for verifying edge boundaries, and determining if this scroll
     * event somehow requires that new views be added or old views get recycled.
     */
    override fun scrollVerticallyBy(dy: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): Int {
        if (childCount == 0) {
            return 0
        }

        val direction = if (dy > 0) 1 else -1
        val absDy = Math.abs(dy)

        //Take top measurements from the top-left child
        val topView = getChildAt(0)
        //Take bottom measurements from the bottom-right child.
        val bottomView = getChildAt(childCount - 1)

        //Optimize the case where the entire data set is too small to scroll
        val viewSpan = getDecoratedBottom(bottomView!!) - getDecoratedTop(topView!!)
        if (viewSpan < verticalSpace) {
            //We cannot scroll in either direction
            Log.d(TAG, "Can't scroll vertially")
            return 0
        }

        // There is likely some amount of space that we could already
        // scroll without needing to create/layout any new views.
        // For example, when the view at the bottom of the screen is only
        // partially in view, we could scroll to the end of that view
        // without having to to do anything.
        // prefilledSpace holds that amount
        val prefilledSpace: Int

        if (dy > 0) {
            layoutState.fillDirection = DIRECTION_DOWN
            prefilledSpace = getDecoratedBottom(bottomView) - height - paddingBottom
        } else {
            layoutState.fillDirection = DIRECTION_UP
            prefilledSpace = -getDecoratedTop(topView) + paddingTop
        }

        val spaceToFill = Math.max(absDy - prefilledSpace, 0)

        var rowsToAdd = spaceToFill / mDecoratedChildHeight
        if (spaceToFill > 0 && spaceToFill % mDecoratedChildHeight != 0) {
            rowsToAdd += 1
        }

        if (dy > 0) {
            if (lastVisibleRow + rowsToAdd >= totalRowCount) {
                rowsToAdd = totalRowCount - lastVisibleRow - 1
            }
        } else if (dy < 0) {
            if (firstVisibleRow - rowsToAdd < 0) {
                rowsToAdd = firstVisibleRow
            }
        }

        val consumed = when (rowsToAdd < 1) {
            true -> prefilledSpace
            else -> {
                layoutState.numberOfRowsToFill = rowsToAdd
                layoutState.numberOfColumnsToFill = mVisibleColumnCount
                prefilledSpace + fillRows(recycler, state)
            }
        }
        val scrolled = if (absDy > consumed) consumed * direction else dy
        offsetChildrenVertical(-scrolled)

        if (rowsToAdd > 0) {
            recycleByLayoutState(recycler)
        }

        val child = getChildAt(0)
        mFirstVisiblePosition = when(child) {
            null -> -1
            else -> {
                val lp = child.layoutParams as LayoutParams
                (lp.row * totalColumnCount) + lp.column
            }
        }

        return scrolled
    }


    private fun layoutChunk(recycler: RecyclerView.Recycler, result: FixedGridLayoutManager.LayoutChunkResult) {

        //Keep a reference to some of the "current"
        //properties. When we get the nextView from
        //the layoutState, it'll advance these, and
        //we'll need the current values later.
        val currentPosition = layoutState.currentPosition
        val currentColumn = layoutState.currentColumn
        val currentRow = layoutState.currentRow

        val (view, index) = layoutState.nextView(recycler)

        if (view == null) {
            result.finished = true
            return
        }

        addView(view, index)

        val lp = view.layoutParams as LayoutParams
        lp.row = getGlobalRowOfPosition(currentPosition)
        lp.column = getGlobalColumnOfPosition(currentPosition)

        /*
         * It is prudent to measure/layout each new view we
         * receive from the Recycler. We don't have to do
         * this for views we are just re-arranging.
         */
        measureChildWithMargins(view, 0, 0)
        val left: Int = layoutState.startOffset
        val right: Int = layoutState.startOffset + mDecoratedChildWidth
        val top: Int = layoutState.topOffset
        val bottom: Int = layoutState.topOffset + mDecoratedChildHeight

        layoutDecoratedWithMargins(view, left, top, right, bottom)
        result.consumedWidth = right - left
        result.consumedHeight = bottom - top
        result.viewTop = getDecoratedTop(view)
        result.viewLeft = getDecoratedLeft(view)
        result.layoutColumn = currentColumn
        result.layoutRow = currentRow
    }

    private fun recycleByLayoutState(recycler: RecyclerView.Recycler) {
        layoutState.startColumn = layoutState.numberOfColumnsToFill - 1
        layoutState.currentColumn = layoutState.startColumn
        layoutState.currentRow = layoutState.numberOfRowsToFill - 1

        val totalColumns = mVisibleColumnCount + layoutState.numberOfColumnsToFill

        //TODO Handle the case where we ran out of positions and didn't fill the last (or a given) row,
        // or perhaps we created less than numberOfColumnsToFill columns....
        // This is really only applicable for DIRECTION_START

        views@
        while (layoutState.currentRow >= 0 && layoutState.currentColumn >= 0) {
            val viewIndex = when (layoutState.fillDirection) {
                DIRECTION_END -> (layoutState.currentRow * totalColumns) + layoutState.currentColumn
                DIRECTION_START -> mVisibleColumnCount + layoutState.currentColumn + (layoutState.currentRow * totalColumns)
                DIRECTION_DOWN -> (layoutState.currentRow * mVisibleColumnCount) + layoutState.currentColumn
                DIRECTION_UP -> ((mVisibleRowCount + layoutState.currentRow) * mVisibleColumnCount) + layoutState.currentColumn
                else -> break@views
            }
            val view = getChildAt(viewIndex)
            if (view != null) {
                removeAndRecycleView(view, recycler)
            }
            if (layoutState.currentColumn == 0) {
                layoutState.currentColumn = layoutState.startColumn
                layoutState.currentRow -= 1
            } else {
                layoutState.currentColumn -= 1
            }
        }
    }

    /*
     * This is a helper method used by RecyclerView to determine
     * if a specific child view can be returned.
     */
    override fun findViewByPosition(position: Int): View? {
        for (i in 0 until childCount) {
            if (positionOfIndex(i) == position) {
                return getChildAt(i)
            }
        }

        return null
    }

    /** Boilerplate to extend LayoutParams for tracking row/column of attached views  */

    /*
     * Even without extending LayoutParams, we must override this method
     * to provide the default layout parameters that each child view
     * will receive when added.
     */
    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams {
        return LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun generateLayoutParams(c: Context, attrs: AttributeSet): RecyclerView.LayoutParams {
        return LayoutParams(c, attrs)
    }

    override fun generateLayoutParams(lp: ViewGroup.LayoutParams): RecyclerView.LayoutParams {
        return if (lp is ViewGroup.MarginLayoutParams) {
            LayoutParams(lp)
        } else {
            LayoutParams(lp)
        }
    }

    override fun checkLayoutParams(lp: RecyclerView.LayoutParams?): Boolean {
        return lp is LayoutParams
    }

    class LayoutParams : RecyclerView.LayoutParams {

        //Current row in the grid
        var row: Int = 0
        //Current column in the grid
        var column: Int = 0

        constructor(c: Context, attrs: AttributeSet) : super(c, attrs)
        constructor(width: Int, height: Int) : super(width, height)
        constructor(source: ViewGroup.MarginLayoutParams) : super(source)
        constructor(source: ViewGroup.LayoutParams) : super(source)
        constructor(source: RecyclerView.LayoutParams) : super(source)
    }

    /** Animation Layout Helpers  */

    /* Helper to obtain and place extra appearing views */
    private fun layoutAppearingViews(recycler: RecyclerView.Recycler, referenceView: View, referencePosition: Int, extraCount: Int, offset: Int) {
        //Nothing to do...
        if (extraCount < 1) return

        //FIXME: This code currently causes double layout of views that are still visible…
        for (extra in 1..extraCount) {
            //Grab the next position after the reference
            val extraPosition = referencePosition + extra
            if (extraPosition < 0 || extraPosition >= itemCount) {
                //Can't do anything with this
                continue
            }

            /*
             * Obtain additional position views that we expect to appear
             * as part of the animation.
             */
            val appearing = recycler.getViewForPosition(extraPosition)
            addView(appearing)

            //Find layout delta from reference position
            val newRow = getGlobalRowOfPosition(extraPosition + offset)
            val rowDelta = newRow - getGlobalRowOfPosition(referencePosition + offset)
            val newCol = getGlobalColumnOfPosition(extraPosition + offset)
            val colDelta = newCol - getGlobalColumnOfPosition(referencePosition + offset)

            layoutTempChildView(appearing, rowDelta, colDelta, referenceView)
        }
    }

    /* Helper to place a disappearing view */
    private fun layoutDisappearingView(disappearingChild: View) {
        /*
         * LayoutManager has a special method for attaching views that
         * will only be around long enough to animate.
         */
        addDisappearingView(disappearingChild)

        //Adjust each disappearing view to its proper place
        val lp = disappearingChild.layoutParams as LayoutParams

        val newRow = getGlobalRowOfPosition(lp.viewAdapterPosition)
        val rowDelta = newRow - lp.row
        val newCol = getGlobalColumnOfPosition(lp.viewAdapterPosition)
        val colDelta = newCol - lp.column

        layoutTempChildView(disappearingChild, rowDelta, colDelta, disappearingChild)
    }


    /* Helper to lay out appearing/disappearing children */
    private fun layoutTempChildView(child: View, rowDelta: Int, colDelta: Int, referenceView: View) {
        //Set the layout position to the global row/column difference from the reference view
        val layoutTop = getDecoratedTop(referenceView) + rowDelta * mDecoratedChildHeight
        val layoutLeft = getDecoratedLeft(referenceView) + colDelta * mDecoratedChildWidth

        measureChildWithMargins(child, 0, 0)
        layoutDecorated(child, layoutLeft, layoutTop,
                layoutLeft + mDecoratedChildWidth,
                layoutTop + mDecoratedChildHeight)
    }


    private class LayoutChunkResult {
        var consumedWidth: Int = 0
        var consumedHeight: Int = 0
        var finished: Boolean = false
        var ignoreConsumed: Boolean = false
        var viewTop: Int = 0
        var viewLeft: Int = 0
        var layoutRow: Int = 0
        var layoutColumn: Int = 0

        internal fun reset() {
            consumedWidth = 0
            consumedHeight = 0
            finished = false
            ignoreConsumed = false
            viewTop = 0
            viewLeft = 0
            layoutRow = 0
            layoutColumn = 0
        }
    }

    private inner class GridLayoutState {
        var currentRow: Int = 0
        var currentColumn: Int = 0
        var startOffset: Int = 0
        var topOffset: Int = 0
        var fillDirection: Int = DIRECTION_NONE
        var startPosition: Int = 0
        var startColumn: Int = 0
        var startRow: Int = 0
        var numberOfColumnsToFill: Int = 0
        var numberOfRowsToFill: Int = 0

        fun canFillMoreCells(state: RecyclerView.State): Boolean {
            return (currentPosition <= state.itemCount - 1)
        }

        val currentPosition: Int
            get() = getPositionForCell(currentRow, currentColumn)

        fun getPositionForCell(row: Int, column: Int): Int {
            return startPosition + column + (row * totalColumnCount)
        }

        fun nextView(recycler: RecyclerView.Recycler): Pair<View?, Int> {
            val position = currentPosition

            val view = recycler.getViewForPosition(position)

            val index = when (fillDirection) {
                DIRECTION_END -> mVisibleColumnCount + currentColumn + (currentRow * (mVisibleColumnCount + numberOfColumnsToFill))
                DIRECTION_START -> (currentRow * (mVisibleColumnCount + numberOfColumnsToFill)) + currentColumn
                DIRECTION_DOWN -> ((mVisibleRowCount + currentRow) * mVisibleColumnCount) + currentColumn
                DIRECTION_UP -> (currentRow * mVisibleColumnCount) + currentColumn
                else -> 0 // What? - This should never happen
            }

            currentColumn += 1
            if (currentColumn >= numberOfColumnsToFill) {
                currentColumn = 0
                currentRow += 1
            }

            return Pair(view, index)
        }

    }


    /**
     * This guy is good to have around if you are ever
     * changing things and need to debug the
     * currently displayed grid of views.
     */
    private fun logViews(msg: String? = null) {
        Log.d(TAG, "===================================")
        if (msg != null) {
            Log.d(TAG, msg)
        }

        Log.d(TAG, "total views: $childCount")
        Log.d(TAG, "visible cols: $mVisibleColumnCount, visible rows: $mVisibleRowCount")

        var i = 0
        while (i < childCount) {
            var row = ""
            var child = getChildAt(i)
            child ?: continue

            row += logView(child, i)

            val top = getDecoratedTop(child)
            var newTop = top
            var nextIndex = i+1

            do {
                child = getChildAt(nextIndex)
                if (child != null) {
                    newTop = getDecoratedTop(child)
                    if (newTop == top) {
                        row += logView(child, nextIndex)
                        nextIndex++
                    }
                }

                if (child == null) {
                    break
                }
            } while (newTop == top)

            i = nextIndex
            Log.d(TAG, row)
        }
    }

    private fun logView(view: View, index: Int): String {
        val top = getDecoratedTop(view)
        val left = getDecoratedLeft(view)
        val lp = view.layoutParams as LayoutParams
        return "[$index, $top, $left, ${lp.viewLayoutPosition}] "
    }



}
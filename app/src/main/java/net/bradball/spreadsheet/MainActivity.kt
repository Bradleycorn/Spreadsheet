package net.bradball.spreadsheet

import android.hardware.SensorManager.getOrientation
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.GestureDetectorCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main.view.*
import kotlinx.android.synthetic.main.vh_header_item.view.*
import net.bradball.spreadsheet2.SpreadsheetActivity


class MainActivity : AppCompatActivity() {

    private val data = mapOf(
            1 to 1.25,
            2 to 2.25,
            3 to 3.25,
            4 to 4.25,
            5 to 5.25,
            6 to 6.25,
            7 to 7.25,
            8 to 8.25,
            9 to 9.25,
            10 to 10.25,
            11 to 11.25,
            12 to 12.25,
            13 to 13.25,
            14 to 14.25,
            15 to 15.25,
            16 to 16.25,
            17 to 17.25,
            18 to 18.25,
            19 to 19.25,
            20 to 20.25,
            21 to 21.25,
            22 to 22.25,
            23 to 23.25,
            24 to 24.25,
            25 to 25.25,
            26 to 26.25,
            27 to 27.25,
            28 to 28.25,
            29 to 29.25,
            30 to 30.25,
            31 to 31.25,
            32 to 32.25,
            33 to 33.25,
            34 to 34.25,
            35 to 35.25,
            36 to 36.25,
            37 to 37.25,
            38 to 38.25,
            39 to 39.25
    )
    private val TAG = "ScrollLogs"
    private val GTAG = "GLog"

    val topHeaderScrollListener = object: SelfRemovingRecyclerScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            Log.d(TAG, "Top Header Scrolled")
            main_content_list.scrollBy(dx, 0)
        }
    }

    val sideHeaderScrollListener = object: SelfRemovingRecyclerScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            Log.d(TAG, "Side Header Scrolled")
            main_content_list.scrollBy(0, dy)
        }
    }

    val mainContentScrollListener = object: SelfRemovingRecyclerScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            Log.d(TAG, "Main Content Scrolled")
            top_header_list.scrollBy(dx, 0)
            side_header_list.scrollBy(0, dy)
        }
    }

    val mainContentFlingListener = object: RecyclerView.OnFlingListener() {
        override fun onFling(velocityX: Int, velocityY: Int): Boolean {
            Log.d(TAG, "Main Content Flinged $velocityX , $velocityY")
            main_content_list.removeOnScrollListener(mainContentScrollListener)
            top_header_list.onFlingListener = null
            side_header_list.onFlingListener = null
            top_header_list.fling(velocityX, 0)
            side_header_list.fling(0, velocityY)
            return false
        }
    }

    val topHeaderFlingListener = object: RecyclerView.OnFlingListener() {
        override fun onFling(velocityX: Int, velocityY: Int): Boolean {
            Log.d(TAG, "Top Header Flinged")
            top_header_list.removeOnScrollListener(topHeaderScrollListener)
            main_content_list.onFlingListener = null
            main_content_list.fling(velocityX, 0)
            return false
        }
    }

    val sideHeaderFlingListener = object: RecyclerView.OnFlingListener() {
        override fun onFling(velocityX: Int, velocityY: Int): Boolean {
            Log.d(TAG, "Side Header Flinged")
            side_header_list.removeOnScrollListener(topHeaderScrollListener)
            main_content_list.onFlingListener = null
            main_content_list.fling(0, velocityY)
            return false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        title = "Custom LayoutManager"

        val headerItems = data.keys.toList()
        val contentItems = data.values.toList()

        top_header_list.adapter = HeaderAdapter(headerItems)
        top_header_list.layoutManager = MyLinearLayoutManager(this, RecyclerView.HORIZONTAL)

        side_header_list.adapter = HeaderAdapter(headerItems)

        val mgr = FixedGridLayoutManager()
        mgr.totalColumnCount = headerItems.size

        main_content_list.layoutManager = mgr
        main_content_list.adapter = MainContentAdapter(contentItems)

        allLists = listOf(top_header_list, side_header_list, main_content_list)

        top_header_list.addOnItemTouchListener(touchListener)
        side_header_list.addOnItemTouchListener(touchListener)
        main_content_list.addOnItemTouchListener(touchListener)
    }

    private lateinit var allLists: List<RecyclerView>

    private val touchListener = object: RecyclerView.SimpleOnItemTouchListener() {
        private var lastX: Int = 0
        private var lastY: Int = 0

        private fun getOrientation(rv: RecyclerView): Int {
            return when (rv.layoutManager) {
                is LinearLayoutManager -> {
                    (rv.layoutManager as LinearLayoutManager).orientation
                }
                else -> return -1
            }
        }

        private fun positionSettled(rv: RecyclerView): Boolean {
            val orientation = getOrientation(rv)

            return when (orientation) {
                RecyclerView.VERTICAL -> lastY == rv.scrollY
                RecyclerView.HORIZONTAL -> lastX == rv.scrollX
                else -> {
                    lastY == rv.scrollY &&
                    lastX == rv.scrollX
                }
            }
        }

        private fun areOthersIdle(rv: RecyclerView): Boolean {
            return allLists.fold(true) { idle, recycler ->
                when (recycler == rv) {
                    true -> idle
                    else -> idle && (recycler.scrollState == RecyclerView.SCROLL_STATE_IDLE)
                }
            }
        }

        override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {

            val list = when (rv) {
                top_header_list -> "Top Header"
                side_header_list -> "Side Header"
                main_content_list -> "Main Content"
                else -> ""
            }

            Log.d(TAG, "onInterceptTouchEvent: $list")
            if (rv.scrollState == RecyclerView.SCROLL_STATE_IDLE) {
                    onTouchEvent(rv, e)
            }

            if (!areOthersIdle(rv)) {
                return true
            } else {
                return false
            }

        }

        override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
            val orientation = getOrientation(rv)
            val action = e.action
            Log.d(TAG, "onTouchEvent: $orientation")

            if (action == MotionEvent.ACTION_DOWN && areOthersIdle(rv)) {
                Log.d(TAG, "Action Down")
                when (orientation) {
                    RecyclerView.VERTICAL -> lastY = rv.scrollY
                    RecyclerView.HORIZONTAL -> lastX = rv.scrollX
                    else -> {
                        lastY = rv.scrollY
                        lastX = rv.scrollX
                    }
                }

                when (rv) {
                    side_header_list -> {
                        rv.addOnScrollListener(sideHeaderScrollListener)
                    }
                    top_header_list -> {
                        rv.addOnScrollListener(topHeaderScrollListener)
                    }
                    main_content_list -> {
                        rv.addOnScrollListener(mainContentScrollListener)
                    }
                }
            } else if (action == MotionEvent.ACTION_UP && positionSettled(rv)) {
                Log.d(TAG, "Action Up")
                when (rv) {
                    side_header_list -> {
                        rv.removeOnScrollListener(sideHeaderScrollListener)
                    }
                    top_header_list -> {
                        rv.removeOnScrollListener(topHeaderScrollListener)
                    }
                    main_content_list -> {
                        rv.removeOnScrollListener(mainContentScrollListener)
                    }
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.options_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {

        return when (item?.itemId) {
            R.id.switch_activity -> {
                startActivity(SpreadsheetActivity.createIntent(this))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}


class HeaderAdapter(val items: List<Int>): RecyclerView.Adapter<ItemViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.vh_header_item, parent, false)
        return ItemViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val value = items[position]
        holder.bindView(value.toString())
    }
}


class MainContentAdapter(val items: List<Double>): RecyclerView.Adapter<ItemViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.vh_header_item, parent, false)
        return ItemViewHolder(view)
    }

    override fun getItemCount(): Int = items.size * items.size

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val value = items[position % items.size]
        holder.bindView(value.toString())
    }
}



class ItemViewHolder(val view: View): RecyclerView.ViewHolder(view) {
    fun bindView(value: String) {
        itemView.header_value.text = value
    }
}
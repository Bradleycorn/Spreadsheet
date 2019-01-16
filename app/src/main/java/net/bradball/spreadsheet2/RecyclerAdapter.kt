package net.bradball.spreadsheet2

import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.vh_recycler_item.view.*
import net.bradball.spreadsheet.R
import net.bradball.spreadsheet2.syncedRecycler.SyncedRecyclerView

class RecyclerAdapter(private var items: List<String>? = null): RecyclerView.Adapter<RecyclerItemViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.vh_recycler_item, parent, false)
        return RecyclerItemViewHolder(view)
    }

    override fun getItemCount(): Int = items?.size ?: 0

    override fun onBindViewHolder(holder: RecyclerItemViewHolder, position: Int) {
        val value = items?.get(position) ?: ""
        holder.bindView(value)
    }

    fun setItems(newList: List<String>) {
        items = newList
        notifyDataSetChanged()
    }
}

class RecyclerItemViewHolder(val view: View): RecyclerView.ViewHolder(view) {
    fun bindView(value: String) {
        itemView.item_value.text = value
    }
}

class MainContentAdapter(private var rows: List<List<Double>>, private val onViewBound: (view: SyncedRecyclerView) -> Unit): RecyclerView.Adapter<MainContentRowHolder>() {

    private val viewPool = RecyclerView.RecycledViewPool()
    private var layoutState: Parcelable? = null


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MainContentRowHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.vh_main_content_row, parent, false) as SyncedRecyclerView
        //view.setRecycledViewPool(viewPool)
        return MainContentRowHolder(view)
    }

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: MainContentRowHolder, position: Int) {

        if (holder.currentValue != null) {
            //currentLayoutState = holder.view.layoutManager?.onSaveInstanceState()
        }

        val row = rows[position]
        holder.view.tag = position
        holder.bindRow(row.map { it.toString() })
        onViewBound(holder.view)
    }

}

class MainContentRowHolder(val view: SyncedRecyclerView): RecyclerView.ViewHolder(view) {

    var currentValue: List<String>? = null
        private set

    fun bindRow(value: List<String>, layoutState: Parcelable? = null) {
        currentValue = value
        view.adapter = RecyclerAdapter(value)

       if (layoutState != null) {
           view.layoutManager?.onRestoreInstanceState(layoutState)
       }
    }
}
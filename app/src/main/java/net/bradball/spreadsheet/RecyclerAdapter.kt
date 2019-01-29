package net.bradball.spreadsheet

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.vh_grid_item.view.*
import kotlinx.android.synthetic.main.vh_header_item.view.*

class RecyclerAdapter(private var items: List<Int>): RecyclerView.Adapter<RecyclerItemViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerItemViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.vh_header_item, parent, false)
        return RecyclerItemViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerItemViewHolder, position: Int) {
        holder.bindView(items[position].toString())
    }

}

class RecyclerItemViewHolder(view: View): RecyclerView.ViewHolder(view) {
    fun bindView(value: String) {
        itemView.item_value.text = value
    }
}

class MainContentAdapter(private var items: List<String>): RecyclerView.Adapter<MainContentRowHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MainContentRowHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.vh_grid_item, parent, false)
        return MainContentRowHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: MainContentRowHolder, position: Int) {
        holder.bindView(items[position])
    }

}

class MainContentRowHolder(view: View): RecyclerView.ViewHolder(view) {

    fun bindView(value: String) {
        itemView.header_value.text = value
    }
}
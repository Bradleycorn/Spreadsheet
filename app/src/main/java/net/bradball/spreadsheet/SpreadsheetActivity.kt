package net.bradball.spreadsheet

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.activity_spreadsheet.*
import net.bradball.spreadsheet.syncedRecycler.SyncedRecyclerView

class SpreadsheetActivity : AppCompatActivity() {

    companion object {
        private val TAG = "SpreadsheetActivity"

        fun createIntent(context: Context): Intent = Intent(context, SpreadsheetActivity::class.java)
    }

    private val headers = listOf(1..48).flatten()
    private val data = headers.flatMap { row ->
        headers.map { col ->
            "$row,$col"
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_spreadsheet)
        title = "Spreadsheet View"

        column_headers.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        column_headers.adapter = RecyclerAdapter(headers)

        row_headers.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        row_headers.adapter = RecyclerAdapter(headers)

        main_content.layoutManager = FixedGridLayoutManager().apply {
            totalColumnCount = headers.size
        }
        main_content.adapter = MainContentAdapter(data)


        main_content.bindTo(column_headers, SyncedRecyclerView.ALIGN_ORIENTATION_HORIZONTAL)
        column_headers.bindTo(main_content, SyncedRecyclerView.ALIGN_ORIENTATION_HORIZONTAL)
        main_content.bindTo(row_headers, SyncedRecyclerView.ALIGN_ORIENTATION_VERTICAL)
        row_headers.bindTo(main_content, SyncedRecyclerView.ALIGN_ORIENTATION_VERTICAL)
    }

}

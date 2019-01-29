package net.bradball.spreadsheet.syncedRecycler


internal class SyncedRecyclerViewBinding(val from: SyncedRecyclerView, val to: SyncedRecyclerView, val orientation: Int) {

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false

        val binding = o as SyncedRecyclerViewBinding?

        return orientation == binding!!.orientation && from.equals(binding.from) && to.equals(binding.to)

    }

    override fun hashCode(): Int {
        var result = from.hashCode()
        result = 31 * result + to.hashCode()
        result = 31 * result + orientation
        return result
    }
}
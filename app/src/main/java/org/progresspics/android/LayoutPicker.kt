package org.progresspics.android

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

object LayoutPicker {

    interface Listener {
        fun onItemClicked(cellsPerRow: IntArray)
    }

    private fun getDescriptors(resources: Resources): Array<String> {
        return resources.getStringArray(R.array.layouts_array)
    }

    fun findBestLayout(resources: Resources, count: Int): IntArray {
        val descriptors = getDescriptors(resources)
        for (descriptor in descriptors) {
            val layout = parseLayoutDescriptor(descriptor)
            var layoutSize = 0
            for (rowSize in layout) {
                layoutSize += rowSize
            }
            if (layoutSize >= count) {
                return layout
            }
        }
        return parseLayoutDescriptor(descriptors[descriptors.size - 1])
    }

    @SuppressLint("InflateParams")
    fun inflate(context: Context, listener: Listener): View {
        val content = LayoutInflater.from(context).inflate(R.layout.layout_picker, null)

        val recyclerView = content.findViewById<RecyclerView>(R.id.layout_list)
        recyclerView.setHasFixedSize(true)

        val layoutManager = GridLayoutManager(context, 4)
        recyclerView.layoutManager = layoutManager

        val adapter = Adapter(
                listener, getDescriptors(context.resources))
        recyclerView.adapter = adapter

        return content
    }

    private class Adapter internal constructor(private val mListener: Listener, private val mDescriptors: Array<String>) : RecyclerView.Adapter<ViewHolder>() {
        @SuppressLint("InflateParams")
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val itemView = inflater.inflate(R.layout.layout_sample_grid, null)
            return ViewHolder(itemView)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val cellsPerRow = parseLayoutDescriptor(mDescriptors[position])

            holder.mItemView.setOnClickListener { mListener.onItemClicked(cellsPerRow) }

            val grid = (holder.mItemView as ViewGroup).getChildAt(0) as ViewGroup

            for (r in 0 until grid.childCount) {
                val activeRow = r < cellsPerRow.size
                val row = grid.getChildAt(r) as ViewGroup
                row.visibility = if (activeRow) View.VISIBLE else View.GONE
                for (c in 0 until row.childCount) {
                    val activeColumn = activeRow && c < cellsPerRow[r]
                    val cell = row.getChildAt(c)
                    cell.visibility = if (activeColumn) View.VISIBLE else View.GONE
                }
            }
        }

        override fun getItemCount(): Int {
            return mDescriptors.size
        }
    }

    private class ViewHolder internal constructor(internal var mItemView: View) : RecyclerView.ViewHolder(mItemView)

    private fun parseLayoutDescriptor(descriptor: String): IntArray {
        var parts = descriptor.split("x".toRegex()).toTypedArray()
        if (parts.size == 2) {
            val rows = Integer.parseInt(parts[1])
            val columns = Integer.parseInt(parts[0])
            val cellsPerRow = IntArray(rows)
            for (r in 0 until rows) {
                cellsPerRow[r] = columns
            }
            return cellsPerRow
        }
        parts = descriptor.split("\\+".toRegex()).toTypedArray()
        if (parts.size > 1) {
            val cellsPerRow = IntArray(parts.size)
            for (r in parts.indices) {
                cellsPerRow[r] = Integer.parseInt(parts[r])
            }
            return cellsPerRow
        }
        return IntArray(0)
    }
}
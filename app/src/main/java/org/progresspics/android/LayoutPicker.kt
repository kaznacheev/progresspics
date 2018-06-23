package org.progresspics.android

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import java.util.Arrays

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
            val layoutSize = layout.fold(0) { sum, value -> sum + value }
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
        recyclerView.layoutManager = GridLayoutManager(context, 4)
        recyclerView.adapter = Adapter(listener, getDescriptors(context.resources))

        return content
    }

    private class Adapter internal constructor(
            private val listener: Listener, private val descriptors: Array<String>)
        : RecyclerView.Adapter<ViewHolder>() {

        override fun getItemCount(): Int {
            return descriptors.size
        }

        @SuppressLint("InflateParams")
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(LayoutInflater.from(parent.context).inflate(
                    R.layout.layout_sample_grid, null))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val cellsPerRow = parseLayoutDescriptor(descriptors[position])

            holder.itemView.setOnClickListener { listener.onItemClicked(cellsPerRow) }

            val grid = (holder.itemView as ViewGroup).getChildAt(0) as ViewGroup

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
    }

    private class ViewHolder internal constructor(itemView: View)
        : RecyclerView.ViewHolder(itemView)

    private fun parseLayoutDescriptor(descriptor: String): IntArray {
        val dimensions = descriptor.split("x".toRegex()).toTypedArray()
        if (dimensions.size == 2) {
            val rows = Integer.parseInt(dimensions[1])
            val columns = Integer.parseInt(dimensions[0])
            val cellsPerRow = IntArray(rows)
            Arrays.fill(cellsPerRow, columns)
            return cellsPerRow
        }
        val rowCounts = descriptor.split("\\+".toRegex()).toTypedArray()
        return rowCounts.map { Integer.parseInt(it) }.toIntArray()
    }
}
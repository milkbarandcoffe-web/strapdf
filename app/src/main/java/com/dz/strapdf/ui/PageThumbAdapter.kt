package com.dz.strapdf.ui

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.dz.strapdf.databinding.ItemPageBinding

class PageThumbAdapter(
    private val thumbs: List<Bitmap?>,
    private val selected: MutableSet<Int>,
    private val onChange: () -> Unit
) : RecyclerView.Adapter<PageThumbAdapter.VH>() {

    class VH(val b: ItemPageBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemPageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = thumbs.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        h.b.thumb.setImageBitmap(thumbs[pos])
        h.b.check.setOnCheckedChangeListener(null)
        h.b.check.isChecked = selected.contains(pos)
        h.b.check.text = "Pag. ${pos + 1}"
        h.b.check.setOnCheckedChangeListener { _, checked ->
            if (checked) selected.add(pos) else selected.remove(pos)
            onChange()
        }
        h.b.thumb.setOnClickListener { h.b.check.toggle() }
    }
}

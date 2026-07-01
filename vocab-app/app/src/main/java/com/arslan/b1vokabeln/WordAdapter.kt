package com.arslan.b1vokabeln

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.arslan.b1vokabeln.databinding.ItemWordBinding

class WordAdapter(
    private val onToggle: (Word, Boolean) -> Unit
) : RecyclerView.Adapter<WordAdapter.WordViewHolder>() {

    private val items = ArrayList<Word>()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<Word>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class WordViewHolder(val b: ItemWordBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WordViewHolder {
        val b = ItemWordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return WordViewHolder(b)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: WordViewHolder, position: Int) {
        val word = items[position]
        holder.b.number.text = word.id.toString()
        holder.b.word.text = word.text

        // Detach listener before setting state to avoid recycling glitches.
        holder.b.checkbox.setOnCheckedChangeListener(null)
        holder.b.checkbox.isChecked = word.learned
        applyStyle(holder, word.learned)

        holder.b.root.setOnClickListener {
            holder.b.checkbox.isChecked = !holder.b.checkbox.isChecked
        }
        holder.b.checkbox.setOnCheckedChangeListener { _, isChecked ->
            word.learned = isChecked
            applyStyle(holder, isChecked)
            onToggle(word, isChecked)
        }
    }

    private fun applyStyle(holder: WordViewHolder, learned: Boolean) {
        holder.b.root.alpha = if (learned) 0.55f else 1f
    }
}

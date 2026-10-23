package me.nomi.urdutyper.presentation.utils.extensions.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView

abstract class BaseAdapter<T, B : ViewDataBinding>(
    private val layoutResId: Int,
) : RecyclerView.Adapter<BaseAdapter.ViewHolder<B>>() {

    private val diffUtilCallbackMaker: DiffUtilMaker<T> = { BaseDiffUtilItemCallback() }

    private val listDiffer by lazy {
        AsyncListDiffer(this, diffUtilCallbackMaker())
    }

    var onItemClickListener: ((Int) -> Unit)? = null
    var onLongItemClickListener: ((Int) -> Boolean)? = null
    var onBottomReachedListener: ((Boolean) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder<B> {
        val inflater = LayoutInflater.from(parent.context)
        val binding = DataBindingUtil.inflate<B>(inflater, layoutResId, parent, false)
        return ViewHolder(
            binding
        )
    }

    override fun onBindViewHolder(holder: ViewHolder<B>, position: Int) {
        if (onItemClickListener != null) {
            holder.itemView.setOnClickListener {
                val finalPosition = holder.absoluteAdapterPosition
                if (finalPosition != RecyclerView.NO_POSITION) {
                    onItemClickListener?.invoke(finalPosition)
                }
            }
        }

        onLongItemClickListener?.let { onLongClick ->
            holder.itemView.setOnLongClickListener {
                val finalPosition = holder.absoluteAdapterPosition
                if (finalPosition != RecyclerView.NO_POSITION) {
                    onLongClick.invoke(finalPosition)
                } else false
            }
        }

        bind(holder.binding, getItem(position))
        onBottomReachedListener?.invoke(isBottom(holder))
    }

    override fun getItemCount() = getData().size

    private fun isBottom(viewHolder: RecyclerView.ViewHolder) =
        viewHolder.absoluteAdapterPosition == itemCount - 1

    abstract fun bind(binding: B, item: T)

    fun pushData(data: List<T>) {
        listDiffer.submitList(data)
    }

    fun isEmpty() = itemCount == 0
    fun getItem(position: Int): T = listDiffer.currentList[position]
    fun getItemPosition(item: T): Int = getData().indexOf(item)
    fun removeItem(item: T) {
        val data = getData().toMutableList()
        data.remove(item)
        pushData(data)
    }

    fun getData(): List<T> = listDiffer.currentList

    class ViewHolder<B : ViewDataBinding>(val binding: B) : RecyclerView.ViewHolder(binding.root)
}

package me.nomi.urdutyper.presentation.utils.extensions.adapter

import android.util.Log
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

fun <T> RecyclerView.attach(
    layoutManager: RecyclerView.LayoutManager = LinearLayoutManager(
        context,
        RecyclerView.VERTICAL,
        false
    ),
    adapter: BaseAdapter<T, *>,
    itemDecoration: RecyclerView.ItemDecoration? = null,
    hasFixedSize: Boolean = false,
    snap: androidx.recyclerview.widget.SnapHelper? = null,
    onBottomReached: ((Boolean) -> Unit)? = null,
    onLongClickListener: ((Int, T) -> Boolean)? = null,
    onItemClick: ((Int, T) -> Unit)? = null,
    isNestedScrollingEnabled: Boolean = false
) {
    setLayoutManager(layoutManager)
    setAdapter(adapter)
    setHasFixedSize(hasFixedSize)
    setNestedScrollingEnabled(isNestedScrollingEnabled)

    if (itemDecoration != null) {
        addItemDecoration(itemDecoration)
    }

    if (onItemClick != null) {
        adapter.onItemClickListener = {
            onItemClick(it, adapter.getItem(it))
        }
    }

    if (onLongClickListener != null) {
        adapter.onLongItemClickListener = {
            onLongClickListener.invoke(it, adapter.getItem(it))
        }
    }

    if (onBottomReached != null) {
        adapter.onBottomReachedListener = {
            Log.d("RecyclerView", "OnBottomReached")
            onBottomReached(it)
        }
    }

    snap?.attachToRecyclerView(this)
}

val RecyclerView.firstVisiblePosition: Int
    get() = (layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition() ?: -1
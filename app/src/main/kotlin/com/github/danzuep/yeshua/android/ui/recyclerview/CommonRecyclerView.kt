/*
 * Copyright (C) 2023 Xizhi Zhu
 * Copyright (C) 2026 Daniel Collingwood
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.danzuep.yeshua.android.ui.recyclerview

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.github.danzuep.yeshua.android.core.Settings

abstract class BaseItem protected constructor(val viewType: Int, viewHolderCreator: (LayoutInflater, ViewGroup) -> BaseViewHolder<out BaseItem, *>) {
    companion object {
        private val VIEW_HOLDER_CREATORS = mutableMapOf<Int, (LayoutInflater, ViewGroup) -> BaseViewHolder<out BaseItem, *>>()

        fun getViewHolderCreator(viewType: Int) = VIEW_HOLDER_CREATORS
                .getOrElse(viewType) { throw IllegalStateException("Unknown view type - $viewType") }
    }

    open val isReorderable: Boolean
        get() = false

    init {
        VIEW_HOLDER_CREATORS[viewType] = viewHolderCreator
    }
}

abstract class BaseViewHolder<BI : BaseItem, VB : ViewBinding>(protected val viewBinding: VB) : RecyclerView.ViewHolder(viewBinding.root) {
    var item: BI? = null
        private set

    fun bindData(settings: Settings, item: BI, payloads: List<Any> = emptyList()) {
        this.item = item
        bind(settings, item, payloads)
    }

    protected abstract fun bind(settings: Settings, item: BI, payloads: List<Any>)
}

private class CommonAdapter(context: Context) : RecyclerView.Adapter<BaseViewHolder<BaseItem, *>>() {
    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private val items: ArrayList<BaseItem> = ArrayList()
    private var settings: Settings? = null

    fun setItems(items: Collection<BaseItem>) {
        this.items.clear()
        this.items.addAll(items)

        if (settings != null) {
            notifyDataSetChanged()
        }
    }

    fun isReorderable(position: Int): Boolean = items.getOrNull(position)?.isReorderable == true

    fun move(fromPosition: Int, toPosition: Int) {
        items.add(toPosition, items.removeAt(fromPosition))
        notifyItemMoved(fromPosition, toPosition)
    }

    fun currentItems(): List<BaseItem> = items.toList()

    fun setSettings(settings: Settings) {
        this.settings = settings

        if (items.isNotEmpty()) {
            notifyDataSetChanged()
        }
    }

    override fun getItemCount(): Int = if (settings != null) items.size else 0

    override fun getItemViewType(position: Int): Int = items[position].viewType

    @Suppress("UNCHECKED_CAST")
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder<BaseItem, *> =
            BaseItem.getViewHolderCreator(viewType).invoke(inflater, parent) as BaseViewHolder<BaseItem, *>

    override fun onBindViewHolder(holder: BaseViewHolder<BaseItem, *>, position: Int) {
        holder.bindData(settings!!, items[position])
    }

    override fun onBindViewHolder(holder: BaseViewHolder<BaseItem, *>, position: Int, payloads: MutableList<Any>) {
        holder.bindData(settings!!, items[position], payloads)
    }
}

class CommonRecyclerView : RecyclerView {
    private val adapter: CommonAdapter = CommonAdapter(context).apply { setAdapter(this) }
    private var onItemsReordered: ((List<BaseItem>) -> Unit)? = null
    private var itemsMoved = false

    private val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
        override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int =
                if (adapter.isReorderable(viewHolder.bindingAdapterPosition)) super.getMovementFlags(recyclerView, viewHolder) else 0

        override fun isLongPressDragEnabled(): Boolean = false

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION || !adapter.isReorderable(to)) return false

            adapter.move(from, to)
            itemsMoved = true
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            // Swiping is not supported
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            if (itemsMoved) {
                itemsMoved = false
                onItemsReordered?.invoke(adapter.currentItems())
            }
        }
    })

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        layoutManager = LinearLayoutManager(context, VERTICAL, false)
        itemTouchHelper.attachToRecyclerView(this)
    }

    fun setSettings(settings: Settings) {
        adapter.setSettings(settings)
    }

    fun setItems(items: List<BaseItem>) {
        adapter.setItems(items)
    }

    /** Items with [BaseItem.isReorderable] can be dragged, only within the reorderable items. */
    fun setOnItemsReorderedListener(listener: (List<BaseItem>) -> Unit) {
        onItemsReordered = listener
    }

    fun startDrag(viewHolder: RecyclerView.ViewHolder) {
        itemTouchHelper.startDrag(viewHolder)
    }
}

package me.nomi.urdutyper.presentation.utils.extensions.adapter

import androidx.recyclerview.widget.DiffUtil

typealias DiffUtilMaker<T> = () -> DiffUtil.ItemCallback<T>
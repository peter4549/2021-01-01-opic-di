package com.duke.elliot.opicdi.script.audio

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.duke.elliot.opicdi.database.AudioFileModel
import com.duke.elliot.opicdi.databinding.ItemAudioFileBinding
import java.lang.IllegalArgumentException

private const val VIEW_TYPE_HEADER = 0
private const val VIEW_TYPE_YEAR = 1
private const val VIEW_TYPE_AUDIO_FILE = 2

class AudioFileAdapter: ListAdapter<AdapterItem, RecyclerView.ViewHolder>(AudioFileDiffCallback()) {

    inner class ViewHolder constructor(private val binding: ViewDataBinding):
            RecyclerView.ViewHolder(binding.root) {

        fun bind(adapterItem: AdapterItem) {
            when (binding) {
                is ItemAudioFileBinding -> {

                }
            }
        }
    }

    private fun from(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = when(viewType) {
            // VIEW_TYPE_HEADER -> {  }
            // VIEW_TYPE_YEAR -> {  }
            VIEW_TYPE_AUDIO_FILE -> {
                ItemAudioFileBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                )
            }
            else -> throw IllegalArgumentException("Invalid viewType.")
        }

        return ViewHolder(binding)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return from(parent, viewType)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        TODO("Not yet implemented")
    }
}


class AudioFileDiffCallback: DiffUtil.ItemCallback<AdapterItem>() {
    override fun areItemsTheSame(oldItem: AdapterItem, newItem: AdapterItem): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: AdapterItem, newItem: AdapterItem): Boolean {
        return oldItem == newItem
    }
}

sealed class AdapterItem {
    data class AudioFileItem(val audioFileModel: AudioFileModel): AdapterItem() {
        override val id = audioFileModel.id
        val name = audioFileModel.name
        val duration = audioFileModel.duration
        val date = audioFileModel.date
    }

    object Header: AdapterItem() {
        override val id = Long.MIN_VALUE
    }

    abstract val id: Long
}
package com.duke.elliot.opicdi.script.audio

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.duke.elliot.opicdi.R
import com.duke.elliot.opicdi.databinding.ItemAudioFileBinding
import com.duke.elliot.opicdi.util.getAudioFileMetadata
import com.duke.elliot.opicdi.util.toDateFormat
import java.lang.IllegalArgumentException

private const val VIEW_TYPE_HEADER = 0
private const val VIEW_TYPE_YEAR = 1
private const val VIEW_TYPE_AUDIO_FILE = 2

private const val DURATION_PATTERN = "mm:ss"

class AudioFileAdapter: ListAdapter<AdapterItem, RecyclerView.ViewHolder>(AudioFileDiffCallback()) {

    private lateinit var recyclerView: RecyclerView

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    inner class ViewHolder constructor(private val binding: ViewDataBinding):
        RecyclerView.ViewHolder(binding.root) {

        fun bind(adapterItem: AdapterItem) {
            when (binding) {
                is ItemAudioFileBinding -> {
                    binding.name.text = (adapterItem as AdapterItem.AudioFileItem).name
                    binding.duration.text = adapterItem.duration.toDateFormat(DURATION_PATTERN)
                    binding.name.text = adapterItem.date.toDateFormat(
                        binding.root.context.getString(R.string.date_format_M_d)
                    )
                }
            }
        }
    }

    fun submitAudioFilePaths(list: List<String>) {
        submitList(list.map { AdapterItem.AudioFileItem(it) })
        recyclerView.scheduleLayoutAnimation()
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

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is AdapterItem.Header -> VIEW_TYPE_HEADER
            is AdapterItem.AudioFileItem -> VIEW_TYPE_AUDIO_FILE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return from(parent, viewType)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ViewHolder -> {
                holder.bind(getItem(position))
            }
        }
    }
}


class AudioFileDiffCallback: DiffUtil.ItemCallback<AdapterItem>() {
    override fun areItemsTheSame(oldItem: AdapterItem, newItem: AdapterItem): Boolean {
        return oldItem == newItem
    }

    override fun areContentsTheSame(oldItem: AdapterItem, newItem: AdapterItem): Boolean {
        return oldItem == newItem
    }
}

sealed class AdapterItem {
    data class AudioFileItem(val audioFilePath: String): AdapterItem() {
        private val audioFileMetaData = getAudioFileMetadata(audioFilePath)
        val name = audioFileMetaData.name
        val duration = audioFileMetaData.duration
        val date = audioFileMetaData.date
    }

    object Header: AdapterItem() {

    }
}
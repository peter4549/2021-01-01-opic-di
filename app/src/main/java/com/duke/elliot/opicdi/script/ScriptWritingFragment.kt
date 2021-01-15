package com.duke.elliot.opicdi.script

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.GravityCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.duke.elliot.opicdi.R
import com.duke.elliot.opicdi.audio_recoder.AudioRecorderActivity
import com.duke.elliot.opicdi.base.BaseFragment
import com.duke.elliot.opicdi.databinding.FragmentScriptWritingDrawerBinding
import com.duke.elliot.opicdi.script.audio.AudioFileAdapter

class ScriptWritingFragment: BaseFragment() {

    private lateinit var binding: FragmentScriptWritingDrawerBinding
    private lateinit var viewModel: ScriptWritingViewModel
    private lateinit var audioFileAdapter: AudioFileAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_script_writing_drawer, container, false)
        val scriptWritingFragmentArgs by navArgs<ScriptWritingFragmentArgs>()
        val originalScript = scriptWritingFragmentArgs.originalScript
        val viewModelFactory = ScriptWritingViewModelFactory(requireActivity().application, originalScript)
        viewModel = ViewModelProvider(viewModelStore, viewModelFactory)[ScriptWritingViewModel::class.java]



        binding.recyclerViewAudioFile.apply {
            audioFileAdapter = AudioFileAdapter()
            adapter = audioFileAdapter
        }
        audioFileAdapter.submitAudioFilePaths(viewModel.audioFilePaths)

        setDisplayHomeAsUpEnabled(binding.fragmentScriptWriting.toolbar)
        setOnHomePressedCallback {
            findNavController().popBackStack()
        }

        initText()
        initToolsClickListener()

        return binding.root
    }

    private fun initText() {
        viewModel.originalScript?.let { script ->
            binding.fragmentScriptWriting.title.setText(script.title)
            binding.fragmentScriptWriting.script.setText(script.script)
        }
    }

    private fun initToolsClickListener() {
        binding.fragmentScriptWriting.audioFiles.setOnClickListener {
            // TODO: Must be changed.
            val intent = Intent(requireContext(), AudioRecorderActivity::class.java)
            startActivityForResult(intent, REQUEST_CODE_AUDIO_RECORDER)
        }

        binding.fragmentScriptWriting.save.setOnClickListener {
            // TODO: Change.
            // check drawer action.
            if (binding.drawerLayout.isDrawerOpen(GravityCompat.END))
                binding.drawerLayout.closeDrawer(GravityCompat.END, true)
            else
                binding.drawerLayout.openDrawer(GravityCompat.END, true)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK) {
            when(requestCode) {
                REQUEST_CODE_AUDIO_RECORDER -> {
                    /** Add Audio Meta data.*/
                    val audioFilePath = data?.getStringExtra(EXTRA_NAME_AUDIO_FILE_METADATA)
                    audioFilePath?.let {
                        viewModel.audioFilePaths.add(0, it)
                        audioFileAdapter.submitAudioFilePaths(viewModel.audioFilePaths)
                    }
                }
            }
        }
    }

    /*
    private fun createScript() = Script(

    )
     */

    companion object {
        private const val REQUEST_CODE_AUDIO_RECORDER = 1608

        const val EXTRA_NAME_AUDIO_FILE_METADATA = "com.duke.elliot.opicdi.script.script_writing_fragment.extra_name_audio_file_metadata"
    }
}
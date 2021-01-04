package com.duke.elliot.opicdi.script

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.duke.elliot.opicdi.R
import com.duke.elliot.opicdi.base.BaseFragment
import com.duke.elliot.opicdi.database.Script
import com.duke.elliot.opicdi.databinding.FragmentScriptWritingDrawerBinding
import com.duke.elliot.opicdi.main.ScriptsViewModelFactory

class ScriptWritingFragment: BaseFragment() {

    private lateinit var binding: FragmentScriptWritingDrawerBinding
    private lateinit var viewModel: ScriptWritingViewModel

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

        setDisplayHomeAsUpEnabled(binding.toolbar)
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
            findNavController().navigate(ScriptWritingFragmentDirections.actionScriptWritingFragmentToAudioRecorderFragment())
        }
        binding.fragmentScriptWriting.save.setOnClickListener {

        }
    }

    /*
    private fun createScript() = Script(

    )
     */
}
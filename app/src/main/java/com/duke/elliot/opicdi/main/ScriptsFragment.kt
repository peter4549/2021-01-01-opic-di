package com.duke.elliot.opicdi.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.navigation.fragment.findNavController
import com.duke.elliot.opicdi.R
import com.duke.elliot.opicdi.base.BaseFragment
import com.duke.elliot.opicdi.databinding.FragmentScriptsBinding

class ScriptsFragment: BaseFragment() {

    private lateinit var binding: FragmentScriptsBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_scripts, container, false)

        binding.addScriptFloatingActionButton.setOnClickListener {
            findNavController().navigate(ScriptsFragmentDirections.actionScriptsFragmentToScriptWritingFragment())
        }

        return binding.root
    }
}
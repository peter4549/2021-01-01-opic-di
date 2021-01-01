package com.duke.elliot.opicdi.base

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.ColorInt
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import com.duke.elliot.opicdi.main.MainActivity

open class BaseFragment: Fragment() {

    private var menuRes: Int? = null
    private var onBackPressed: (() -> Unit)? = null
    private var onHomePressed: (() -> Unit)? = null
    private var optionsItemIdAndOnSelectedListeners = mutableMapOf<Int, () -> Unit>()

    protected fun setOnHomePressedCallback(onHomePressed: () -> Unit) {
        this.onHomePressed = onHomePressed
    }

    protected fun setOnBackPressedCallback(onBackPressed: () -> Unit) {
        this.onBackPressed = onBackPressed
        val onBackPressedCallback: OnBackPressedCallback =
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    this@BaseFragment.onBackPressed?.invoke()
                }
            }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            onBackPressedCallback
        )
    }

    protected fun setDisplayHomeAsUpEnabled(toolbar: Toolbar) {
        (requireActivity() as MainActivity).setSupportActionBar(toolbar)
        (requireActivity() as MainActivity).supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setHasOptionsMenu(true)
    }

    protected fun setOptionsMenu(toolbar: Toolbar, menuRes: Int?) {
        (requireActivity() as MainActivity).setSupportActionBar(toolbar)
        this.menuRes = menuRes
        setHasOptionsMenu(true)
    }

    protected fun setOnOptionsItemSelectedListeners(vararg optionsItemIdAndOnSelectedListeners: Pair<Int, () -> Unit>) {
        optionsItemIdAndOnSelectedListeners.forEach {
            if (!this.optionsItemIdAndOnSelectedListeners.keys.contains(it.first))
                this.optionsItemIdAndOnSelectedListeners[it.first] = it.second
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.clear()
        menuRes?.let {
            inflater.inflate(it, menu)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            android.R.id.home -> onHomePressed?.invoke()
            else -> optionsItemIdAndOnSelectedListeners[item.itemId]?.invoke()
        }

        return super.onOptionsItemSelected(item)
    }

    protected fun setBackgroundColor(vararg views: View, @ColorInt color: Int) {
        for (view in views) {
            view.setBackgroundColor(color)
            view.invalidate()
        }
    }

    protected fun showToast(text: String, duration: Int = Toast.LENGTH_LONG) {
        Toast.makeText(requireContext(), text, duration).show()
    }
}
package com.duke.elliot.opicdi.base

import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar

open class BaseActivity: AppCompatActivity() {

    private var menuRes: Int? = null
    private var onHomePressed: (() -> Unit)? = null
    private var optionsItemIdAndOnSelectedListeners = mutableMapOf<Int, () -> Unit>()
    private var showHomeAsUp = false

    protected fun setOnHomePressedCallback(onHomePressed: () -> Unit) {
        this.onHomePressed = onHomePressed
        showToast("HOME INIT")
    }

    protected fun setDisplayHomeAsUpEnabled(toolbar: Toolbar) {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        showHomeAsUp = true
    }

    protected fun setOptionsMenu(toolbar: Toolbar, menuRes: Int?) {
        this.menuRes = menuRes
    }

    protected fun setOnOptionsItemSelectedListeners(vararg optionsItemIdAndOnSelectedListeners: Pair<Int, () -> Unit>) {
        optionsItemIdAndOnSelectedListeners.forEach {
            if (!this.optionsItemIdAndOnSelectedListeners.keys.contains(it.first))
                this.optionsItemIdAndOnSelectedListeners[it.first] = it.second
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.clear()
        menuRes?.let { menuInflater.inflate(it, menu) }
        return menuRes != null || showHomeAsUp
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId) {
            android.R.id.home -> {
                onHomePressed?.invoke()
                true
            }
            else -> {
                optionsItemIdAndOnSelectedListeners[item.itemId]?.invoke()
                true
            }
        }
    }

    protected fun setBackgroundColor(vararg views: View, @ColorInt color: Int) {
        for (view in views) {
            view.setBackgroundColor(color)
            view.invalidate()
        }
    }

    protected fun showToast(text: String, duration: Int = Toast.LENGTH_LONG) {
        Toast.makeText(this, text, duration).show()
    }
}
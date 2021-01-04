package com.duke.elliot.opicdi.main

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.duke.elliot.opicdi.R
import com.duke.elliot.opicdi.fluid_keyboard_resize.FluidContentResize

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        FluidContentResize.listen(this)
    }
}
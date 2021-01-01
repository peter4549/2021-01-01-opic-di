package com.duke.elliot.opicdi.fluid_keyboard_resize

data class KeyboardVisibilityChanged(
    val visible: Boolean,
    val contentHeight: Int,
    val contentHeightBeforeResize: Int
)
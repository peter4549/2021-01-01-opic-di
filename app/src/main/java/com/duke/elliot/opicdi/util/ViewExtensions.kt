package com.duke.elliot.opicdi.util

import android.animation.Animator
import android.view.View

fun View.scale (
    scale: Float = 0.8F,
    duration: Long = 200L,
    animationEndCallback: ((view: View) -> Unit)? = null
) {
    this.animate()
        .scaleX(scale)
        .scaleY(scale)
        .alpha(1F)
        .setDuration(duration)
        .setListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animator: Animator?) {
                this@scale.visibility = View.VISIBLE
            }

            override fun onAnimationEnd(animator: Animator?) {
                animationEndCallback?.invoke(this@scale)
            }

            override fun onAnimationCancel(animator: Animator?) {}
            override fun onAnimationRepeat(animator: Animator?) {}
        })
        .start()
}
package com.duke.elliot.opicdi.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.View
import android.widget.SeekBar

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

fun SeekBar.progressRate(): Float {
    return if (max == 0)
        0F
    else
        progress / max.toFloat()
}

fun SeekBar.isEnd(): Boolean = progress == max

fun View.fadeIn(duration: Number, onAnimationEndCallback: (view: View) -> Unit) {
    this.apply {
        alpha = 0F
        visibility = View.VISIBLE

        animate()
                .alpha(1F)
                .setDuration(duration.toLong())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator?) {
                        super.onAnimationEnd(animation)
                        onAnimationEndCallback.invoke(this@fadeIn)
                    }
                })
    }
}

fun View.fadeOut(duration: Number, onAnimationEndCallback: (view: View) -> Unit) {
    this.apply {
        alpha = 1F
        visibility = View.VISIBLE

        animate()
                .alpha(0F)
                .setDuration(duration.toLong())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator?) {
                        super.onAnimationEnd(animation)
                        this@fadeOut.visibility = View.GONE
                        onAnimationEndCallback.invoke(this@fadeOut)
                    }
                })
    }
}
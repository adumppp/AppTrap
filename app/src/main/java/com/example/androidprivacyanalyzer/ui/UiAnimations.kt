package com.example.androidprivacyanalyzer.ui

import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ProgressBar
import android.widget.TextView
import androidx.transition.TransitionInflater
import androidx.transition.TransitionManager
import com.example.androidprivacyanalyzer.R

/**
 * The "animation characteristics" layer — every motion the app uses lives here
 * so screens stay declarative and timings stay consistent.
 */
object UiAnimations {

    /** Infinite gentle breathing, used on the hero shield. */
    fun breathe(view: View) {
        view.startAnimation(AnimationUtils.loadAnimation(view.context, R.anim.shield_breathe))
    }

    /** Infinite soft pulse, used on the "Checking: <app>" line. */
    fun pulse(view: View) {
        view.startAnimation(AnimationUtils.loadAnimation(view.context, R.anim.text_pulse))
    }

    /** Bump a counter whenever its value changes. */
    fun bumpTo(textView: TextView, value: Int) {
        if (textView.text?.toString() == value.toString()) return
        textView.text = value.toString()
        textView.startAnimation(AnimationUtils.loadAnimation(textView.context, R.anim.count_bump))
    }

    /** Smoothly tween a ProgressBar instead of snapping between percentages. */
    fun animateProgress(bar: ProgressBar, target: Int) {
        ValueAnimator.ofInt(bar.progress, target.coerceIn(0, bar.max)).apply {
            duration = 320
            addUpdateListener { bar.progress = it.animatedValue as Int }
            start()
        }
    }

    /** Count a score up from 0 — used on the detail screen ring. */
    fun countUp(textView: TextView, target: Int, durationMs: Long = 900) {
        ValueAnimator.ofInt(0, target).apply {
            duration = durationMs
            interpolator = android.view.animation.DecelerateInterpolator(1.5f)
            addUpdateListener { textView.text = it.animatedValue.toString() }
            start()
        }
    }

    /**
     * Tween every distribution-bar segment from its current weight to the
     * target weight (prototype: `transition-all duration-1000` on the bar).
     */
    fun animateBarWeights(
        views: List<View>,
        targets: List<Float>,
        durationMs: Long = 900,
    ) {
        val starts = views.map {
            (it.layoutParams as? android.widget.LinearLayout.LayoutParams)?.weight ?: 0f
        }
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = android.view.animation.DecelerateInterpolator(1.6f)
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                views.forEachIndexed { i, v ->
                    val lp = v.layoutParams as android.widget.LinearLayout.LayoutParams
                    lp.weight = starts[i] + (targets[i] - starts[i]) * t
                    v.layoutParams = lp
                }
            }
            start()
        }
    }

    /** Cross-fade a state swap (idle -> scanning -> results -> empty). */
    fun morph(container: ViewGroup, changes: () -> Unit) {
        val transition = TransitionInflater.from(container.context)
            .inflateTransition(R.transition.layout_changes)
        TransitionManager.beginDelayedTransition(container, transition)
        changes()
    }

    /** Expand/collapse the "everything else looks normal" section. */
    fun toggleSection(container: ViewGroup, section: View, chevron: View) {
        morph(container) {
            val showing = section.visibility == View.VISIBLE
            section.visibility = if (showing) View.GONE else View.VISIBLE
            chevron.animate().rotation(if (showing) 90f else 270f).setDuration(200).start()
        }
    }
}

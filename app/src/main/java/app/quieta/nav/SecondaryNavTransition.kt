// SPDX-License-Identifier: GPL-3.0-only
// Secondary-stack transitions inspired by InstallerX InstallerNavTransition.
package app.quieta.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import app.quieta.core.settings.PredictiveBackAnimation
import app.quieta.core.settings.PredictiveBackExitDirection

private val FastOutExtraSlowIn = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val MiuixEase = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * InstallerX-aligned secondary-page transforms.
 * None: instant-ish; MIUIX: spring-like ease; AOSP/Classic/Scale: directional + scale fade.
 */
fun secondaryNavTransform(
    animation: PredictiveBackAnimation,
    exitDirection: PredictiveBackExitDirection,
    forward: Boolean,
): ContentTransform {
    val sign = when (exitDirection) {
        PredictiveBackExitDirection.ALWAYS_LEFT -> -1
        PredictiveBackExitDirection.ALWAYS_RIGHT -> 1
        PredictiveBackExitDirection.FOLLOW_GESTURE -> if (forward) 1 else -1
    }
    return when (animation) {
        PredictiveBackAnimation.NONE -> {
            if (forward) {
                fadeIn(tween(100)) togetherWith fadeOut(tween(100))
            } else {
                fadeIn(tween(100)) togetherWith fadeOut(tween(100))
            }
        }
        PredictiveBackAnimation.MIUIX -> {
            val enter = slideInHorizontally(tween(420, easing = MiuixEase)) { w -> w / 3 * sign } +
                fadeIn(tween(280))
            val exit = slideOutHorizontally(tween(420, easing = MiuixEase)) { w -> -w / 3 * sign } +
                fadeOut(tween(220))
            enter togetherWith exit
        }
        PredictiveBackAnimation.AOSP, PredictiveBackAnimation.CLASSIC, PredictiveBackAnimation.SCALE -> {
            // Classic / Scale: page scales down and drifts (InstallerX ClassicScalePop / scaleNav).
            val duration = if (animation == PredictiveBackAnimation.SCALE) 450 else 200
            val easing = if (animation == PredictiveBackAnimation.SCALE) FastOutExtraSlowIn else MiuixEase
            if (forward) {
                slideInHorizontally(tween(duration, easing = easing)) { w -> w * sign } +
                    fadeIn(tween(duration / 2)) togetherWith
                    slideOutHorizontally(tween(duration, easing = easing)) { w -> -w / 2 * sign } +
                    fadeOut(tween(duration / 2))
            } else {
                slideInHorizontally(tween(duration, easing = easing)) { w -> -w / 2 * sign } +
                    fadeIn(tween(duration / 2)) togetherWith
                    slideOutHorizontally(tween(duration, easing = easing)) { w -> w * sign } +
                    fadeOut(tween(duration / 2))
            }
        }
    }
}

/** Scale factor applied while a secondary page is on screen for Scale/Classic styles. */
fun secondaryNavScale(animation: PredictiveBackAnimation): Float = when (animation) {
    PredictiveBackAnimation.SCALE -> 0.92f
    PredictiveBackAnimation.CLASSIC -> 0.95f
    PredictiveBackAnimation.AOSP -> 0.96f
    PredictiveBackAnimation.MIUIX -> 1f
    PredictiveBackAnimation.NONE -> 1f
}

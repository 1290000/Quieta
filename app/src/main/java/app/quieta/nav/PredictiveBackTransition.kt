// SPDX-License-Identifier: GPL-3.0-only
// Predictive-back geometry ported from InstallerX Revived
// (ui/animation/predictiveback/*) and miuix-nav NavTransitions.
// KernelSU Classic naming follows InstallerX PredictiveBackAnimation.Classic.
package app.quieta.nav

import androidx.activity.BackEventCompat
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.quieta.core.settings.PredictiveBackAnimation
import app.quieta.core.settings.PredictiveBackExitDirection
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec

/** InstallerX two-segment `fast_out_extra_slow_in`. */
internal val FastOutExtraSlowIn: Easing = run {
    val knotX = 0.166666f
    val knotY = 0.4f
    val first = CubicBezierEasing(0.05f / knotX, 0f, 0.133333f / knotX, 0.06f / knotY)
    val second = CubicBezierEasing(
        (0.208333f - knotX) / (1f - knotX),
        (0.82f - knotY) / (1f - knotY),
        (0.25f - knotX) / (1f - knotX),
        (1f - knotY) / (1f - knotY),
    )
    Easing { fraction ->
        if (fraction < knotX) {
            knotY * first.transform(fraction / knotX)
        } else {
            knotY + (1f - knotY) * second.transform((fraction - knotX) / (1f - knotX))
        }
    }
}

/** InstallerX / AOSP pre-commit gesture interpolator. */
internal val BackGestureEasing: Easing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

/**
 * miuix `NavProgrammaticEasing` — baked underdamped spring step response
 * (response=0.8, damping=0.95). Used for MiuixDefault push/pop pacing.
 */
internal val NavProgrammaticEasing: Easing = object : Easing {
    private val r: Float
    private val w: Float
    private val c2: Float

    init {
        val response = 0.8f
        val damping = 0.95f
        val omega = 2.0 * Math.PI / response
        val k = omega * omega
        val c = damping * 4.0 * Math.PI / response
        w = (sqrt(4.0 * k - c * c) / 2.0).toFloat()
        r = (-c / 2.0).toFloat()
        c2 = r / w
    }

    override fun transform(fraction: Float): Float {
        val t = fraction.toDouble()
        val decay = exp(r * t)
        return (decay * (-cos(w * t) + c2 * sin(w * t)) + 1.0).toFloat()
    }
}

/** Legacy cubic used by Classic/Scale programmatic pop (InstallerX tween 200ms). */
private val ProgrammaticEase = CubicBezierEasing(0.2f, 0f, 0f, 1f)

internal const val SCALE_MIN = 0.85f
internal const val AOSP_MIN_SCALE = 0.9f
internal const val CLASSIC_MIN_SCALE = 0.9f
internal val ScaleExitDrift = 96.dp
internal val CrossActivityDrift = 96.dp
internal val CrossActivityEdgeMargin = 8.dp
private const val BOUNCE_STIFFNESS = 200f
private const val BOUNCE_DAMPING = 0.75f
private const val BOUNCE_MAX_KICK = 1000f
private const val BOUNCE_MIN_KICK = 120f
private const val MIN_RELEASE_SPAN = 0.001f
private const val COMMIT_ALPHA_FACTOR = 5f

/** Visual result applied to one navigation layer. */
data class LayerTransform(
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val alpha: Float = 1f,
    val pivotFractionX: Float = 0.5f,
    val pivotFractionY: Float = 0.5f,
) {
    val transformOrigin: TransformOrigin
        get() = TransformOrigin(pivotFractionX, pivotFractionY)
}

enum class PredictiveNavPhase {
    /** Secondary fully covering main. */
    Idle,

    /** Finger-driven predictive progress. */
    Gesture,

    /** Predictive commit settle (geometry continues from release pose). */
    Commit,

    /** Predictive cancel settle. */
    Cancel,

    /** Programmatic open of a secondary page. */
    Push,

    /** Programmatic close via back button / onBack. */
    Pop,
}

/**
 * Live driver state for secondary-stack predictive back.
 *
 * Backed by Compose snapshot state so gesture/settle updates invalidate composition/draw.
 * [leaveProgress]: 0 = secondary fully on top, 1 = secondary fully gone.
 */
@Stable
class PredictiveBackDriver {
    var animation by mutableStateOf(PredictiveBackAnimation.MIUIX)
    var exitDirection by mutableStateOf(PredictiveBackExitDirection.ALWAYS_RIGHT)
    var phase by mutableStateOf(PredictiveNavPhase.Idle)
        private set
    var leaveProgress by mutableFloatStateOf(0f)
        private set
    var gestureProgress by mutableFloatStateOf(0f)
        private set
    var releaseProgress by mutableFloatStateOf(0f)
        private set
    var swipeEdge by mutableIntStateOf(BackEventCompat.EDGE_LEFT)
        private set
    var touchY by mutableFloatStateOf(0f)
        private set
    var initialTouchY by mutableFloatStateOf(0f)
        private set
    var releaseVelocity by mutableFloatStateOf(0f)
        private set

    /** Wall-clock 0..1 across the current commit/pop duration (alpha/scrim tracks). */
    var settleRaw by mutableFloatStateOf(0f)
        private set

    /** Eased 0..1 across the current settle/pop. */
    var settleEased by mutableFloatStateOf(0f)
        private set

    val isActive: Boolean
        get() = phase != PredictiveNavPhase.Idle

    fun onGestureEvent(event: BackEventCompat) {
        if (phase != PredictiveNavPhase.Gesture) {
            initialTouchY = event.touchY
            phase = PredictiveNavPhase.Gesture
        }
        swipeEdge = event.swipeEdge
        touchY = event.touchY
        gestureProgress = event.progress.coerceIn(0f, 1f)
        leaveProgress = gestureProgress
        phase = PredictiveNavPhase.Gesture
    }

    fun beginCommit() {
        releaseProgress = gestureProgress
        phase = PredictiveNavPhase.Commit
        settleRaw = 0f
        settleEased = 0f
        leaveProgress = releaseProgress
    }

    fun beginCancel() {
        releaseProgress = gestureProgress
        phase = PredictiveNavPhase.Cancel
        settleRaw = 0f
        settleEased = 0f
        leaveProgress = releaseProgress
    }

    fun beginPush() {
        phase = PredictiveNavPhase.Push
        leaveProgress = 1f
        settleRaw = 0f
        settleEased = 0f
    }

    fun beginPop() {
        phase = PredictiveNavPhase.Pop
        leaveProgress = 0f
        settleRaw = 0f
        settleEased = 0f
        if (swipeEdge != BackEventCompat.EDGE_RIGHT && swipeEdge != BackEventCompat.EDGE_LEFT) {
            swipeEdge = BackEventCompat.EDGE_LEFT
        }
    }

    fun updateSettle(raw: Float, eased: Float) {
        settleRaw = raw.coerceIn(0f, 1f)
        settleEased = eased.coerceIn(0f, 1f)
        leaveProgress = when (phase) {
            PredictiveNavPhase.Commit -> releaseProgress + (1f - releaseProgress) * settleEased
            PredictiveNavPhase.Cancel -> releaseProgress * (1f - settleEased)
            PredictiveNavPhase.Push -> 1f - settleEased
            PredictiveNavPhase.Pop -> settleEased
            else -> leaveProgress
        }
    }

    fun reset() {
        phase = PredictiveNavPhase.Idle
        leaveProgress = 0f
        gestureProgress = 0f
        releaseProgress = 0f
        settleRaw = 0f
        settleEased = 0f
        releaseVelocity = 0f
    }

    fun releaseVelocityOf(previousProgress: Float, dtMillis: Float) {
        if (dtMillis <= 0f) return
        releaseVelocity = (gestureProgress - previousProgress) / (dtMillis / 1000f)
    }
}

internal fun exitDirectionSign(
    direction: PredictiveBackExitDirection,
    swipeEdge: Int,
): Float = when (direction) {
    // InstallerX: left-edge swipe exits to the right (+1); right-edge exits left (-1).
    PredictiveBackExitDirection.FOLLOW_GESTURE ->
        if (swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
    PredictiveBackExitDirection.ALWAYS_RIGHT -> 1f
    PredictiveBackExitDirection.ALWAYS_LEFT -> -1f
}

/** Only Scale consumes the exit-direction setting (InstallerX theme page). */
fun predictiveExitDirectionVisible(animation: PredictiveBackAnimation): Boolean =
    animation == PredictiveBackAnimation.SCALE

/** InstallerX NavDisplayEffects: card-style animations round all corners at 32dp. */
fun predictiveRoundAllCorners(animation: PredictiveBackAnimation): Boolean =
    animation == PredictiveBackAnimation.AOSP ||
        animation == PredictiveBackAnimation.SCALE ||
        animation == PredictiveBackAnimation.CLASSIC

private fun snapScale(scale: Float, extent: Float): Float =
    if (extent > 0f) (scale * extent).roundToInt() / extent else scale

private fun snapTranslation(
    translation: Float,
    scale: Float,
    extent: Float,
    pivotFraction: Float = 0.5f,
): Float {
    if (extent <= 0f) return translation
    val scaledEdgeOffset = extent * pivotFraction * (1f - scale)
    return (translation + scaledEdgeOffset).roundToInt() - scaledEdgeOffset
}

private fun shapedTopProgress(progress: Float, gestureDriven: Boolean): Float =
    if (!gestureDriven) {
        progress
    } else {
        1f - BackGestureEasing.transform((1f - progress).coerceIn(0f, 1f))
    }

private fun gesturePivotY(touchY: Float, height: Float): Float =
    if (height > 0f) (touchY / height).coerceIn(0.1f, 0.9f) else 0.5f

private fun crossActivityYShift(
    gesture: Boolean,
    touchY: Float,
    initialTouchY: Float,
    height: Float,
    scale: Float,
    density: Density,
): Float {
    if (!gesture || height <= 0f) return 0f
    val rawDelta = touchY - initialTouchY
    val half = height / 2f
    val ratio = min(half, abs(rawDelta)) / half
    val damped = 1f - (1f - ratio) * (1f - ratio)
    val marginPx = with(density) { CrossActivityEdgeMargin.toPx() }
    val maxShift = ((height - height * scale) / 2f - marginPx).coerceAtLeast(0f)
    return maxShift * damped * (if (rawDelta < 0f) -1f else 1f)
}



/**
 * Outgoing (secondary) layer transform — InstallerX geometry.
 *
 * [leaveProgress] 0..1, [phase] distinguishes finger tracking from settle.
 * [settleEased]/[settleRaw] drive commit/pop tracks.
 */
fun predictiveOutgoingTransform(
    animation: PredictiveBackAnimation,
    exitDirection: PredictiveBackExitDirection,
    phase: PredictiveNavPhase,
    leaveProgress: Float,
    gestureProgress: Float,
    releaseProgress: Float,
    settleEased: Float,
    settleRaw: Float,
    swipeEdge: Int,
    touchY: Float,
    initialTouchY: Float,
    widthPx: Float,
    heightPx: Float,
    density: Density,
    rtl: Boolean,
): LayerTransform {
    val p = leaveProgress.coerceIn(0f, 1f)
    val gestureDriven = phase == PredictiveNavPhase.Gesture ||
        phase == PredictiveNavPhase.Commit ||
        phase == PredictiveNavPhase.Cancel ||
        phase == PredictiveNavPhase.Pop
    val rtlSign = if (rtl) -1f else 1f

    return when (animation) {
        PredictiveBackAnimation.NONE -> {
            // NoPredictiveBack: page stays put during the finger; commit slowly plays out.
            when (phase) {
                PredictiveNavPhase.Gesture -> LayerTransform()
                PredictiveNavPhase.Commit -> {
                    val tx = rtlSign * settleEased * widthPx
                    LayerTransform(translationX = tx)
                }
                PredictiveNavPhase.Cancel -> LayerTransform()
                PredictiveNavPhase.Pop -> {
                    val tx = rtlSign * p * widthPx
                    LayerTransform(translationX = tx)
                }
                PredictiveNavPhase.Push -> {
                    val tx = rtlSign * p * widthPx
                    LayerTransform(translationX = tx)
                }
                PredictiveNavPhase.Idle -> LayerTransform()
            }
        }

        PredictiveBackAnimation.MIUIX -> {
            // NavTransitions.MiuixDefault: full-width slide from trailing edge.
            val tx = rtlSign * p * widthPx
            LayerTransform(translationX = snapTranslation(tx, 1f, widthPx))
        }

        PredictiveBackAnimation.SCALE -> {
            val sign = exitDirectionSign(exitDirection, swipeEdge)
            val driftPx = with(density) { ScaleExitDrift.toPx() }
            val leftEdge = swipeEdge == BackEventCompat.EDGE_LEFT
            val pivotX = if (leftEdge) 0.8f else 0.2f
            val pivotY = gesturePivotY(touchY, heightPx)

            when (phase) {
                PredictiveNavPhase.Gesture -> {
                    val eased = shapedTopProgress(1f - p, gestureDriven = true)
                    val scale = snapScale(SCALE_MIN + (1f - SCALE_MIN) * eased, widthPx)
                    LayerTransform(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = 0f,
                        pivotFractionX = pivotX,
                        pivotFractionY = pivotY,
                    )
                }
                PredictiveNavPhase.Commit -> {
                    val releaseP = (1f - releaseProgress).coerceAtLeast(MIN_RELEASE_SPAN)
                    // InstallerX: post runs 0→1 as remaining top-progress drains to 0.
                    val post = settleEased
                    val releaseEased = shapedTopProgress(releaseProgress, gestureDriven = true)
                    val committedScale = SCALE_MIN + (1f - SCALE_MIN) * releaseEased
                    val pageScale = committedScale + (SCALE_MIN - committedScale) * post
                    val scale = snapScale(pageScale, widthPx)
                    val tx = sign * post * driftPx
                    val alpha = (1f - COMMIT_ALPHA_FACTOR * settleRaw).coerceAtLeast(0f)
                    LayerTransform(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = snapTranslation(tx, scale, widthPx, pivotX),
                        translationY = snapTranslation(0f, scale, heightPx, pivotY),
                        alpha = alpha,
                        pivotFractionX = pivotX,
                        pivotFractionY = pivotY,
                    )
                }
                PredictiveNavPhase.Cancel -> {
                    val eased = shapedTopProgress(1f - p, gestureDriven = true)
                    val scale = snapScale(SCALE_MIN + (1f - SCALE_MIN) * eased, widthPx)
                    LayerTransform(
                        scaleX = scale,
                        scaleY = scale,
                        pivotFractionX = pivotX,
                        pivotFractionY = pivotY,
                    )
                }
                PredictiveNavPhase.Pop -> {
                    // Programmatic pop uses Scale programmatic motion: tween 200ms.
                    val eased = shapedTopProgress(1f - p, gestureDriven = false)
                    val scale = snapScale(SCALE_MIN + (1f - SCALE_MIN) * eased, widthPx)
                    val tx = sign * p * driftPx
                    val alpha = 1f - p
                    LayerTransform(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = snapTranslation(tx, scale, widthPx, pivotX),
                        alpha = alpha.coerceIn(0f, 1f),
                        pivotFractionX = pivotX,
                        pivotFractionY = pivotY,
                    )
                }
                PredictiveNavPhase.Push -> {
                    // Scale push uses MiuixDefault.
                    val tx = rtlSign * p * widthPx
                    LayerTransform(translationX = snapTranslation(tx, 1f, widthPx))
                }
                PredictiveNavPhase.Idle -> LayerTransform()
            }
        }

        PredictiveBackAnimation.CLASSIC -> {
            // ClassicNavTransition ClassicScalePop — scale + alpha only, no directional drift.
            when (phase) {
                PredictiveNavPhase.Gesture, PredictiveNavPhase.Cancel -> {
                    val remain = 1f - p
                    val scale = snapScale(CLASSIC_MIN_SCALE + (1f - CLASSIC_MIN_SCALE) * remain, widthPx)
                    LayerTransform(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = 0f,
                        alpha = remain.coerceIn(0f, 1f),
                    )
                }
                PredictiveNavPhase.Commit -> {
                    val remain = 1f - p
                    val scale = snapScale(CLASSIC_MIN_SCALE + (1f - CLASSIC_MIN_SCALE) * remain, widthPx)
                    LayerTransform(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = 0f,
                        alpha = remain.coerceIn(0f, 1f),
                    )
                }
                PredictiveNavPhase.Pop -> {
                    val remain = 1f - p
                    val scale = snapScale(CLASSIC_MIN_SCALE + (1f - CLASSIC_MIN_SCALE) * remain, widthPx)
                    LayerTransform(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = 0f,
                        alpha = remain.coerceIn(0f, 1f),
                    )
                }
                PredictiveNavPhase.Push -> {
                    val tx = rtlSign * p * widthPx
                    LayerTransform(translationX = snapTranslation(tx, 1f, widthPx))
                }
                PredictiveNavPhase.Idle -> LayerTransform()
            }
        }

        PredictiveBackAnimation.AOSP -> {
            val hugs = swipeEdge != BackEventCompat.EDGE_RIGHT
            val driftPx = with(density) { CrossActivityDrift.toPx() }
            val hugMax = (
                widthPx * (1f - AOSP_MIN_SCALE) / 2f -
                    with(density) { CrossActivityEdgeMargin.toPx() }
                ).coerceAtLeast(0f)
            val gestureLike = phase == PredictiveNavPhase.Gesture ||
                phase == PredictiveNavPhase.Commit ||
                phase == PredictiveNavPhase.Cancel

            if (gestureLike) {
                val committing = phase == PredictiveNavPhase.Commit
                if (committing) {
                    val releaseP = (1f - releaseProgress).coerceAtLeast(MIN_RELEASE_SPAN)
                    val post = settleEased
                    val releaseEased = shapedTopProgress(releaseProgress, gestureDriven = true)
                    val committedScale = AOSP_MIN_SCALE + (1f - AOSP_MIN_SCALE) * releaseEased
                    val grown = committedScale + (1f - committedScale) * post
                    val velocityKick = abs((gestureProgress - releaseProgress) * 10f)
                    val bounce = aospBounce(gestureProgress, velocityKick, swipeEdge)
                    val scale = snapScale(grown * bounce, widthPx)
                    var tx = if (hugs) (1f - releaseEased) * hugMax else 0f
                    tx += post * driftPx
                    val alpha = (1f - COMMIT_ALPHA_FACTOR * settleRaw).coerceAtLeast(0f)
                    val ty = crossActivityYShift(
                        gesture = true,
                        touchY = touchY,
                        initialTouchY = initialTouchY,
                        height = heightPx,
                        scale = scale,
                        density = density,
                    )
                    LayerTransform(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = snapTranslation(tx, scale, widthPx),
                        translationY = snapTranslation(ty, scale, heightPx),
                        alpha = alpha,
                    )
                } else {
                    val eased = shapedTopProgress(1f - p, gestureDriven = true)
                    val scale = snapScale(AOSP_MIN_SCALE + (1f - AOSP_MIN_SCALE) * eased, widthPx)
                    val tx = if (hugs) (1f - eased) * hugMax else 0f
                    val ty = crossActivityYShift(
                        gesture = true,
                        touchY = touchY,
                        initialTouchY = initialTouchY,
                        height = heightPx,
                        scale = scale,
                        density = density,
                    )
                    LayerTransform(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = snapTranslation(tx, scale, widthPx),
                        translationY = snapTranslation(ty, scale, heightPx),
                    )
                }
            } else {
                when (phase) {
                    PredictiveNavPhase.Pop -> {
                        // ClassicActivityClose: 96dp drift + wall-clock fade, no card scale.
                        val tx = (1f - p) * driftPx
                        // elapsed = settleRaw * 450; close fade [35ms, 118ms].
                        val elapsed = settleRaw * 450f
                        val alpha = if (settleRaw > 0f) {
                            (1f - (elapsed - 35f) / 83f).coerceIn(0f, 1f)
                        } else {
                            ((p - 0.21f) / 0.74f).coerceIn(0f, 1f)
                        }
                        LayerTransform(
                            translationX = snapTranslation(tx, 1f, widthPx),
                            alpha = alpha,
                        )
                    }
                    PredictiveNavPhase.Push -> {
                        // ClassicActivityOpen: incoming drifts from +96dp with wall-clock fade-in.
                        // leaveProgress 1→0 as the page arrives; elapsed = settleRaw * 450.
                        val tx = p * driftPx
                        val elapsed = settleRaw * 450f
                        val alpha = if (settleRaw > 0f) {
                            ((elapsed - 50f) / 83f).coerceIn(0f, 1f)
                        } else {
                            val arrive = 1f - p
                            ((arrive - 0.12f) / 0.71f).coerceIn(0f, 1f)
                        }
                        LayerTransform(
                            translationX = snapTranslation(tx, 1f, widthPx),
                            alpha = alpha,
                        )
                    }
                    PredictiveNavPhase.Idle -> LayerTransform()
                }
            }
        }
    }
}

/**
 * Underlay (previous / main) layer transform — InstallerX covered-entry treatment.
 */
fun predictiveUnderlayTransform(
    animation: PredictiveBackAnimation,
    phase: PredictiveNavPhase,
    leaveProgress: Float,
    gestureProgress: Float,
    releaseProgress: Float,
    settleEased: Float,
    swipeEdge: Int,
    touchY: Float,
    initialTouchY: Float,
    widthPx: Float,
    heightPx: Float,
    density: Density,
    rtl: Boolean,
): LayerTransform {
    val p = leaveProgress.coerceIn(0f, 1f)
    val cover = 1f - p
    val rtlSign = if (rtl) -1f else 1f

    return when (animation) {
        PredictiveBackAnimation.NONE -> {
            when (phase) {
                PredictiveNavPhase.Gesture -> LayerTransform(alpha = 0.9f + 0.1f * (1f - p))
                else -> {
                    val revealed = p // 0 covered → 1 revealed when leaveProgress→1? underlay rest when secondary gone
                    // leaveProgress 0 = secondary on top (underlay covered); 1 = secondary gone (underlay at rest).
                    val reveal = p
                    LayerTransform(
                        translationX = snapTranslation(-rtlSign * (1f - reveal) * widthPx * 0.25f, 1f, widthPx),
                        alpha = 1f - 0.1f * (1f - reveal),
                    )
                }
            }
        }

        PredictiveBackAnimation.MIUIX -> {
            // Covered parallax: quarter-width toward leading edge.
            val tx = -rtlSign * cover * widthPx * 0.25f
            LayerTransform(
                translationX = snapTranslation(tx, 1f, widthPx),
                alpha = 1f - 0.1f * cover,
            )
        }

        PredictiveBackAnimation.SCALE -> {
            // Scale pop leaves covered entry untransformed (InstallerX ScaleNavTransition).
            LayerTransform()
        }

        PredictiveBackAnimation.CLASSIC -> {
            // ClassicScalePop covered branch: slides in from -width as it is revealed.
            val tx = -rtlSign * cover * widthPx
            LayerTransform(translationX = snapTranslation(tx, 1f, widthPx))
        }

        PredictiveBackAnimation.AOSP -> {
            val gestureLike = phase == PredictiveNavPhase.Gesture ||
                phase == PredictiveNavPhase.Commit ||
                phase == PredictiveNavPhase.Cancel
            val driftPx = with(density) { CrossActivityDrift.toPx() }
            if (gestureLike) {
                val eased = shapedTopProgress(p, gestureDriven = true)
                val scale = snapScale(AOSP_MIN_SCALE + (1f - AOSP_MIN_SCALE) * eased, widthPx)
                val ty = crossActivityYShift(
                    gesture = true,
                    touchY = touchY,
                    initialTouchY = initialTouchY,
                    height = heightPx,
                    scale = scale,
                    density = density,
                )
                // Parked behind until post-commit sweep; during finger both layers scale in sync.
                val tx = when (phase) {
                    PredictiveNavPhase.Commit -> {
                        val post = settleEased
                        -driftPx + post * driftPx
                    }
                    else -> -driftPx
                }
                LayerTransform(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = snapTranslation(tx, scale, widthPx),
                    translationY = snapTranslation(ty, scale, heightPx),
                )
            } else {
                when (phase) {
                    PredictiveNavPhase.Pop -> {
                        // close_enter: from -96dp to rest.
                        val tx = -driftPx + p * driftPx
                        LayerTransform(translationX = snapTranslation(tx, 1f, widthPx))
                    }
                    PredictiveNavPhase.Push -> {
                        // open_exit: covered page slides toward -96dp.
                        val tx = -cover * driftPx
                        LayerTransform(translationX = snapTranslation(tx, 1f, widthPx))
                    }
                    PredictiveNavPhase.Idle -> LayerTransform()
                }
            }
        }
    }
}

/** InstallerX dimAmount = 0.5f times each transition's scrim fraction. */
fun predictiveScrimAlpha(
    animation: PredictiveBackAnimation,
    phase: PredictiveNavPhase,
    leaveProgress: Float,
    settleRaw: Float,
): Float {
    val p = leaveProgress.coerceIn(0f, 1f)
    val cover = 1f - p
    val fraction = when (animation) {
        PredictiveBackAnimation.NONE -> if (phase == PredictiveNavPhase.Gesture) 0f else cover
        PredictiveBackAnimation.MIUIX -> cover
        PredictiveBackAnimation.SCALE -> when (phase) {
            PredictiveNavPhase.Gesture -> 1f
            PredictiveNavPhase.Commit -> (1f - settleRaw).coerceIn(0f, 1f)
            else -> cover
        }
        PredictiveBackAnimation.CLASSIC -> cover
        PredictiveBackAnimation.AOSP -> when (phase) {
            PredictiveNavPhase.Gesture -> 1f
            PredictiveNavPhase.Commit -> (1f - settleRaw).coerceIn(0f, 1f)
            // ClassicActivityOpen/Close: scrim = 0f.
            PredictiveNavPhase.Pop, PredictiveNavPhase.Push -> 0f
            else -> cover
        }
    }
    return (0.5f * fraction).coerceIn(0f, 0.5f)
}

fun predictiveCardStyle(animation: PredictiveBackAnimation): Boolean =
    animation == PredictiveBackAnimation.SCALE ||
        animation == PredictiveBackAnimation.AOSP ||
        animation == PredictiveBackAnimation.CLASSIC

/**
 * Commit / cancel / programmatic animation specs matching InstallerX NavMotion.
 *
 * MiuixDefault push/pop (None / MIUIX / Scale push / Classic push): Tween(500, NavProgrammaticEasing).
 * AOSP programmatic open/close: Tween(450, FastOutExtraSlowIn).
 * Scale/Classic programmatic pop: Tween(200, CubicBezier(0.2,0,0,1)).
 */
fun predictiveSettleSpec(animation: PredictiveBackAnimation, phase: PredictiveNavPhase): AnimationSpec<Float> =
    when (phase) {
        PredictiveNavPhase.Commit -> when (animation) {
            PredictiveBackAnimation.SCALE,
            PredictiveBackAnimation.AOSP,
            -> tween(durationMillis = 450, easing = FastOutExtraSlowIn)
            PredictiveBackAnimation.CLASSIC -> tween(durationMillis = 200, easing = ProgrammaticEase)
            PredictiveBackAnimation.NONE -> tween(durationMillis = 450, easing = NavProgrammaticEasing)
            PredictiveBackAnimation.MIUIX -> spring(
                dampingRatio = 1f,
                stiffness = 146f,
            )
        }
        PredictiveNavPhase.Cancel -> when (animation) {
            PredictiveBackAnimation.MIUIX -> spring(dampingRatio = 1f, stiffness = 146f)
            else -> spring(dampingRatio = 1f, stiffness = 1500f)
        }
        PredictiveNavPhase.Pop -> when (animation) {
            // Scale/Classic pop use their own card transforms with 200ms programmatic tween.
            PredictiveBackAnimation.SCALE,
            PredictiveBackAnimation.CLASSIC,
            -> tween(durationMillis = 200, easing = ProgrammaticEase)
            PredictiveBackAnimation.AOSP -> tween(durationMillis = 450, easing = FastOutExtraSlowIn)
            // MiuixDefault-style pop (None + MIUIX).
            PredictiveBackAnimation.NONE,
            PredictiveBackAnimation.MIUIX,
            -> tween(durationMillis = 500, easing = NavProgrammaticEasing)
        }
        PredictiveNavPhase.Push -> when (animation) {
            PredictiveBackAnimation.AOSP -> tween(durationMillis = 450, easing = FastOutExtraSlowIn)
            // None / MIUIX / Scale / Classic push all use MiuixDefault programmatic motion.
            else -> tween(durationMillis = 500, easing = NavProgrammaticEasing)
        }
        else -> tween(durationMillis = 200)
    }

/** Wall-clock fraction used by AOSP/Scale alpha & scrim (maps settle duration → 450ms clock). */
fun predictiveWallClock(animation: PredictiveBackAnimation, phase: PredictiveNavPhase, easedOrRaw: Float): Float {
    val duration = when (phase) {
        PredictiveNavPhase.Commit -> when (animation) {
            PredictiveBackAnimation.SCALE, PredictiveBackAnimation.AOSP, PredictiveBackAnimation.NONE -> 450f
            PredictiveBackAnimation.CLASSIC -> 200f
            PredictiveBackAnimation.MIUIX -> 450f
        }
        PredictiveNavPhase.Pop -> when (animation) {
            PredictiveBackAnimation.SCALE, PredictiveBackAnimation.CLASSIC -> 200f
            PredictiveBackAnimation.AOSP, PredictiveBackAnimation.NONE -> 450f
            PredictiveBackAnimation.MIUIX -> 500f
        }
        else -> 450f
    }
    // Geometry easing already applied to leaveProgress; alpha tracks use a linear clock.
    return easedOrRaw.coerceIn(0f, 1f)
}

private fun aospBounce(gestureProgress: Float, velocity: Float, swipeEdge: Int): Float {
    val factor = if (swipeEdge == BackEventCompat.EDGE_LEFT || swipeEdge == BackEventCompat.EDGE_RIGHT) 2f else 1f
    val floorKick = if (gestureProgress < 0.1f) BOUNCE_MIN_KICK else 0f
    val kick = (velocity * 100f * (1f - AOSP_MIN_SCALE) * factor).coerceIn(floorKick, BOUNCE_MAX_KICK)
    if (kick <= 0f) return 1f
    val omega = sqrt(BOUNCE_STIFFNESS)
    val omegaD = omega * sqrt(1f - BOUNCE_DAMPING * BOUNCE_DAMPING)
    // Evaluate at a short post-commit window (~50ms) for a subtle dip.
    val t = 0.05f
    val overlay = -(kick / omegaD) * exp(-BOUNCE_DAMPING * omega * t) * sin(omegaD * t)
    return ((100f + overlay) / 100f).coerceAtMost(1f)
}

private fun settleDurationMillis(animation: PredictiveBackAnimation, phase: PredictiveNavPhase): Float =
    when (phase) {
        PredictiveNavPhase.Commit -> when (animation) {
            PredictiveBackAnimation.SCALE,
            PredictiveBackAnimation.AOSP,
            PredictiveBackAnimation.NONE,
            PredictiveBackAnimation.MIUIX,
            -> 450f
            PredictiveBackAnimation.CLASSIC -> 200f
        }
        PredictiveNavPhase.Cancel -> 320f
        PredictiveNavPhase.Pop -> when (animation) {
            PredictiveBackAnimation.SCALE, PredictiveBackAnimation.CLASSIC -> 200f
            PredictiveBackAnimation.AOSP -> 450f
            PredictiveBackAnimation.NONE, PredictiveBackAnimation.MIUIX -> 500f
        }
        PredictiveNavPhase.Push -> when (animation) {
            PredictiveBackAnimation.AOSP -> 450f
            else -> 500f
        }
        else -> 450f
    }

/** Drives [PredictiveBackDriver] settle progress with the animation-appropriate spec. */
suspend fun animatePredictiveSettle(
    driver: PredictiveBackDriver,
    animation: PredictiveBackAnimation,
    phase: PredictiveNavPhase,
) {
    val spec = predictiveSettleSpec(animation, phase)
    val durationMs = settleDurationMillis(animation, phase)
    val anim = Animatable(0f)
    val start = System.nanoTime()
    anim.animateTo(1f, spec) {
        val elapsedMs = (System.nanoTime() - start) / 1_000_000f
        val raw = (elapsedMs / durationMs).coerceIn(0f, 1f)
        driver.updateSettle(raw = raw, eased = value)
    }
}

/** Legacy helper kept for ContentTransform-based callers. */
fun secondaryNavScale(animation: PredictiveBackAnimation): Float = when (animation) {
    PredictiveBackAnimation.SCALE -> 0.92f
    PredictiveBackAnimation.CLASSIC -> 0.95f
    PredictiveBackAnimation.AOSP -> 0.96f
    PredictiveBackAnimation.MIUIX -> 1f
    PredictiveBackAnimation.NONE -> 1f
}

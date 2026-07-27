package com.urwah.dhikr

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.widget.NestedScrollView
import kotlin.math.abs

/**
 * طبقة تتحكم في تقليب الصفحات أفقيًا مع السماح بالتمرير الرأسي الطبيعي داخل كل صفحة.
 *
 * القاعدة الذهبية: الحركة الرأسية لا تتدخل أبدًا في الأفقية، والعكس صحيح. لتحقيق هذا
 * بشكل مستقر (وليس ترقيعيًا) تم التعامل مع مشكلتين جذريتين كانتا سبب التذبذب السابق:
 *
 * 1) تصنيف اتجاه اللفتة كان يُحسم بفرق بكسل واحد بين dx/dy، فأي سحبة بزاوية قريبة
 *    من 45° كانت تُصنَّف عشوائيًا. تم إضافة [directionBias] كهامش أمان واضح.
 *
 * 2) الأهم: NestedScrollView الداخلي يستدعي parent.requestDisallowInterceptTouchEvent(true)
 *    بمجرد أي اهتزاز رأسي بسيط، وهذا كان يمنع هذه الطبقة نهائيًا من اعتراض اللفتة لاحقًا
 *    حتى لو تبيّن أنها أفقية فعلاً. تم تجاوز هذا عبر override لـ
 *    requestDisallowInterceptTouchEvent بحيث يُتجاهل طلب "المنع" طالما لم نحسم
 *    اتجاه اللفتة بعد.
 */
class ReaderGestureLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var currentPageScrollView: NestedScrollView? = null
    private var isPageScrollable = false
    private var isAtTop = true
    private var isAtBottom = false

    private var onPageChangeRequested: ((direction: Int) -> Unit)? = null
    private var bounceAnimator: ValueAnimator? = null
    private var isBouncing = false

    /** يُستخدم لتعطيل التقليب كليًا أثناء حالات معينة (مثل فتح شريط البحث) */
    private var swipeEnabled = true

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minSwipeDistance: Int

    // هامش تحيّز يمنع تصنيف الاتجاه (أفقي/رأسي) بفارق بكسل واحد فقط عند الزوايا الملتبسة
    private val directionBias = 1.4f

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var gestureDecided = false
    private var isHorizontalGesture = false

    init {
        val density = resources.displayMetrics.density
        minSwipeDistance = (80 * density).toInt()
    }

    fun setup(onPageChange: (direction: Int) -> Unit) {
        onPageChangeRequested = onPageChange
    }

    /** تعطيل/تفعيل تقليب الصفحات بالكامل (مثلاً أثناء فتح شريط البحث أو الفهرس) */
    fun setSwipeEnabled(enabled: Boolean) {
        swipeEnabled = enabled
        if (!enabled) {
            gestureDecided = false
            isHorizontalGesture = false
        }
    }

    fun updateScrollState(scrollView: NestedScrollView?) {
        currentPageScrollView = scrollView
        scrollView?.post {
            val child = scrollView.getChildAt(0) ?: return@post
            val totalScrollable = child.height - scrollView.height
            isPageScrollable = totalScrollable > 0
            isAtBottom = scrollView.scrollY >= totalScrollable - 4
            isAtTop = scrollView.scrollY <= 4
        }
    }

    // الشرط منفصل الآن لكل اتجاه: التقدّم للصفحة التالية يشترط الوصول لأسفل الصفحة
    // الحالية، بينما الرجوع للصفحة السابقة يشترط الوجود في أعلى الصفحة الحالية فقط.
    private fun canSwipeToNext(): Boolean = !isPageScrollable || isAtBottom
    private fun canSwipeToPrevious(): Boolean = !isPageScrollable || isAtTop

    override fun requestDisallowInterceptTouchEvent(disallow: Boolean) {
        // لا نسمح لأي ابن (NestedScrollView) بمنع اعتراضنا للفتة طالما لم نحسم
        // اتجاهها بعد. بدون هذا الشرط، يستطيع الابن "قفل" اللمس لنفسه من أول
        // اهتزاز رأسي بسيط، قبل أن نكتشف أن اللفتة أفقية فعليًا.
        if (disallow && !gestureDecided) {
            return
        }
        super.requestDisallowInterceptTouchEvent(disallow)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!swipeEnabled || isBouncing) return false

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                downTime = System.currentTimeMillis()
                gestureDecided = false
                isHorizontalGesture = false
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!gestureDecided) {
                    val dx = abs(ev.x - downX)
                    val dy = abs(ev.y - downY)
                    if (dx > touchSlop || dy > touchSlop) {
                        gestureDecided = true
                        isHorizontalGesture = dx > dy * directionBias
                        if (!isHorizontalGesture) {
                            preventViewPagerIntercept()
                        }
                    }
                }
                return gestureDecided && isHorizontalGesture
            }
        }
        return false
    }

    /**
     * عند تصنيف اللفتة كرأسية، نمنع ViewPager2 (وداخله RecyclerView أفقي) من
     * اعتراض اللمس حتى يصل مباشرة إلى NestedScrollView دون تدخل. نستخدم
     * requestDisallowInterceptTouchEvent على الابن الداخلي (RecyclerView) لينتشر
     * التوقيع إلى الأب (ViewPager2).
     */
    private fun preventViewPagerIntercept() {
        for (i in 0 until childCount) {
            val vp = getChildAt(i) as? ViewGroup ?: continue
            for (j in 0 until vp.childCount) {
                (vp.getChildAt(j) as? ViewGroup)?.requestDisallowInterceptTouchEvent(true)
            }
        }
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!swipeEnabled) return false

        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (isHorizontalGesture) {
                    val dx = ev.x - downX
                    val movingToNext = dx > 0
                    val allowed = if (movingToNext) canSwipeToNext() else canSwipeToPrevious()
                    if (!allowed) {
                        showBounce(dx)
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isBouncing) {
                    gestureDecided = false
                    isHorizontalGesture = false
                    return true
                }

                val dx = ev.x - downX
                val elapsed = System.currentTimeMillis() - downTime
                val distance = abs(dx)
                val velocity = distance / (elapsed.coerceAtLeast(1)).toFloat() * 1000f

                val isFastSwipe = velocity > 600f
                val isFarEnough = distance > minSwipeDistance
                val wantsSwipe = isHorizontalGesture && (isFastSwipe || isFarEnough)

                if (wantsSwipe) {
                    if (dx > 0 && canSwipeToNext()) {
                        onPageChangeRequested?.invoke(1)
                    } else if (dx < 0 && canSwipeToPrevious()) {
                        onPageChangeRequested?.invoke(-1)
                    }
                }

                gestureDecided = false
                isHorizontalGesture = false
            }
        }
        return true
    }

    private fun showBounce(offsetX: Float) {
        val clampedOffset = (offsetX * 0.12f).coerceIn(-100f, 100f)
        if (abs(clampedOffset) < 1f) return
        isBouncing = true
        bounceAnimator?.cancel()
        bounceAnimator = ValueAnimator.ofFloat(clampedOffset, 0f).apply {
            duration = 280
            interpolator = DecelerateInterpolator(3f)
            addUpdateListener { translationX = it.animatedValue as Float }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    translationX = 0f
                    isBouncing = false
                }
            })
            start()
        }
    }
}

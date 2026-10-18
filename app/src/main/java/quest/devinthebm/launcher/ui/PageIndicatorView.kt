package quest.devinthebm.launcher.ui

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import quest.devinthebm.launcher.R

/**
 * A column of dots marking how many pages the app drawer has and which one is
 * focused. Lives in the rail to the right of the grid, centered vertically.
 */
class PageIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private var pageCount = 0
    private var currentPage = 0

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
    }

    fun setPageCount(count: Int) {
        if (count == pageCount) return
        pageCount = count
        removeAllViews()
        val size = resources.getDimensionPixelSize(R.dimen.page_dot_size)
        val margin = resources.getDimensionPixelSize(R.dimen.page_dot_margin)
        repeat(pageCount) {
            val dot = View(context)
            val params = LayoutParams(size, size)
            params.topMargin = margin
            params.bottomMargin = margin
            dot.layoutParams = params
            addView(dot)
        }
        updateDots()
    }

    fun setCurrentPage(page: Int) {
        if (page == currentPage) return
        currentPage = page
        updateDots()
    }

    private fun updateDots() {
        for (i in 0 until childCount) {
            getChildAt(i).setBackgroundResource(
                if (i == currentPage) R.drawable.dot_indicator_active else R.drawable.dot_indicator_inactive,
            )
        }
    }
}

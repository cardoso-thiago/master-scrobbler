package com.kanedasoftware.masterscrobbler.components

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.GridView

class WrappedGridView : GridView {
    constructor(context: Context) : super(context) {}

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {}

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {}

    public override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val expandSpec = MeasureSpec.makeMeasureSpec(View.MEASURED_SIZE_MASK, MeasureSpec.AT_MOST)
        super.onMeasure(widthMeasureSpec, expandSpec)

        val params = layoutParams
        params.height = measuredHeight
    }
}
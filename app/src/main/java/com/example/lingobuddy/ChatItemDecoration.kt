package com.example.lingobuddypck

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class ChatItemDecoration(private val spaceHeight: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        outRect.bottom = spaceHeight // Tạo khoảng cách dưới mỗi item
    }
}
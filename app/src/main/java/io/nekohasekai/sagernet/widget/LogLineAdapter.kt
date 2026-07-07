package io.nekohasekai.sagernet.widget

import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ktx.dp2px

class LogLineAdapter : RecyclerView.Adapter<LogLineAdapter.Holder>() {

    private val lines = mutableListOf<CharSequence>()

    fun append(line: CharSequence) {
        lines.add(line)
        notifyItemInserted(lines.lastIndex)
    }

    override fun getItemCount(): Int = lines.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val textView = TextView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            val typedArray = context.theme.obtainStyledAttributes(
                intArrayOf(R.attr.textAppearanceBody2),
            )
            val style = typedArray.getResourceId(0, 0)
            typedArray.recycle()
            if (style != 0) {
                setTextAppearance(style)
            }
            setLineSpacing(dp2px(2).toFloat(), 1f)
        }
        return Holder(textView)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.text.text = lines[position]
    }

    class Holder(val text: TextView) : RecyclerView.ViewHolder(text)
}

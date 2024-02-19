package io.github.auag0.mocklocationdetector

import android.annotation.SuppressLint
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textview.MaterialTextView

class ResultAdapter : RecyclerView.Adapter<ResultAdapter.ViewHolder>() {
    var results: List<DetectionResult> = emptyList()
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_detection_result, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int {
        return results.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(results[position])
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val status: ImageView = itemView.findViewById(R.id.status)
        private val title: MaterialTextView = itemView.findViewById(R.id.title)
        private val content: MaterialTextView = itemView.findViewById(R.id.content)
        fun bind(result: DetectionResult) {
            if(result.isDetected) {
                status.setImageResource(R.drawable.ic_error_circle)
            } else {
                status.setImageResource(R.drawable.ic_check_circle)
            }
            title.text = result.title
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                title.tooltipText = title.text
            }
            content.text = result.content
        }
    }
}
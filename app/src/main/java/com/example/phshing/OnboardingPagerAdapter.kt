package com.example.phshing

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.phshing.R
import java.util.ArrayList

class OnboardingPagerAdapter : RecyclerView.Adapter<OnboardingPagerAdapter.OnboardingViewHolder>() {

    // Data for each onboarding page
    private val pages: ArrayList<OnboardingPage> = ArrayList<OnboardingPage>()
    
    // Initialize pages in constructor
    init {
        pages.add(OnboardingPage(
            R.drawable.secure_transaction_icon,
            R.string.onboarding_title_1,
            R.string.onboarding_desc_1
        ))
        pages.add(OnboardingPage(
            R.drawable.url_scanning_icon,
            R.string.onboarding_title_2,
            R.string.onboarding_desc_2
        ))
        pages.add(OnboardingPage(
            R.drawable.secure_transaction_icon,
            R.string.onboarding_title_3,
            R.string.onboarding_desc_3
        ))
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OnboardingViewHolder {
        return OnboardingViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.onboarding_page, parent, false
            )
        )
    }

    override fun onBindViewHolder(holder: OnboardingViewHolder, position: Int) {
        holder.bind(pages[position])
    }

    override fun getItemCount(): Int = pages.size

    inner class OnboardingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconImage: ImageView = itemView.findViewById(R.id.icon_image)
        private val titleText: TextView = itemView.findViewById(R.id.title_text)
        private val descriptionText: TextView = itemView.findViewById(R.id.description_text)

        fun bind(page: OnboardingPage) {
            iconImage.setImageResource(page.iconResId)
            titleText.setText(page.titleResId)
            descriptionText.setText(page.descriptionResId)
        }
    }

    data class OnboardingPage(
        val iconResId: Int,
        val titleResId: Int,
        val descriptionResId: Int
    )
} 
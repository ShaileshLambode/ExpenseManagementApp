package com.example.expensemanagementapp.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.expensemanagementapp.R

class OnboardingSlideFragment : Fragment() {

    private var position: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            position = it.getInt(ARG_POSITION)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_onboarding_slide, container, false)
        
        val ivImage = view.findViewById<ImageView>(R.id.ivSlideImage)
        val tvTitle = view.findViewById<TextView>(R.id.tvSlideTitle)
        val tvDesc = view.findViewById<TextView>(R.id.tvSlideDesc)

        when (position) {
            0 -> {
                ivImage.setImageResource(R.drawable.ic_onboarding_1)
                tvTitle.text = "Welcome to Mulya 2.0"
                tvDesc.text = "Track multiple budgets (Personal, Savings, Business) in one powerful app."
            }
            1 -> {
                ivImage.setImageResource(R.drawable.ic_onboarding_2)
                tvTitle.text = "Smart Analytics"
                tvDesc.text = "Visualize your spending patterns with detailed category breakdowns and trends."
            }
            2 -> {
                ivImage.setImageResource(R.drawable.ic_onboarding_3)
                tvTitle.text = "Achieve Your Goals"
                tvDesc.text = "Set financial targets and watch your progress as you save for what matters."
            }
        }
        
        return view
    }

    companion object {
        private const val ARG_POSITION = "position"

        @JvmStatic
        fun newInstance(position: Int) =
            OnboardingSlideFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_POSITION, position)
                }
            }
    }
}

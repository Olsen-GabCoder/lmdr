// PRÊT À COLLER - Créez le nouveau fichier RatingDialogFragment.kt
package com.lesmangeursdurouleau.app.ui.members

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.databinding.RatingDialogFragmentBinding

class RatingDialogFragment : DialogFragment() {

    private var _binding: RatingDialogFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = RatingDialogFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val bookTitle = arguments?.getString(ARG_BOOK_TITLE) ?: ""
        val currentRating = arguments?.getFloat(ARG_CURRENT_RATING) ?: 0f

        binding.tvRatingDialogBookTitle.text = bookTitle
        binding.ratingBarInput.rating = currentRating

        binding.btnRatingCancel.setOnClickListener {
            dismiss()
        }

        binding.btnRatingSubmit.setOnClickListener {
            val newRating = binding.ratingBarInput.rating
            setFragmentResult(REQUEST_KEY, bundleOf(RESULT_KEY_RATING to newRating))
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "RatingDialogFragment"
        const val REQUEST_KEY = "rating_request"
        const val RESULT_KEY_RATING = "new_rating"

        private const val ARG_BOOK_TITLE = "book_title"
        private const val ARG_CURRENT_RATING = "current_rating"

        fun newInstance(bookTitle: String, currentRating: Float): RatingDialogFragment {
            return RatingDialogFragment().apply {
                arguments = bundleOf(
                    ARG_BOOK_TITLE to bookTitle,
                    ARG_CURRENT_RATING to currentRating
                )
            }
        }
    }
}
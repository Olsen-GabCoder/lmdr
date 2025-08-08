// PRÊT À COLLER - Remplacez tout le contenu de votre fichier EditCurrentReadingFragment.kt
package com.lesmangeursdurouleau.app.ui.readings

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.snackbar.Snackbar
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.databinding.FragmentEditCurrentReadingBinding
import com.lesmangeursdurouleau.app.ui.readings.selection.BookSelectionFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EditCurrentReadingFragment : Fragment() {

    private var _binding: FragmentEditCurrentReadingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EditCurrentReadingViewModel by viewModels()

    companion object {
        private const val TAG = "EditCurrentReadingFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditCurrentReadingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupObservers()
        setupClickListeners()
        setupInputListeners()
        setupBookSelectionResultListener()
    }

    private fun setupBookSelectionResultListener() {
        setFragmentResultListener(BookSelectionFragment.KEY_REQUEST_BOOK_SELECTION) { _, bundle ->
            val selectedBook: Book? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bundle.getParcelable(BookSelectionFragment.KEY_SELECTED_BOOK, Book::class.java)
            } else {
                @Suppress("DEPRECATION")
                bundle.getParcelable(BookSelectionFragment.KEY_SELECTED_BOOK)
            }
            selectedBook?.let { viewModel.setSelectedBook(it) }
        }
    }

    private fun setupToolbar() {
        binding.toolbarEditReading.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { uiState ->
                    showLoading(uiState.isLoading)
                    enableForm(!uiState.isLoading)

                    if (uiState.error != null) {
                        Snackbar.make(binding.root, uiState.error, Snackbar.LENGTH_LONG).show()
                    }

                    populateForm(uiState)

                    val hasBook = uiState.libraryEntry != null || uiState.selectedBook != null
                    binding.btnRemoveReading.text = if (hasBook) {
                        getString(R.string.remove_reading_button)
                    } else {
                        getString(R.string.add_reading_button)
                    }
                    binding.btnSaveReading.isEnabled = !uiState.isLoading && hasBook
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collectLatest { event ->
                    when (event) {
                        is EditReadingEvent.ShowToast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                        is EditReadingEvent.NavigateBack -> findNavController().navigateUp()
                        is EditReadingEvent.ShowDeleteConfirmationDialog -> showDeleteConfirmationDialog()
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnSelectBook.setOnClickListener {
            val action = EditCurrentReadingFragmentDirections.actionEditCurrentReadingFragmentToBookSelectionFragment()
            findNavController().navigate(action)
        }

        binding.btnSaveReading.setOnClickListener {
            val currentPageText = binding.etCurrentPageInput.text.toString()
            val totalPagesText = binding.etTotalPagesInput.text.toString()
            // NOTE: Les champs 'favoriteQuote' et 'personalReflection' ont été retirés du modèle UserLibraryEntry pour le moment.
            // Si besoin, ils devront être ajoutés au modèle et gérés ici.
            viewModel.saveReadingEntry(currentPageText, totalPagesText)
        }

        binding.btnRemoveReading.setOnClickListener {
            val uiState = viewModel.uiState.value
            if (uiState.libraryEntry != null || uiState.selectedBook != null) {
                viewModel.confirmRemoveReading()
            } else {
                binding.btnSelectBook.performClick()
            }
        }
    }

    private fun setupInputListeners() {
        binding.etCurrentPageInput.doAfterTextChanged { binding.tilCurrentPage.error = null }
        binding.etTotalPagesInput.doAfterTextChanged { binding.tilTotalPages.error = null }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBarEditReading.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun enableForm(enable: Boolean) {
        binding.btnSelectBook.isEnabled = enable
        binding.etCurrentPageInput.isEnabled = enable
        binding.etTotalPagesInput.isEnabled = enable
        // Les champs 'favoriteQuote' et 'personalReflection' n'existent plus dans le layout par défaut.
        // Si vous les avez, leurs lignes correspondantes seraient ici:
        // binding.etFavoriteQuoteInput.isEnabled = enable
        // binding.etPersonalReflectionInput.isEnabled = enable
    }

    private fun populateForm(uiState: EditReadingUiState) {
        val bookToDisplay = uiState.selectedBook ?: uiState.bookDetails

        if (bookToDisplay != null) {
            Glide.with(this)
                .load(bookToDisplay.coverImageUrl)
                .placeholder(R.drawable.ic_book_placeholder)
                .error(R.drawable.ic_book_placeholder_error)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(binding.ivSelectedBookCover)

            binding.tvSelectedBookTitle.text = bookToDisplay.title.ifBlank { getString(R.string.title_not_available) }
            binding.tvSelectedBookAuthor.text = bookToDisplay.author.ifBlank { getString(R.string.author_not_available) }
            binding.btnSelectBook.text = getString(R.string.change_book_button)
        } else {
            binding.ivSelectedBookCover.setImageResource(R.drawable.ic_book_placeholder)
            binding.tvSelectedBookTitle.text = getString(R.string.no_book_selected_title)
            binding.tvSelectedBookAuthor.text = getString(R.string.select_a_book_prompt)
            binding.btnSelectBook.text = getString(R.string.select_book_button)
        }

        // Pré-remplir les champs de progression
        val libraryEntry = uiState.libraryEntry
        if (uiState.selectedBook == null && libraryEntry != null) {
            binding.etCurrentPageInput.setText(libraryEntry.currentPage.toString())
            binding.etTotalPagesInput.setText(libraryEntry.totalPages.toString())
        } else {
            // Si un nouveau livre est sélectionné ou s'il n'y a pas d'entrée, on vide les champs.
            binding.etCurrentPageInput.setText("")
            binding.etTotalPagesInput.setText("")
        }
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.confirm_removal_title))
            .setMessage(getString(R.string.confirm_removal_message))
            .setPositiveButton(getString(R.string.confirm_button)) { dialog, _ ->
                viewModel.removeReadingEntry()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel_button)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
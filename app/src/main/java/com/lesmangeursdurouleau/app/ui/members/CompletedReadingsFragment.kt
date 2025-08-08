// PRÊT À COLLER - Remplacez tout le contenu de votre fichier CompletedReadingsFragment.kt
package com.lesmangeursdurouleau.app.ui.members

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.databinding.FragmentCompletedReadingsBinding
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CompletedReadingsFragment : Fragment() {

    private var _binding: FragmentCompletedReadingsBinding? = null
    private val binding get() = _binding!!

    private val args: CompletedReadingsFragmentArgs by navArgs()
    private val viewModel: CompletedReadingsViewModel by viewModels()
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private lateinit var completedReadingsAdapter: CompletedReadingsAdapter

    companion object {
        private const val TAG = "CompletedReadingsFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCompletedReadingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateActionBarTitle(args.username)
        setupRecyclerView()
        setupSortChips()
        setupObservers()
    }

    private fun setupRecyclerView() {
        val currentUserId = firebaseAuth.currentUser?.uid
        completedReadingsAdapter = CompletedReadingsAdapter(
            currentUserId = currentUserId,
            profileOwnerId = args.userId,
            onItemClickListener = { item, _ ->
                item.book?.let { book ->
                    Log.d(TAG, "Clic sur la lecture terminée : ${book.title}")
                    val action = CompletedReadingsFragmentDirections.actionCompletedReadingsFragmentToCompletedReadingDetailFragment(
                        userId = args.userId,
                        bookId = book.id,
                        username = args.username,
                        bookTitle = book.title
                    )
                    findNavController().navigate(action)
                } ?: Toast.makeText(context, "Détails du livre non disponibles.", Toast.LENGTH_SHORT).show()
            },
            onDeleteClickListener = { item ->
                showDeleteConfirmationDialog(item)
            }
        )

        binding.rvCompletedReadings.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = completedReadingsAdapter
        }
    }

    private fun setupSortChips() {
        binding.sortChipGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.chip_sort_date_desc -> viewModel.setSortOption("lastReadDate", SortDirection.DESCENDING)
                R.id.chip_sort_date_asc -> viewModel.setSortOption("lastReadDate", SortDirection.ASCENDING)
                R.id.chip_sort_title_asc -> viewModel.setSortOption("title", SortDirection.ASCENDING)
                R.id.chip_sort_author_asc -> viewModel.setSortOption("author", SortDirection.ASCENDING)
            }
        }
    }

    private fun showDeleteConfirmationDialog(item: CompletedReadingUiModel) {
        val bookTitle = item.book?.title ?: getString(R.string.unknown_book_title)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_reading_dialog_title)
            .setMessage(getString(R.string.delete_reading_dialog_message, bookTitle))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.removeReadingFromLibrary(item.libraryEntry.bookId)
            }
            .show()
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.completedReadings.collectLatest { resource ->
                        val showToolbar = resource is Resource.Success && !resource.data.isNullOrEmpty()
                        binding.sortToolbar.isVisible = showToolbar
                        binding.divider.isVisible = showToolbar

                        binding.progressBarCompletedReadings.isVisible = resource is Resource.Loading
                        binding.rvCompletedReadings.isVisible = resource is Resource.Success && !resource.data.isNullOrEmpty()

                        val isSuccessAndEmpty = resource is Resource.Success && resource.data.isNullOrEmpty()
                        binding.tvNoCompletedReadings.isVisible = isSuccessAndEmpty
                        if (isSuccessAndEmpty) {
                            binding.tvNoCompletedReadings.text = getString(R.string.no_completed_readings_yet, args.username ?: "cet utilisateur")
                        }

                        binding.tvCompletedReadingsError.isVisible = resource is Resource.Error

                        when (resource) {
                            is Resource.Success -> {
                                completedReadingsAdapter.submitList(resource.data)
                            }
                            is Resource.Error -> {
                                binding.tvCompletedReadingsError.text = resource.message ?: getString(R.string.error_unknown)
                            }
                            is Resource.Loading -> { /* Géré par isVisible */ }
                        }
                    }
                }

                launch {
                    viewModel.deleteStatus.collectLatest { resource ->
                        when (resource) {
                            is Resource.Success -> Toast.makeText(context, R.string.reading_deleted_successfully, Toast.LENGTH_SHORT).show()
                            is Resource.Error -> Toast.makeText(context, resource.message, Toast.LENGTH_LONG).show()
                            else -> { /* No-op */ }
                        }
                    }
                }
            }
        }
    }

    private fun updateActionBarTitle(username: String?) {
        val title = username?.takeIf { it.isNotBlank() }?.let {
            getString(R.string.completed_readings_title_format, it)
        } ?: getString(R.string.title_completed_readings)
        (activity as? AppCompatActivity)?.supportActionBar?.title = title
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvCompletedReadings.adapter = null
        _binding = null
    }
}
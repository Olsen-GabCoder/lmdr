// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier ReadingsFragment.kt
package com.lesmangeursdurouleau.app.ui.readings

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.databinding.FragmentReadingsBinding
import com.lesmangeursdurouleau.app.ui.readings.adapter.MonthlyReadingListAdapter
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@AndroidEntryPoint
class ReadingsFragment : Fragment() {

    private var _binding: FragmentReadingsBinding? = null
    private val binding get() = _binding!!

    private val readingsViewModel: ReadingsViewModel by viewModels()
    private lateinit var monthlyReadingListAdapter: MonthlyReadingListAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReadingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupClickListeners()
        setupObservers()
    }

    private fun setupRecyclerView() {
        monthlyReadingListAdapter = MonthlyReadingListAdapter(
            // JUSTIFICATION DE LA MODIFICATION : La lambda `onEditClicked` est supprimée.
            // L'édition est une action administrative qui n'a plus sa place dans cette interface de consultation.
            // Elle sera ré-implémentée dans le backoffice.
            onLikeClicked = {
                Toast.makeText(requireContext(), "Fonctionnalité 'J'aime' à venir", Toast.LENGTH_SHORT).show()
            },
            onCommentClicked = {
                Toast.makeText(requireContext(), "Fonctionnalité 'Commenter' à venir", Toast.LENGTH_SHORT).show()
            },
            onJoinClicked = { meetingLink ->
                openMeetingLink(meetingLink)
            },
            onBookCoverClicked = { bookId, bookTitle ->
                val action = ReadingsFragmentDirections.actionReadingsFragmentToBookDetailFragment(
                    bookId = bookId,
                    bookTitle = bookTitle ?: getString(R.string.book_details_default_title)
                )
                findNavController().navigate(action)
            }
        )

        binding.recyclerViewMonthlyReadings.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = monthlyReadingListAdapter
        }
    }

    private fun openMeetingLink(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("ReadingsFragment", "Could not open meeting link: $url", e)
            Toast.makeText(requireContext(), "Impossible d'ouvrir le lien de la réunion.", Toast.LENGTH_SHORT).show()
        }
    }

    // JUSTIFICATION DE LA SUPPRESSION : Ces méthodes étaient liées à la logique de permission
    // et de navigation vers l'écran d'édition. Cette responsabilité est entièrement retirée
    // de ce fragment pour être déplacée dans le backoffice.
    // private fun checkEditPermissionAndNavigate(...) { ... }
    // private fun navigateToAddEditMonthlyReading(...) { ... }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    readingsViewModel.monthlyReadingsWithBooks.collect { resource ->
                        binding.progressBarReadings.isVisible = resource is Resource.Loading
                        binding.recyclerViewMonthlyReadings.isVisible = resource is Resource.Success
                        binding.tvErrorReadings.isVisible = resource is Resource.Error

                        when (resource) {
                            is Resource.Success -> {
                                val data = resource.data ?: emptyList()
                                monthlyReadingListAdapter.submitList(data)
                                // Logique pour afficher "Aucune lecture" si la liste est vide.
                                binding.tvErrorReadings.isVisible = data.isEmpty()
                                binding.tvErrorReadings.text = getString(R.string.no_monthly_readings_available)
                            }
                            is Resource.Error -> {
                                binding.tvErrorReadings.text = resource.message ?: getString(R.string.error_loading_monthly_readings, "inconnu")
                            }
                            is Resource.Loading -> {
                                binding.tvErrorReadings.isVisible = false
                            }
                        }
                    }
                }

                // JUSTIFICATION DE LA SUPPRESSION : L'observation de `canEditReadings` est retirée
                // car le FAB a été supprimé de la vue.
                /*
                launch {
                    readingsViewModel.canEditReadings.collect { canEdit ->
                        binding.fabAddMonthlyReading.isVisible = canEdit
                        Log.d("ReadingsFragment", "Visibilité FAB admin mise à jour: $canEdit")
                    }
                }
                */

                launch {
                    readingsViewModel.currentMonthYear.collect { calendar ->
                        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                        binding.tvCurrentMonthYear.text = monthFormat.format(calendar.time)
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnPreviousMonth.setOnClickListener { readingsViewModel.goToPreviousMonth() }
        binding.btnNextMonth.setOnClickListener { readingsViewModel.goToNextMonth() }
        binding.chipGroupFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            val filter = when (checkedIds.firstOrNull()) {
                R.id.chip_filter_all -> ReadingsFilter.ALL
                R.id.chip_filter_in_progress -> ReadingsFilter.IN_PROGRESS
                R.id.chip_filter_planned -> ReadingsFilter.PLANNED
                R.id.chip_filter_past -> ReadingsFilter.PAST
                else -> ReadingsFilter.ALL
            }
            readingsViewModel.setFilter(filter)
        }
        // JUSTIFICATION DE LA SUPPRESSION : Le listener pour le FAB est retiré car le FAB n'existe plus.
        /*
        binding.fabAddMonthlyReading.setOnClickListener {
            checkEditPermissionAndNavigate(null)
        }
        */
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerViewMonthlyReadings.adapter = null
        _binding = null
    }
}
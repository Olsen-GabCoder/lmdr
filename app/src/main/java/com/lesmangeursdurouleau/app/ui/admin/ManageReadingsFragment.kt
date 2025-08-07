// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier ManageReadingsFragment.kt
package com.lesmangeursdurouleau.app.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.databinding.FragmentManageReadingsBinding
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ManageReadingsFragment : Fragment() {

    private var _binding: FragmentManageReadingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ManageReadingsViewModel by viewModels()
    private lateinit var adapter: ManageReadingsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManageReadingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
    }

    private fun setupRecyclerView() {
        adapter = ManageReadingsAdapter { readingWithBook ->
            // Le clic pour MODIFIER une lecture existante continue de mener à l'écran de planification.
            // C'est logique, car le livre est déjà défini.
            val action = ManageReadingsFragmentDirections
                .actionManageReadingsFragmentToAddEditMonthlyReadingFragment(
                    monthlyReadingId = readingWithBook.monthlyReading.id
                )
            findNavController().navigate(action)
        }
        binding.rvManageReadings.adapter = adapter
    }

    private fun setupListeners() {
        binding.fabAddReading.setOnClickListener {
            // JUSTIFICATION DE LA MODIFICATION : C'est la correction clé.
            // Au lieu de naviguer vers l'ancien formulaire, le bouton "+" (ajout)
            // navigue maintenant vers notre NOUVEL écran de gestion de livre (`ManageBookFragment`),
            // qui contient les fonctionnalités d'upload.
            val action = ManageReadingsFragmentDirections
                .actionManageReadingsFragmentToManageBookFragment()
            findNavController().navigate(action)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.readingsListState.collect { resource ->
                    binding.progressBar.isVisible = resource is Resource.Loading
                    binding.rvManageReadings.isVisible = resource is Resource.Success
                    binding.tvInfoState.isVisible = resource !is Resource.Loading

                    when(resource) {
                        is Resource.Success -> {
                            val data = resource.data ?: emptyList()
                            adapter.submitList(data)
                            binding.tvInfoState.isVisible = data.isEmpty()
                            binding.tvInfoState.text = getString(R.string.admin_no_readings_found)
                        }
                        is Resource.Error -> {
                            binding.tvInfoState.text = resource.message
                        }
                        is Resource.Loading -> {
                            binding.tvInfoState.isVisible = false
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
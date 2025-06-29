// PRÊT À COLLER - Fichier complet
package com.lesmangeursdurouleau.app.ui.members // ou com.lesmangeursdurouleau.app.ui.leaderboard

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
import androidx.recyclerview.widget.LinearLayoutManager
import com.lesmangeursdurouleau.app.data.model.Conversation
import com.lesmangeursdurouleau.app.databinding.FragmentLeaderboardBinding
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LeaderboardFragment : Fragment() {

    private var _binding: FragmentLeaderboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LeaderboardViewModel by viewModels()
    private lateinit var leaderboardAdapter: LeaderboardAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLeaderboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        observeLeaderboard()
    }

    private fun setupToolbar() {
        binding.toolbarLeaderboard.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupRecyclerView() {
        leaderboardAdapter = LeaderboardAdapter()
        binding.rvLeaderboard.apply {
            adapter = leaderboardAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    private fun observeLeaderboard() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.leaderboardState.collect { resource ->
                    handleUiState(resource)
                }
            }
        }
    }

    private fun handleUiState(resource: Resource<List<Conversation>>) {
        when (resource) {
            is Resource.Loading -> {
                binding.shimmerLayoutLeaderboard.startShimmer()
                binding.shimmerLayoutLeaderboard.isVisible = true
                binding.rvLeaderboard.isVisible = false
                binding.tvLeaderboardEmpty.isVisible = false
                binding.tvLeaderboardError.isVisible = false
            }
            is Resource.Success -> {
                binding.shimmerLayoutLeaderboard.stopShimmer()
                binding.shimmerLayoutLeaderboard.isVisible = false

                val data = resource.data
                if (data.isNullOrEmpty()) {
                    // État vide
                    binding.rvLeaderboard.isVisible = false
                    binding.tvLeaderboardEmpty.isVisible = true
                    binding.tvLeaderboardError.isVisible = false
                } else {
                    // État de succès avec données
                    binding.rvLeaderboard.isVisible = true
                    binding.tvLeaderboardEmpty.isVisible = false
                    binding.tvLeaderboardError.isVisible = false
                    leaderboardAdapter.submitList(data)
                }
            }
            is Resource.Error -> {
                binding.shimmerLayoutLeaderboard.stopShimmer()
                binding.shimmerLayoutLeaderboard.isVisible = false
                binding.rvLeaderboard.isVisible = false
                binding.tvLeaderboardEmpty.isVisible = false
                binding.tvLeaderboardError.isVisible = true
                binding.tvLeaderboardError.text = resource.message
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvLeaderboard.adapter = null // Éviter les fuites de mémoire
        _binding = null
    }
}
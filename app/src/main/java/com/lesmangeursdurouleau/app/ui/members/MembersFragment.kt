// Fichier Modifié : MembersFragment.kt

package com.lesmangeursdurouleau.app.ui.members

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.databinding.FragmentMembersBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MembersFragment : Fragment() {

    private var _binding: FragmentMembersBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MembersViewModel by viewModels()
    private val args: MembersFragmentArgs by navArgs()

    private lateinit var membersAdapter: MembersAdapter

    companion object {
        private const val TAG = "MembersFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMembersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated: Fragment créé. User ID: ${args.userId}, List Type: ${args.listType}, List Title: ${args.listTitle}")

        binding.tvMembersTitle.text = args.listTitle
        (activity as? AppCompatActivity)?.supportActionBar?.title = args.listTitle
        Log.i(TAG, "Titre du fragment et ActionBar mis à jour avec: '${args.listTitle}'")

        setupRecyclerView()
        setupSearch()
        observeUiState()
    }

    private fun setupRecyclerView() {
        membersAdapter = MembersAdapter { member ->
            val action = MembersFragmentDirections.actionMembersFragmentToPublicProfileFragment(
                userId = member.uid,
                username = member.username.ifEmpty { null }
            )
            findNavController().navigate(action)
            Log.d(TAG, "Navigation vers le profil public de : ${member.username} (UID: ${member.uid})")
        }
        binding.rvMembers.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = membersAdapter
            setHasFixedSize(false)
            val verticalSpacing = resources.getDimensionPixelSize(R.dimen.spacing_small)
            addItemDecoration(VerticalSpaceItemDecoration(verticalSpacing))
        }
    }

    /**
     * JUSTIFICATION DE L'AJOUT : Cette nouvelle fonction configure le listener sur
     * le champ de recherche. Elle notifie le ViewModel à chaque changement de texte.
     */
    private fun setupSearch() {
        binding.etSearchMembers.addTextChangedListener { text ->
            viewModel.onSearchQueryChanged(text.toString())
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collectLatest { state ->
                        handleUiState(state)
                    }
                }

                launch {
                    membersAdapter.loadStateFlow.collectLatest { loadStates ->
                        binding.progressBarMembers.isVisible = loadStates.refresh is LoadState.Loading
                        binding.rvMembers.isVisible = loadStates.refresh is LoadState.NotLoading

                        val refreshState = loadStates.refresh
                        if (refreshState is LoadState.Error) {
                            binding.tvErrorMessage.text = getString(R.string.error_loading_members, refreshState.error.localizedMessage)
                            Log.e(TAG, "Erreur de chargement Paging: ${refreshState.error.localizedMessage}")
                        }

                        // Gère le cas "pas de résultats" après un chargement réussi
                        if (loadStates.refresh is LoadState.NotLoading && membersAdapter.itemCount == 0) {
                            binding.tvErrorMessage.text = getString(R.string.no_members_found_for_search) // Nouvelle string à ajouter
                            binding.tvErrorMessage.isVisible = true
                            binding.rvMembers.isVisible = false
                        } else {
                            binding.tvErrorMessage.isVisible = refreshState is LoadState.Error
                        }
                    }
                }
            }
        }
    }

    private fun handleUiState(state: MembersUiState) {
        when (state) {
            is MembersUiState.PagedSuccess -> {
                viewLifecycleOwner.lifecycleScope.launch {
                    state.pagedUsers.collectLatest { pagingData ->
                        membersAdapter.submitData(pagingData)
                    }
                }
            }
            is MembersUiState.Success -> {
                Log.d(TAG, "Reçu une liste non-paginée qui ne peut être affichée par le PagingDataAdapter.")
                binding.progressBarMembers.isVisible = false
                binding.rvMembers.isVisible = true
                binding.tvErrorMessage.isVisible = state.users.isEmpty()
                if (state.users.isEmpty()) {
                    binding.tvErrorMessage.text = when (args.listType) {
                        "followers" -> getString(R.string.no_followers_found)
                        "following" -> getString(R.string.no_following_found)
                        else -> getString(R.string.no_members_found)
                    }
                }
            }
            is MembersUiState.Error -> {
                binding.progressBarMembers.isVisible = false
                binding.rvMembers.isVisible = false
                binding.tvErrorMessage.isVisible = true
                binding.tvErrorMessage.text = state.message
            }
            is MembersUiState.Loading -> {
                binding.progressBarMembers.isVisible = true
                binding.rvMembers.isVisible = false
                binding.tvErrorMessage.isVisible = false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvMembers.adapter = null
        _binding = null
        Log.d(TAG, "onDestroyView: Binding nulifié.")
    }
}

private class VerticalSpaceItemDecoration(private val verticalSpaceHeight: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        if (parent.getChildAdapterPosition(view) != 0) {
            outRect.top = verticalSpaceHeight
        }
    }
}
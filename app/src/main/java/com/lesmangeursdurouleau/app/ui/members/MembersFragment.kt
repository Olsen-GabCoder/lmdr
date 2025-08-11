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
        (activity as? AppCompatActivity)?.supportActionBar?.title = args.listTitle
        binding.tvMembersTitle.text = args.listTitle

        setupRecyclerView()
        setupSearch()
        observeUiState()
    }

    private fun setupRecyclerView() {
        // === DÉBUT DE LA MODIFICATION ===
        // JUSTIFICATION : L'initialisation de l'adapter est mise à jour pour correspondre
        // à sa nouvelle signature. On lui passe maintenant les callbacks pour les clics
        // sur l'item et sur les boutons "Suivre" / "Ne plus suivre".
        membersAdapter = MembersAdapter(
            onItemClick = { member ->
                val action = MembersFragmentDirections.actionMembersFragmentToPublicProfileFragment(
                    userId = member.uid,
                    username = member.username.ifEmpty { null }
                )
                findNavController().navigate(action)
            },
            onFollowClick = { member ->
                viewModel.followUser(member.uid)
            },
            onUnfollowClick = { member ->
                viewModel.unfollowUser(member.uid)
            }
        )
        // === FIN DE LA MODIFICATION ===

        binding.rvMembers.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = membersAdapter
            val verticalSpacing = resources.getDimensionPixelSize(R.dimen.spacing_small)
            addItemDecoration(VerticalSpaceItemDecoration(verticalSpacing))
        }
    }

    private fun setupSearch() {
        val isSearchable = args.listType == null
        binding.tilSearchMembers.isVisible = isSearchable

        if (isSearchable) {
            binding.etSearchMembers.addTextChangedListener { text ->
                viewModel.onSearchQueryChanged(text.toString())
            }
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
                        val refreshState = loadStates.refresh
                        binding.progressBarMembers.isVisible = refreshState is LoadState.Loading

                        if (refreshState is LoadState.Error) {
                            binding.tvErrorMessage.text = getString(R.string.error_loading_members, refreshState.error.localizedMessage)
                            binding.tvErrorMessage.isVisible = true
                            binding.rvMembers.isVisible = false
                        } else {
                            // On vérifie si la pagination est terminée et si la liste est vide
                            if (loadStates.append.endOfPaginationReached && membersAdapter.itemCount < 1) {
                                binding.tvErrorMessage.text = getEmptyListMessage()
                                binding.tvErrorMessage.isVisible = true
                                binding.rvMembers.isVisible = false
                            } else {
                                binding.tvErrorMessage.isVisible = false
                                binding.rvMembers.isVisible = true
                            }
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

    private fun getEmptyListMessage(): String {
        return when (args.listType) {
            "followers" -> getString(R.string.no_followers_found)
            "following" -> getString(R.string.no_following_found)
            else -> {
                if (binding.etSearchMembers.text?.isNotEmpty() == true) {
                    getString(R.string.no_members_found_for_search)
                } else {
                    getString(R.string.no_members_found)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvMembers.adapter = null
        _binding = null
    }
}

private class VerticalSpaceItemDecoration(private val verticalSpaceHeight: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        if (parent.getChildAdapterPosition(view) != 0) {
            outRect.top = verticalSpaceHeight
        }
    }
}
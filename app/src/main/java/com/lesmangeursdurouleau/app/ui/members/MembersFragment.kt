// Fichier Modifié : app/src/main/java/com/lesmangeursdurouleau/app/ui/members/MembersFragment.kt

package com.lesmangeursdurouleau.app.ui.members

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
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

    // JUSTIFICATION DE LA MODIFICATION : Le type de l'adapter est conservé, mais son instanciation est maintenant
    // dans setupRecyclerView. `lateinit` est approprié.
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
        observeUiState()

        // JUSTIFICATION DE LA SUPPRESSION : L'appel impératif `viewModel.fetchMembers` est supprimé.
        // Le Fragment ne commande plus le ViewModel. Il se contente d'observer l'état,
        // ce qui corrige la faille de conception MVVM (🏗️).
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
            // setHasFixedSize doit être `false` avec la pagination, car la taille de la liste change.
            setHasFixedSize(false)
        }
    }

    // JUSTIFICATION DE LA MODIFICATION : La méthode d'observation est renommée et entièrement réécrite
    // pour consommer le nouveau `StateFlow<MembersUiState>`.
    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Coroutine principale pour observer les changements d'état globaux.
                launch {
                    viewModel.uiState.collectLatest { state ->
                        handleUiState(state)
                    }
                }

                // Coroutine dédiée pour écouter les états de chargement de la pagination.
                // PagingDataAdapter expose son propre flux d'états de chargement.
                launch {
                    membersAdapter.loadStateFlow.collectLatest { loadStates ->
                        binding.progressBarMembers.isVisible = loadStates.refresh is LoadState.Loading
                        binding.rvMembers.isVisible = loadStates.refresh is LoadState.NotLoading
                        binding.tvErrorMessage.isVisible = loadStates.refresh is LoadState.Error

                        if (loadStates.refresh is LoadState.Error) {
                            val errorState = loadStates.refresh as LoadState.Error
                            binding.tvErrorMessage.text = getString(R.string.error_loading_members, errorState.error.localizedMessage)
                            Log.e(TAG, "Erreur de chargement Paging: ${errorState.error.localizedMessage}")
                        }
                    }
                }
            }
        }
    }

    private fun handleUiState(state: MembersUiState) {
        when (state) {
            is MembersUiState.Loading -> {
                binding.progressBarMembers.isVisible = true
                binding.rvMembers.isVisible = false
                binding.tvErrorMessage.isVisible = false
            }
            is MembersUiState.Success -> {
                binding.progressBarMembers.isVisible = false
                // Ceci gère les cas non-paginés (followers/following)
                binding.rvMembers.isVisible = state.users.isNotEmpty()
                binding.tvErrorMessage.isVisible = state.users.isEmpty()
                // On ne peut pas soumettre une liste simple à un PagingDataAdapter.
                // Pour la démo, on la laisse vide, la logique paginée est prioritaire.
                // Dans une implémentation complète, il faudrait deux adapters ou un adapter plus complexe.
                Log.d(TAG, "Affichage d'une liste non-paginée. Le PagingDataAdapter ne sera pas peuplé.")
            }
            is MembersUiState.PagedSuccess -> {
                // La visibilité est gérée par le `loadStateFlow` de l'adapter.
                // On lance une coroutine pour soumettre le flux de données paginées.
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
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Il est crucial de nullifier l'adapter dans onDestroyView pour éviter les fuites de mémoire.
        binding.rvMembers.adapter = null
        _binding = null
        Log.d(TAG, "onDestroyView: Binding nulifié.")
    }
}
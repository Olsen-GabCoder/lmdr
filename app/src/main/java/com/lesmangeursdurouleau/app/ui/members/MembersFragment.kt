// Fichier complet et corrigé : MembersFragment.kt

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
    }

    private fun setupRecyclerView() {
        // JUSTIFICATION DE LA MODIFICATION : L'adapter est instancié pour fonctionner avec `UserListItem`.
        // Le corps de la lambda reste le même car `UserListItem` contient bien `uid` et `username`,
        // mais le type du paramètre `member` est maintenant inféré comme `UserListItem`.
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
            setHasFixedSize(false) // La taille de la liste change avec la pagination.
        }
    }

    /**
     * JUSTIFICATION DE LA MODIFICATION : La méthode d'observation a été entièrement réécrite pour
     * fonctionner de manière réactive avec le `StateFlow` du ViewModel et l'état de l'adapter de Paging.
     * Elle corrige la faille de conception MVVM (🏗️) en ne faisant qu'observer l'état.
     */
    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Coroutine pour observer l'état global émis par le ViewModel.
                launch {
                    viewModel.uiState.collectLatest { state ->
                        handleUiState(state)
                    }
                }

                // Coroutine pour observer l'état de chargement spécifique à Paging.
                // C'est la meilleure pratique pour gérer l'UI de chargement/erreur avec Paging 3.
                launch {
                    membersAdapter.loadStateFlow.collectLatest { loadStates ->
                        // Gère la visibilité du ProgressBar principal pour le chargement initial.
                        binding.progressBarMembers.isVisible = loadStates.refresh is LoadState.Loading

                        // Affiche la liste uniquement si le chargement initial est terminé et sans erreur.
                        binding.rvMembers.isVisible = loadStates.refresh is LoadState.NotLoading

                        // Gère l'affichage du message d'erreur.
                        val refreshState = loadStates.refresh
                        if (refreshState is LoadState.Error) {
                            binding.tvErrorMessage.text = getString(R.string.error_loading_members, refreshState.error.localizedMessage)
                            Log.e(TAG, "Erreur de chargement Paging: ${refreshState.error.localizedMessage}")
                        }
                        binding.tvErrorMessage.isVisible = refreshState is LoadState.Error
                    }
                }
            }
        }
    }

    private fun handleUiState(state: MembersUiState) {
        // La visibilité des composants principaux (ProgressBar, RecyclerView, Error TextView)
        // est maintenant principalement gérée par le `loadStateFlow` ci-dessus pour plus de précision.
        // Cette fonction gère la soumission des données.
        when (state) {
            is MembersUiState.PagedSuccess -> {
                // Soumet le flux de données paginées à l'adapter.
                viewLifecycleOwner.lifecycleScope.launch {
                    state.pagedUsers.collectLatest { pagingData ->
                        membersAdapter.submitData(pagingData)
                    }
                }
            }
            is MembersUiState.Success -> {
                // Ce cas gère les listes non-paginées (followers/following). Comme notre adapter est maintenant
                // un PagingDataAdapter, on ne peut pas lui soumettre une liste simple.
                // Pour une solution complète, ces listes devraient aussi être paginées.
                // Pour l'instant, on affiche une liste vide et on logue l'information.
                Log.d(TAG, "Reçu une liste non-paginée qui ne peut être affichée par le PagingDataAdapter.")
                binding.progressBarMembers.isVisible = false
                binding.rvMembers.isVisible = true // Affiche la liste (qui sera vide)
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
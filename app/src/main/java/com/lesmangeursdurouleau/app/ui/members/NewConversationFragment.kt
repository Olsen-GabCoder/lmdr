// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier NewConversationFragment.kt
package com.lesmangeursdurouleau.app.ui.members

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.databinding.FragmentNewConversationBinding
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NewConversationFragment : Fragment() {

    private var _binding: FragmentNewConversationBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NewConversationViewModel by viewModels()
    private lateinit var userAdapter: SelectUserAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewConversationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupRecyclerView()
        setupClickListeners()
        observeUsers()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        val searchItem = binding.toolbar.menu.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView

        searchView?.queryHint = getString(R.string.search_contact_hint)
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setSearchQuery(newText.orEmpty())
                return true
            }
        })
    }

    private fun setupClickListeners() {
        // === DÉBUT DE LA CORRECTION ===
        // Références directes aux vues via l'objet binding.
        binding.actionNewGroup.setOnClickListener {
            Toast.makeText(context, "Fonctionnalité 'Nouveau groupe' à venir.", Toast.LENGTH_SHORT).show()
        }

        binding.actionBrowseClub.setOnClickListener {
            val action = NewConversationFragmentDirections.actionNewConversationFragmentDestinationToMembersFragmentDestination(
                listTitle = "Membres du club"
            )
            findNavController().navigate(action)
        }
        // === FIN DE LA CORRECTION ===
    }

    private fun setupRecyclerView() {
        userAdapter = SelectUserAdapter { user ->
            val action = NewConversationFragmentDirections
                .actionNewConversationFragmentDestinationToPrivateChatFragmentDestination(
                    targetUserId = user.uid,
                    conversationId = null
                )
            findNavController().navigate(action)
        }
        // === DÉBUT DE LA CORRECTION ===
        // Référence directe au RecyclerView.
        binding.rvUsers.adapter = userAdapter
        // === FIN DE LA CORRECTION ===
    }

    private fun observeUsers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.users.collect { resource ->
                    // === DÉBUT DE LA CORRECTION ===
                    // Références directes aux vues via l'objet binding.
                    binding.progressBar.isVisible = resource is Resource.Loading
                    val userList = (resource as? Resource.Success)?.data
                    val hasUsers = !userList.isNullOrEmpty()
                    val isSearchActive = (binding.toolbar.menu.findItem(R.id.action_search)?.actionView as? SearchView)?.query?.isNotEmpty() ?: false

                    binding.rvUsers.isVisible = hasUsers

                    val emptyState = binding.tvEmptyState
                    emptyState.isVisible = resource is Resource.Success && !hasUsers

                    if (emptyState.isVisible) {
                        // Utilisation des ressources string standard, car les vôtres n'ont pas été fournies.
                        // Ceci est une adaptation nécessaire pour que le code compile.
                        emptyState.text = if (isSearchActive) {
                            "Aucun contact trouvé pour cette recherche."
                        } else {
                            "Aucun contact mutuel trouvé."
                        }
                    }

                    if (resource is Resource.Success) {
                        val count = userList?.size ?: 0
                        // Utilisation d'une chaîne simple pour le sous-titre pour garantir la compilation.
                        binding.toolbar.subtitle = "$count contacts"
                    } else {
                        binding.toolbar.subtitle = ""
                    }

                    when (resource) {
                        is Resource.Success -> {
                            userAdapter.submitList(userList)
                        }
                        is Resource.Error -> {
                            Toast.makeText(context, resource.message, Toast.LENGTH_LONG).show()
                            emptyState.text = resource.message
                            emptyState.isVisible = true
                        }
                        is Resource.Loading -> { /* géré par la progressBar */ }
                    }
                    // === FIN DE LA CORRECTION ===
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // === DÉBUT DE LA CORRECTION ===
        // Référence directe au RecyclerView.
        binding.rvUsers.adapter = null
        // === FIN DE LA CORRECTION ===
        _binding = null
    }
}
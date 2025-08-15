// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier ForwardMessageFragment.kt
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
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.databinding.FragmentForwardMessageBinding
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ForwardMessageFragment : Fragment() {

    private var _binding: FragmentForwardMessageBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ForwardMessageViewModel by viewModels()
    private lateinit var destinationAdapter: ForwardDestinationAdapter

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    private val currentUserId: String by lazy { firebaseAuth.currentUser?.uid ?: "" }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentForwardMessageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupRecyclerView()
        setupSearch()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupRecyclerView() {
        destinationAdapter = ForwardDestinationAdapter(currentUserId) { conversation ->
            // Le clic déclenche maintenant la logique de transfert dans le ViewModel
            viewModel.forwardMessage(conversation)
        }
        binding.rvConversations.adapter = destinationAdapter
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setSearchQuery(newText.orEmpty())
                return true
            }
        })
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe la liste des conversations à afficher
                launch {
                    viewModel.conversations.collect { resource ->
                        binding.progressBar.isVisible = resource is Resource.Loading

                        val conversations = (resource as? Resource.Success)?.data
                        binding.rvConversations.isVisible = !conversations.isNullOrEmpty()
                        destinationAdapter.submitList(conversations)

                        binding.tvEmptyState.isVisible = conversations.isNullOrEmpty() && resource is Resource.Success

                        if (resource is Resource.Error) {
                            binding.tvEmptyState.text = resource.message
                            binding.tvEmptyState.isVisible = true
                        }
                    }
                }

                // Observe les événements d'envoi (succès ou erreur)
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is ForwardEvent.Success -> {
                                Toast.makeText(context, "Message transféré à ${event.conversationName}", Toast.LENGTH_SHORT).show()
                                // Navigue vers la liste principale des conversations après le succès
                                findNavController().popBackStack(R.id.conversationsListFragmentDestination, false)
                            }
                            is ForwardEvent.Error -> {
                                Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvConversations.adapter = null
        _binding = null
    }
}
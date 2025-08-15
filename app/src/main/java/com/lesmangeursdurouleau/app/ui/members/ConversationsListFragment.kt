// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier ConversationsListFragment.kt
package com.lesmangeursdurouleau.app.ui.members

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.Conversation
import com.lesmangeursdurouleau.app.databinding.FragmentConversationsListBinding
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ConversationsListFragment : Fragment(), ConversationAdapterListener {

    private var _binding: FragmentConversationsListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ConversationsViewModel by viewModels()
    private lateinit var conversationsAdapter: ConversationsAdapter

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    private val currentUserId: String by lazy { firebaseAuth.currentUser?.uid ?: "" }

    private var actionMode: ActionMode? = null

    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            mode?.menuInflater?.inflate(R.menu.conversations_contextual_menu, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            return false
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            val selectedConversations = conversationsAdapter.getSelectedConversations()
            if (selectedConversations.isEmpty()) return false

            when (item?.itemId) {
                R.id.action_pin -> {
                    viewModel.pinConversations(selectedConversations)
                    mode?.finish()
                    return true
                }
                R.id.action_archive -> {
                    viewModel.archiveConversations(selectedConversations)
                    mode?.finish()
                    return true
                }
                R.id.action_delete -> {
                    showDeleteConfirmationDialog(selectedConversations)
                    return true
                }
                R.id.action_favorite -> {
                    // === DÉBUT DE LA CORRECTION ===
                    viewModel.toggleFavoriteStatus(selectedConversations)
                    // === FIN DE LA CORRECTION ===
                    mode?.finish()
                    return true
                }
                else -> return false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            actionMode = null
            conversationsAdapter.clearSelection()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConversationsListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupUI()
        observeViewModel()

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (actionMode != null) {
                actionMode?.finish()
            } else {
                if (!findNavController().popBackStack()) {
                    requireActivity().finish()
                }
            }
        }
    }

    private fun setupUI() {
        binding.fabNewConversation.setOnClickListener {
            val action = ConversationsListFragmentDirections.actionConversationsListFragmentDestinationToNewConversationFragmentDestination()
            findNavController().navigate(action)
        }

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setSearchQuery(newText.orEmpty())
                return true
            }
        })

        binding.chipGroupFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                R.id.chip_unread -> viewModel.setFilter(ConversationFilterType.UNREAD)
                R.id.chip_favorites -> viewModel.setFilter(ConversationFilterType.FAVORITES)
                R.id.chip_groups -> viewModel.setFilter(ConversationFilterType.GROUPS)
                R.id.chip_archived -> viewModel.setFilter(ConversationFilterType.ARCHIVED)
                R.id.chip_pinned -> viewModel.setFilter(ConversationFilterType.PINNED)
                else -> viewModel.setFilter(ConversationFilterType.ALL)
            }
        }
    }

    private fun setupRecyclerView() {
        conversationsAdapter = ConversationsAdapter(currentUserId, this)
        binding.rvConversations.adapter = conversationsAdapter
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.conversations.collect { resource ->
                        binding.progressBar.isVisible = resource is Resource.Loading
                        val conversations = (resource as? Resource.Success)?.data
                        binding.rvConversations.isVisible = !conversations.isNullOrEmpty()
                        conversationsAdapter.submitList(conversations)
                        binding.tvEmptyState.isVisible = conversations.isNullOrEmpty() && resource is Resource.Success
                        if (resource is Resource.Error) {
                            binding.tvEmptyState.text = resource.message
                            binding.tvEmptyState.isVisible = true
                            Toast.makeText(context, resource.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }

                launch {
                    viewModel.unreadConversationsCount.collect { count ->
                        val unreadChip = binding.chipGroupFilters.findViewById<com.google.android.material.chip.Chip>(R.id.chip_unread)
                        unreadChip.text = if (count > 0) {
                            getString(R.string.filter_unread_with_count, count)
                        } else {
                            getString(R.string.filter_unread)
                        }
                    }
                }

                launch {
                    viewModel.events.collect { event ->
                        when(event) {
                            is ConversationListEvent.ShowSuccessMessage -> {
                                Snackbar.make(requireView(), event.message, Snackbar.LENGTH_SHORT).show()
                            }
                            is ConversationListEvent.ShowErrorMessage -> {
                                Snackbar.make(requireView(), event.message, Snackbar.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showDeleteConfirmationDialog(conversationsToDelete: List<Conversation>) {
        val count = conversationsToDelete.size
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(resources.getQuantityString(R.plurals.delete_conversations_dialog_title, count, count))
            .setMessage(R.string.delete_conversations_dialog_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                // === DÉBUT DE LA CORRECTION ===
                viewModel.deleteConversations(conversationsToDelete)
                // === FIN DE LA CORRECTION ===
                actionMode?.finish()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        actionMode?.finish()
        binding.rvConversations.adapter = null
        _binding = null
    }

    override fun onConversationClicked(conversation: Conversation) {
        if (conversationsAdapter.isInSelectionMode()) {
            conversationsAdapter.toggleSelection(conversation.id!!)
            onConversationSelected(conversation, conversationsAdapter.getSelectedConversations().contains(conversation))
        } else {
            val otherUserId = conversation.participantIds.firstOrNull { it != currentUserId }
            if (conversation.id != null && otherUserId != null) {
                val action = ConversationsListFragmentDirections
                    .actionConversationsListFragmentDestinationToPrivateChatFragmentDestination(
                        targetUserId = otherUserId,
                        conversationId = conversation.id
                    )
                findNavController().navigate(action)
            } else {
                Toast.makeText(context, "Impossible d'ouvrir la conversation.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onConversationLongClicked(conversation: Conversation) {
        if (actionMode == null) {
            actionMode = (activity as? AppCompatActivity)?.startSupportActionMode(actionModeCallback)
        }
        conversationsAdapter.startSelectionMode(conversation)
        updateActionModeTitle()
    }

    override fun onConversationSelected(conversation: Conversation, isSelected: Boolean) {
        if (conversationsAdapter.getSelectedConversations().isEmpty()) {
            actionMode?.finish()
        } else {
            updateActionModeTitle()
        }
    }

    private fun updateActionModeTitle() {
        val count = conversationsAdapter.getSelectedConversations().size
        actionMode?.title = resources.getQuantityString(R.plurals.conversations_selected_count, count, count)
    }
}
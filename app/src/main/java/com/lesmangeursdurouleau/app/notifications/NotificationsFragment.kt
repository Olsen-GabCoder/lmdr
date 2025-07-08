// PRÊT À COLLER - Fichier NotificationsFragment.kt CORRIGÉ
package com.lesmangeursdurouleau.app.notifications

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
import androidx.recyclerview.widget.DividerItemDecoration
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.NotificationType
import com.lesmangeursdurouleau.app.databinding.FragmentNotificationsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NotificationsViewModel by viewModels()
    private lateinit var notificationsAdapter: NotificationsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        // Infler le menu dans la toolbar
        binding.toolbarNotifications.inflateMenu(R.menu.notifications_menu)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupRecyclerView()
        observeUiState()
    }

    private fun setupToolbar() {
        binding.toolbarNotifications.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_mark_all_as_read -> {
                    viewModel.markAllAsRead()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupRecyclerView() {
        notificationsAdapter = NotificationsAdapter { notification ->
            // D'abord, on notifie le ViewModel du clic pour marquer comme lu
            viewModel.onNotificationClicked(notification)

            // Ensuite, on gère la navigation
            when (notification.type) {
                NotificationType.NEW_FOLLOWER,
                NotificationType.LIKE_ON_READING,
                NotificationType.COMMENT_ON_READING -> {
                    // CORRECTION : Utilisation de la bonne action de navigation définie dans le graphe
                    val action = NotificationsFragmentDirections.actionNotificationsFragmentToPublicProfileFragment(
                        userId = notification.actorId,
                        username = notification.actorUsername
                    )
                    findNavController().navigate(action)
                }
                else -> {
                    // Gérer d'autres types de navigation ici, par exemple vers un livre ou une conversation.
                }
            }
        }

        binding.rvNotifications.apply {
            adapter = notificationsAdapter
            val divider = DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
            addItemDecoration(divider)
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collectLatest { state ->
                    binding.progressBarNotifications.isVisible = state.isLoading

                    val menu = binding.toolbarNotifications.menu
                    val markAllAsReadItem = menu.findItem(R.id.action_mark_all_as_read)

                    if (state.error != null) {
                        binding.rvNotifications.isVisible = false
                        binding.tvNotificationsInfo.isVisible = true
                        binding.tvNotificationsInfo.text = state.error
                        markAllAsReadItem?.isVisible = false
                    } else if (!state.isLoading && state.notifications.isEmpty()) {
                        binding.rvNotifications.isVisible = false
                        binding.tvNotificationsInfo.isVisible = true
                        binding.tvNotificationsInfo.text = getString(R.string.no_notifications_yet)
                        markAllAsReadItem?.isVisible = false
                    } else {
                        binding.tvNotificationsInfo.isVisible = false
                        binding.rvNotifications.isVisible = true
                        notificationsAdapter.submitList(state.notifications)

                        // Gérer la visibilité de l'action "Tout marquer comme lu"
                        val hasUnread = state.notifications.any { !it.isRead }
                        markAllAsReadItem?.isVisible = hasUnread
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvNotifications.adapter = null
        _binding = null
    }
}
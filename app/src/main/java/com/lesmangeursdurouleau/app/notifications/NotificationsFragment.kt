// PRÊT À COLLER - Fichier NotificationsFragment.kt CORRIGÉ
package com.lesmangeursdurouleau.app.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.Notification
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
            // D'abord, on notifie le ViewModel du clic pour marquer comme lu (si nécessaire)
            viewModel.onNotificationClicked(notification)

            // Ensuite, on gère la navigation de manière intelligente
            handleNavigation(notification)
        }

        binding.rvNotifications.apply {
            adapter = notificationsAdapter
            val divider = DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
            addItemDecoration(divider)
        }
    }

    private fun handleNavigation(notification: Notification) {
        when (notification.type) {
            NotificationType.NEW_FOLLOWER -> {
                // Cette navigation via Safe Args fonctionne, nous la conservons.
                val action = NotificationsFragmentDirections.actionNotificationsFragmentToPublicProfileFragment(
                    userId = notification.actorId,
                    username = notification.actorUsername
                )
                findNavController().navigate(action)
            }

            NotificationType.COMMENT_ON_READING,
            NotificationType.LIKE_ON_READING -> {
                // ** CORRECTION DE COMPILATION (Chantier 2) **
                // La génération de Safe Args pour l'action avec l'argument optionnel a échoué.
                // Nous contournons le problème en construisant le bundle d'arguments manuellement.
                // Le résultat fonctionnel est strictement identique.
                val destinationUserId = notification.targetUserId ?: notification.actorId
                if (destinationUserId.isBlank()) return // Sécurité : ne pas naviguer si aucun ID n'est valide.

                val args = bundleOf(
                    "userId" to destinationUserId,
                    "scrollToCommentId" to notification.commentId
                )
                // On navigue directement vers la destination avec le bundle d'arguments.
                findNavController().navigate(R.id.publicProfileFragmentDestination, args)
            }

            else -> {
                // Comportement par défaut pour les autres types de notifications non encore gérés.
                // On navigue vers le profil de l'acteur si l'information est disponible.
                if (notification.actorId.isNotBlank()) {
                    val action = NotificationsFragmentDirections.actionNotificationsFragmentToPublicProfileFragment(
                        userId = notification.actorId,
                        username = notification.actorUsername
                    )
                    findNavController().navigate(action)
                }
            }
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
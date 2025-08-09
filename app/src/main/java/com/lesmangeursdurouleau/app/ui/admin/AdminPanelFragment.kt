// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier AdminPanelFragment.kt
package com.lesmangeursdurouleau.app.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.databinding.FragmentAdminPanelBinding
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdminPanelFragment : Fragment() {

    private var _binding: FragmentAdminPanelBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminPanelViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminPanelBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupListeners()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupListeners() {
        binding.btnSetAdmin.setOnClickListener {
            val email = binding.etUserEmail.text.toString().trim()
            if (email.isNotBlank()) {
                viewModel.onSetUserAsAdminClicked(email)
            } else {
                Toast.makeText(context, "Veuillez saisir un e-mail", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnManageReadings.setOnClickListener {
            findNavController().navigate(R.id.action_adminPanelFragment_to_manageReadingsFragment)
        }
    }

    // === DÉBUT DE LA MODIFICATION ===
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // JUSTIFICATION: Un premier `launch` est dédié à la collecte des états de l'UI.
                // Ici, nous ne nous occupons que de l'état de chargement.
                launch {
                    viewModel.isLoading.collect { isLoading ->
                        setUiLoadingState(isLoading)
                    }
                }

                // JUSTIFICATION: Un second `launch`, parallèle, est dédié à la collecte des événements.
                // Il gère les actions à usage unique comme l'affichage de Toasts.
                launch {
                    viewModel.eventFlow.collect { result ->
                        when (result) {
                            is Resource.Success -> {
                                Toast.makeText(context, result.data, Toast.LENGTH_LONG).show()
                                binding.etUserEmail.text?.clear()
                            }
                            is Resource.Error -> {
                                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                            }
                            is Resource.Loading -> {
                                // L'état de chargement est déjà géré par le premier collecteur,
                                // donc il n'y a rien à faire ici.
                            }
                        }
                        // La méthode viewModel.consume...() est supprimée, ce qui rend le code plus propre.
                    }
                }
            }
        }
    }
    // === FIN DE LA MODIFICATION ===

    private fun setUiLoadingState(isLoading: Boolean) {
        binding.progressBar.isVisible = isLoading
        binding.btnSetAdmin.isVisible = !isLoading
        binding.tilUserEmail.isEnabled = !isLoading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
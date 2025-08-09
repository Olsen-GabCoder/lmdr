// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier PdfReaderFragment.kt
package com.lesmangeursdurouleau.app.ui.pdfreader

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
import com.lesmangeursdurouleau.app.databinding.FragmentPdfReaderBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PdfReaderFragment : Fragment() {

    private var _binding: FragmentPdfReaderBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PdfReaderViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPdfReaderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBar.isVisible = state.isLoading
                    binding.tvError.isVisible = state.error != null
                    binding.tvError.text = state.error

                    if (state.bookTitle != null) {
                        binding.toolbar.title = state.bookTitle
                    }

                    // === DÉBUT DE LA MODIFICATION ===
                    // JUSTIFICATION: Lorsque le ViewModel nous fournit le flux de données du PDF,
                    // nous le passons à la vue PDFView pour qu'elle l'affiche.
                    if (state.pdfInputStream != null) {
                        binding.pdfView.fromStream(state.pdfInputStream)
                            .enableSwipe(true) // Activer le défilement par balayage
                            .swipeHorizontal(false) // Défilement vertical par défaut
                            .enableDoubletap(true) // Activer le zoom par double-tap
                            .defaultPage(0) // Commencer à la première page
                            .load() // Charger le document
                    }
                    // === FIN DE LA MODIFICATION ===
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 
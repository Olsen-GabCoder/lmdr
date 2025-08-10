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
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener
import com.lesmangeursdurouleau.app.databinding.FragmentPdfReaderBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PdfReaderFragment : Fragment(), OnPageChangeListener { // MODIFIÉ: Implémente l'interface

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

                    if (state.pdfFile != null && binding.pdfView.pageCount == 0) {
                        binding.pdfView.fromFile(state.pdfFile)
                            .enableSwipe(true)
                            .swipeHorizontal(false)
                            .enableDoubletap(true)
                            // MODIFIÉ: Le lecteur s'ouvre à la page sauvegardée.
                            .defaultPage(state.initialPage)
                            // MODIFIÉ: Le fragment écoute les changements de page.
                            .onPageChange(this@PdfReaderFragment)
                            .load()
                    }
                }
            }
        }
    }

    // === DÉBUT DE LA MODIFICATION ===
    /**
     * NOUVEAU: Cette méthode est appelée à chaque fois que l'utilisateur change de page.
     * Elle délègue la sauvegarde au ViewModel.
     */
    override fun onPageChanged(page: Int, pageCount: Int) {
        viewModel.saveCurrentPage(page)
    }
    // === FIN DE LA MODIFICATION ===


    override fun onDestroyView() {
        super.onDestroyView()
        binding.pdfView.recycle()
        _binding = null
    }
}
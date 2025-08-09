// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier BookDetailFragment.kt
package com.lesmangeursdurouleau.app.ui.readings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.databinding.FragmentBookDetailBinding
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BookDetailFragment : Fragment() {

    private var _binding: FragmentBookDetailBinding? = null
    private val binding get() = _binding!!

    private val args: BookDetailFragmentArgs by navArgs()
    private val viewModel: BookDetailViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBookDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupClickListeners()
        setupObservers()

        val bookId = args.bookId
        if (bookId.isNotBlank()) {
            viewModel.loadBookDetails(bookId)
        } else {
            binding.progressBarBookDetail.isVisible = false
            binding.tvBookDetailSynopsis.text = "Erreur : ID du livre manquant."
        }
    }

    private fun setupClickListeners() {
        binding.btnAddToLibrary.setOnClickListener {
            viewModel.addBookToLibrary()
        }
        binding.btnGoToLibrary.setOnClickListener {
            findNavController().navigate(R.id.action_bookDetailFragment_to_myLibraryFragment)
        }

        // === DÉBUT DE LA MODIFICATION ===
        // JUSTIFICATION: Ajout du listener pour le bouton de lecture.
        // Pour l'instant, il affiche un Toast, mais il est prêt à déclencher la navigation
        // vers le futur écran de lecture PDF.
        binding.btnReadBook.setOnClickListener {
            val book = viewModel.uiState.value.book
            if (book?.contentUrl != null) {
                // Ici, nous naviguerons vers le PdfReaderFragment
                Toast.makeText(context, "Lancement du lecteur PDF à venir...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Le contenu de ce livre n'est pas disponible.", Toast.LENGTH_LONG).show()
            }
        }
        // === FIN DE LA MODIFICATION ===
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        updateUiState(state)
                    }
                }
                launch {
                    viewModel.addBookEvent.collect { resource ->
                        handleAddToLibraryEvent(resource)
                    }
                }
            }
        }
    }

    private fun updateUiState(state: BookDetailUiState) {
        binding.progressBarBookDetail.isVisible = state.isLoading

        if (state.book != null) {
            populateBookDetails(state.book)
            binding.ivBookDetailCover.isVisible = true
            binding.tvBookDetailTitle.isVisible = true
        } else {
            binding.ivBookDetailCover.isVisible = false
            binding.tvBookDetailTitle.isVisible = false
        }

        if (state.error != null) {
            binding.tvBookDetailSynopsis.text = state.error
            binding.btnAddToLibrary.isVisible = false
            binding.btnGoToLibrary.isVisible = false
            binding.btnReadBook.isVisible = false // Cacher en cas d'erreur
        } else {
            binding.tvBookDetailSynopsis.text = state.book?.synopsis ?: getString(R.string.no_synopsis_available)
        }

        if (!state.isLoading && state.error == null) {
            // === DÉBUT DE LA MODIFICATION ===
            // JUSTIFICATION: La visibilité de tous les boutons est maintenant pilotée par l'état du ViewModel.
            // Le bouton "Lire" n'apparaît que si `canBeRead` est vrai.
            binding.btnReadBook.isVisible = state.canBeRead

            // Le bouton "Ajouter" est visible si le livre ne peut pas être lu (soit parce qu'il n'est
            // pas dans la bibliothèque, soit parce qu'il n'a pas de contenu).
            binding.btnAddToLibrary.isVisible = !state.canBeRead

            if (state.isInLibrary) {
                // Si le livre est dans la bibliothèque mais ne peut pas être lu (pas de contentUrl),
                // on affiche quand même l'état "Dans ma bibliothèque".
                binding.btnAddToLibrary.isEnabled = false
                binding.btnAddToLibrary.text = getString(R.string.in_my_library) // Nouvelle chaîne pour la cohérence
                binding.btnAddToLibrary.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_check_single)
                binding.btnGoToLibrary.isVisible = true
            } else {
                binding.btnAddToLibrary.isEnabled = true
                binding.btnAddToLibrary.text = getString(R.string.add_to_my_library)
                binding.btnAddToLibrary.icon = null
                binding.btnGoToLibrary.isVisible = false
            }
            // === FIN DE LA MODIFICATION ===
        }
    }

    private fun handleAddToLibraryEvent(resource: Resource<Unit>) {
        when (resource) {
            is Resource.Loading -> {
                binding.btnAddToLibrary.isEnabled = false
                binding.btnAddToLibrary.text = "Ajout en cours..."
            }
            is Resource.Success -> {
                Toast.makeText(context, "Livre ajouté avec succès !", Toast.LENGTH_SHORT).show()
            }
            is Resource.Error -> {
                Toast.makeText(context, "Erreur : ${resource.message}", Toast.LENGTH_LONG).show()
                binding.btnAddToLibrary.isEnabled = true
                binding.btnAddToLibrary.text = getString(R.string.add_to_my_library)
            }
        }
    }

    private fun populateBookDetails(book: Book) {
        binding.tvBookDetailTitle.text = book.title
        binding.tvBookDetailAuthor.text = book.author

        binding.ivBookDetailCover.contentDescription = getString(
            R.string.book_cover_of_title_description,
            book.title.ifBlank { getString(R.string.unknown_book_title) }
        )

        Glide.with(this)
            .load(book.coverImageUrl)
            .placeholder(R.drawable.ic_book_placeholder)
            .error(R.drawable.ic_book_placeholder_error)
            .into(binding.ivBookDetailCover)

        (activity as? AppCompatActivity)?.supportActionBar?.title = book.title
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
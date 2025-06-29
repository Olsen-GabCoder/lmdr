// app/src/main/java/com/lesmangeursdurouleau/app/ui/readings/detail/BookDetailFragment.kt
package com.lesmangeursdurouleau.app.ui.readings

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.databinding.FragmentBookDetailBinding
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BookDetailFragment : Fragment() {

    private var _binding: FragmentBookDetailBinding? = null
    private val binding get() = _binding!!

    private val args: BookDetailFragmentArgs by navArgs()
    // Le ViewModel est maintenant injecté avec ses nouvelles dépendances
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

        val bookId = args.bookId
        Log.d("BookDetailFragment", "Received Book ID for detail: $bookId")

        if (bookId.isNotBlank()) {
            viewModel.loadBookDetails(bookId)
        } else {
            Log.e("BookDetailFragment", "Book ID is blank, cannot load details.")
            binding.tvBookDetailSynopsis.text = "Erreur : ID du livre manquant."
            binding.progressBarBookDetail.visibility = View.GONE
        }
        setupObservers()
    }

    private fun setupClickListeners() {
        binding.btnAddToLibrary.setOnClickListener {
            viewModel.addBookToLibrary()
        }
    }

    private fun setupObservers() {
        viewModel.bookDetails.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.progressBarBookDetail.visibility = View.VISIBLE
                    binding.ivBookDetailCover.visibility = View.INVISIBLE
                    binding.tvBookDetailTitle.visibility = View.INVISIBLE
                    binding.btnAddToLibrary.visibility = View.GONE // Cacher le bouton pendant le chargement initial
                }
                is Resource.Success -> {
                    binding.progressBarBookDetail.visibility = View.GONE
                    resource.data?.let { book ->
                        populateBookDetails(book)
                        binding.ivBookDetailCover.visibility = View.VISIBLE
                        binding.tvBookDetailTitle.visibility = View.VISIBLE
                    } ?: run {
                        binding.tvBookDetailSynopsis.text = "Livre non trouvé ou données invalides."
                        Log.e("BookDetailFragment", "Book data is null on success for ID: ${args.bookId}")
                    }
                }
                is Resource.Error -> {
                    binding.progressBarBookDetail.visibility = View.GONE
                    binding.tvBookDetailSynopsis.text = resource.message ?: getString(R.string.error_loading_book_details)
                    Log.e("BookDetailFragment", "Error loading book details: ${resource.message}")
                }
            }
        }

        // Observer pour savoir si le livre est dans la bibliothèque
        viewModel.isBookInLibrary.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.btnAddToLibrary.visibility = View.VISIBLE
                    binding.btnAddToLibrary.isEnabled = false
                    binding.btnAddToLibrary.text = "Vérification..."
                }
                is Resource.Success -> {
                    binding.btnAddToLibrary.visibility = View.VISIBLE
                    val isInLibrary = resource.data
                    if (isInLibrary == true) {
                        binding.btnAddToLibrary.isEnabled = false
                        binding.btnAddToLibrary.text = "Dans ma bibliothèque"
                        // Optionnel : Changer la couleur pour un feedback visuel plus fort
                        binding.btnAddToLibrary.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_check_single)
                    } else {
                        binding.btnAddToLibrary.isEnabled = true
                        binding.btnAddToLibrary.text = getString(R.string.add_to_my_library)
                        binding.btnAddToLibrary.icon = null
                    }
                }
                is Resource.Error -> {
                    // En cas d'erreur de vérification, on cache le bouton pour ne pas induire en erreur
                    binding.btnAddToLibrary.visibility = View.GONE
                    Toast.makeText(context, resource.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Observer pour le résultat de l'action d'ajout
        viewModel.addBookToLibraryResult.observe(viewLifecycleOwner) { resource ->
            when(resource) {
                is Resource.Loading -> {
                    binding.btnAddToLibrary.isEnabled = false
                    binding.btnAddToLibrary.text = "Ajout en cours..."
                }
                is Resource.Success -> {
                    Toast.makeText(context, "Livre ajouté avec succès !", Toast.LENGTH_SHORT).show()
                    // Pas besoin de changer l'état du bouton ici. L'observer isBookInLibrary s'en chargera automatiquement grâce au listener temps réel.
                }
                is Resource.Error -> {
                    Toast.makeText(context, "Erreur : ${resource.message}", Toast.LENGTH_LONG).show()
                    // On réactive le bouton pour que l'utilisateur puisse réessayer
                    binding.btnAddToLibrary.isEnabled = true
                    binding.btnAddToLibrary.text = getString(R.string.add_to_my_library)
                }
            }
        }
    }

    private fun populateBookDetails(book: Book) {
        binding.tvBookDetailTitle.text = book.title
        binding.tvBookDetailAuthor.text = book.author
        binding.tvBookDetailSynopsis.text = book.synopsis ?: getString(R.string.no_synopsis_available)

        if (!book.coverImageUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(book.coverImageUrl)
                .placeholder(R.drawable.ic_book_placeholder)
                .error(R.drawable.ic_book_placeholder_error)
                .into(binding.ivBookDetailCover)
        } else {
            binding.ivBookDetailCover.setImageResource(R.drawable.ic_book_placeholder)
        }

        if (activity is AppCompatActivity) {
            (activity as AppCompatActivity).supportActionBar?.title = book.title
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
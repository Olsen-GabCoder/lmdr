// PRÊT À COLLER - Remplacez tout le contenu de votre fichier BookDetailFragment.kt par ceci.
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
        if (bookId.isNotBlank()) {
            viewModel.loadBookDetails(bookId)
        } else {
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
                    binding.btnAddToLibrary.visibility = View.GONE
                }
                is Resource.Success -> {
                    binding.progressBarBookDetail.visibility = View.GONE
                    resource.data?.let { book ->
                        populateBookDetails(book)
                        binding.ivBookDetailCover.visibility = View.VISIBLE
                        binding.tvBookDetailTitle.visibility = View.VISIBLE
                    } ?: run {
                        binding.tvBookDetailSynopsis.text = "Livre non trouvé ou données invalides."
                    }
                }
                is Resource.Error -> {
                    binding.progressBarBookDetail.visibility = View.GONE
                    binding.tvBookDetailSynopsis.text = resource.message ?: getString(R.string.error_loading_book_details)
                }
            }
        }

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
                        binding.btnAddToLibrary.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_check_single)
                    } else {
                        binding.btnAddToLibrary.isEnabled = true
                        binding.btnAddToLibrary.text = getString(R.string.add_to_my_library)
                        binding.btnAddToLibrary.icon = null
                    }
                }
                is Resource.Error -> {
                    binding.btnAddToLibrary.visibility = View.GONE
                    Toast.makeText(context, resource.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        viewModel.addBookToLibraryResult.observe(viewLifecycleOwner) { resource ->
            when(resource) {
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
    }

    private fun populateBookDetails(book: Book) {
        binding.tvBookDetailTitle.text = book.title
        binding.tvBookDetailAuthor.text = book.author
        binding.tvBookDetailSynopsis.text = book.synopsis ?: getString(R.string.no_synopsis_available)

        // === DÉBUT DE LA MODIFICATION ===
        binding.ivBookDetailCover.contentDescription = getString(
            R.string.book_cover_of_title_description,
            book.title.ifBlank { getString(R.string.unknown_book_title) }
        )
        // === FIN DE LA MODIFICATION ===

        if (!book.coverImageUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(book.coverImageUrl)
                .placeholder(R.drawable.ic_book_placeholder)
                .error(R.drawable.ic_book_placeholder_error)
                .into(binding.ivBookDetailCover)
        } else {
            binding.ivBookDetailCover.setImageResource(R.drawable.ic_book_placeholder)
        }

        (activity as? AppCompatActivity)?.supportActionBar?.title = book.title
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
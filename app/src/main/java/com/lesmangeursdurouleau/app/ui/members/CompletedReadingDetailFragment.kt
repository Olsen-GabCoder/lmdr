// PRÊT À COLLER - Fichier 100% complet et corrigé
package com.lesmangeursdurouleau.app.ui.members

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.navArgs
import androidx.transition.Transition
import androidx.transition.TransitionInflater
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.snackbar.Snackbar
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.databinding.FragmentCompletedReadingDetailBinding
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class CompletedReadingDetailFragment : Fragment() {

    private var _binding: FragmentCompletedReadingDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CompletedReadingDetailViewModel by viewModels()
    private val args: CompletedReadingDetailFragmentArgs by navArgs()

    private var commentsAdapter: CommentsAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedElementEnterTransition = TransitionInflater.from(requireContext())
            .inflateTransition(android.R.transition.move)?.apply {
                duration = 300
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCompletedReadingDetailBinding.inflate(inflater, container, false)
        ViewCompat.setTransitionName(binding.ivBookCover, "cover_${args.bookId}")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition() // On attend que Glide soit prêt

        observeViewModel()
        setupClickListeners()
    }

    private fun setupRecyclerView(currentUserId: String?) {
        if (commentsAdapter == null) {
            commentsAdapter = CommentsAdapter(
                currentUserId = currentUserId,
                targetProfileOwnerId = args.userId,
                onDeleteClickListener = { comment -> viewModel.deleteComment(comment.commentId) },
                onLikeClickListener = { comment -> viewModel.toggleLikeOnComment(comment.commentId) },
                getCommentLikeStatus = { commentId -> viewModel.isCommentLikedByCurrentUser(commentId) },
                lifecycleOwner = viewLifecycleOwner
            )
            binding.rvComments.adapter = commentsAdapter
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe l'état global de l'UI
                launch {
                    viewModel.uiState.collectLatest { state ->
                        binding.progressBarDetails.isVisible = state.isLoading

                        state.book?.let { book ->
                            updateReadingDetailsUI(book, state.completionDate)
                        }

                        if (state.error != null) {
                            Snackbar.make(binding.root, state.error, Snackbar.LENGTH_LONG).show()
                            // Optionnel: Cacher les vues principales en cas d'erreur fatale
                            binding.ivBookCover.isVisible = false
                            binding.tvBookTitle.isVisible = false
                            binding.tvBookAuthor.isVisible = false
                        }
                    }
                }

                // Observe les commentaires séparément
                launch {
                    viewModel.comments.collect { resource ->
                        binding.progressBarComments.isVisible = resource is Resource.Loading
                        binding.rvComments.isVisible = resource is Resource.Success
                        binding.tvNoComments.isVisible = resource is Resource.Success && resource.data.isNullOrEmpty()

                        if (resource is Resource.Success) {
                            commentsAdapter?.submitList(resource.data)
                        }
                    }
                }

                // Observe le statut du like de l'utilisateur
                launch {
                    viewModel.isReadingLikedByCurrentUser.collect { resource ->
                        if (resource is Resource.Success) {
                            val isLiked = resource.data == true
                            binding.btnLikeReading.setIconResource(
                                if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
                            )
                        }
                    }
                }

                // Observe le nombre total de likes
                launch {
                    viewModel.readingLikesCount.collect { resource ->
                        if (resource is Resource.Success) {
                            binding.btnLikeReading.text = resource.data?.toString() ?: "0"
                        } else {
                            binding.btnLikeReading.text = "-"
                        }
                    }
                }

                // Initialise l'adapter avec l'ID de l'utilisateur courant
                launch {
                    viewModel.currentUserId.collectLatest { uid ->
                        setupRecyclerView(uid)
                        binding.commentInputBar.isVisible = uid != null
                    }
                }
            }
        }
    }

    private fun updateReadingDetailsUI(book: Book, completionDate: Date?) {
        Glide.with(this)
            .load(book.coverImageUrl)
            .placeholder(R.drawable.ic_book_placeholder)
            .error(R.drawable.ic_book_placeholder_error)
            .dontAnimate()
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                    startPostponedEnterTransition()
                    return false
                }
                override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                    startPostponedEnterTransition()
                    return false
                }
            })
            .into(binding.ivBookCover)

        binding.tvBookTitle.text = book.title
        binding.tvBookAuthor.text = book.author

        completionDate?.let { date ->
            val dateFormat = SimpleDateFormat("'Terminé le' dd MMMM yyyy", Locale.getDefault())
            binding.tvCompletionDate.text = dateFormat.format(date)
        } ?: run {
            binding.tvCompletionDate.text = getString(R.string.date_completion_unknown)
        }
    }

    private fun setupClickListeners() {
        binding.btnLikeReading.setOnClickListener {
            viewModel.toggleLikeOnReading()
        }

        binding.btnSendComment.setOnClickListener {
            val commentText = binding.etComment.text.toString().trim()
            if (commentText.isNotEmpty()) {
                viewModel.addComment(commentText)
                binding.etComment.text?.clear()
                val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(view?.windowToken, 0)
                binding.etComment.clearFocus()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvComments.adapter = null
        commentsAdapter = null
        _binding = null
    }
}
// Fichier complet : CompletedReadingDetailFragment.kt

package com.lesmangeursdurouleau.app.ui.members

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.transition.TransitionInflater
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.snackbar.Snackbar
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.Comment
import com.lesmangeursdurouleau.app.databinding.FragmentCompletedReadingDetailBinding
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class CompletedReadingDetailFragment : Fragment(), OnCommentInteractionListener {

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
        postponeEnterTransition()
        setupRecyclerView()
        observeViewModel()
        setupClickListeners()
    }

    override fun onMentionClicked(username: String) {
        Toast.makeText(context, "Clic sur la mention : @$username", Toast.LENGTH_SHORT).show()
    }

    override fun onHashtagClicked(hashtag: String) {
        Toast.makeText(context, "Clic sur le hashtag : #$hashtag", Toast.LENGTH_SHORT).show()
    }

    private fun setupRecyclerView() {
        // JUSTIFICATION DE LA MODIFICATION : Le paramètre `isOwnerOfReading` est ajouté pour corriger l'erreur de compilation.
        // Sa valeur est déterminée en comparant l'ID de l'utilisateur courant (fourni par le ViewModel) à l'ID
        // de l'utilisateur dont la lecture est consultée (fourni par les arguments de navigation).
        // Ceci garantit que l'adapter reçoit le contexte de permission correct.
        commentsAdapter = CommentsAdapter(
            currentUserId = viewModel.currentUserId.value,
            isOwnerOfReading = viewModel.currentUserId.value == args.userId,
            lifecycleOwner = viewLifecycleOwner,
            interactionListener = this,
            onReplyClickListener = { comment ->
                Toast.makeText(context, "Répondre n'est pas disponible ici.", Toast.LENGTH_SHORT).show()
            },
            onCommentOptionsClickListener = { comment, anchorView ->
                showCommentOptionsMenu(comment, anchorView)
            },
            onLikeClickListener = { comment -> viewModel.toggleLikeOnComment(comment.commentId) },
            onUnhideClickListener = { commentId -> viewModel.unhideComment(commentId) },
            getCommentLikeStatus = { commentId -> viewModel.isCommentLikedByCurrentUser(commentId) }
        )
        binding.rvComments.adapter = commentsAdapter
        binding.rvComments.layoutManager = LinearLayoutManager(context)
    }

    @SuppressLint("RestrictedApi")
    private fun showCommentOptionsMenu(comment: Comment, anchorView: View) {
        val popup = PopupMenu(requireContext(), anchorView)
        popup.inflate(R.menu.comment_options_menu)

        if (popup.menu is MenuBuilder) {
            (popup.menu as MenuBuilder).setOptionalIconsVisible(true)
        }

        val isAuthor = viewModel.currentUserId.value == comment.userId
        // JUSTIFICATION DE LA MODIFICATION : La variable `isOwnerOfReading` est ajoutée pour aligner ce fragment
        // sur la logique de permission définie dans les spécifications.
        val isOwnerOfReading = viewModel.currentUserId.value == args.userId

        popup.menu.findItem(R.id.action_edit_comment).isVisible = false
        popup.menu.findItem(R.id.action_delete_comment).isVisible = false
        popup.menu.findItem(R.id.action_report_comment).isVisible = !isAuthor
        popup.menu.findItem(R.id.action_reply_to_comment).isVisible = false
        popup.menu.findItem(R.id.action_share_comment).isVisible = false
        popup.menu.findItem(R.id.action_copy_comment_text).isVisible = true
        // JUSTIFICATION DE LA MODIFICATION : La visibilité de l'option "Masquer" est maintenant
        // conditionnée par `isOwnerOfReading` et non plus par `!isAuthor`, corrigeant ainsi
        // la non-conformité et respectant la spécification.
        popup.menu.findItem(R.id.action_hide_comment).isVisible = isOwnerOfReading

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_hide_comment -> {
                    viewModel.hideComment(comment.commentId)
                    true
                }
                R.id.action_report_comment -> {
                    Toast.makeText(context, "Signalement bientôt disponible.", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.action_copy_comment_text -> {
                    val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("comment text", comment.commentText)
                    clipboard?.setPrimaryClip(clip)
                    Toast.makeText(context, "Texte du commentaire copié.", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collectLatest { state ->
                        binding.progressBarDetails.isVisible = state.isLoading
                        state.book?.let { book ->
                            updateReadingDetailsUI(book, state.completionDate)
                        }
                        if (state.error != null) {
                            Snackbar.make(binding.root, state.error, Snackbar.LENGTH_LONG).show()
                            binding.ivBookCover.isVisible = false
                            binding.tvBookTitle.isVisible = false
                            binding.tvBookAuthor.isVisible = false
                        }
                    }
                }

                launch {
                    viewModel.comments.collect { resource ->
                        binding.progressBarComments.isVisible = resource is Resource.Loading
                        if (resource is Resource.Success) {
                            val uiComments = resource.data
                            binding.rvComments.isVisible = !uiComments.isNullOrEmpty()
                            binding.tvNoComments.isVisible = uiComments.isNullOrEmpty()
                            commentsAdapter?.submitList(uiComments ?: emptyList())
                        }
                    }
                }

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

                launch {
                    viewModel.readingLikesCount.collect { resource ->
                        if (resource is Resource.Success) {
                            binding.btnLikeReading.text = resource.data?.toString() ?: "0"
                        } else {
                            binding.btnLikeReading.text = "-"
                        }
                    }
                }

                launch {
                    viewModel.currentUserId.collectLatest { uid ->
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
            .error(R.drawable.ic_book_placeholder)
            .dontAnimate()
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
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
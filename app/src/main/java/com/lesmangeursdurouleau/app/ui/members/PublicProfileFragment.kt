// Fichier complet : PublicProfileFragment.kt

package com.lesmangeursdurouleau.app.ui.members

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.Comment
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.databinding.FragmentPublicProfileBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PublicProfileFragment : Fragment(), OnCommentInteractionListener {

    private var _binding: FragmentPublicProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PublicProfileViewModel by viewModels()
    private val args: PublicProfileFragmentArgs by navArgs()

    private lateinit var commentsAdapter: CommentsAdapter
    private lateinit var mentionSuggestionsAdapter: MentionSuggestionsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPublicProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerViews()
        setupResultListener()
        observeViewModel()
        setupMainClickListeners()
        setupCommentSectionListeners()
        setupMentionListener()
    }

    override fun onMentionClicked(username: String) {
        showToast("Clic sur la mention : @$username")
    }

    override fun onHashtagClicked(hashtag: String) {
        showToast("Clic sur le hashtag : #$hashtag")
    }

    private fun setupResultListener() {
        childFragmentManager.setFragmentResultListener(RatingDialogFragment.REQUEST_KEY, this) { _, bundle ->
            val rating = bundle.getFloat(RatingDialogFragment.RESULT_KEY_RATING)
            viewModel.rateCurrentReading(rating)
        }
    }

    private fun setupRecyclerViews() {
        commentsAdapter = CommentsAdapter(
            currentUserId = viewModel.currentUserId,
            isOwnerOfReading = viewModel.uiState.value.isOwnedProfile,
            lifecycleOwner = viewLifecycleOwner,
            interactionListener = this,
            onReplyClickListener = { parentComment ->
                viewModel.startReplyingTo(parentComment)
                binding.etCommentInput.requestFocus()
                val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(binding.etCommentInput, InputMethodManager.SHOW_IMPLICIT)
            },
            onCommentOptionsClickListener = { comment, anchorView ->
                showCommentOptionsMenu(comment, anchorView)
            },
            onLikeClickListener = { comment -> viewModel.toggleLikeOnComment(comment) },
            onUnhideClickListener = { commentId ->
                viewModel.unhideComment(commentId)
            },
            getCommentLikeStatus = { commentId -> viewModel.getCommentLikeStatus(commentId) }
        )
        binding.rvComments.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = commentsAdapter
            isNestedScrollingEnabled = false
        }

        mentionSuggestionsAdapter = MentionSuggestionsAdapter { user ->
            replaceMention(user)
        }
        binding.rvMentionSuggestions.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = mentionSuggestionsAdapter
        }
    }

    private fun setupMentionListener() {
        binding.etCommentInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s.toString()
                val cursorPosition = binding.etCommentInput.selectionStart

                val wordStart = text.lastIndexOf('@', cursorPosition - 1)

                if (wordStart != -1 && !text.substring(wordStart, cursorPosition).contains(' ')) {
                    val query = text.substring(wordStart + 1, cursorPosition)
                    viewModel.searchForMention(query)
                } else {
                    viewModel.clearMentionSuggestions()
                }
            }
        })
    }

    private fun replaceMention(user: User) {
        val editText = binding.etCommentInput
        val text = editText.text.toString()
        val cursorPosition = editText.selectionStart

        val wordStart = text.lastIndexOf('@', cursorPosition - 1)
        if (wordStart == -1) return

        val newText = buildString {
            append(text.substring(0, wordStart))
            append("@${user.username} ")
            append(text.substring(cursorPosition))
        }

        editText.setText(newText)
        editText.setSelection(wordStart + user.username.length + 2)

        viewModel.clearMentionSuggestions()
    }


    @SuppressLint("RestrictedApi")
    private fun showCommentOptionsMenu(comment: Comment, anchorView: View) {
        val popup = PopupMenu(requireContext(), anchorView)
        popup.inflate(R.menu.comment_options_menu)

        if (popup.menu is MenuBuilder) {
            val menuBuilder = popup.menu as MenuBuilder
            menuBuilder.setOptionalIconsVisible(true)
        }

        val isAuthor = viewModel.currentUserId == comment.userId
        val isOwnerOfReading = viewModel.uiState.value.isOwnedProfile

        popup.menu.findItem(R.id.action_edit_comment).isVisible = isAuthor
        popup.menu.findItem(R.id.action_delete_comment).isVisible = isAuthor
        popup.menu.findItem(R.id.action_report_comment).isVisible = !isAuthor && !isOwnerOfReading
        popup.menu.findItem(R.id.action_hide_comment).isVisible = isOwnerOfReading

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_edit_comment -> {
                    showEditCommentDialog(comment)
                    true
                }
                R.id.action_delete_comment -> {
                    showDeleteConfirmationDialog(comment)
                    true
                }
                R.id.action_reply_to_comment -> {
                    viewModel.startReplyingTo(comment)
                    binding.etCommentInput.requestFocus()
                    val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.showSoftInput(binding.etCommentInput, InputMethodManager.SHOW_IMPLICIT)
                    true
                }
                R.id.action_copy_comment_text -> {
                    copyCommentTextToClipboard(comment.commentText)
                    true
                }
                R.id.action_report_comment -> {
                    showReportConfirmationDialog(comment)
                    true
                }
                R.id.action_hide_comment -> {
                    viewModel.hideComment(comment.commentId)
                    true
                }
                R.id.action_share_comment -> {
                    viewModel.shareComment(comment)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showEditCommentDialog(comment: Comment) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_comment, null)
        val editText = dialogView.findViewById<EditText>(R.id.et_edit_comment)
        editText.setText(comment.commentText)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.edit_comment_dialog_title)
            .setView(dialogView)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_edit) { _, _ ->
                val newText = editText.text.toString()
                if (newText.isNotBlank() && newText != comment.commentText) {
                    viewModel.updateComment(comment, newText)
                }
            }
            .show()
    }

    private fun showDeleteConfirmationDialog(comment: Comment) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_comment_dialog_title)
            .setMessage(R.string.delete_comment_dialog_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { dialog, _ ->
                viewModel.deleteComment(comment)
                dialog.dismiss()
            }
            .show()
    }

    private fun showReportConfirmationDialog(comment: Comment) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.report_comment_dialog_title)
            .setMessage(R.string.report_comment_dialog_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_report) { dialog, _ ->
                viewModel.reportComment(comment)
                dialog.dismiss()
            }
            .show()
    }

    private fun copyCommentTextToClipboard(text: String) {
        val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) {
            showToast(getString(R.string.error_clipboard_not_available))
            return
        }
        val clip = ClipData.newPlainText("comment_text", text)
        clipboard.setPrimaryClip(clip)
        showToast(getString(R.string.comment_copied_to_clipboard))
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collectLatest { state ->
                        bindUiState(state)
                    }
                }
                launch {
                    viewModel.events.collectLatest { event ->
                        when (event) {
                            is Event.ShowToast -> showToast(event.message)
                            is Event.ShareContent -> shareContent(event.text)
                        }
                    }
                }
            }
        }
    }

    private fun shareContent(text: String) {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, null)
        startActivity(shareIntent)
    }

    @SuppressLint("SetTextI18n")
    private fun bindUiState(state: PublicProfileUiState) {
        binding.progressBarPublicProfile.isVisible = state.profileLoadState is ProfileLoadState.Loading
        binding.tvPublicProfileError.isVisible = state.profileLoadState is ProfileLoadState.Error
        binding.contentContainer.visibility = if (state.profileLoadState is ProfileLoadState.Success) View.VISIBLE else View.INVISIBLE

        if (state.profileLoadState is ProfileLoadState.Error) {
            binding.tvPublicProfileError.text = state.profileLoadState.message
        }

        state.user?.let { user ->
            (activity as? AppCompatActivity)?.supportActionBar?.title = user.username
            populateProfileData(user)
            updateFollowButton(state.isFollowing, state.isOwnedProfile)

            binding.tvPublicProfileBio.isVisible = !user.bio.isNullOrBlank()
            binding.tvPublicProfileBio.text = user.bio
            binding.tvPublicProfileCity.isVisible = !user.city.isNullOrBlank()
            binding.tvPublicProfileCity.text = user.city

            val hasAffinityPartner = user.highestAffinityScore > 0 && !user.highestAffinityPartnerUsername.isNullOrBlank()
            binding.cardStrongestAffinity.isVisible = hasAffinityPartner
            if (hasAffinityPartner) {
                binding.tvAffinityPartnerInfo.text = getString(R.string.literary_accomplice_template, user.highestAffinityPartnerUsername)
                binding.tvAffinityTier.text = user.highestAffinityTierName ?: ""
            }
        }

        val readingExperience = state.readingExperience
        binding.cardCurrentReading.isVisible = readingExperience != null
        if (readingExperience != null) {
            binding.tvCurrentReadingPostHeader.text = getString(R.string.current_reading_post_header_template, state.user?.username ?: "")
            binding.tvCurrentReadingBookTitle.text = readingExperience.book.title
            binding.tvCurrentReadingBookAuthor.text = readingExperience.book.author
            binding.ivCurrentReadingBookCover.contentDescription = getString(R.string.book_cover_of_title_description, readingExperience.book.title)
            Glide.with(this).load(readingExperience.book.coverImageUrl).placeholder(R.drawable.ic_book_placeholder).into(binding.ivCurrentReadingBookCover)

            val progress = if (readingExperience.reading.totalPages > 0) (readingExperience.reading.currentPage.toFloat() / readingExperience.reading.totalPages * 100).toInt() else 0
            binding.progressBarCurrentReading.progress = progress
            binding.tvCurrentReadingProgressText.text = "$progress%"

            val quote = readingExperience.reading.favoriteQuote
            binding.llFavoriteQuoteSection.isVisible = !quote.isNullOrBlank()
            binding.tvFavoriteQuote.text = if (!quote.isNullOrBlank()) "“${quote}”" else ""
            val note = readingExperience.reading.personalReflection
            binding.llPersonalNoteSection.isVisible = !note.isNullOrBlank()
            binding.tvPersonalNote.text = note

            updateSocialActionButtons(readingExperience)
        }

        when (val commentsState = state.commentsState) {
            is CommentsState.NotLoadedYet -> {
                binding.btnShowComments.isVisible = false
                binding.progressBarCommentsInitial.isVisible = false
                binding.rvComments.isVisible = false
                binding.progressBarCommentsPagination.isVisible = false
                commentsAdapter.clearAllComments()
            }
            is CommentsState.ReadyToLoad -> {
                binding.btnShowComments.isVisible = commentsState.count > 0
                binding.btnShowComments.text = resources.getQuantityString(R.plurals.show_comments_count, commentsState.count, commentsState.count)
                binding.progressBarCommentsInitial.isVisible = false
                binding.rvComments.isVisible = false
                binding.progressBarCommentsPagination.isVisible = false
                commentsAdapter.clearAllComments()
            }
            is CommentsState.LoadingInitial -> {
                binding.btnShowComments.isVisible = false
                binding.progressBarCommentsInitial.isVisible = true
                binding.rvComments.isVisible = false
                binding.progressBarCommentsPagination.isVisible = false
                commentsAdapter.clearAllComments()
            }
            is CommentsState.Loaded -> {
                binding.btnShowComments.isVisible = false
                binding.progressBarCommentsInitial.isVisible = false
                binding.rvComments.isVisible = true
                binding.progressBarCommentsPagination.isVisible = false
                commentsAdapter.submitNewComments(commentsState.parentComments, commentsState.repliesMap)

                state.highlightedCommentId?.let { commentId ->
                    commentsAdapter.highlightedCommentId = commentId
                    val index = commentsState.parentComments.indexOfFirst { it.comment.commentId == commentId }
                    if (index != -1) {
                        (binding.rvComments.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(index, 200)
                    }
                    viewModel.onHighlightConsumed()
                }
            }
            is CommentsState.Error -> {
                binding.btnShowComments.isVisible = false
                binding.progressBarCommentsInitial.isVisible = false
                binding.rvComments.isVisible = false
                binding.progressBarCommentsPagination.isVisible = false
                showToast(commentsState.message)
            }
        }

        binding.progressBarMentions.isVisible = state.isSearchingMentions
        val hasSuggestions = state.mentionSuggestions.isNotEmpty()
        binding.cardMentionSuggestions.isVisible = hasSuggestions && !state.isSearchingMentions
        if(hasSuggestions) {
            mentionSuggestionsAdapter.submitList(state.mentionSuggestions)
        }

        val replyingTo = state.replyingToComment
        if (replyingTo != null) {
            binding.tilCommentInput.hint = getString(R.string.reply_to_user_hint, replyingTo.userName)
            binding.btnCancelReply.isVisible = true
        } else {
            binding.tilCommentInput.hint = getString(R.string.comment_input_hint)
            binding.btnCancelReply.isVisible = false
        }
    }

    private fun updateSocialActionButtons(experience: CurrentReadingExperience) {
        binding.tvSocialLikeCount.text = experience.likesCount.toString()
        val likeIconRes = if (experience.isLikedByCurrentUser) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
        binding.btnSocialLike.setImageResource(likeIconRes)
        val likeColor = if (experience.isLikedByCurrentUser) ContextCompat.getColor(requireContext(), R.color.red_love) else MaterialColors.getColor(binding.btnSocialLike, com.google.android.material.R.attr.colorOnSurfaceVariant)
        binding.btnSocialLike.imageTintList = ColorStateList.valueOf(likeColor)

        val bookmarkIconRes = if (experience.isBookmarkedByCurrentUser) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark_outline
        binding.btnSocialBookmark.setImageResource(bookmarkIconRes)
        val bookmarkColor = if (experience.isBookmarkedByCurrentUser) MaterialColors.getColor(binding.btnSocialBookmark, com.google.android.material.R.attr.colorPrimary) else MaterialColors.getColor(binding.btnSocialBookmark, com.google.android.material.R.attr.colorOnSurfaceVariant)
        binding.btnSocialBookmark.imageTintList = ColorStateList.valueOf(bookmarkColor)

        val hasRated = experience.currentUserRating != null && experience.currentUserRating > 0f
        val ratingIconRes = if (hasRated) R.drawable.ic_star_filled else R.drawable.ic_star_outline
        binding.btnSocialRate.setImageResource(ratingIconRes)
        val ratingColor = if (hasRated) ContextCompat.getColor(requireContext(), R.color.amber_star) else MaterialColors.getColor(binding.btnSocialRate, com.google.android.material.R.attr.colorOnSurfaceVariant)
        binding.btnSocialRate.imageTintList = ColorStateList.valueOf(ratingColor)

        val recommendIconRes = if (experience.isRecommendedByCurrentUser) R.drawable.ic_thumb_up_filled else R.drawable.ic_thumb_up_outline
        binding.btnSocialRecommend.setImageResource(recommendIconRes)
        val recommendColor = if (experience.isRecommendedByCurrentUser) MaterialColors.getColor(binding.btnSocialRecommend, com.google.android.material.R.attr.colorPrimary) else MaterialColors.getColor(binding.btnSocialRecommend, com.google.android.material.R.attr.colorOnSurfaceVariant)
        binding.btnSocialRecommend.imageTintList = ColorStateList.valueOf(recommendColor)
    }

    private fun populateProfileData(user: User) {
        binding.tvPublicProfileUsername.text = user.username
        binding.tvFollowersCount.text = user.followersCount.toString()
        binding.tvFollowingCount.text = user.followingCount.toString()
        binding.tvBooksReadCount.text = user.booksReadCount.toString()
        Glide.with(this).load(user.profilePictureUrl).placeholder(R.drawable.ic_profile_placeholder).error(R.drawable.ic_profile_placeholder).circleCrop().transition(DrawableTransitionOptions.withCrossFade()).into(binding.ivPublicProfilePicture)
    }

    private fun updateFollowButton(isFollowing: Boolean, isOwnedProfile: Boolean) {
        val followButton = binding.btnToggleFollow
        if (isOwnedProfile) {
            binding.llActionButtons.visibility = View.GONE
            return
        }
        binding.llActionButtons.visibility = View.VISIBLE
        followButton.isEnabled = true
        if (isFollowing) {
            followButton.text = getString(R.string.unfollow)
            val errorColor = ContextCompat.getColor(requireContext(), R.color.error_color)
            followButton.setTextColor(errorColor)
            followButton.icon = null
            followButton.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), android.R.color.transparent))
            followButton.strokeColor = ColorStateList.valueOf(errorColor)
            followButton.strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1f, resources.displayMetrics).toInt()
        } else {
            followButton.text = getString(R.string.follow)
            val colorPrimary = MaterialColors.getColor(followButton, com.google.android.material.R.attr.colorPrimary)
            val colorOnPrimary = MaterialColors.getColor(followButton, com.google.android.material.R.attr.colorOnPrimary)
            followButton.setTextColor(colorOnPrimary)
            followButton.backgroundTintList = ColorStateList.valueOf(colorPrimary)
            followButton.setIconResource(R.drawable.ic_person_outline)
            followButton.strokeWidth = 0
        }
    }

    private fun setupMainClickListeners() {
        binding.btnToggleFollow.setOnClickListener {
            viewModel.toggleFollowStatus()
            it.isEnabled = false
        }
        binding.btnSendMessage.setOnClickListener {
            findNavController().navigate(PublicProfileFragmentDirections.actionPublicProfileFragmentDestinationToPrivateChatFragmentDestination(args.userId))
        }
        binding.btnSocialLike.setOnClickListener { viewModel.toggleLikeOnCurrentReading() }
        binding.btnSocialBookmark.setOnClickListener { viewModel.toggleBookmarkOnCurrentReading() }
        binding.btnSocialRate.setOnClickListener {
            viewModel.uiState.value.readingExperience?.let {
                RatingDialogFragment.newInstance(it.book.title, it.currentUserRating ?: 0f).show(childFragmentManager, RatingDialogFragment.TAG)
            }
        }
        binding.btnSocialRecommend.setOnClickListener { viewModel.toggleRecommendationOnCurrentReading() }
        binding.btnSocialShare.setOnClickListener { showToast(getString(R.string.action_share) + " : Bientôt disponible !") }

        val targetUsername = { viewModel.uiState.value.user?.username ?: args.username }

        // === DÉBUT DE LA CORRECTION FINALE ===
        // JUSTIFICATION : Le bug provenait de l'utilisation d'une action de navigation générique.
        // Le `nav_graph.xml` a bien défini des actions spécifiques pour les "followers" et "following",
        // qui portent des noms comme `...action...Followers` et `...action...Following`.
        // En appelant ces actions spécifiques générées par Safe Args, nous nous assurons que
        // tous les arguments (y compris `listType`) sont correctement passés au MembersFragment.
        binding.llFollowersClickableArea.setOnClickListener {
            val action = PublicProfileFragmentDirections.actionPublicProfileFragmentDestinationToMembersFragmentDestinationFollowers(
                userId = args.userId,
                listTitle = getString(R.string.title_followers_of, targetUsername())
            )
            findNavController().navigate(action)
        }
        binding.llFollowingClickableArea.setOnClickListener {
            val action = PublicProfileFragmentDirections.actionPublicProfileFragmentDestinationToMembersFragmentDestinationFollowing(
                userId = args.userId,
                listTitle = getString(R.string.title_following_by, targetUsername())
            )
            findNavController().navigate(action)
        }
        // === FIN DE LA CORRECTION FINALE ===

        binding.llBooksReadClickableArea.setOnClickListener {
            findNavController().navigate(PublicProfileFragmentDirections.actionPublicProfileFragmentDestinationToCompletedReadingsFragment(args.userId, targetUsername()))
        }
    }

    private fun setupCommentSectionListeners() {
        binding.btnSendComment.setOnClickListener {
            val commentText = binding.etCommentInput.text.toString()
            viewModel.postCommentOnCurrentReading(commentText)
            binding.etCommentInput.text?.clear()
            val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(view?.windowToken, 0)
        }

        binding.btnCancelReply.setOnClickListener {
            viewModel.cancelReply()
        }

        binding.btnShowComments.setOnClickListener {
            viewModel.loadComments()
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvComments.adapter = null
        binding.rvMentionSuggestions.adapter = null
        _binding = null
    }
}
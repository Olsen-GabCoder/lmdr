// PRÊT À COLLER - Fichier PublicProfileFragment.kt complet et MODIFIÉ
package com.lesmangeursdurouleau.app.ui.members

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
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
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.color.MaterialColors
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.User
import com.lesmangeursdurouleau.app.databinding.FragmentPublicProfileBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PublicProfileFragment : Fragment() {

    private var _binding: FragmentPublicProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PublicProfileViewModel by viewModels()
    private val args: PublicProfileFragmentArgs by navArgs()

    private lateinit var commentsAdapter: CommentsAdapter

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
        observeUiState()
        // MODIFIÉ : Séparation des listeners pour la clarté
        setupMainClickListeners()
        setupCommentSectionListeners()
    }

    private fun setupResultListener() {
        childFragmentManager.setFragmentResultListener(RatingDialogFragment.REQUEST_KEY, this) { _, bundle ->
            val rating = bundle.getFloat(RatingDialogFragment.RESULT_KEY_RATING)
            viewModel.rateCurrentReading(rating)
        }
    }

    private fun setupRecyclerViews() {
        // MODIFIÉ : Instanciation de l'adapter avec les nouveaux listeners
        commentsAdapter = CommentsAdapter(
            currentUserId = viewModel.currentUserId,
            targetProfileOwnerId = args.userId,
            lifecycleOwner = viewLifecycleOwner,
            onReplyClickListener = { parentComment ->
                viewModel.startReplyingTo(parentComment)
                // Faire apparaître le clavier et focus sur le champ de texte
                binding.etCommentInput.requestFocus()
                val imm = context?.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(binding.etCommentInput, InputMethodManager.SHOW_IMPLICIT)
            },
            onViewRepliesClickListener = { parentComment ->
                // Logique pour voir/cacher les réponses à implémenter si nécessaire
                Toast.makeText(context, "Voir les réponses pour ${parentComment.commentId}", Toast.LENGTH_SHORT).show()
            },
            onDeleteClickListener = { comment -> viewModel.deleteComment(comment) },
            onLikeClickListener = { comment -> viewModel.toggleLikeOnComment(comment) },
            getCommentLikeStatus = { commentId -> viewModel.getCommentLikeStatus(commentId) }
        )
        binding.rvComments.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = commentsAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collectLatest { state ->
                        bindUiState(state)
                    }
                }
                launch {
                    viewModel.userInteractionEvents.collectLatest { message ->
                        showToast(message)
                    }
                }
            }
        }
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
        readingExperience?.let { experience ->
            binding.tvCurrentReadingPostHeader.text = getString(R.string.current_reading_post_header_template, state.user?.username ?: "")
            binding.tvCurrentReadingBookTitle.text = experience.book.title
            binding.tvCurrentReadingBookAuthor.text = experience.book.author
            binding.ivCurrentReadingBookCover.contentDescription = getString(R.string.book_cover_of_title_description, experience.book.title)
            Glide.with(this).load(experience.book.coverImageUrl).placeholder(R.drawable.ic_book_placeholder).into(binding.ivCurrentReadingBookCover)

            val progress = if (experience.reading.totalPages > 0) (experience.reading.currentPage.toFloat() / experience.reading.totalPages * 100).toInt() else 0
            binding.progressBarCurrentReading.progress = progress
            binding.tvCurrentReadingProgressText.text = "$progress%"

            val quote = experience.reading.favoriteQuote
            binding.llFavoriteQuoteSection.isVisible = !quote.isNullOrBlank()
            binding.tvFavoriteQuote.text = if (!quote.isNullOrBlank()) "“${quote}”" else ""
            val note = experience.reading.personalReflection
            binding.llPersonalNoteSection.isVisible = !note.isNullOrBlank()
            binding.tvPersonalNote.text = note

            updateSocialActionButtons(experience)

            // MODIFIÉ : Appel à la nouvelle méthode de soumission de l'adapter.
            commentsAdapter.submitCommentList(experience.comments)
        }

        // AJOUT : Gérer l'UI en fonction de l'état de la réponse.
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
        binding.llFollowersClickableArea.setOnClickListener {
            findNavController().navigate(PublicProfileFragmentDirections.actionPublicProfileFragmentDestinationToMembersFragmentDestinationFollowers(args.userId, "followers", getString(R.string.title_followers_of, targetUsername())))
        }
        binding.llFollowingClickableArea.setOnClickListener {
            findNavController().navigate(PublicProfileFragmentDirections.actionPublicProfileFragmentDestinationToMembersFragmentDestinationFollowing(args.userId, "following", getString(R.string.title_following_by, targetUsername())))
        }
        binding.llBooksReadClickableArea.setOnClickListener {
            findNavController().navigate(PublicProfileFragmentDirections.actionPublicProfileFragmentDestinationToCompletedReadingsFragment(args.userId, targetUsername()))
        }
    }

    // AJOUT : Listeners spécifiques à la section des commentaires.
    private fun setupCommentSectionListeners() {
        binding.btnSendComment.setOnClickListener {
            val commentText = binding.etCommentInput.text.toString()
            viewModel.postCommentOnCurrentReading(commentText)
            binding.etCommentInput.text?.clear()
            val imm = context?.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(view?.windowToken, 0)
        }

        binding.btnCancelReply.setOnClickListener {
            viewModel.cancelReply()
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvComments.adapter = null
        _binding = null
    }
}
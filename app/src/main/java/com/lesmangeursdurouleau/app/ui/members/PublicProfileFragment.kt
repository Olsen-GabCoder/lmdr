package com.lesmangeursdurouleau.app.ui.members

import android.content.res.ColorStateList
import android.os.Bundle
import android.util.TypedValue
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
        observeUiState()
        setupClickListeners()
    }

    private fun setupRecyclerViews() {
        commentsAdapter = CommentsAdapter(
            currentUserId = viewModel.currentUserId,
            targetProfileOwnerId = args.userId,
            onDeleteClickListener = { comment -> viewModel.deleteComment(comment) },
            onLikeClickListener = { comment -> viewModel.toggleLikeOnComment(comment) },
            getCommentLikeStatus = { commentId -> viewModel.getCommentLikeStatus(commentId) },
            lifecycleOwner = viewLifecycleOwner
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
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun bindUiState(state: PublicProfileUiState) {
        binding.progressBarPublicProfile.isVisible = state.profileLoadState is ProfileLoadState.Loading
        binding.tvPublicProfileError.isVisible = state.profileLoadState is ProfileLoadState.Error

        val isSuccess = state.profileLoadState is ProfileLoadState.Success
        binding.contentContainer.visibility = if (isSuccess) View.VISIBLE else View.INVISIBLE

        if (state.profileLoadState is ProfileLoadState.Error) {
            binding.tvPublicProfileError.text = state.profileLoadState.message
        }

        if (isSuccess && state.user != null) {
            val user = state.user
            (activity as? AppCompatActivity)?.supportActionBar?.title = user.username
            populateProfileData(user)
            updateFollowButton(state.isFollowing, state.isOwnedProfile)

            binding.cardPublicProfileBio.isVisible = !user.bio.isNullOrBlank()
            binding.tvPublicProfileBio.text = user.bio

            binding.cardPublicProfileCity.isVisible = !user.city.isNullOrBlank()
            binding.tvPublicProfileCity.text = user.city

            val hasAffinityPartner = user.highestAffinityScore > 0 && !user.highestAffinityPartnerUsername.isNullOrBlank()
            binding.cardStrongestAffinity.isVisible = hasAffinityPartner
            if (hasAffinityPartner) {
                binding.tvAffinityPartnerInfo.text = getString(R.string.strongest_affinity_score_format, user.highestAffinityScore, user.highestAffinityPartnerUsername)
                binding.tvAffinityTier.text = user.highestAffinityTierName ?: ""
            }

            val readingExperience = state.readingExperience
            binding.cardCurrentReading.isVisible = readingExperience != null
            if (readingExperience != null) {
                binding.tvCurrentReadingBookTitle.text = readingExperience.book.title
                binding.tvCurrentReadingBookAuthor.text = readingExperience.book.author
                binding.ivCurrentReadingBookCover.contentDescription = getString(R.string.book_cover_of_title_description, readingExperience.book.title)

                Glide.with(this)
                    .load(readingExperience.book.coverImageUrl)
                    .placeholder(R.drawable.ic_book_placeholder)
                    .into(binding.ivCurrentReadingBookCover)

                val progress = if (readingExperience.reading.totalPages > 0) {
                    (readingExperience.reading.currentPage.toFloat() / readingExperience.reading.totalPages * 100).toInt()
                } else 0
                binding.progressBarCurrentReading.progress = progress
                binding.tvCurrentReadingProgressText.text = "$progress%"

                val quote = readingExperience.reading.favoriteQuote
                binding.llFavoriteQuoteSection.isVisible = !quote.isNullOrBlank()
                binding.tvFavoriteQuote.text = if(!quote.isNullOrBlank()) "“${quote}”" else ""

                val note = readingExperience.reading.personalReflection
                binding.llPersonalNoteSection.isVisible = !note.isNullOrBlank()
                binding.tvPersonalNote.text = note

                binding.btnToggleLike.text = readingExperience.likesCount.toString()
                binding.btnToggleLike.setIconResource(if (readingExperience.isLikedByCurrentUser) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline)

                // CORRECTION DU CRASH
                val likeColor: Int
                if (readingExperience.isLikedByCurrentUser) {
                    likeColor = ContextCompat.getColor(requireContext(), R.color.red_love)
                } else {
                    likeColor = MaterialColors.getColor(binding.btnToggleLike, com.google.android.material.R.attr.colorOnSurfaceVariant)
                }
                binding.btnToggleLike.iconTint = ColorStateList.valueOf(likeColor)
                binding.btnToggleLike.setTextColor(likeColor)

                commentsAdapter.submitList(readingExperience.comments)
            }
        }
    }

    private fun populateProfileData(user: User) {
        binding.tvPublicProfileUsername.text = user.username
        binding.tvFollowersCount.text = user.followersCount.toString()
        binding.tvFollowingCount.text = user.followingCount.toString()
        binding.tvBooksReadCount.text = user.booksReadCount.toString()

        Glide.with(this)
            .load(user.profilePictureUrl)
            .placeholder(R.drawable.ic_profile_placeholder)
            .error(R.drawable.ic_profile_placeholder)
            .circleCrop()
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(binding.ivPublicProfilePicture)
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
            followButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.error_color))
            followButton.icon = null
            followButton.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), android.R.color.transparent))
            followButton.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.error_color))
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

    private fun setupClickListeners() {
        binding.btnToggleFollow.setOnClickListener {
            viewModel.toggleFollowStatus()
            it.isEnabled = false
        }

        binding.btnSendMessage.setOnClickListener {
            val action = PublicProfileFragmentDirections.actionPublicProfileFragmentDestinationToPrivateChatFragmentDestination(
                targetUserId = args.userId
            )
            findNavController().navigate(action)
        }

        binding.btnToggleLike.setOnClickListener { viewModel.toggleLikeOnCurrentReading() }
        binding.btnSendComment.setOnClickListener {
            val commentText = binding.etCommentInput.text.toString()
            viewModel.postCommentOnCurrentReading(commentText)
            binding.etCommentInput.text?.clear()
            val imm = context?.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.hideSoftInputFromWindow(view?.windowToken, 0)
        }

        binding.llFollowersClickableArea.setOnClickListener {
            val targetUsername = viewModel.uiState.value.user?.username ?: args.username
            val action = PublicProfileFragmentDirections.actionPublicProfileFragmentDestinationToMembersFragmentDestinationFollowers(
                userId = args.userId,
                listType = "followers",
                listTitle = getString(R.string.title_followers_of, targetUsername)
            )
            findNavController().navigate(action)
        }

        binding.llFollowingClickableArea.setOnClickListener {
            val targetUsername = viewModel.uiState.value.user?.username ?: args.username
            val action = PublicProfileFragmentDirections.actionPublicProfileFragmentDestinationToMembersFragmentDestinationFollowing(
                userId = args.userId,
                listType = "following",
                listTitle = getString(R.string.title_following_by, targetUsername)
            )
            findNavController().navigate(action)
        }

        binding.llBooksReadClickableArea.setOnClickListener {
            val targetUsername = viewModel.uiState.value.user?.username ?: args.username
            val action = PublicProfileFragmentDirections.actionPublicProfileFragmentDestinationToCompletedReadingsFragment(
                userId = args.userId,
                username = targetUsername
            )
            findNavController().navigate(action)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvComments.adapter = null
        _binding = null
    }
}
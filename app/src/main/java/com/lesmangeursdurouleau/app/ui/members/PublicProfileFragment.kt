// PRÊT À COLLER - Fichier 100% complet et corrigé
package com.lesmangeursdurouleau.app.ui.members

import android.content.res.ColorStateList
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
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        updateActionBarTitle(args.username)
        setupRecyclerViews()
        setupObservers()
        setupClickListeners()
    }

    private fun setupRecyclerViews() {
        commentsAdapter = CommentsAdapter(
            currentUserId = viewModel.currentUserId.value,
            targetProfileOwnerId = args.userId,
            onDeleteClickListener = { commentToDelete ->
                showDeleteConfirmationDialog(commentToDelete)
            },
            onLikeClickListener = { commentToLike ->
                viewModel.toggleLikeOnComment(commentToLike)
            },
            getCommentLikeStatus = { commentId ->
                viewModel.getCommentLikeStatus(commentId)
            },
            lifecycleOwner = viewLifecycleOwner
        )
        binding.rvComments.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = commentsAdapter
            isNestedScrollingEnabled = false
            setHasFixedSize(false)
        }
    }

    private fun setupClickListeners() {
        setupFollowButton()
        setupMessageButton()
        setupCounterClickListeners()
        setupCurrentReadingButton()
        setupCommentInputListeners()
        setupLikeButton()
        setupBooksReadClickListener()
    }

    private fun setupMessageButton() {
        binding.btnSendMessage.setOnClickListener {
            val action = PublicProfileFragmentDirections.actionPublicProfileFragmentDestinationToPrivateChatFragmentDestination(
                targetUserId = args.userId
            )
            findNavController().navigate(action)
        }
    }

    private fun setupObservers() {
        viewModel.userProfile.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.progressBarPublicProfile.visibility = View.VISIBLE
                    binding.tvPublicProfileError.visibility = View.GONE
                    binding.scrollViewPublicProfile.visibility = View.GONE
                }
                is Resource.Success -> {
                    binding.progressBarPublicProfile.visibility = View.GONE
                    resource.data?.let { user ->
                        binding.scrollViewPublicProfile.visibility = View.VISIBLE
                        populateProfileData(user)
                        updateActionBarTitle(user.username)
                    } ?: run {
                        binding.tvPublicProfileError.text = getString(R.string.error_loading_user_data)
                        binding.tvPublicProfileError.visibility = View.VISIBLE
                        binding.scrollViewPublicProfile.visibility = View.GONE
                    }
                }
                is Resource.Error -> {
                    binding.progressBarPublicProfile.visibility = View.GONE
                    binding.tvPublicProfileError.text = resource.message ?: getString(R.string.error_unknown)
                    binding.tvPublicProfileError.visibility = View.VISIBLE
                    binding.scrollViewPublicProfile.visibility = View.GONE
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isFollowing.collectLatest { isFollowingResource ->
                        val currentUserId = viewModel.currentUserId.value
                        val targetUserId = args.userId

                        if (currentUserId != null && currentUserId == targetUserId) {
                            binding.btnToggleFollow.visibility = View.GONE
                            binding.btnSendMessage.visibility = View.GONE
                            return@collectLatest
                        }

                        binding.btnToggleFollow.visibility = View.VISIBLE
                        binding.btnSendMessage.visibility = View.VISIBLE

                        when (isFollowingResource) {
                            is Resource.Loading -> {
                                binding.btnToggleFollow.text = getString(R.string.loading_follow_status)
                                binding.btnToggleFollow.isEnabled = false
                            }
                            is Resource.Success -> {
                                binding.btnToggleFollow.isEnabled = true
                                if (isFollowingResource.data == true) {
                                    binding.btnToggleFollow.text = getString(R.string.unfollow)
                                    binding.btnToggleFollow.setTextColor(requireContext().getColor(R.color.error_color))
                                    binding.btnToggleFollow.strokeColor = requireContext().getColorStateList(R.color.error_color)
                                } else {
                                    binding.btnToggleFollow.text = getString(R.string.follow)
                                    binding.btnToggleFollow.setTextColor(requireContext().getColor(R.color.primary_accent))
                                    binding.btnToggleFollow.strokeColor = requireContext().getColorStateList(R.color.primary_accent)
                                }
                            }
                            is Resource.Error -> {
                                binding.btnToggleFollow.text = getString(R.string.follow_error)
                                binding.btnToggleFollow.isEnabled = false
                                Toast.makeText(context, isFollowingResource.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }

                launch {
                    viewModel.isMutualFollow.collectLatest { mutualFollowResource ->
                        val currentUserId = viewModel.currentUserId.value
                        val targetUserId = args.userId

                        if (currentUserId != null && currentUserId == targetUserId) {
                            binding.cardMutualFollowBadge.visibility = View.GONE
                            return@collectLatest
                        }

                        when (mutualFollowResource) {
                            is Resource.Loading -> binding.cardMutualFollowBadge.visibility = View.GONE
                            is Resource.Success -> binding.cardMutualFollowBadge.visibility = if (mutualFollowResource.data == true) View.VISIBLE else View.GONE
                            is Resource.Error -> binding.cardMutualFollowBadge.visibility = View.GONE
                        }
                    }
                }

                launch {
                    viewModel.currentReadingUiState.collectLatest { uiState ->
                        binding.btnEditCurrentReading.visibility = View.GONE
                        val hasReading = uiState.bookReading != null && uiState.bookDetails != null
                        binding.cardCurrentReading.visibility = if(hasReading) View.VISIBLE else View.GONE
                        binding.cardCommentsSection.visibility = if(hasReading) View.VISIBLE else View.GONE
                        binding.llLikeSection.visibility = if(hasReading) View.VISIBLE else View.GONE

                        if (uiState.isLoading) {
                            binding.cardCurrentReading.visibility = View.GONE
                        }

                        if (uiState.error != null) {
                            Toast.makeText(context, uiState.error, Toast.LENGTH_LONG).show()
                        }

                        if(hasReading) {
                            val reading = uiState.bookReading!!
                            val book = uiState.bookDetails!!

                            Glide.with(this@PublicProfileFragment)
                                .load(book.coverImageUrl)
                                .placeholder(R.drawable.ic_book_placeholder)
                                .error(R.drawable.ic_book_placeholder)
                                .transition(DrawableTransitionOptions.withCrossFade())
                                .into(binding.ivCurrentReadingBookCover)

                            binding.tvCurrentReadingBookTitle.text = book.title
                            binding.tvCurrentReadingBookAuthor.text = book.author

                            if (reading.totalPages > 0) {
                                binding.tvCurrentReadingProgressText.text = getString(R.string.page_progress_format, reading.currentPage, reading.totalPages)
                                binding.progressBarCurrentReading.progress = (reading.currentPage.toFloat() / reading.totalPages.toFloat() * 100).toInt()
                            } else {
                                binding.tvCurrentReadingProgressText.text = getString(R.string.page_progress_unknown)
                                binding.progressBarCurrentReading.progress = 0
                            }

                            val personalNote = reading.favoriteQuote?.takeIf { it.isNotBlank() } ?: reading.personalReflection?.takeIf { it.isNotBlank() }
                            binding.llPersonalReflectionSection.visibility = if (!personalNote.isNullOrBlank()) View.VISIBLE else View.GONE
                            binding.tvCurrentReadingPersonalNote.text = personalNote ?: ""

                            binding.btnEditCurrentReading.visibility = if (uiState.isOwnedProfile) View.VISIBLE else View.GONE
                        }
                    }
                }

                launch {
                    viewModel.comments.collectLatest { resource ->
                        if (resource is Resource.Success) {
                            val commentsList = resource.data ?: emptyList()
                            commentsAdapter.submitList(commentsList)
                            binding.tvNoCommentsYet.visibility = if (commentsList.isEmpty()) View.VISIBLE else View.GONE
                        } else if (resource is Resource.Error) {
                            binding.tvNoCommentsYet.text = getString(R.string.error_loading_comments, resource.message ?: getString(R.string.error_unknown))
                            binding.tvNoCommentsYet.visibility = View.VISIBLE
                        }
                    }
                }

                launch {
                    viewModel.commentEvents.collectLatest { event ->
                        when (event) {
                            is CommentEvent.ShowCommentError -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                            is CommentEvent.ClearCommentInput -> binding.etCommentInput.setText("")
                            is CommentEvent.CommentDeletedSuccess -> Toast.makeText(context, getString(R.string.comment_deleted_success), Toast.LENGTH_SHORT).show()
                            is CommentEvent.ShowCommentLikeError -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                launch {
                    viewModel.isLikedByCurrentUser.collectLatest { resource ->
                        if (resource is Resource.Success) {
                            val isLiked = resource.data == true
                            binding.btnToggleLike.setIconResource(if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline)

                            val colorAttr = if (isLiked) R.color.red_love else com.google.android.material.R.attr.colorOnSurfaceVariant
                            val colorStateList = if (isLiked) {
                                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), colorAttr))
                            } else {
                                ColorStateList.valueOf(MaterialColors.getColor(binding.btnToggleLike, colorAttr))
                            }
                            binding.btnToggleLike.iconTint = colorStateList
                        }
                    }
                }

                launch {
                    viewModel.likesCount.collectLatest { resource ->
                        binding.btnToggleLike.text = if (resource is Resource.Success) (resource.data ?: 0).toString() else "..."
                    }
                }

                launch {
                    viewModel.likeEvents.collectLatest { event ->
                        if (event is LikeEvent.ShowLikeError) {
                            Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun showDeleteConfirmationDialog(comment: Comment) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.delete_comment_dialog_title))
            .setMessage(getString(R.string.delete_comment_dialog_message))
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                viewModel.deleteComment(comment)
            }
            .show()
    }

    private fun setupFollowButton() {
        binding.btnToggleFollow.setOnClickListener {
            viewModel.toggleFollowStatus()
        }
    }

    private fun setupCounterClickListeners() {
        val targetUserId = args.userId
        val targetUsername = viewModel.userProfile.value?.data?.username ?: getString(R.string.profile_title_default)

        binding.llFollowersClickableArea.setOnClickListener {
            val action = PublicProfileFragmentDirections.actionPublicProfileFragmentDestinationToMembersFragmentDestinationFollowers(
                userId = targetUserId,
                listType = "followers",
                listTitle = getString(R.string.title_followers_of, targetUsername)
            )
            findNavController().navigate(action)
        }

        binding.llFollowingClickableArea.setOnClickListener {
            val action = PublicProfileFragmentDirections.actionPublicProfileFragmentDestinationToMembersFragmentDestinationFollowing(
                userId = targetUserId,
                listType = "following",
                listTitle = getString(R.string.title_following_by, targetUsername)
            )
            findNavController().navigate(action)
        }
    }

    private fun setupCurrentReadingButton() {
        binding.btnEditCurrentReading.setOnClickListener {
            if (viewModel.currentReadingUiState.value.isOwnedProfile) {
                val action = PublicProfileFragmentDirections.actionPublicProfileFragmentDestinationToEditCurrentReadingFragment()
                findNavController().navigate(action)
            }
        }
    }

    private fun setupCommentInputListeners() {
        binding.btnSendComment.setOnClickListener {
            viewModel.postComment(binding.etCommentInput.text.toString())
        }
    }

    private fun setupLikeButton() {
        binding.btnToggleLike.setOnClickListener {
            viewModel.toggleLike()
        }
    }

    private fun setupBooksReadClickListener() {
        binding.llBooksReadClickableArea.setOnClickListener {
            val action = PublicProfileFragmentDirections.actionPublicProfileFragmentDestinationToCompletedReadingsFragment(
                userId = args.userId,
                username = viewModel.userProfile.value?.data?.username
            )
            findNavController().navigate(action)
        }
    }

    private fun updateActionBarTitle(username: String?) {
        val title = username?.takeIf { it.isNotBlank() } ?: getString(R.string.profile_title_default)
        (activity as? AppCompatActivity)?.supportActionBar?.title = title
    }

    private fun populateProfileData(user: User) {
        Glide.with(this)
            .load(user.profilePictureUrl)
            .placeholder(R.drawable.ic_profile_placeholder)
            .error(R.drawable.ic_profile_placeholder)
            .circleCrop()
            .into(binding.ivPublicProfilePicture)

        binding.tvPublicProfileUsername.text = user.username.ifEmpty { getString(R.string.username_not_set) }
        binding.tvFollowersCount.text = user.followersCount.toString()
        binding.tvFollowingCount.text = user.followingCount.toString()
        binding.tvBooksReadCount.text = user.booksReadCount.toString()
        binding.tvPublicProfileEmail.text = user.email.ifEmpty { getString(R.string.na) }

        user.createdAt?.let {
            binding.tvPublicProfileJoinedDate.text = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(it)
        } ?: run { binding.tvPublicProfileJoinedDate.text = getString(R.string.na) }

        binding.cardPublicProfileBio.visibility = if (!user.bio.isNullOrBlank()) View.VISIBLE else View.GONE
        binding.tvPublicProfileBio.text = user.bio

        binding.cardPublicProfileCity.visibility = if (!user.city.isNullOrBlank()) View.VISIBLE else View.GONE
        binding.tvPublicProfileCity.text = user.city

        // Ce bloc a été déplacé pour plus de clarté, mais la logique est identique.
        if (user.highestAffinityScore > 0 && !user.highestAffinityPartnerUsername.isNullOrBlank() && !user.highestAffinityTierName.isNullOrBlank()) {
            binding.cardStrongestAffinity.visibility = View.VISIBLE
            binding.tvStrongestAffinityScore.text = getString(
                R.string.strongest_affinity_score_format,
                user.highestAffinityScore,
                user.highestAffinityPartnerUsername
            )
            binding.tvStrongestAffinityTier.text = getString(
                R.string.strongest_affinity_tier_format,
                user.highestAffinityTierName
            )
        } else {
            binding.cardStrongestAffinity.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvComments.adapter = null
        _binding = null
    }
}
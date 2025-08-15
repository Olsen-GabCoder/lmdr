// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier PrivateChatFragment.kt
package com.lesmangeursdurouleau.app.ui.members

import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.*
import com.lesmangeursdurouleau.app.databinding.FragmentPrivateChatBinding
import com.lesmangeursdurouleau.app.ui.members.dictionary.DictionaryDialogFragment
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PrivateChatFragment : Fragment() {

    private var _binding: FragmentPrivateChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PrivateChatViewModel by viewModels()
    private lateinit var messagesAdapter: PrivateMessagesAdapter
    private lateinit var layoutManager: LinearLayoutManager

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    private lateinit var imagePickerLauncher: ActivityResultLauncher<String>

    private var selectedMessage: PrivateMessage? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { viewModel.sendImageMessage(it) }
        }

        childFragmentManager.setFragmentResultListener(LiteraryMenuDialogFragment.REQUEST_KEY, this) { _, bundle ->
            val resultKey = bundle.getString(LiteraryMenuDialogFragment.ACTION_KEY)
            handleLiteraryAction(resultKey)
        }

        childFragmentManager.setFragmentResultListener(MessageActionsDialogFragment.REQUEST_KEY, this) { _, bundle ->
            bundle.getString(MessageActionsDialogFragment.BUNDLE_KEY_REACTION)?.let { emoji ->
                handleMessageReaction(emoji)
            }
            bundle.getString(MessageActionsDialogFragment.BUNDLE_KEY_ACTION)?.let { action ->
                handleMessageAction(action)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPrivateChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupToolbar()
        setupInput()
        observeViewModel()
        setupReplyBar()
    }

    override fun onResume() {
        super.onResume()
        viewModel.setChatActive(true)
    }

    override fun onPause() {
        super.onPause()
        viewModel.setChatActive(false)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun setupRecyclerView() {
        val currentUserId = firebaseAuth.currentUser?.uid ?: ""

        messagesAdapter = PrivateMessagesAdapter(
            currentUserId = currentUserId,
            onMessageLongClick = { _, message -> showActionsMenuForMessage(message) },
            onImageClick = { imageUrl ->
                val action = PrivateChatFragmentDirections.actionPrivateChatFragmentToFullScreenImageFragment(imageUrl)
                findNavController().navigate(action)
            },
            formatDateLabel = { date -> viewModel.formatDateLabel(date) },
            onMessageSwiped = { message -> viewModel.onReplyMessage(message) },
            onReplyClicked = { messageId -> scrollToMessageAndHighlight(messageId) }
        )

        layoutManager = LinearLayoutManager(context).apply { stackFromEnd = true }
        binding.rvMessages.adapter = messagesAdapter
        binding.rvMessages.layoutManager = layoutManager

        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val chatItem = messagesAdapter.currentList.getOrNull(position)
                    if (chatItem is MessageItem) {
                        messagesAdapter.onMessageSwiped(chatItem.message)
                    }
                    messagesAdapter.notifyItemChanged(position)
                }
            }
            override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                val position = viewHolder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = messagesAdapter.currentList[position]
                    if (item is MessageItem) return super.getSwipeDirs(recyclerView, viewHolder)
                }
                return 0
            }
        }
        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(binding.rvMessages)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.chatItems.collect { items ->
                        val wasAtBottom = !binding.rvMessages.canScrollVertically(1)
                        messagesAdapter.submitList(items) {
                            if (wasAtBottom) {
                                binding.rvMessages.scrollToPosition(messagesAdapter.itemCount - 1)
                            }
                        }
                    }
                }

                launch { viewModel.isLoading.collect { binding.progressBar.isVisible = it } }

                launch {
                    viewModel.events.collect { event ->
                        when(event) {
                            is ChatEvent.TierUpgrade -> {
                                val heartAnimation = AnimationUtils.loadAnimation(context, R.anim.anim_heart_pop)
                                binding.toolbarLayout.ivAffinityHeart.startAnimation(heartAnimation)
                                Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()
                            }
                            is ChatEvent.ChallengeCompleted -> {}
                            is ChatEvent.PaginationError -> {
                                Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }

                launch {
                    viewModel.sendState.collectLatest { resource ->
                        if (resource is Resource.Success) {
                            binding.etMessageInput.text.clear()
                        } else if (resource is Resource.Error) {
                            Toast.makeText(context, "Erreur d'envoi: ${resource.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                launch { viewModel.currentUser.collect { resource -> if (resource is Resource.Success<*>) { messagesAdapter.setCurrentUser(resource.data as User?) } } }
                launch { viewModel.targetUser.collect { resource -> if (resource is Resource.Success) { messagesAdapter.setTargetUser(resource.data) } } }
                launch { viewModel.toolbarState.collect { state ->
                    binding.toolbarLayout.tvToolbarName.text = state.userName
                    Glide.with(this@PrivateChatFragment).load(state.userPhotoUrl).placeholder(R.drawable.ic_profile_placeholder).error(R.drawable.ic_profile_placeholder).into(binding.toolbarLayout.ivToolbarPhoto)
                    binding.toolbarLayout.tvToolbarStatus.text = state.userStatus
                    binding.toolbarLayout.tvToolbarStatus.isVisible = state.userStatus.isNotBlank()
                    binding.toolbarLayout.ivAffinityHeart.isVisible = state.showAffinity
                    binding.toolbarLayout.tvAffinityScore.isVisible = state.showAffinity
                    if (state.showAffinity) {
                        binding.toolbarLayout.tvAffinityScore.text = state.affinityScoreText
                        state.affinityIconRes?.let { binding.toolbarLayout.ivAffinityHeart.setImageResource(it) }
                    }
                    binding.toolbarLayout.ivStreakFlame.isVisible = state.isStreakVisible
                }}
                launch { viewModel.isAffinityDataLoading.collect { isLoading ->
                    binding.toolbarLayout.ivAffinityHeart.isEnabled = !isLoading
                    binding.toolbarLayout.tvAffinityScore.isEnabled = !isLoading
                    binding.toolbarLayout.ivAffinityHeart.alpha = if (isLoading) 0.5f else 1.0f
                    binding.toolbarLayout.tvAffinityScore.alpha = if (isLoading) 0.5f else 1.0f
                }}
                launch { viewModel.replyingToMessage.collect { message ->
                    binding.replyBarContainer.isVisible = message != null
                    if (message != null) {
                        binding.tvReplyBarSenderName.text = "Réponse à ${ if (message.senderId == firebaseAuth.currentUser?.uid) "vous-même" else viewModel.targetUser.value.data?.username }"
                        binding.tvReplyBarPreview.text = message.text ?: (if (message.imageUrl != null) "Image" else "Message")
                    }
                }}
                launch { viewModel.deleteState.collectLatest { resource ->
                    when (resource) {
                        is Resource.Success -> { Toast.makeText(context, getString(R.string.message_deleted_successfully), Toast.LENGTH_SHORT).show(); viewModel.resetDeleteState() }
                        is Resource.Error -> { Toast.makeText(context, "Erreur: ${resource.message}", Toast.LENGTH_LONG).show(); viewModel.resetDeleteState() }
                        else -> {}
                    }
                }}
                launch { viewModel.editState.collectLatest { resource ->
                    when (resource) {
                        is Resource.Success -> { Toast.makeText(context, "Message modifié", Toast.LENGTH_SHORT).show(); viewModel.resetEditState() }
                        is Resource.Error -> { Toast.makeText(context, "Erreur: ${resource.message}", Toast.LENGTH_LONG).show(); viewModel.resetEditState() }
                        else -> {}
                    }
                }}
            }
        }
    }

    private fun setupInput() {
        binding.etMessageInput.addTextChangedListener {
            binding.btnSend.isEnabled = it.toString().isNotBlank()
            viewModel.onUserTyping(it.toString())
        }
        binding.btnSend.setOnClickListener {
            val messageText = binding.etMessageInput.text.toString().trim()
            if (messageText.isNotEmpty()) {
                viewModel.sendPrivateMessage(messageText)
            }
        }
        binding.btnAttachFile.setOnClickListener {
            LiteraryMenuDialogFragment().show(childFragmentManager, LiteraryMenuDialogFragment.TAG)
        }
    }

    private fun handleLiteraryAction(actionKey: String?) {
        val action = LiteraryMenuDialogFragment.LiteraryAction.entries.find { it.key == actionKey }
        when (action) {
            LiteraryMenuDialogFragment.LiteraryAction.ATTACH_IMAGE -> imagePickerLauncher.launch("image/*")
            LiteraryMenuDialogFragment.LiteraryAction.SEARCH_DICTIONARY -> DictionaryDialogFragment.newInstance().show(childFragmentManager, DictionaryDialogFragment.TAG)
            else -> Toast.makeText(context, "Action non implémentée.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvMessages.adapter = null
        _binding = null
    }

    private fun scrollToMessageAndHighlight(messageId: String) {
        val position = messagesAdapter.currentList.indexOfFirst { it is MessageItem && it.message.id == messageId }
        if (position != -1) {
            binding.rvMessages.smoothScrollToPosition(position)
            Handler(Looper.getMainLooper()).postDelayed({
                val viewHolder = binding.rvMessages.findViewHolderForAdapterPosition(position)
                viewHolder?.itemView?.let { view ->
                    val animation = AnimationUtils.loadAnimation(context, R.anim.message_highlight_flash)
                    view.startAnimation(animation)
                }
            }, 300)
        } else {
            Toast.makeText(context, "Message original non trouvé", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupReplyBar() {
        binding.btnCancelReply.setOnClickListener { viewModel.cancelReply() }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        val navigateToProfile: (View) -> Unit = {
            viewModel.targetUser.value.data?.let { user ->
                if (user.uid.isNotEmpty()) {
                    val action = PrivateChatFragmentDirections.actionPrivateChatFragmentToPublicProfileFragmentDestination(userId = user.uid, username = user.username)
                    findNavController().navigate(action)
                }
            }
        }
        binding.toolbarLayout.ivToolbarPhoto.setOnClickListener(navigateToProfile)
        binding.toolbarLayout.tvToolbarName.setOnClickListener(navigateToProfile)
        binding.toolbarLayout.ivAffinityHeart.setOnClickListener { showAffinityRetrospectiveDialog() }
        binding.toolbarLayout.tvAffinityScore.setOnClickListener { showAffinityRetrospectiveDialog() }
    }

    private fun showAffinityRetrospectiveDialog() {
        val conversation = viewModel.conversation.value
        val challenges = (viewModel.weeklyChallenges.value as? Resource.Success)?.data
        if (isAdded && conversation != null && challenges != null) {
            val dialog = AffinityRetrospectiveDialogFragment.newInstance(conversation = conversation, challenges = challenges)
            dialog.show(childFragmentManager, AffinityRetrospectiveDialogFragment.TAG)
        } else {
            Toast.makeText(context, "Veuillez patienter, les données se chargent.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showActionsMenuForMessage(message: PrivateMessage) {
        this.selectedMessage = message
        val isSentByCurrentUser = message.senderId == firebaseAuth.currentUser?.uid
        val hasTextContent = !message.text.isNullOrBlank()

        val dialog = MessageActionsDialogFragment.newInstance(isSentByCurrentUser, hasTextContent)
        dialog.show(childFragmentManager, MessageActionsDialogFragment.TAG)
    }

    private fun handleMessageReaction(emoji: String) {
        val message = selectedMessage ?: return
        message.id?.let { messageId ->
            viewModel.addOrUpdateReaction(messageId, emoji)
            messagesAdapter.animateReactionForMessage(messageId)
        }
        this.selectedMessage = null
    }

    private fun handleMessageAction(action: String?) {
        val message = selectedMessage ?: return
        when (action) {
            MessageActionsDialogFragment.ACTION_REPLY -> viewModel.onReplyMessage(message)
            // === DÉBUT DE LA CORRECTION : Restauration de la ligne de code valide ===
            MessageActionsDialogFragment.ACTION_COPY -> copyMessageToClipboard(message.text)
            // === FIN DE LA CORRECTION ===
            MessageActionsDialogFragment.ACTION_EDIT -> showEditMessageDialog(message)
            MessageActionsDialogFragment.ACTION_DELETE -> showDeleteConfirmationDialog(message)

            MessageActionsDialogFragment.ACTION_FORWARD -> {
                val navAction = PrivateChatFragmentDirections.actionPrivateChatFragmentToForwardMessageFragment(
                    messageText = message.text,
                    imageUrl = message.imageUrl
                )
                findNavController().navigate(navAction)
            }

            MessageActionsDialogFragment.ACTION_REPORT -> {
                Toast.makeText(context, "Action '$action' non implémentée.", Toast.LENGTH_SHORT).show()
            }
        }
        this.selectedMessage = null
    }

    private fun showEditMessageDialog(message: PrivateMessage) {
        val messageId = message.id ?: return
        val textInputLayout = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_message, null) as TextInputLayout
        val editText = textInputLayout.editText
        editText?.setText(message.text)
        message.text?.let { editText?.setSelection(it.length) }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Modifier le message")
            .setView(textInputLayout)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton("Enregistrer") { _, _ ->
                val newText = editText?.text.toString().trim()
                if (newText.isNotEmpty() && newText != message.text) {
                    viewModel.editMessage(messageId, newText)
                }
            }
            .show()
    }

    private fun copyMessageToClipboard(text: String?) {
        if (text.isNullOrEmpty()) return
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Message Text", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, getString(R.string.message_copied_to_clipboard), Toast.LENGTH_SHORT).show()
    }

    private fun showDeleteConfirmationDialog(message: PrivateMessage) {
        val messageId = message.id ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.delete_message_dialog_title))
            .setMessage(getString(R.string.delete_message_dialog_message))
            .setNegativeButton(getString(R.string.cancel), null)
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                viewModel.deleteMessage(messageId)
            }
            .show()
    }
}
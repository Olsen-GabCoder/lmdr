// PRÊT À COLLER - Fichier 100% final, avec l'intégration du Dictionnaire
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
import android.widget.PopupWindow
import android.widget.TextView
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
import androidx.localbroadcastmanager.content.LocalBroadcastManager
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
import com.lesmangeursdurouleau.app.notifications.MyFirebaseMessagingService
import com.lesmangeursdurouleau.app.ui.members.dictionary.DictionaryDialogFragment // NOUVEL IMPORT
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

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    private lateinit var imagePickerLauncher: ActivityResultLauncher<String>

    private val tierUpgradeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == MyFirebaseMessagingService.ACTION_TIER_UPGRADE) {
                val conversationId = intent.getStringExtra(MyFirebaseMessagingService.CONVERSATION_ID_KEY)
                val message = intent.getStringExtra(MyFirebaseMessagingService.BODY_KEY) ?: "Nouveau palier atteint !"

                if (conversationId == viewModel.conversationId.value) {
                    Log.d("PrivateChatFragment", "Tier Upgrade event received for this conversation!")
                    viewModel.triggerTierUpgradeEffect(message)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // L'initialisation de l'image picker reste ici, elle est essentielle.
        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                viewModel.sendImageMessage(it)
            }
        }

        childFragmentManager.setFragmentResultListener(LiteraryMenuDialogFragment.REQUEST_KEY, this) { _, bundle ->
            val resultKey = bundle.getString(LiteraryMenuDialogFragment.ACTION_KEY)
            handleLiteraryAction(resultKey)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
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
        val filter = IntentFilter(MyFirebaseMessagingService.ACTION_TIER_UPGRADE)
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(tierUpgradeReceiver, filter)
    }

    override fun onPause() {
        super.onPause()
        viewModel.setChatActive(false)
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(tierUpgradeReceiver)
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

    // ==========================================================
    // DÉBUT DE LA MODIFICATION : GESTION DE L'ACTION DU DICTIONNAIRE
    // ==========================================================
    private fun handleLiteraryAction(actionKey: String?) {
        val action = LiteraryMenuDialogFragment.LiteraryAction.values().find { it.key == actionKey }

        when (action) {
            LiteraryMenuDialogFragment.LiteraryAction.ATTACH_IMAGE -> {
                imagePickerLauncher.launch("image/*")
            }
            LiteraryMenuDialogFragment.LiteraryAction.SEARCH_DICTIONARY -> {
                DictionaryDialogFragment.newInstance().show(childFragmentManager, DictionaryDialogFragment.TAG)
            }
            // Les autres actions affichent un Toast pour le moment
            LiteraryMenuDialogFragment.LiteraryAction.RECOMMEND_BOOK -> Toast.makeText(context, "Action : Conseiller un livre", Toast.LENGTH_SHORT).show()
            LiteraryMenuDialogFragment.LiteraryAction.ADD_TO_LIST -> Toast.makeText(context, "Action : Ajouter à ma liste", Toast.LENGTH_SHORT).show()
            LiteraryMenuDialogFragment.LiteraryAction.LITERARY_CHALLENGES -> Toast.makeText(context, "Action : Défis littéraires", Toast.LENGTH_SHORT).show()
            LiteraryMenuDialogFragment.LiteraryAction.QUICK_SUMMARY -> Toast.makeText(context, "Action : Résumé rapide", Toast.LENGTH_SHORT).show()
            LiteraryMenuDialogFragment.LiteraryAction.START_DEBATE -> Toast.makeText(context, "Action : Lancer un débat", Toast.LENGTH_SHORT).show()
            LiteraryMenuDialogFragment.LiteraryAction.GUIDED_SUGGESTION -> Toast.makeText(context, "Action : Suggestion guidée", Toast.LENGTH_SHORT).show()
            LiteraryMenuDialogFragment.LiteraryAction.FAVORITE_CHARACTER -> Toast.makeText(context, "Action : Personnage préféré", Toast.LENGTH_SHORT).show()
            LiteraryMenuDialogFragment.LiteraryAction.PLAN_SESSION -> Toast.makeText(context, "Action : Planifier une séance", Toast.LENGTH_SHORT).show()
            null -> {
                // Gère le cas où la clé serait nulle ou inconnue
                Toast.makeText(context, "Action inconnue", Toast.LENGTH_SHORT).show()
            }
        }
    }
    // ========================================================
    // FIN DE LA MODIFICATION
    // ========================================================

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Le reste du fichier est identique...
    @RequiresApi(Build.VERSION_CODES.N)
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.chatItems.collect { resource ->
                        binding.progressBar.isVisible = resource is Resource.Loading
                        when (resource) {
                            is Resource.Loading -> { /* No-op */ }
                            is Resource.Success -> messagesAdapter.submitList(resource.data)
                            is Resource.Error -> Toast.makeText(context, resource.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }

                launch {
                    viewModel.currentUser.collect { resource ->
                        if (resource is Resource.Success<*>) {
                            messagesAdapter.setCurrentUser(resource.data as User?)
                        }
                    }
                }

                launch {
                    viewModel.targetUser.collect { resource ->
                        if (resource is Resource.Success) {
                            messagesAdapter.setTargetUser(resource.data)
                        }
                    }
                }

                launch {
                    viewModel.toolbarState.collect { state ->
                        binding.toolbarLayout.tvToolbarName.text = state.userName
                        Glide.with(this@PrivateChatFragment)
                            .load(state.userPhotoUrl)
                            .placeholder(R.drawable.ic_profile_placeholder)
                            .error(R.drawable.ic_profile_placeholder)
                            .into(binding.toolbarLayout.ivToolbarPhoto)

                        val toolbarStatus = binding.toolbarLayout.tvToolbarStatus
                        toolbarStatus.text = state.userStatus
                        toolbarStatus.isVisible = state.userStatus.isNotBlank()

                        val shouldShowAffinity = state.showAffinity
                        binding.toolbarLayout.ivAffinityHeart.isVisible = shouldShowAffinity
                        binding.toolbarLayout.tvAffinityScore.isVisible = shouldShowAffinity

                        if (shouldShowAffinity) {
                            binding.toolbarLayout.tvAffinityScore.text = state.affinityScoreText
                            state.affinityIconRes?.let {
                                binding.toolbarLayout.ivAffinityHeart.setImageResource(it)
                            }
                        }

                        binding.toolbarLayout.ivStreakFlame.isVisible = state.isStreakVisible
                    }
                }

                // OBSERVATEUR QUI GÈRE L'ÉTAT (ACTIVÉ/DÉSACITVÉ) DES BOUTONS D'AFFINITÉ
                launch {
                    viewModel.isAffinityDataLoading.collect { isLoading ->
                        binding.toolbarLayout.ivAffinityHeart.isEnabled = !isLoading
                        binding.toolbarLayout.tvAffinityScore.isEnabled = !isLoading
                        // Effet visuel pour indiquer que le bouton est désactivé
                        binding.toolbarLayout.ivAffinityHeart.alpha = if (isLoading) 0.5f else 1.0f
                        binding.toolbarLayout.tvAffinityScore.alpha = if (isLoading) 0.5f else 1.0f
                    }
                }

                launch {
                    viewModel.events.collect { event ->
                        when(event) {
                            is ChatEvent.TierUpgrade -> {
                                val heartAnimation = AnimationUtils.loadAnimation(context, R.anim.anim_heart_pop)
                                binding.toolbarLayout.ivAffinityHeart.startAnimation(heartAnimation)
                                Snackbar.make(binding.root, event.message, Snackbar.LENGTH_LONG).show()
                            }
                            is ChatEvent.ChallengeCompleted -> {
                                // Géré dans le DialogFragment
                            }
                        }
                    }
                }

                launch {
                    viewModel.replyingToMessage.collect { message ->
                        if (message != null) {
                            binding.replyBarContainer.isVisible = true
                            binding.tvReplyBarSenderName.text = "Réponse à ${
                                if (message.senderId == firebaseAuth.currentUser?.uid) "vous-même"
                                else viewModel.targetUser.value.data?.username
                            }"
                            binding.tvReplyBarPreview.text = message.text ?: (if (message.imageUrl != null) "Image" else "Message")
                        } else {
                            binding.replyBarContainer.isVisible = false
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

                launch {
                    viewModel.deleteState.collectLatest { resource ->
                        when (resource) {
                            is Resource.Loading -> { /* No-op */ }
                            is Resource.Success -> {
                                Toast.makeText(context, getString(R.string.message_deleted_successfully), Toast.LENGTH_SHORT).show()
                                viewModel.resetDeleteState()
                            }
                            is Resource.Error -> {
                                Toast.makeText(context, "Erreur: ${resource.message}", Toast.LENGTH_LONG).show()
                                viewModel.resetDeleteState()
                            }
                            null -> { /* Initial state */ }
                        }
                    }
                }

                launch {
                    viewModel.editState.collectLatest { resource ->
                        when (resource) {
                            is Resource.Loading -> { /* No-op */ }
                            is Resource.Success -> {
                                Toast.makeText(context, "Message modifié", Toast.LENGTH_SHORT).show()
                                viewModel.resetEditState()
                            }
                            is Resource.Error -> {
                                Toast.makeText(context, "Erreur: ${resource.message}", Toast.LENGTH_LONG).show()
                                viewModel.resetEditState()
                            }
                            null -> { /* Initial state */ }
                        }
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun setupRecyclerView() {
        val currentUserId = firebaseAuth.currentUser?.uid ?: ""

        messagesAdapter = PrivateMessagesAdapter(
            currentUserId = currentUserId,
            onMessageLongClick = { anchorView, message -> showActionsMenuForMessage(anchorView, message) },
            onImageClick = { imageUrl ->
                val action = PrivateChatFragmentDirections.actionPrivateChatFragmentToFullScreenImageFragment(imageUrl)
                findNavController().navigate(action)
            },
            formatDateLabel = { date, context -> viewModel.formatDateLabel(date, context) },
            onMessageSwiped = { message ->
                viewModel.onReplyMessage(message)
            },
            onReplyClicked = { messageId ->
                scrollToMessageAndHighlight(messageId)
            }
        )

        val layoutManager = LinearLayoutManager(context).apply { stackFromEnd = true }
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
        messagesAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                binding.rvMessages.scrollToPosition(messagesAdapter.itemCount - 1)
            }
        })
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
        binding.btnCancelReply.setOnClickListener {
            viewModel.cancelReply()
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        val navigateToProfile: (View) -> Unit = {
            viewModel.targetUser.value.data?.let { user ->
                if (user.uid.isNotEmpty()) {
                    val action = PrivateChatFragmentDirections.actionPrivateChatFragmentToPublicProfileFragmentDestination(
                        userId = user.uid,
                        username = user.username
                    )
                    findNavController().navigate(action)
                }
            }
        }

        binding.toolbarLayout.ivToolbarPhoto.setOnClickListener(navigateToProfile)
        binding.toolbarLayout.tvToolbarName.setOnClickListener(navigateToProfile)

        binding.toolbarLayout.ivAffinityHeart.setOnClickListener {
            showAffinityRetrospectiveDialog()
        }
        binding.toolbarLayout.tvAffinityScore.setOnClickListener {
            showAffinityRetrospectiveDialog()
        }
    }

    private fun showAffinityRetrospectiveDialog() {
        val conversation = viewModel.conversation.value
        val challengesResource = viewModel.weeklyChallenges.value
        val challenges = (challengesResource as? Resource.Success)?.data

        if (isAdded && conversation != null && challenges != null) {
            val dialog = AffinityRetrospectiveDialogFragment.newInstance(
                conversation = conversation,
                challenges = challenges
            )
            dialog.show(childFragmentManager, AffinityRetrospectiveDialogFragment.TAG)
        } else {
            Toast.makeText(context, "Veuillez patienter, les données se chargent.", Toast.LENGTH_SHORT).show()
            Log.w("AffinityDialog", "Tentative d'ouverture du dialogue alors que les données n'étaient pas prêtes.")
        }
    }

    private fun showActionsMenuForMessage(anchorView: View, message: PrivateMessage) {
        val messageId = message.id
        if (messageId.isNullOrBlank()) {
            Toast.makeText(context, getString(R.string.error_invalid_message_id), Toast.LENGTH_SHORT).show()
            return
        }
        val inflater = LayoutInflater.from(requireContext())
        val popupView = inflater.inflate(R.layout.popup_message_actions, binding.root, false)
        val popupWindow = PopupWindow(popupView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)

        val emojis = mapOf(R.id.emoji_thumbs_up to "👍", R.id.emoji_heart to "❤️", R.id.emoji_laugh to "😂", R.id.emoji_wow to "😮", R.id.emoji_sad to "😢")
        emojis.forEach { (id, emoji) ->
            popupView.findViewById<TextView>(id).setOnClickListener {
                viewModel.addOrUpdateReaction(messageId, emoji)
                messagesAdapter.animateReactionForMessage(messageId)
                popupWindow.dismiss()
            }
        }

        popupView.findViewById<TextView>(R.id.action_copy_message_popup).setOnClickListener {
            copyMessageToClipboard(message.text)
            popupWindow.dismiss()
        }

        val editActionView = popupView.findViewById<TextView>(R.id.action_edit_message_popup)
        val deleteActionView = popupView.findViewById<TextView>(R.id.action_delete_message_popup)
        val separatorView = popupView.findViewById<View>(R.id.separator)

        val isSentByCurrentUser = message.senderId == firebaseAuth.currentUser?.uid
        if (isSentByCurrentUser && !message.text.isNullOrBlank()) {
            editActionView.setOnClickListener {
                showEditMessageDialog(message)
                popupWindow.dismiss()
            }
        } else {
            editActionView.isVisible = false
        }

        if (isSentByCurrentUser) {
            deleteActionView.setOnClickListener {
                showDeleteConfirmationDialog(message)
                popupWindow.dismiss()
            }
        } else {
            deleteActionView.isVisible = false
        }

        if (!editActionView.isVisible && !deleteActionView.isVisible) {
            separatorView.isVisible = false
        }

        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val popupWidth = popupView.measuredWidth
        val popupHeight = popupView.measuredHeight
        val location = IntArray(2)
        anchorView.getLocationOnScreen(location)
        val x = location[0] + (anchorView.width - popupWidth) / 2
        val y = location[1] - popupHeight - 16
        popupWindow.showAtLocation(anchorView, 0, x, y)
    }

    private fun showEditMessageDialog(message: PrivateMessage) {
        if (message.id.isNullOrBlank()) return
        val textInputLayout = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_message, null) as TextInputLayout
        val editText = textInputLayout.editText
        editText?.setText(message.text)
        message.text?.let { editText?.setSelection(it.length) }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Modifier le message")
            .setView(textInputLayout)
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .setPositiveButton("Enregistrer") { dialog, _ ->
                val newText = editText?.text.toString().trim()
                if (newText.isNotEmpty() && newText != message.text) {
                    viewModel.editMessage(message.id, newText)
                }
                dialog.dismiss()
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
        val messageId = message.id
        if (messageId.isNullOrBlank()) {
            Toast.makeText(context, getString(R.string.error_invalid_message_id), Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.delete_message_dialog_title))
            .setMessage(getString(R.string.delete_message_dialog_message))
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ -> dialog.dismiss() }
            .setPositiveButton(getString(R.string.delete)) { dialog, _ ->
                viewModel.deleteMessage(messageId)
                dialog.dismiss()
            }
            .show()
    }
}
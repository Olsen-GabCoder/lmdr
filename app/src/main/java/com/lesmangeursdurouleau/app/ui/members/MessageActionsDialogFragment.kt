// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier MessageActionsDialogFragment.kt
package com.lesmangeursdurouleau.app.ui.members

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lesmangeursdurouleau.app.databinding.DialogMessageActionsBinding
import com.lesmangeursdurouleau.app.databinding.ItemEmojiReactionBinding

class MessageActionsDialogFragment : BottomSheetDialogFragment() {

    private var _binding: DialogMessageActionsBinding? = null
    private val binding get() = _binding!!

    private var isSentByCurrentUser: Boolean = false
    private var hasTextContent: Boolean = false

    private val emojiList = listOf(
        "👍", "❤️", "😂", "😮", "😢", "🙏", "👏", "🔥", "🤔", "🎉"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isSentByCurrentUser = arguments?.getBoolean(ARG_IS_SENT_BY_CURRENT_USER) ?: false
        hasTextContent = arguments?.getBoolean(ARG_HAS_TEXT_CONTENT) ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogMessageActionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupVisibility()
        setupClickListeners()
        setupEmojiRecyclerView()
    }

    private fun setupEmojiRecyclerView() {
        val emojiAdapter = EmojiAdapter { emoji ->
            sendResult(BUNDLE_KEY_REACTION, emoji)
        }
        binding.rvEmojiReactions.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = emojiAdapter
        }
        emojiAdapter.submitList(emojiList)
    }

    private fun setupVisibility() {
        binding.actionCopy.isVisible = hasTextContent
        binding.actionEdit.isVisible = isSentByCurrentUser && hasTextContent

        // La section destructive entière (incluant Supprimer et Signaler) n'est visible
        // que si le message a été envoyé par l'utilisateur actuel.
        binding.destructiveActionsSection.isVisible = isSentByCurrentUser
    }

    private fun setupClickListeners() {
        // Clics sur les actions de la grille
        binding.actionReply.setOnClickListener { sendResult(BUNDLE_KEY_ACTION, ACTION_REPLY) }
        binding.actionCopy.setOnClickListener { sendResult(BUNDLE_KEY_ACTION, ACTION_COPY) }
        binding.actionEdit.setOnClickListener { sendResult(BUNDLE_KEY_ACTION, ACTION_EDIT) }
        binding.actionForward.setOnClickListener { sendResult(BUNDLE_KEY_ACTION, ACTION_FORWARD) }

        // Clics sur les actions destructives
        binding.actionDelete.setOnClickListener { sendResult(BUNDLE_KEY_ACTION, ACTION_DELETE) }
        binding.actionReport.setOnClickListener { sendResult(BUNDLE_KEY_ACTION, ACTION_REPORT) }
    }

    private fun sendResult(key: String, value: String) {
        setFragmentResult(REQUEST_KEY, bundleOf(key to value))
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "MessageActionsDialogFragment"
        const val REQUEST_KEY = "messageActionsRequest"
        const val BUNDLE_KEY_ACTION = "selectedAction"
        const val BUNDLE_KEY_REACTION = "selectedReaction"

        const val ACTION_REPLY = "reply"
        const val ACTION_COPY = "copy"
        const val ACTION_EDIT = "edit"
        const val ACTION_FORWARD = "forward"
        const val ACTION_DELETE = "delete"
        const val ACTION_REPORT = "report"

        private const val ARG_IS_SENT_BY_CURRENT_USER = "isSentByCurrentUser"
        private const val ARG_HAS_TEXT_CONTENT = "hasTextContent"

        fun newInstance(isSentByCurrentUser: Boolean, hasTextContent: Boolean): MessageActionsDialogFragment {
            return MessageActionsDialogFragment().apply {
                arguments = bundleOf(
                    ARG_IS_SENT_BY_CURRENT_USER to isSentByCurrentUser,
                    ARG_HAS_TEXT_CONTENT to hasTextContent
                )
            }
        }
    }

    private class EmojiAdapter(private val onEmojiClick: (String) -> Unit) :
        RecyclerView.Adapter<EmojiAdapter.EmojiViewHolder>() {

        private val emojis = mutableListOf<String>()

        fun submitList(list: List<String>) {
            emojis.clear()
            emojis.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiViewHolder {
            val binding = ItemEmojiReactionBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return EmojiViewHolder(binding, onEmojiClick)
        }

        override fun onBindViewHolder(holder: EmojiViewHolder, position: Int) {
            holder.bind(emojis[position])
        }

        override fun getItemCount(): Int = emojis.size

        class EmojiViewHolder(
            private val binding: ItemEmojiReactionBinding,
            private val onEmojiClick: (String) -> Unit
        ) : RecyclerView.ViewHolder(binding.root) {
            fun bind(emoji: String) {
                binding.emojiText.text = emoji
                itemView.setOnClickListener { onEmojiClick(emoji) }
            }
        }
    }
}
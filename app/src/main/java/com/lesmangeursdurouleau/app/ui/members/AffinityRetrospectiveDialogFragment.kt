// PRÊT À COLLER - Fichier complet synchronisé avec le nouveau design
package com.lesmangeursdurouleau.app.ui.members

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.R as MaterialR
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.Challenge
import com.lesmangeursdurouleau.app.data.model.Conversation
import com.lesmangeursdurouleau.app.databinding.DialogAffinityRetrospectiveBinding
import dagger.hilt.android.AndroidEntryPoint
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

@AndroidEntryPoint
class AffinityRetrospectiveDialogFragment : DialogFragment() {

    private var _binding: DialogAffinityRetrospectiveBinding? = null
    private val binding get() = _binding!!

    private var challenges: List<Challenge> = emptyList()
    private var completedChallengeIds: List<String> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAffinityRetrospectiveBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val affinityScore = arguments?.getInt(ARG_SCORE) ?: 0
        val totalMessages = arguments?.getLong(ARG_TOTAL_MESSAGES) ?: 0L
        val firstMessageTimestamp = arguments?.getLong(ARG_FIRST_MESSAGE_DATE) ?: 0L
        challenges = arguments?.getParcelableArrayList(ARG_CHALLENGES) ?: emptyList()
        completedChallengeIds = arguments?.getStringArrayList(ARG_COMPLETED_CHALLENGES) ?: emptyList()

        setupStaticInfo(affinityScore, totalMessages, firstMessageTimestamp)
        setupChallenges()
        setupClickListeners()
    }

    // ==============================================================
    // ====          FONCTION MISE À JOUR POUR LE NOUVEAU DESIGN         ====
    // ==============================================================
    private fun setupStaticInfo(score: Int, totalMessages: Long, firstMessageTimestamp: Long) {
        // Lier les données aux nouveaux TextViews
        binding.tvAffinityScoreValue.text = score.toString()
        binding.tvTotalMessages.text = totalMessages.toString()

        // Gérer l'affichage de la date
        if (firstMessageTimestamp > 0) {
            binding.tvFirstMessageDate.text = formatDaysSince(firstMessageTimestamp)
        } else {
            binding.tvFirstMessageDate.text = "-" // Affichage neutre si la date n'est pas dispo
        }
    }

    /**
     * Calcule le nombre de jours depuis un timestamp donné et le formate.
     */
    private fun formatDaysSince(timestamp: Long): String {
        val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - timestamp)
        return when {
            days < 1 -> "Auj." // Aujourd'hui
            else -> "${days}j"
        }
    }


    private fun setupChallenges() {
        binding.challengesContainer.removeAllViews()
        if (challenges.isEmpty()) {
            val noChallengeView = TextView(requireContext()).apply {
                text = getString(R.string.no_challenges_available)
                setTextAppearance(MaterialR.style.TextAppearance_Material3_BodyMedium)
            }
            binding.challengesContainer.addView(noChallengeView)
        } else {
            challenges.forEach { challenge ->
                val isCompleted = completedChallengeIds.contains(challenge.id)
                addChallengeView(challenge, isCompleted)
            }
        }
    }

    private fun addChallengeView(challenge: Challenge, isCompleted: Boolean) {
        val inflater = LayoutInflater.from(requireContext())
        val challengeView = inflater.inflate(R.layout.item_weekly_challenge, binding.challengesContainer, false)

        val title: TextView = challengeView.findViewById(R.id.tv_challenge_title)
        val button: Button = challengeView.findViewById(R.id.btn_complete_challenge)

        title.text = challenge.title
        button.isEnabled = !isCompleted
        button.text = if (isCompleted) getString(R.string.challenge_completed) else getString(R.string.complete_challenge_button, challenge.bonusPoints)

        if (!isCompleted) {
            button.setOnClickListener {
                Toast.makeText(context, "Logique de complétion à implémenter.", Toast.LENGTH_SHORT).show()
            }
        }
        binding.challengesContainer.addView(challengeView)
    }

    private fun setupClickListeners() {
        binding.btnClose.setOnClickListener { dismiss() }

        binding.btnViewLeaderboard.setOnClickListener {
            findNavController().navigate(R.id.action_privateChatFragment_to_leaderboardFragment)
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AffinityRetrospectiveDialog"

        private const val ARG_SCORE = "ARG_SCORE"
        private const val ARG_TOTAL_MESSAGES = "ARG_TOTAL_MESSAGES"
        private const val ARG_FIRST_MESSAGE_DATE = "ARG_FIRST_MESSAGE_DATE"
        private const val ARG_CHALLENGES = "ARG_CHALLENGES"
        private const val ARG_COMPLETED_CHALLENGES = "ARG_COMPLETED_CHALLENGES"

        fun newInstance(
            conversation: Conversation,
            challenges: List<Challenge>
        ): AffinityRetrospectiveDialogFragment {
            val fragment = AffinityRetrospectiveDialogFragment()
            val args = Bundle().apply {
                putInt(ARG_SCORE, conversation.affinityScore)
                putLong(ARG_TOTAL_MESSAGES, conversation.totalMessageCount)
                conversation.firstMessageTimestamp?.let { putLong(ARG_FIRST_MESSAGE_DATE, it.time) }
                putParcelableArrayList(ARG_CHALLENGES, ArrayList(challenges))
                putStringArrayList(ARG_COMPLETED_CHALLENGES, ArrayList(conversation.completedChallengeIds))
            }
            fragment.arguments = args
            return fragment
        }
    }
}
// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier AffinityRetrospectiveFragment.kt
package com.lesmangeursdurouleau.app.ui.members

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.R as MaterialR
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.Challenge
import com.lesmangeursdurouleau.app.databinding.FragmentAffinityRetrospectiveBinding
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class AffinityRetrospectiveFragment : Fragment() {

    private var _binding: FragmentAffinityRetrospectiveBinding? = null
    private val binding get() = _binding!!

    // Utilisation des Safe Args pour récupérer les arguments de navigation de manière type-safe.
    private val args: AffinityRetrospectiveFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAffinityRetrospectiveBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val conversation = args.conversation
        val challenges = args.challenges.toList()

        setupToolbar()
        setupStaticInfo(conversation)
        setupChallenges(challenges, conversation.completedChallengeIds)
        setupClickListeners()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupStaticInfo(conversation: com.lesmangeursdurouleau.app.data.model.Conversation) {
        binding.tvAffinityScoreValue.text = conversation.affinityScore.toString()
        binding.tvTotalMessages.text = conversation.totalMessageCount.toString()

        conversation.firstMessageTimestamp?.let {
            binding.tvFirstMessageDate.text = formatDaysSince(it.time)
        } ?: run {
            binding.tvFirstMessageDate.text = "-"
        }
    }

    private fun formatDaysSince(timestamp: Long): String {
        val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - timestamp)
        return when {
            days < 1 -> getString(R.string.today_abbreviation)
            else -> getString(R.string.days_abbreviation, days)
        }
    }

    private fun setupChallenges(challenges: List<Challenge>, completedIds: List<String>) {
        binding.challengesContainer.removeAllViews()
        if (challenges.isEmpty()) {
            val noChallengeView = TextView(requireContext()).apply {
                text = getString(R.string.no_challenges_available)
                setTextAppearance(MaterialR.style.TextAppearance_Material3_BodyMedium)
            }
            binding.challengesContainer.addView(noChallengeView)
        } else {
            challenges.forEach { challenge ->
                val isCompleted = completedIds.contains(challenge.id)
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
        binding.btnViewLeaderboard.setOnClickListener {
            // L'action de navigation vers le leaderboard doit être définie depuis ce nouveau fragment
            val action = AffinityRetrospectiveFragmentDirections.actionAffinityRetrospectiveFragmentToLeaderboardFragment()
            findNavController().navigate(action)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
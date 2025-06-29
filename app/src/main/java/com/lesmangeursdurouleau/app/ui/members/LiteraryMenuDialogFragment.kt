// PRÊT À COLLER - Fichier 100% final et corrigé
package com.lesmangeursdurouleau.app.ui.members

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.databinding.FragmentLiteraryMenuBinding
import com.lesmangeursdurouleau.app.databinding.ItemLiteraryMenuButtonBinding

class LiteraryMenuDialogFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentLiteraryMenuBinding? = null
    private val binding get() = _binding!!

    enum class LiteraryAction(val key: String) {
        ATTACH_IMAGE("ATTACH_IMAGE"),
        RECOMMEND_BOOK("RECOMMEND_BOOK"),
        SEARCH_DICTIONARY("SEARCH_DICTIONARY"),
        ADD_TO_LIST("ADD_TO_LIST"),
        LITERARY_CHALLENGES("LITERARY_CHALLENGES"),
        QUICK_SUMMARY("QUICK_SUMMARY"),
        START_DEBATE("START_DEBATE"),
        GUIDED_SUGGESTION("GUIDED_SUGGESTION"),
        FAVORITE_CHARACTER("FAVORITE_CHARACTER"),
        PLAN_SESSION("PLAN_SESSION")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLiteraryMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenu()
    }

    // =================================================================================
    // DÉBUT DE LA MODIFICATION : Utilisation des nouvelles icônes et des nouveaux libellés
    // =================================================================================
    private fun setupMenu() {
        // Configuration de tous les boutons avec les nouvelles ressources graphiques et les libellés courts
        setupButton(binding.actionAttachImage, R.drawable.icon_menu_gallery, "Galerie", LiteraryAction.ATTACH_IMAGE)
        setupButton(binding.actionRecommendBook, R.drawable.icon_menu_book, "Livre", LiteraryAction.RECOMMEND_BOOK)
        setupButton(binding.actionSearchDictionary, R.drawable.icon_menu_search, "Dico", LiteraryAction.SEARCH_DICTIONARY)
        setupButton(binding.actionAddToList, R.drawable.icon_menu_list, "Liste", LiteraryAction.ADD_TO_LIST)

        setupButton(binding.actionLiteraryChallenges, R.drawable.icon_menu_challenges, "Défis", LiteraryAction.LITERARY_CHALLENGES)
        setupButton(binding.actionQuickSummary, R.drawable.icon_menu_summary, "Résumé", LiteraryAction.QUICK_SUMMARY)
        setupButton(binding.actionStartDebate, R.drawable.icon_menu_debate, "Débat", LiteraryAction.START_DEBATE)
        setupButton(binding.actionGuidedSuggestion, R.drawable.icon_menu_suggestion, "Suggestion", LiteraryAction.GUIDED_SUGGESTION)

        setupButton(binding.actionFavoriteCharacter, R.drawable.icon_menu_character, "Personnage", LiteraryAction.FAVORITE_CHARACTER)
        setupButton(binding.actionPlanSession, R.drawable.icon_menu_event, "Événement", LiteraryAction.PLAN_SESSION)
    }
    // =================================================================================
    // FIN DE LA MODIFICATION
    // =================================================================================

    private fun setupButton(
        buttonBinding: ItemLiteraryMenuButtonBinding,
        iconRes: Int,
        label: String,
        action: LiteraryAction
    ) {
        buttonBinding.ivMenuItemIcon.setImageResource(iconRes)
        buttonBinding.tvMenuItemLabel.text = label
        buttonBinding.root.setOnClickListener {
            onActionClicked(action)
        }
    }

    private fun onActionClicked(action: LiteraryAction) {
        // Utilisation de FragmentResult API pour communiquer avec le fragment parent
        setFragmentResult(REQUEST_KEY, bundleOf(ACTION_KEY to action.key))
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "LiteraryMenuDialogFragment"
        const val REQUEST_KEY = "literaryMenuRequest"
        const val ACTION_KEY = "selectedAction"
    }
}
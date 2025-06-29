package com.lesmangeursdurouleau.app.ui.members.dictionary

import android.os.Build
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.core.text.HtmlCompat // Importation nécessaire pour HtmlCompat
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.databinding.DialogDictionaryBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DictionaryDialogFragment : DialogFragment() {

    private var _binding: DialogDictionaryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DictionaryViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogDictionaryBinding.inflate(inflater, container, false)
        // Permet au dialogue d'avoir un titre personnalisé
        dialog?.setTitle(getString(R.string.dictionary_title))
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        observeUiState()
    }

    private fun setupListeners() {
        binding.btnSearch.setOnClickListener {
            val word = binding.etWordInput.text.toString().trim()
            viewModel.searchWord(word)
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // Gestion de la visibilité des éléments de l'interface
                    binding.progressBar.isVisible = state.isLoading
                    binding.tvResult.isVisible = !state.isLoading // Le TextView est visible si pas en chargement

                    // Mise à jour du contenu affiché dans le TextView
                    when {
                        state.definition != null -> {
                            val word = state.definition.word
                            val phonetic = state.definition.phonetic
                            val rawMeaning = state.definition.meaning

                            // Construction de la chaîne HTML pour le TextView
                            val htmlContent = buildString {
                                // 1. Affichage du mot recherché (titre principal, couleur bleu profond)
                                append("<h2 style=\"font-size: 1.8em; color: #0D47A1; font-weight: bold; margin-bottom: 5px;\">${Html.escapeHtml(word)}</h2>") // Dark Blue

                                // 2. Affichage de la phonétique (italique, couleur gris tamisé)
                                if (phonetic.isNotBlank()) {
                                    append("<p style=\"font-size: 1.1em; color: #616161; margin-top: 0px; margin-bottom: 15px;\"><i>${Html.escapeHtml(phonetic)}</i></p>") // Grey
                                }

                                // 3. Ligne de séparation visuelle pour délimiter l'en-tête de la définition
                                append("<hr style=\"border: 0; height: 1px; background-color: #E0E0E0; margin: 20px 0;\"/>") // Gris clair

                                // 4. Traitement du contenu principal de la définition
                                val parts = rawMeaning.split("\n\n") // Divise le texte en blocs par double saut de ligne

                                var primaryDefinitionFound = false // Flag pour marquer si la section de définition principale a été affichée

                                parts.forEach { part ->
                                    val trimmedPart = part.trim()
                                    if (trimmedPart.isBlank()) return@forEach // Ignorer les parties vides

                                    when {
                                        // A. Détection et affichage de la section "Étymologie"
                                        trimmedPart.startsWith("Étymologie:", ignoreCase = true) -> {
                                            append("<p style=\"font-size: 1.2em; font-weight: bold; color: #880E4F; margin-top: 15px; margin-bottom: 5px;\">Étymologie :</p>") // Titre rose foncé
                                            val etymologyContent = Html.escapeHtml(trimmedPart.substringAfter("Étymologie:").trim().replace("\n", "<br/>"))
                                            append("<p style=\"font-size: 1.0em; line-height: 1.5; color: #424242; margin-left: 10px; margin-bottom: 10px;\">$etymologyContent</p>") // Contenu de l'étymologie
                                        }
                                        // B. Détection et affichage de la section "Exemples"
                                        trimmedPart.startsWith("Exemple:", ignoreCase = true) || trimmedPart.startsWith("Exemples:", ignoreCase = true) -> {
                                            append("<p style=\"font-size: 1.2em; font-weight: bold; color: #33691E; margin-top: 15px; margin-bottom: 5px;\">Exemples :</p>") // Titre vert foncé
                                            val examplesRaw = trimmedPart.substringAfter("Exemple:").substringAfter("Exemples:").trim()
                                            val exampleLines = examplesRaw.split("\n") // Divise les exemples en lignes individuelles

                                            if (exampleLines.any { it.isNotBlank() }) {
                                                append("<ul style=\"margin-left: 10px; margin-bottom: 10px;\">") // Liste non ordonnée pour les exemples
                                                exampleLines.forEach { exLine ->
                                                    val cleanExLine = exLine.trim()
                                                    if (cleanExLine.isNotBlank()) {
                                                        // Chaque exemple est un élément de liste
                                                        append("<li><p style=\"font-size: 1.0em; line-height: 1.5; color: #424242;\">${Html.escapeHtml(cleanExLine)}</p></li>")
                                                    }
                                                }
                                                append("</ul>")
                                            } else {
                                                append("<p style=\"font-size: 1.0em; line-height: 1.5; color: #757575; margin-left: 10px; margin-bottom: 10px;\">Aucun exemple fourni.</p>") // Message si pas d'exemples
                                            }
                                        }
                                        // C. Gestion de la Définition Principale et des paragraphes généraux
                                        !primaryDefinitionFound -> {
                                            // Affiche le titre "Définition :" seulement pour le premier bloc qui n'est pas une section spécifique
                                            append("<p style=\"font-size: 1.2em; font-weight: bold; color: #2E7D32; margin-top: 15px; margin-bottom: 5px;\">Définition :</p>") // Titre vert pour la définition principale
                                            val formattedParagraph = Html.escapeHtml(trimmedPart).replace("\n", "<br/>") // Les sauts de ligne internes deviennent <br/>
                                            append("<p style=\"font-size: 1.05em; line-height: 1.6; color: #212121; margin-bottom: 10px;\">$formattedParagraph</p>") // Contenu de la définition
                                            primaryDefinitionFound = true // Marque que la définition principale a été traitée
                                        }
                                        else -> {
                                            // Tout autre paragraphe général qui suit (définitions additionnelles, texte non catégorisé)
                                            val formattedParagraph = Html.escapeHtml(trimmedPart).replace("\n", "<br/>")
                                            append("<p style=\"font-size: 1.05em; line-height: 1.6; color: #212121; margin-top: 10px; margin-bottom: 10px;\">$formattedParagraph</p>")
                                        }
                                    }
                                }

                                // Message de fallback si aucune définition n'est trouvée
                                if (rawMeaning.isBlank() || parts.all { it.isBlank() }) {
                                    append("<p style=\"font-size: 1.05em; color: #757575;\">Aucune définition détaillée n'est disponible pour ce mot.</p>")
                                }
                            }

                            // Affectation de la chaîne HTML formatée au TextView
                            // HtmlCompat est utilisé pour une compatibilité robuste avec les anciennes versions d'Android.
                            binding.tvResult.text = HtmlCompat.fromHtml(htmlContent, HtmlCompat.FROM_HTML_MODE_LEGACY)
                            binding.tvResult.visibility = View.VISIBLE
                        }
                        state.errorMessage != null -> {
                            // Affichage du message d'erreur en cas de problème
                            binding.tvResult.text = state.errorMessage
                            binding.tvResult.visibility = View.VISIBLE
                        }
                        else -> {
                            // État initial ou si aucune définition/erreur, le TextView de résultat reste masqué
                            binding.tvResult.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Ajuste la largeur du dialogue pour qu'il soit plus lisible, prenant toute la largeur de l'écran
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "DictionaryDialogFragment"

        fun newInstance(): DictionaryDialogFragment {
            return DictionaryDialogFragment()
        }
    }
}

// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier ManageBookFragment.kt
package com.lesmangeursdurouleau.app.ui.admin

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.databinding.FragmentManageBookBinding
import com.lesmangeursdurouleau.app.ui.admin.ManageBookViewModel
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.IOException

@AndroidEntryPoint
class ManageBookFragment : Fragment() {

    private var _binding: FragmentManageBookBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ManageBookViewModel by viewModels()

    private var selectedCoverImage: ByteArray? = null
    private var selectedPdfUri: Uri? = null

    private val pickCoverImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { imageUri ->
            binding.ivCoverPreview.setImageURI(imageUri)
            try {
                context?.contentResolver?.openInputStream(imageUri)?.use { inputStream ->
                    selectedCoverImage = inputStream.readBytes()
                }
            } catch (e: IOException) {
                Toast.makeText(context, "Erreur de lecture de l'image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val pickPdfLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { pdfUri ->
            selectedPdfUri = pdfUri
            context?.contentResolver?.query(pdfUri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    binding.tvPdfName.text = cursor.getString(nameIndex)
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentManageBookBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupListeners()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
    }

    private fun setupListeners() {
        binding.btnUploadCover.setOnClickListener { pickCoverImageLauncher.launch("image/*") }
        binding.btnUploadPdf.setOnClickListener { pickPdfLauncher.launch("application/pdf") }
        binding.btnSaveBook.setOnClickListener {
            val title = binding.etBookTitle.text.toString().trim()
            val author = binding.etBookAuthor.text.toString().trim()
            val totalPages = binding.etTotalPages.text.toString().toIntOrNull() ?: 0
            val synopsis = binding.etBookSynopsis.text.toString()

            viewModel.createBook(title, author, synopsis, totalPages, selectedCoverImage, selectedPdfUri)
        }
    }

    // === DÉBUT DE LA MODIFICATION ===
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                // Lancement d'une coroutine pour gérer l'état de l'interface (chargement)
                launch {
                    viewModel.isLoading.collect { isLoading ->
                        setLoading(isLoading)
                    }
                }

                // Lancement d'une coroutine parallèle pour gérer les événements
                launch {
                    viewModel.eventFlow.collect { result ->
                        when(result) {
                            is Resource.Success -> {
                                val newBookId = result.data
                                if (newBookId != null) {
                                    Toast.makeText(context, "Livre créé avec succès !", Toast.LENGTH_SHORT).show()
                                    val action = ManageBookFragmentDirections
                                        .actionManageBookFragmentToAddEditMonthlyReadingFragment(
                                            monthlyReadingId = null,
                                            bookId = newBookId,
                                            title = "Planifier : ${binding.etBookTitle.text}"
                                        )
                                    findNavController().navigate(action)
                                } else {
                                    // Cas de secours, ne devrait pas arriver mais assure une navigation
                                    Toast.makeText(context, "Erreur : ID du livre manquant.", Toast.LENGTH_LONG).show()
                                    findNavController().navigateUp()
                                }
                            }
                            is Resource.Error -> {
                                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                            }
                            is Resource.Loading -> {
                                // L'état de chargement est déjà géré par l'autre collecteur, rien à faire ici.
                            }
                        }
                    }
                }
            }
        }
    }
    // === FIN DE LA MODIFICATION ===

    private fun setLoading(isLoading: Boolean) {
        binding.progressBarSave.isVisible = isLoading
        binding.btnSaveBook.isVisible = !isLoading
        binding.tilBookTitle.isEnabled = !isLoading
        binding.tilBookAuthor.isEnabled = !isLoading
        binding.tilTotalPages.isEnabled = !isLoading
        binding.tilBookSynopsis.isEnabled = !isLoading
        binding.btnUploadCover.isEnabled = !isLoading
        binding.btnUploadPdf.isEnabled = !isLoading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
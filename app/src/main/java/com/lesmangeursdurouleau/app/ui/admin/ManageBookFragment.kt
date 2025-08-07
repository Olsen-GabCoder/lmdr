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
        uri?.let {
            binding.ivCoverPreview.setImageURI(it)
            try {
                selectedCoverImage = requireContext().contentResolver.openInputStream(it)?.readBytes()
            } catch (e: IOException) {
                Toast.makeText(context, "Erreur de lecture de l'image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val pickPdfLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedPdfUri = it
            val cursor = requireContext().contentResolver.query(it, null, null, null, null)
            cursor?.use { c ->
                val nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    c.moveToFirst()
                    binding.tvPdfName.text = c.getString(nameIndex)
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
            val title = binding.etBookTitle.text.toString()
            val author = binding.etBookAuthor.text.toString()
            val totalPages = binding.etTotalPages.text.toString().toIntOrNull() ?: 0
            val synopsis = binding.etBookSynopsis.text.toString()

            viewModel.createBook(title, author, synopsis, totalPages, selectedCoverImage, selectedPdfUri)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    setLoading(state.saveResult is Resource.Loading)

                    state.saveResult?.let { result ->
                        when(result) {
                            is Resource.Success -> {
                                Toast.makeText(context, "Livre créé avec succès !", Toast.LENGTH_SHORT).show()
                                val newBookId = result.data
                                if (newBookId != null) {
                                    // JUSTIFICATION DE LA MODIFICATION : C'est le cœur de la nouvelle logique.
                                    // Au lieu de simplement revenir en arrière, nous naviguons vers le formulaire de
                                    // planification en lui passant l'ID du livre que nous venons de créer.
                                    val action = ManageBookFragmentDirections
                                        .actionManageBookFragmentToAddEditMonthlyReadingFragment(
                                            monthlyReadingId = null, // C'est une NOUVELLE lecture
                                            bookId = newBookId,
                                            title = "Planifier : ${binding.etBookTitle.text}"
                                        )
                                    findNavController().navigate(action)
                                } else {
                                    // Cas de secours, ne devrait pas arriver
                                    findNavController().navigateUp()
                                }
                                viewModel.consumeSaveResult()
                            }
                            is Resource.Error -> {
                                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                                viewModel.consumeSaveResult()
                            }
                            is Resource.Loading -> { /* Géré par setLoading */ }
                        }
                    }
                }
            }
        }
    }

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
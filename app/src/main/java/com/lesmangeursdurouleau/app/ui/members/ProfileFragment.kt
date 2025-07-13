// PRÊT À COLLER - Fichier ProfileFragment.kt mis à jour par ajout, sans régression.
package com.lesmangeursdurouleau.app.ui.members

import android.app.Activity // IMPORT AJOUTÉ
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher // IMPORT AJOUTÉ
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.snackbar.Snackbar
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.databinding.FragmentProfileBinding
import com.lesmangeursdurouleau.app.ui.auth.AuthActivity
import com.lesmangeursdurouleau.app.ui.auth.AuthViewModel
import com.lesmangeursdurouleau.app.ui.cropper.CropperActivity // IMPORT AJOUTÉ
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val profileViewModel: ProfileViewModel by viewModels()
    private val authViewModel: AuthViewModel by activityViewModels()

    companion object {
        private const val TAG = "ProfileFragment"
        // CONSTANTES AJOUTÉES
        private const val CROP_TYPE_PROFILE = "CROP_TYPE_PROFILE"
        private const val CROP_TYPE_COVER = "CROP_TYPE_COVER"
    }

    // VARIABLE AJOUTÉE
    private var currentCropType: String? = null

    // MODIFICATION : Le launcher existant est modifié pour lancer l'activité de recadrage au lieu de l'upload.
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { sourceUri ->
            when (currentCropType) {
                CROP_TYPE_PROFILE -> startCropActivity(sourceUri, isCircle = true, aspectRatioX = 1f, aspectRatioY = 1f)
                CROP_TYPE_COVER -> startCropActivity(sourceUri, isCircle = false, aspectRatioX = 16f, aspectRatioY = 9f)
            }
        }
    }

    // AJOUT : Nouveau launcher pour gérer le résultat de CropperActivity.
    private val cropResultLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val croppedUri = result.data?.data
                if (croppedUri != null) {
                    Log.d(TAG, "Image recadrée reçue: $croppedUri")
                    when (currentCropType) {
                        CROP_TYPE_PROFILE -> authViewModel.updateProfilePicture(croppedUri)
                        CROP_TYPE_COVER -> authViewModel.updateCoverPicture(croppedUri)
                    }
                } else {
                    Snackbar.make(binding.root, getString(R.string.error_cropping_image), Snackbar.LENGTH_SHORT).show()
                }
            } else {
                Log.d(TAG, "Le recadrage a été annulé ou a échoué.")
            }
        }

    // Le launcher `pickCoverImageLauncher` est maintenant redondant et est supprimé,
    // car `pickImageLauncher` gère les deux cas. Le code ci-dessous est votre code original inchangé.

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupStateAndEventCollectors()
        setupClickListeners()
    }

    // AJOUT : Nouvelle fonction helper pour lancer CropperActivity.
    private fun startCropActivity(sourceUri: Uri, isCircle: Boolean, aspectRatioX: Float, aspectRatioY: Float) {
        val intent = Intent(requireContext(), CropperActivity::class.java).apply {
            putExtra(CropperActivity.EXTRA_INPUT_URI, sourceUri.toString())
            putExtra(CropperActivity.EXTRA_CROP_SHAPE, if (isCircle) CropperActivity.SHAPE_CIRCLE else CropperActivity.SHAPE_RECTANGLE)
            putExtra(CropperActivity.EXTRA_ASPECT_RATIO_X, aspectRatioX)
            putExtra(CropperActivity.EXTRA_ASPECT_RATIO_Y, aspectRatioY)
        }
        cropResultLauncher.launch(intent)
    }

    private fun setupStateAndEventCollectors() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    profileViewModel.uiState.collectLatest { state ->
                        Log.d(TAG, "Nouvel état UI du profil reçu: $state")
                        updateUi(state)
                    }
                }

                launch {
                    profileViewModel.eventFlow.collectLatest { event ->
                        Log.d(TAG, "Nouvel événement de profil reçu: $event")
                        handleEvent(event)
                    }
                }

                launch {
                    authViewModel.profilePictureUpdateResult.collectLatest { result ->
                        binding.fabSelectPicture.isEnabled = result !is Resource.Loading<*>
                        when (result) {
                            is Resource.Success<*> -> {
                                Snackbar.make(binding.root, getString(R.string.profile_picture_updated_success), Snackbar.LENGTH_SHORT).show()
                            }
                            is Resource.Error<*> -> {
                                Snackbar.make(binding.root, result.message ?: getString(R.string.error_updating_profile_picture), Snackbar.LENGTH_LONG).show()
                            }
                            is Resource.Loading<*>, null -> { /* No-op */ }
                        }
                    }
                }

                launch {
                    authViewModel.coverPictureUpdateResult.collectLatest { result ->
                        binding.fabEditCover.isEnabled = result !is Resource.Loading<*>
                        when(result) {
                            is Resource.Success<*> -> {
                                Snackbar.make(binding.root, getString(R.string.cover_photo_updated_success), Snackbar.LENGTH_SHORT).show()
                            }
                            is Resource.Error<*> -> {
                                Snackbar.make(binding.root, result.message ?: getString(R.string.error_updating_cover_photo), Snackbar.LENGTH_LONG).show()
                            }
                            is Resource.Loading<*>, null -> { /* No-op */ }
                        }
                    }
                }
            }
        }
    }

    private fun updateUi(state: ProfileUiState) {
        binding.buttonSaveProfile.isEnabled = !state.isSaving
        if (state.isSaving) {
            binding.fabSelectPicture.isEnabled = false
            binding.fabEditCover.isEnabled = false
        }

        state.screenError?.let {
            Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
        }

        if (state.user != null) {
            val user = state.user
            binding.tvProfileEmail.text = user.email ?: getString(R.string.email_not_available)
            if (!binding.etProfileUsername.isFocused) {
                binding.etProfileUsername.setText(user.username ?: getString(R.string.username_not_defined))
            }
            if (!binding.etProfileBio.isFocused) {
                binding.etProfileBio.setText(user.bio ?: "")
            }
            if (!binding.etProfileCity.isFocused) {
                binding.etProfileCity.setText(user.city ?: "")
            }

            Glide.with(this)
                .load(user.profilePictureUrl)
                .placeholder(R.drawable.ic_profile_placeholder)
                .error(R.drawable.ic_profile_placeholder)
                .circleCrop()
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(binding.ivProfilePicture)

            Glide.with(this)
                .load(user.coverPictureUrl)
                .placeholder(R.drawable.profile_header_gradient)
                .error(R.drawable.profile_header_gradient)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(binding.ivCoverPhoto)

        } else {
            if (!state.isLoading) {
                binding.tvProfileEmail.text = getString(R.string.email_not_available)
                binding.etProfileUsername.setText(getString(R.string.username_not_defined))
                binding.etProfileBio.setText("")
                binding.etProfileCity.setText("")
                binding.ivProfilePicture.setImageResource(R.drawable.ic_profile_placeholder)
                binding.ivCoverPhoto.setImageResource(R.drawable.profile_header_gradient)
            }
        }

        updateCurrentReadingUi(state.currentReading)
    }

    private fun handleEvent(event: ProfileEvent) {
        when (event) {
            is ProfileEvent.ShowSnackbar -> {
                Snackbar.make(binding.root, event.message, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateCurrentReadingUi(uiState: PrivateCurrentReadingUiState) {
        binding.cardPrivateCurrentReading.visibility = View.VISIBLE
        binding.btnManageCurrentReading.visibility = View.VISIBLE
        binding.btnManageCurrentReading.isEnabled = !uiState.isLoading

        when {
            uiState.isLoading -> {
                binding.tvPrivateCurrentReadingBookTitle.text = ""
                binding.tvPrivateCurrentReadingBookAuthor.text = ""
                binding.ivPrivateCurrentReadingBookCover.setImageResource(android.R.color.transparent)
                binding.tvPrivateCurrentReadingProgressText.visibility = View.GONE
                binding.progressBarPrivateCurrentReading.visibility = View.GONE
                binding.llPrivatePersonalReflectionSection.visibility = View.GONE
                binding.btnManageCurrentReading.setText(R.string.manage_reading_button)
            }
            uiState.error != null -> {
                binding.tvPrivateCurrentReadingBookTitle.text = getString(R.string.error_loading_data)
                binding.tvPrivateCurrentReadingBookAuthor.text = uiState.error
                binding.ivPrivateCurrentReadingBookCover.setImageResource(R.drawable.ic_error)
            }
            uiState.bookReading == null || uiState.bookDetails == null -> {
                binding.tvPrivateCurrentReadingBookTitle.text = getString(R.string.no_current_reading_title)
                binding.tvPrivateCurrentReadingBookAuthor.text = getString(R.string.tap_to_add_reading)
                binding.ivPrivateCurrentReadingBookCover.setImageResource(R.drawable.ic_add_book_placeholder)
                binding.tvPrivateCurrentReadingProgressText.visibility = View.GONE
                binding.progressBarPrivateCurrentReading.visibility = View.GONE
                binding.llPrivatePersonalReflectionSection.visibility = View.GONE
                binding.btnManageCurrentReading.setText(R.string.add_reading_button)
            }
            else -> {
                binding.tvPrivateCurrentReadingProgressText.visibility = View.VISIBLE
                binding.progressBarPrivateCurrentReading.visibility = View.VISIBLE
                binding.btnManageCurrentReading.setText(R.string.manage_reading_button)

                val reading = uiState.bookReading
                val book = uiState.bookDetails

                Glide.with(this@ProfileFragment)
                    .load(book.coverImageUrl)
                    .placeholder(R.drawable.ic_book_placeholder)
                    .error(R.drawable.ic_book_placeholder)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(binding.ivPrivateCurrentReadingBookCover)

                binding.tvPrivateCurrentReadingBookTitle.text = book.title
                binding.tvPrivateCurrentReadingBookAuthor.text = book.author

                if (reading.totalPages > 0) {
                    binding.tvPrivateCurrentReadingProgressText.text = getString(R.string.page_progress_format, reading.currentPage, reading.totalPages)
                    binding.progressBarPrivateCurrentReading.progress = (reading.currentPage.toFloat() / reading.totalPages.toFloat() * 100).toInt()
                } else {
                    binding.tvPrivateCurrentReadingProgressText.text = getString(R.string.page_progress_unknown)
                    binding.progressBarPrivateCurrentReading.progress = 0
                }

                val personalNote = reading.favoriteQuote?.takeIf { it.isNotBlank() } ?: reading.personalReflection?.takeIf { it.isNotBlank() }
                if (!personalNote.isNullOrBlank()) {
                    binding.llPrivatePersonalReflectionSection.visibility = View.VISIBLE
                    binding.tvPrivateCurrentReadingPersonalNote.text = personalNote
                } else {
                    binding.llPrivatePersonalReflectionSection.visibility = View.GONE
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.buttonLogout.setOnClickListener {
            authViewModel.logoutUser()
            navigateToAuthActivity()
        }

        // MODIFICATION : Le listener définit maintenant le type de recadrage et lance le launcher générique.
        binding.fabSelectPicture.setOnClickListener {
            currentCropType = CROP_TYPE_PROFILE
            pickImageLauncher.launch("image/*")
        }

        // MODIFICATION : Le listener définit maintenant le type de recadrage et lance le launcher générique.
        binding.fabEditCover.setOnClickListener {
            currentCropType = CROP_TYPE_COVER
            pickImageLauncher.launch("image/*")
        }

        binding.buttonSaveProfile.setOnClickListener {
            val newUsername = binding.etProfileUsername.text.toString().trim()
            val newBio = binding.etProfileBio.text.toString().trim()
            val newCity = binding.etProfileCity.text.toString().trim()

            Log.d(TAG, "Clic sur Enregistrer. Transmission des données au ViewModel.")
            profileViewModel.updateProfile(newUsername, newBio, newCity)
        }

        binding.buttonViewMembers.setOnClickListener {
            val action = ProfileFragmentDirections.actionProfileFragmentToMembersFragment()
            findNavController().navigate(action)
        }

        binding.buttonNotifications.setOnClickListener {
            val action = ProfileFragmentDirections.actionNavigationMembersProfileToNotificationsDestination()
            findNavController().navigate(action)
        }

        binding.buttonPrivateMessages.setOnClickListener {
            val action = ProfileFragmentDirections.actionNavigationMembersProfileToConversationsListFragmentDestination()
            findNavController().navigate(action)
        }

        binding.btnManageCurrentReading.setOnClickListener {
            Log.d(TAG, "Bouton 'Gérer la lecture' cliqué. Navigation vers l'écran d'édition.")
            val action = ProfileFragmentDirections.actionNavigationMembersProfileToEditCurrentReadingFragment()
            findNavController().navigate(action)
        }
    }

    private fun navigateToAuthActivity() {
        val intent = Intent(requireActivity(), AuthActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        requireActivity().finishAffinity()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        Log.d(TAG, "onDestroyView: Binding nulifié.")
    }
}
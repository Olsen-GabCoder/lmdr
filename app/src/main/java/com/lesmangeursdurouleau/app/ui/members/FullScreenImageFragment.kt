package com.lesmangeursdurouleau.app.ui.members

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.lesmangeursdurouleau.app.databinding.FragmentFullScreenImageBinding

class FullScreenImageFragment : Fragment() {

    private var _binding: FragmentFullScreenImageBinding? = null
    private val binding get() = _binding!!

    private val args: FullScreenImageFragmentArgs by navArgs()

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                downloadImage(args.imageUrl)
            } else {
                Toast.makeText(requireContext(), "Permission refusée. Le téléchargement est impossible.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFullScreenImageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.progressBar.isVisible = true
        Glide.with(this)
            .load(args.imageUrl)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    binding.progressBar.isVisible = false
                    Toast.makeText(context, "Erreur de chargement de l'image.", Toast.LENGTH_SHORT).show()
                    return false
                }

                // CORRIGÉ : La signature de onResourceReady a été ajustée.
                // Le paramètre 'resource' est maintenant non-nullable (Drawable au lieu de Drawable?).
                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    binding.progressBar.isVisible = false
                    return false
                }
            })
            .into(binding.ivFullScreenImage)

        binding.btnClose.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnDownload.setOnClickListener {
            startDownload(args.imageUrl)
        }

        binding.ivFullScreenImage.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.root.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun startDownload(imageUrl: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            downloadImage(imageUrl)
        } else {
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED -> {
                    downloadImage(imageUrl)
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }
    }

    private fun downloadImage(imageUrl: String) {
        try {
            val downloadManager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val imageUri = Uri.parse(imageUrl)
            val fileName = "LMR_${System.currentTimeMillis()}.jpg"
            val request = DownloadManager.Request(imageUri).apply {
                setTitle("Image de 'Les Mangeurs du Rouleau'")
                setDescription("Téléchargement en cours...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                // SUPPRIMÉ : L'appel à allowScanningByMediaScanner() est déprécié et inutile
                // pour les fichiers sauvegardés dans les répertoires publics.
            }
            downloadManager.enqueue(request)
            Toast.makeText(requireContext(), "Le téléchargement a commencé.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Erreur lors du lancement du téléchargement.", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
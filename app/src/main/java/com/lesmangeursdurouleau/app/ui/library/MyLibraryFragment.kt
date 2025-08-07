// PRÊT À COLLER - Créez un nouveau fichier MyLibraryFragment.kt
package com.lesmangeursdurouleau.app.ui.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.lesmangeursdurouleau.app.databinding.FragmentMyLibraryBinding
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MyLibraryFragment : Fragment() {

    private var _binding: FragmentMyLibraryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MyLibraryViewModel by viewModels()
    private lateinit var libraryAdapter: MyLibraryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeLibraryState()
    }

    private fun setupRecyclerView() {
        libraryAdapter = MyLibraryAdapter(
            onBookClicked = { item ->
                Toast.makeText(context, "Clic sur : ${item.book?.title}", Toast.LENGTH_SHORT).show()
                // TODO: Naviguer vers l'écran de détail/modification de la progression
            },
            onFavoriteClicked = { item ->
                Toast.makeText(context, "Favori : ${item.book?.title}", Toast.LENGTH_SHORT).show()
                // TODO: Appeler le ViewModel pour gérer le changement de favori
            },
            onMarkAsReadClicked = { item ->
                Toast.makeText(context, "Marquer comme lu : ${item.book?.title}", Toast.LENGTH_SHORT).show()
                // TODO: Appeler le ViewModel pour changer le statut
            }
        )

        binding.rvLibraryBooks.apply {
            adapter = libraryAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    private fun observeLibraryState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.libraryState.collect { resource ->
                    binding.progressBar.isVisible = resource is Resource.Loading
                    binding.tvError.isVisible = resource is Resource.Error
                    binding.rvLibraryBooks.isVisible = resource is Resource.Success

                    when (resource) {
                        is Resource.Success -> {
                            val items = resource.data ?: emptyList()
                            libraryAdapter.submitList(items)
                            binding.tvEmptyState.isVisible = items.isEmpty()
                        }
                        is Resource.Error -> {
                            binding.tvError.text = resource.message
                            binding.tvEmptyState.isVisible = false
                        }
                        is Resource.Loading -> {
                            binding.tvEmptyState.isVisible = false
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvLibraryBooks.adapter = null // Éviter les fuites de mémoire
        _binding = null
    }
}
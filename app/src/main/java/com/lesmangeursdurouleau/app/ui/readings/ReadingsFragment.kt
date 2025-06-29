// app/src/main/java/com/lesmangeursdurouleau/app/ui/readings/ReadingsFragment.kt
package com.lesmangeursdurouleau.app.ui.readings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.databinding.FragmentReadingsBinding
import com.lesmangeursdurouleau.app.ui.readings.adapter.MonthlyReadingListAdapter
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@AndroidEntryPoint
class ReadingsFragment : Fragment() {

    private var _binding: FragmentReadingsBinding? = null
    private val binding get() = _binding!!

    private val readingsViewModel: ReadingsViewModel by viewModels()
    private lateinit var monthlyReadingListAdapter: MonthlyReadingListAdapter

    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            monthlyReadingListAdapter.submitList(monthlyReadingListAdapter.currentList)
            Log.d("ReadingsFragment", "Refreshing monthly readings list for status updates.")
            handler.postDelayed(this, REFRESH_INTERVAL_MILLIS)
        }
    }

    private val REFRESH_INTERVAL_MILLIS = 60 * 60 * 1000L

    private var pendingMonthlyReadingId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReadingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupClickListeners()
        setupObservers()
        setupSecretCodeResultListener()
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }

    private fun setupRecyclerView() {
        monthlyReadingListAdapter = MonthlyReadingListAdapter(
            onEditClicked = { selectedMonthlyReadingWithBook ->
                checkEditPermissionAndNavigate(selectedMonthlyReadingWithBook.monthlyReading.id)
            },
            onLikeClicked = { selectedMonthlyReadingWithBook ->
                Log.d("ReadingsFragment", "Like clicked for book: ${selectedMonthlyReadingWithBook.book?.title}")
                Toast.makeText(requireContext(), "Fonctionnalité 'J'aime' à venir", Toast.LENGTH_SHORT).show()
            },
            onCommentClicked = { selectedMonthlyReadingWithBook ->
                Log.d("ReadingsFragment", "Comment clicked for book: ${selectedMonthlyReadingWithBook.book?.title}")
                Toast.makeText(requireContext(), "Fonctionnalité 'Commenter' à venir", Toast.LENGTH_SHORT).show()
            },
            onJoinClicked = { meetingLink ->
                Log.d("ReadingsFragment", "Join clicked with link: $meetingLink")
                openMeetingLink(meetingLink)
            },
            // --- IMPLÉMENTATION DE LA NOUVELLE LOGIQUE DE CLIC ---
            onBookCoverClicked = { bookId, bookTitle ->
                Log.d("ReadingsFragment", "Book cover clicked. Navigating to detail for bookId: $bookId")
                val action = ReadingsFragmentDirections.actionReadingsFragmentToBookDetailFragment(
                    bookId = bookId,
                    bookTitle = bookTitle ?: getString(R.string.book_details_default_title)
                )
                findNavController().navigate(action)
            }
        )

        binding.recyclerViewMonthlyReadings.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = monthlyReadingListAdapter
        }
    }

    private fun openMeetingLink(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("ReadingsFragment", "Could not open meeting link: $url", e)
            Toast.makeText(requireContext(), "Impossible d'ouvrir le lien de la réunion.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkEditPermissionAndNavigate(monthlyReadingId: String?) {
        val hasActiveEditPermission = readingsViewModel.canEditReadings.value
        if (hasActiveEditPermission) {
            Log.d("ReadingsFragment", "User has active edit permission, navigating to AddEditMonthlyReading for ID: $monthlyReadingId")
            navigateToAddEditMonthlyReading(monthlyReadingId)
        } else {
            Log.d("ReadingsFragment", "User does NOT have active edit permission, showing secret code dialog for ID: $monthlyReadingId")
            pendingMonthlyReadingId = monthlyReadingId
            if (childFragmentManager.findFragmentByTag("SecretCodeDialog") == null) {
                EnterSecretCodeDialogFragment().show(childFragmentManager, "SecretCodeDialog")
            }
        }
    }

    private fun navigateToAddEditMonthlyReading(monthlyReadingId: String? = null) {
        val action = ReadingsFragmentDirections.actionReadingsFragmentToAddEditMonthlyReadingFragment(monthlyReadingId)
        findNavController().navigate(action)
        pendingMonthlyReadingId = null
    }

    private fun setupSecretCodeResultListener() {
        setFragmentResultListener(EnterSecretCodeDialogFragment.REQUEST_KEY) { _, bundle ->
            val permissionGranted = bundle.getBoolean(EnterSecretCodeDialogFragment.BUNDLE_KEY_PERMISSION_GRANTED, false)
            if (permissionGranted) {
                Log.d("ReadingsFragment", "Permission granted from dialog. Pending ID: $pendingMonthlyReadingId")
                pendingMonthlyReadingId?.let { id ->
                    navigateToAddEditMonthlyReading(id)
                } ?: run {
                    Log.d("ReadingsFragment", "No pending action, permission re-validated proactively.")
                    readingsViewModel.forcePermissionCheck()
                }
            } else {
                Log.d("ReadingsFragment", "Permission NOT granted or dialog cancelled.")
                pendingMonthlyReadingId = null
            }
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    readingsViewModel.monthlyReadingsWithBooks.collect { resource ->
                        when (resource) {
                            is Resource.Loading -> {
                                binding.progressBarReadings.visibility = View.VISIBLE
                                binding.recyclerViewMonthlyReadings.visibility = View.GONE
                                binding.tvErrorReadings.visibility = View.GONE
                            }
                            is Resource.Success -> {
                                binding.progressBarReadings.visibility = View.GONE
                                val data = resource.data ?: emptyList()
                                monthlyReadingListAdapter.submitList(data)
                                updateEmptyStateView(data.isEmpty(), null)
                                Log.d("ReadingsFragment", "Displayed ${data.size} monthly readings.")
                            }
                            is Resource.Error -> {
                                binding.progressBarReadings.visibility = View.GONE
                                monthlyReadingListAdapter.submitList(emptyList())
                                updateEmptyStateView(true, resource.message ?: getString(R.string.error_loading_monthly_readings, "inconnu"))
                                Log.e("ReadingsFragment", "Error loading monthly readings: ${resource.message}")
                            }
                        }
                    }
                }
                launch {
                    readingsViewModel.canEditReadings.collect { canEdit ->
                        Log.d("ReadingsFragment", "User can edit readings (observed): $canEdit")
                    }
                }
                launch {
                    readingsViewModel.requestPermissionRevalidation.collect { shouldRevalidate ->
                        if (shouldRevalidate) {
                            Log.d("ReadingsFragment", "Received request to revalidate permission from ViewModel (proactive).")
                            if (childFragmentManager.findFragmentByTag("SecretCodeDialog") == null) {
                                pendingMonthlyReadingId = null
                                EnterSecretCodeDialogFragment().show(childFragmentManager, "SecretCodeDialog")
                            }
                        }
                    }
                }
                launch {
                    readingsViewModel.currentMonthYear.collect { calendar ->
                        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                        binding.tvCurrentMonthYear.text = monthFormat.format(calendar.time)
                    }
                }
            }
        }
    }

    private fun updateEmptyStateView(isEmpty: Boolean, errorMessage: String?) {
        if (isEmpty) {
            binding.recyclerViewMonthlyReadings.visibility = View.GONE
            binding.tvErrorReadings.visibility = View.VISIBLE
            binding.tvErrorReadings.text = errorMessage ?: getString(R.string.no_monthly_readings_available)
        } else {
            binding.recyclerViewMonthlyReadings.visibility = View.VISIBLE
            binding.tvErrorReadings.visibility = View.GONE
        }
    }

    private fun setupClickListeners() {
        binding.btnPreviousMonth.setOnClickListener {
            readingsViewModel.goToPreviousMonth()
        }
        binding.btnNextMonth.setOnClickListener {
            readingsViewModel.goToNextMonth()
        }
        binding.chipGroupFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            val filter = when (checkedIds.firstOrNull()) {
                R.id.chip_filter_all -> ReadingsFilter.ALL
                R.id.chip_filter_in_progress -> ReadingsFilter.IN_PROGRESS
                R.id.chip_filter_planned -> ReadingsFilter.PLANNED
                R.id.chip_filter_past -> ReadingsFilter.PAST
                else -> ReadingsFilter.ALL
            }
            readingsViewModel.setFilter(filter)
            Log.d("ReadingsFragment", "Filter changed to: $filter")
        }
        binding.fabAddMonthlyReading.setOnClickListener {
            checkEditPermissionAndNavigate(null)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerViewMonthlyReadings.adapter = null
        _binding = null
        handler.removeCallbacks(refreshRunnable)
    }
}
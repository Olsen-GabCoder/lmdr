// PRÊT À COLLER - Remplacez TOUT le contenu de votre fichier AddEditMonthlyReadingFragment.kt
package com.lesmangeursdurouleau.app.ui.readings.addedit

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.lesmangeursdurouleau.app.R
import com.lesmangeursdurouleau.app.data.model.Book
import com.lesmangeursdurouleau.app.data.model.MonthlyReading
import com.lesmangeursdurouleau.app.data.model.PhaseStatus
import com.lesmangeursdurouleau.app.databinding.FragmentAddEditMonthlyReadingBinding
import com.lesmangeursdurouleau.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class AddEditMonthlyReadingFragment : Fragment() {

    private var _binding: FragmentAddEditMonthlyReadingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AddEditMonthlyReadingViewModel by viewModels()
    private val args: AddEditMonthlyReadingFragmentArgs by navArgs()

    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private lateinit var phaseStatusAdapter: ArrayAdapter<String>

    private var analysisDate: Date? = null
    private var debateDate: Date? = null
    private var analysisStatus: PhaseStatus = PhaseStatus.PLANIFIED
    private var debateStatus: PhaseStatus = PhaseStatus.PLANIFIED

    private var currentBook: Book? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddEditMonthlyReadingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbarAddEditMonthlyReading)
        (activity as? AppCompatActivity)?.supportActionBar?.setDisplayHomeAsUpEnabled(true)
        (activity as? AppCompatActivity)?.supportActionBar?.title = args.title

        setupDatePickers()
        setupPhaseStatusSpinners()
        setupListeners()
        setupObservers()

        if (args.monthlyReadingId == null) {
            setupInitialStateForCreation()
        }
    }

    private fun setupDatePickers() {
        binding.etAnalysisDate.setOnClickListener {
            showDatePicker { year, month, dayOfMonth ->
                val calendar = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
                analysisDate = calendar.time
                binding.etAnalysisDate.setText(dateFormatter.format(calendar.time))
            }
        }
        binding.etDebateDate.setOnClickListener {
            showDatePicker { year, month, dayOfMonth ->
                val calendar = Calendar.getInstance().apply { set(year, month, dayOfMonth) }
                debateDate = calendar.time
                binding.etDebateDate.setText(dateFormatter.format(calendar.time))
            }
        }
    }

    private fun setupPhaseStatusSpinners() {
        val statusOptions = arrayOf(getString(R.string.status_planified), getString(R.string.status_in_progress), getString(R.string.status_completed))
        val statusTextToEnumMap = mapOf(
            getString(R.string.status_planified) to PhaseStatus.PLANIFIED,
            getString(R.string.status_in_progress) to PhaseStatus.IN_PROGRESS,
            getString(R.string.status_completed) to PhaseStatus.COMPLETED
        )
        phaseStatusAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, statusOptions)
        binding.actvAnalysisStatus.setAdapter(phaseStatusAdapter)
        binding.actvDebateStatus.setAdapter(phaseStatusAdapter)
        binding.actvAnalysisStatus.setOnItemClickListener { parent, _, position, _ ->
            statusTextToEnumMap[parent.getItemAtPosition(position).toString()]?.let { analysisStatus = it }
        }
        binding.actvDebateStatus.setOnItemClickListener { parent, _, position, _ ->
            statusTextToEnumMap[parent.getItemAtPosition(position).toString()]?.let { debateStatus = it }
        }
    }

    private fun showDatePicker(onDateSelected: (Int, Int, Int) -> Unit) {
        val c = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, year, month, day -> onDateSelected(year, month, day) }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun setupListeners() {
        binding.btnSaveMonthlyReading.setOnClickListener { validateAndSave() }
    }

    private fun validateAndSave() {
        var isValid = true
        if (analysisDate == null) {
            binding.tilAnalysisDate.error = getString(R.string.error_date_required); isValid = false
        }
        if (debateDate == null) {
            binding.tilDebateDate.error = getString(R.string.error_date_required); isValid = false
        }
        if (analysisDate != null && debateDate != null && analysisDate!!.after(debateDate!!)) {
            binding.tilDebateDate.error = getString(R.string.error_invalid_date_order); isValid = false
        }
        if (currentBook == null) {
            Toast.makeText(context, "Erreur : aucun livre n'est associé à cette lecture.", Toast.LENGTH_LONG).show()
            isValid = false
        }

        if (isValid) {
            val calendar = Calendar.getInstance().apply { time = analysisDate!! }

            viewModel.save(
                book = currentBook!!,
                year = calendar.get(Calendar.YEAR),
                month = calendar.get(Calendar.MONTH) + 1,
                analysisDate = analysisDate!!,
                analysisStatus = analysisStatus,
                debateDate = debateDate!!,
                debateStatus = debateStatus,
                customDescription = binding.etCustomDescription.text.toString().trim().ifEmpty { null }
            )
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collectLatest { resource ->
                        setFormEnabled(resource !is Resource.Loading)
                        when (resource) {
                            is Resource.Success -> {
                                val (monthlyReading, book) = resource.data!!
                                currentBook = book
                                if (monthlyReading != null) {
                                    populateForm(monthlyReading)
                                } else if (book != null) {
                                    (activity as? AppCompatActivity)?.supportActionBar?.title = "Planifier : ${book.title}"
                                }
                            }
                            is Resource.Error -> {
                                Toast.makeText(requireContext(), "Erreur: ${resource.message}", Toast.LENGTH_LONG).show()
                                findNavController().popBackStack()
                            }
                            is Resource.Loading -> { /* Géré par setFormEnabled */ }
                        }
                    }
                }

                launch {
                    viewModel.saveResult.collectLatest { resource ->
                        resource?.let {
                            setFormEnabled(it !is Resource.Loading)
                            binding.progressBarAddEditMonthlyReading.isVisible = it is Resource.Loading
                            when (it) {
                                is Resource.Success -> {
                                    Toast.makeText(requireContext(), "Planification enregistrée avec succès !", Toast.LENGTH_SHORT).show()
                                    findNavController().popBackStack(R.id.manageReadingsFragment, false)
                                }
                                is Resource.Error -> {
                                    Toast.makeText(requireContext(), "Erreur : ${it.message}", Toast.LENGTH_LONG).show()
                                }
                                is Resource.Loading -> { /* Géré */ }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupInitialStateForCreation() {
        val currentCalendar = Calendar.getInstance()
        binding.etYear.setText(currentCalendar.get(Calendar.YEAR).toString())
        binding.etMonth.setText(SimpleDateFormat("MMMM", Locale.getDefault()).format(currentCalendar.time))
        binding.actvAnalysisStatus.setText(getString(R.string.status_planified), false)
        binding.actvDebateStatus.setText(getString(R.string.status_planified), false)
    }

    private fun populateForm(monthlyReading: MonthlyReading) {
        (activity as? AppCompatActivity)?.supportActionBar?.title = "Modifier : ${currentBook?.title ?: ""}"

        binding.etYear.setText(monthlyReading.year.toString())
        binding.etMonth.setText(SimpleDateFormat("MMMM", Locale.getDefault()).format(
            Calendar.getInstance().apply { set(Calendar.MONTH, monthlyReading.month - 1) }.time
        ))
        analysisDate = monthlyReading.analysisPhase.date
        analysisDate?.let { binding.etAnalysisDate.setText(dateFormatter.format(it)) }
        analysisStatus = monthlyReading.analysisPhase.status
        binding.actvAnalysisStatus.setText(statusEnumToText(analysisStatus), false)
        debateDate = monthlyReading.debatePhase.date
        debateDate?.let { binding.etDebateDate.setText(dateFormatter.format(it)) }
        debateStatus = monthlyReading.debatePhase.status
        binding.actvDebateStatus.setText(statusEnumToText(debateStatus), false)
        binding.etCustomDescription.setText(monthlyReading.customDescription)
    }

    private fun statusEnumToText(status: PhaseStatus): String {
        return when (status) {
            PhaseStatus.PLANIFIED -> getString(R.string.status_planified)
            PhaseStatus.IN_PROGRESS -> getString(R.string.status_in_progress)
            PhaseStatus.COMPLETED -> getString(R.string.status_completed)
        }
    }

    private fun setFormEnabled(enabled: Boolean) {
        binding.progressBarAddEditMonthlyReading.isVisible = !enabled
        listOf(
            binding.tilAnalysisDate,
            binding.tilAnalysisStatus,
            binding.tilDebateDate,
            binding.tilDebateStatus,
            binding.tilCustomDescription,
            binding.btnSaveMonthlyReading
        ).forEach { it.isEnabled = enabled }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            findNavController().popBackStack()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
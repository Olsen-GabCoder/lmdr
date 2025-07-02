// PRÊT À COLLER - Remplacez tout le contenu de votre fichier AddEditMonthlyReadingFragment.kt par ceci.
package com.lesmangeursdurouleau.app.ui.readings.addedit

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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

    private lateinit var bookDropdownAdapter: ArrayAdapter<String>
    private lateinit var phaseStatusAdapter: ArrayAdapter<String>

    private var selectedBookId: String? = null
    private var analysisDate: Date? = null
    private var debateDate: Date? = null
    private var analysisStatus: PhaseStatus = PhaseStatus.PLANIFIED
    private var debateStatus: PhaseStatus = PhaseStatus.PLANIFIED

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

        setupBookInputFields()
        setupDatePickers()
        setupPhaseStatusSpinners()
        setupListeners()
        setupObservers()

        if (args.monthlyReadingId == null) {
            val currentCalendar = Calendar.getInstance()
            binding.etYear.setText(currentCalendar.get(Calendar.YEAR).toString())
            binding.etMonth.setText(SimpleDateFormat("MMMM", Locale.getDefault()).format(currentCalendar.time))
            setFormEnabled(true)
            binding.progressBarAddEditMonthlyReading.visibility = View.GONE
            binding.actvAnalysisStatus.setText(getString(R.string.status_planified), false)
            binding.actvDebateStatus.setText(getString(R.string.status_planified), false)
        } else {
            (activity as? AppCompatActivity)?.supportActionBar?.title = getString(R.string.edit_monthly_reading_title)
        }
    }

    private fun setupBookInputFields() {
        bookDropdownAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, mutableListOf())
        binding.actvSelectBookAutocomplete.setAdapter(bookDropdownAdapter)
        binding.actvSelectBookAutocomplete.setOnItemClickListener { parent, _, position, _ ->
            val selectedBookTitle = parent.getItemAtPosition(position).toString()
            val selectedBookFromDropdown = (viewModel.allBooks.value as? Resource.Success)?.data?.find { it.title == selectedBookTitle }
            selectedBookFromDropdown?.let { book ->
                selectedBookId = book.id
                binding.etBookTitle.setText(book.title)
                binding.etBookAuthor.setText(book.author)
                binding.etBookSynopsis.setText(book.synopsis)
                binding.etBookCoverUrl.setText(book.coverImageUrl)
            }
        }
        binding.actvSelectBookAutocomplete.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                selectedBookId = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })
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
        val bookTitle = binding.etBookTitle.text.toString().trim()
        val bookAuthor = binding.etBookAuthor.text.toString().trim()
        var isValid = true
        if (bookTitle.isEmpty()) {
            binding.tilBookTitle.error = getString(R.string.error_book_title_required); isValid = false
        }
        if (bookAuthor.isEmpty()) {
            binding.tilBookAuthor.error = getString(R.string.error_book_author_required); isValid = false
        }
        if (analysisDate == null) {
            binding.tilAnalysisDate.error = getString(R.string.error_date_required); isValid = false
        }
        if (debateDate == null) {
            binding.tilDebateDate.error = getString(R.string.error_date_required); isValid = false
        }
        if (analysisDate != null && debateDate != null && analysisDate!!.after(debateDate!!)) {
            binding.tilDebateDate.error = getString(R.string.error_invalid_date_order); isValid = false
        }

        if (isValid) {
            val bookToSave = Book(
                title = bookTitle,
                author = bookAuthor,
                synopsis = binding.etBookSynopsis.text.toString().trim().ifEmpty { null },
                coverImageUrl = binding.etBookCoverUrl.text.toString().trim().ifEmpty { null }
            )
            val calendar = Calendar.getInstance().apply { time = analysisDate!! }

            // CORRIGÉ: L'appel à la méthode `save` est maintenant aligné avec la signature du ViewModel.
            // Le paramètre `book` est correctement nommé.
            viewModel.save(
                bookFromForm = bookToSave,
                year = calendar.get(Calendar.YEAR),
                month = calendar.get(Calendar.MONTH) + 1,
                analysisDate = analysisDate!!,
                analysisStatus = analysisStatus,
                analysisLink = binding.etAnalysisLink.text.toString().trim().ifEmpty { null },
                debateDate = debateDate!!,
                debateStatus = debateStatus,
                debateLink = binding.etDebateLink.text.toString().trim().ifEmpty { null },
                customDescription = binding.etCustomDescription.text.toString().trim().ifEmpty { null },
                existingBookId = selectedBookId
            )
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.allBooks.collectLatest { resource ->
                        if (resource is Resource.Success) {
                            val bookTitles = resource.data?.map { it.title } ?: emptyList()
                            bookDropdownAdapter.clear()
                            bookDropdownAdapter.addAll(bookTitles)
                        }
                    }
                }

                if (args.monthlyReadingId != null) {
                    launch {
                        viewModel.monthlyReadingAndBookForEdit.collectLatest { resource ->
                            when (resource) {
                                is Resource.Loading -> setFormEnabled(false)
                                is Resource.Success -> {
                                    setFormEnabled(true)
                                    resource.data?.let { (monthlyReading, book) ->
                                        if (monthlyReading != null) populateForm(monthlyReading, book)
                                        else findNavController().popBackStack()
                                    }
                                }
                                is Resource.Error -> {
                                    setFormEnabled(true)
                                    Toast.makeText(requireContext(), "Erreur: ${resource.message}", Toast.LENGTH_LONG).show()
                                    findNavController().popBackStack()
                                }
                            }
                        }
                    }
                }

                launch {
                    viewModel.saveResult.collectLatest { resource ->
                        resource?.let {
                            setFormEnabled(it !is Resource.Loading)
                            binding.progressBarAddEditMonthlyReading.visibility = if (it is Resource.Loading) View.VISIBLE else View.GONE
                            when (it) {
                                is Resource.Success -> {
                                    val message = if (args.monthlyReadingId == null) R.string.success_adding_monthly_reading else R.string.success_updating_monthly_reading
                                    Toast.makeText(requireContext(), getString(message), Toast.LENGTH_SHORT).show()
                                    findNavController().popBackStack()
                                }
                                is Resource.Error -> {
                                    val message = if (args.monthlyReadingId == null) R.string.error_adding_monthly_reading else R.string.error_updating_monthly_reading
                                    Toast.makeText(requireContext(), getString(message, it.message ?: "inconnu"), Toast.LENGTH_LONG).show()
                                }
                                is Resource.Loading -> { /* Géré par setFormEnabled */ }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun populateForm(monthlyReading: MonthlyReading, book: Book?) {
        book?.let {
            binding.actvSelectBookAutocomplete.setText(it.title, false)
            binding.etBookTitle.setText(it.title)
            binding.etBookAuthor.setText(it.author)
            binding.etBookSynopsis.setText(it.synopsis)
            binding.etBookCoverUrl.setText(it.coverImageUrl)
            selectedBookId = it.id
        }
        binding.etYear.setText(monthlyReading.year.toString())
        binding.etMonth.setText(SimpleDateFormat("MMMM", Locale.getDefault()).format(
            Calendar.getInstance().apply { set(Calendar.MONTH, monthlyReading.month - 1) }.time
        ))
        analysisDate = monthlyReading.analysisPhase.date
        analysisDate?.let { binding.etAnalysisDate.setText(dateFormatter.format(it)) }
        binding.etAnalysisLink.setText(monthlyReading.analysisPhase.meetingLink)
        analysisStatus = monthlyReading.analysisPhase.status
        binding.actvAnalysisStatus.setText(statusEnumToText(analysisStatus), false)
        debateDate = monthlyReading.debatePhase.date
        debateDate?.let { binding.etDebateDate.setText(dateFormatter.format(it)) }
        binding.etDebateLink.setText(monthlyReading.debatePhase.meetingLink)
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
        listOf(
            binding.tilSelectBookAutocomplete, binding.tilBookTitle, binding.tilBookAuthor,
            binding.tilBookSynopsis, binding.tilBookCoverUrl, binding.tilAnalysisDate,
            binding.tilAnalysisStatus, binding.tilAnalysisLink, binding.tilDebateDate,
            binding.tilDebateStatus, binding.tilDebateLink, binding.tilCustomDescription,
            binding.btnSaveMonthlyReading
        ).forEach { it.isEnabled = enabled }
        binding.progressBarAddEditMonthlyReading.visibility = if(enabled) View.GONE else View.VISIBLE
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
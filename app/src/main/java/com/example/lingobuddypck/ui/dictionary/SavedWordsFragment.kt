package com.example.lingobuddypck.ui.dictionary
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.lingobuddypck.Factory.QuizService.AiQuizService
import com.example.lingobuddypck.Factory.QuizService.QuizViews
import com.example.lingobuddypck.Factory.QuizService.TestUIManager
import com.example.lingobuddypck.NavigationActivity
import com.example.lingobuddypck.Network.RetrofitClient
import com.example.lingobuddypck.Network.TogetherAI.QuestionData
import com.example.lingobuddypck.Network.TogetherAI.UserAnswer
import com.example.lingobuddypck.R
import com.example.lingobuddypck.ViewModel.Repository.FirebaseWordRepository
import com.example.lingobuddypck.ViewModel.Repository.SavedWord
import com.example.lingobuddypck.adapter.SavedWordListItem
import com.example.lingobuddypck.adapter.SavedWordsAdapter
import com.example.lingobuddypck.ui.utils.enableSelectableSaveAction
import com.google.android.material.dialog.MaterialAlertDialogBuilder // Hoặc AlertDialog thông thường
import com.google.gson.Gson
class SavedWordsFragment : Fragment() {

    private val wordRepository = FirebaseWordRepository()
    private val aiQuizService: AiQuizService by lazy {
        AiQuizService(Gson(), RetrofitClient.instance)
    }
    private val viewModel: SavedWordsViewModel by viewModels {
        SavedWordsViewModel.Factory(aiQuizService)
    }
    private lateinit var wordsAdapter: SavedWordsAdapter
    private lateinit var uiManager: TestUIManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_saved_words, container, false)

        // Initialize RecyclerView for saved words
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerViewSavedWords)
        val textViewNoWords = view.findViewById<TextView>(R.id.textViewNoWords)
        setupRecyclerView(recyclerView, textViewNoWords)

        // Initialize QuizViews for quiz functionality
        val quizViews = QuizViews(
            progressBar = view.findViewById(R.id.progressBarSavedWords),
            questionsContainer = view.findViewById(R.id.questionsContainerLayout),
            buttonSubmit = view.findViewById(R.id.buttonSubmitTest),
            buttonStart = view.findViewById(R.id.buttonCreateQuiz),
            textViewResult = view.findViewById(R.id.textViewTestResult),
            scrollView = view.findViewById(R.id.scrollViewTest),
            textViewLoadingHint = view.findViewById(R.id.textViewLoadingHint),
            textViewCountdown = view.findViewById(R.id.textViewCountdown),
            aiAvatar = view.findViewById(R.id.aiAvatarLoading),
            recyclerView = recyclerView
        )

        // Initialize TestUIManager with confirmation dialog and navigation bar callbacks
        uiManager= TestUIManager(
            context = requireContext(),
            lifecycleOwner = viewLifecycleOwner,
            viewModel = viewModel,
            wordRepository = wordRepository,
            views = quizViews,
            onShowConfirmationDialog = { showConfirmationMakeTestDialog() },
            onShowNavigationBar = { (activity as? NavigationActivity)?.showBottomNavigationBar() },
            onHideNavigationBar = { (activity as? NavigationActivity)?.hideBottomNavigationBar() }
        )

        // Observe ViewModel for saved words
        observeViewModel(recyclerView, textViewNoWords)

        return view
    }

    override fun onPause() {
        super.onPause()
        (activity as? NavigationActivity)?.showBottomNavigationBar()
    }

    private fun setupRecyclerView(recyclerView: RecyclerView, textViewNoWords: TextView) {
        wordsAdapter = SavedWordsAdapter(
            onEditClick = { savedWord -> showEditWordDialog(savedWord) },
            onDeleteClick = { savedWord -> showDeleteConfirmationDialog(savedWord) }
        )
        recyclerView.apply {
            adapter = wordsAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun observeViewModel(recyclerView: RecyclerView, textViewNoWords: TextView) {

        viewModel.savedWords.observe(viewLifecycleOwner) { words ->
            if (words.isNullOrEmpty()) {
                textViewNoWords.isVisible = true
                recyclerView.isVisible = false
            } else {
                textViewNoWords.isVisible = false
                recyclerView.isVisible = true
                val grouped = buildGroupedList(words)
                wordsAdapter.submitList(grouped)
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { errorMessage ->
                if (errorMessage != null) {
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show()
                }
            }
        }

        viewModel.operationResult.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { message ->
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun buildGroupedList(words: List<SavedWord>): List<SavedWordListItem> {
        return words
            .sortedBy { it.word.lowercase() }
            .groupBy { it.word.first().uppercaseChar() }
            .flatMap { (char, items) ->
                listOf(SavedWordListItem.Header(char)) + items.map { SavedWordListItem.WordItem(it) }
            }
    }

    private fun showConfirmationMakeTestDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Bắt đầu bài kiểm tra")
            .setMessage("Bạn đã sẵn sàng làm bài kiểm tra chưa?")
            .setPositiveButton("Rồi") { dialog, _ ->
                uiManager.startNewTestFlow()
                dialog.dismiss()
            }
            .setNegativeButton("Chưa") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showDeleteConfirmationDialog(savedWord: SavedWord) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Xóa từ")
            .setMessage("Bạn chắc chắn muốn xóa từ này chứ '${savedWord.word}'?")
            .setNegativeButton("Hủy") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("Xóa") { dialog, _ ->
                viewModel.deleteWord(savedWord.id)
                dialog.dismiss()
            }
            .show()
    }

    private fun showEditWordDialog(savedWord: SavedWord) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_word, null)
        val editTextWord = dialogView.findViewById<EditText>(R.id.editTextDialogWord)
        val editTextNote = dialogView.findViewById<EditText>(R.id.editTextDialogNote)

        editTextWord.setText(savedWord.word)
        editTextNote.setText(savedWord.note)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Word")
            .setView(dialogView)
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("Save") { dialog, _ ->
                val updatedWord = editTextWord.text.toString().trim()
                val updatedNote = editTextNote.text.toString().trim()
                viewModel.updateWord(savedWord.id, updatedWord, updatedNote)
                dialog.dismiss()
            }
            .show()
    }
}
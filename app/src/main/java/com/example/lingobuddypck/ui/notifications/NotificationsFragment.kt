package com.example.lingobuddypck.ui.notifications

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.lingobuddypck.R
import com.example.lingobuddypck.databinding.FragmentNotificationsBinding
import com.example.lingobuddypck.ui.LoginActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth


class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null // Giả sử bạn dùng ViewBinding
    private val binding get() = _binding!!

    private lateinit var viewModel: NotificationsViewModel
    private lateinit var scoreTextView: TextView
    private lateinit var rankTextView: TextView
    private lateinit var buttonEditInfo: Button
    private lateinit var buttonOpenAiToneDialog: Button // Nút bạn đã khai báo
    private lateinit var buttonLogout: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(this).get(NotificationsViewModel::class.java)
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Khởi tạo views từ binding
        scoreTextView = binding.textViewUserScore
        rankTextView = binding.textViewUserRank
        buttonEditInfo = binding.buttonPersonalInfo
        buttonOpenAiToneDialog = binding.buttonPersonalize // Đây là button bạn muốn dùng
        buttonLogout = binding.buttonLogout

        buttonEditInfo.setOnClickListener {
            showPersonalInfoDialog()
        }

        buttonOpenAiToneDialog.setOnClickListener { // Gán listener cho nút này
            showAiToneDialog()
        }

        buttonLogout.setOnClickListener {
            val sharedPreferences = requireContext().getSharedPreferences("LoginPrefs", Context.MODE_PRIVATE)
            with(sharedPreferences.edit()) {
                putBoolean("rememberMe", false)
                apply()
            }
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(requireActivity(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish() // Đóng Activity chứa Fragment này
        }

        setupObservers()

        // Fetch dữ liệu khi view được tạo
        // fetchUserProficiencyData() đã có, fetchCurrentUserInfo() sẽ lấy cả AI tone
        viewModel.fetchUserProficiencyData() // Nếu bạn vẫn muốn gọi riêng
        viewModel.fetchCurrentUserInfo()

        return root
    }

    private fun showPersonalInfoDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_personal_info, null)
        val editTextName = dialogView.findViewById<TextInputEditText>(R.id.editTextName)
        val editTextJob = dialogView.findViewById<TextInputEditText>(R.id.editTextJob)
        val editTextInterest = dialogView.findViewById<TextInputEditText>(R.id.editTextInterest)
        val editTextOtherInfo = dialogView.findViewById<TextInputEditText>(R.id.editTextOtherInfo)

        viewModel.fetchedUserInfo.value?.let { userInfo ->
            editTextName.setText(userInfo.name ?: "")
            editTextJob.setText(userInfo.job ?: "")
            editTextInterest.setText(userInfo.interest ?: "")
            editTextOtherInfo.setText(userInfo.otherInfo ?: "")
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Thông tin cá nhân")
            .setView(dialogView)
            .setPositiveButton("Lưu", null)
            .setNegativeButton("Hủy") { d, _ -> d.dismiss() }
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val name = editTextName.text.toString()
                val job = editTextJob.text.toString()
                val interest = editTextInterest.text.toString()
                val otherInfo = editTextOtherInfo.text.toString()
                viewModel.savePersonalInfo(name, job, interest, otherInfo)
                // Dialog có thể được đóng trong observer của personalInfoSaveSuccess
            }
        }
        dialog.show()
    }

    // --- Hàm MỚI để hiển thị Dialog cho AI Tone ---
    private fun showAiToneDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_ai_tone_preference, null) // Dùng layout bạn đã tạo
        val editTextAiTone = dialogView.findViewById<TextInputEditText>(R.id.editTextAiTone) // Hoặc EditText

        // Pre-fill với phong cách AI hiện tại từ ViewModel
        val currentTone = viewModel.fetchedUserInfo.value?.aiChatTone ?: "trung lập và thân thiện"
        editTextAiTone.setText(currentTone)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Tùy chỉnh phong cách AI")
            .setView(dialogView)
            .setPositiveButton("Lưu") { d, _ -> // Để dialog tự đóng khi nhấn Lưu
                val newTone = editTextAiTone.text.toString().trim()
                viewModel.saveAiChatTone(newTone)
            }
            .setNegativeButton("Hủy") { d, _ ->
                d.dismiss()
            }
            .create()
        dialog.show()
    }

    private fun setupObservers() {
        // Observers cho điểm và rank (giữ nguyên)
        viewModel.userScoreText.observe(viewLifecycleOwner) { scoreText ->
            scoreTextView.text = scoreText ?: "Đang tải..."
        }
        viewModel.userRankText.observe(viewLifecycleOwner) { rankText ->
            rankTextView.text = rankText ?: "Đang tải..."
        }


        // Observers cho việc fetch chung
        viewModel.isFetchingDetails.observe(viewLifecycleOwner) { isFetching ->
            binding.progressBar2.visibility = if (isFetching) View.VISIBLE else View.GONE // Sử dụng ProgressBar từ binding
            buttonEditInfo.isEnabled = !isFetching
            buttonOpenAiToneDialog.isEnabled = !isFetching
        }

        // Observers cho việc lưu thông tin cá nhân
        viewModel.isSavingPersonalInfo.observe(viewLifecycleOwner) { isSaving ->
            if (isSaving) {
                // Có thể hiển thị loading indicator riêng cho dialog personal info
                buttonEditInfo.isEnabled = false // Vô hiệu hóa nút khi đang lưu
            } else {
                // Chỉ bật lại nếu không có thao tác fetch nào khác đang chạy
                if (viewModel.isFetchingDetails.value == false) {
                    buttonEditInfo.isEnabled = true
                }
            }
        }
        viewModel.personalInfoSaveSuccess.observe(viewLifecycleOwner) { isSuccess ->
            if (isSuccess) {
                Toast.makeText(context, "Đã lưu thông tin cá nhân!", Toast.LENGTH_SHORT).show()
                viewModel.eventPersonalInfoSaveSuccessShown()
                // Dialog personal info nên tự đóng khi nhấn "Lưu" thành công
            }
        }

        // Observers MỚI cho việc lưu AI Tone
        viewModel.isSavingAiTone.observe(viewLifecycleOwner) { isSaving ->
            if (isSaving) {
                // Có thể hiển thị loading indicator riêng cho dialog AI tone
                buttonOpenAiToneDialog.isEnabled = false
            } else {
                if (viewModel.isFetchingDetails.value == false) {
                    buttonOpenAiToneDialog.isEnabled = true
                }
            }
        }
        viewModel.aiToneSaveSuccess.observe(viewLifecycleOwner) { isSuccess ->
            if (isSuccess) {
                Toast.makeText(context, "Đã cập nhật phong cách AI!", Toast.LENGTH_SHORT).show()
                viewModel.eventAiToneSaveSuccessShown()
                // Dialog AI tone nên tự đóng khi nhấn "Lưu"
            }
        }

        // Observer cho lỗi chung
        viewModel.errorMessage.observe(viewLifecycleOwner) { errorMessage ->
            errorMessage?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                viewModel.clearErrorMessage()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
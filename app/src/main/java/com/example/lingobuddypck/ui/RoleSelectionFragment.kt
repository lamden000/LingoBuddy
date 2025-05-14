package com.example.lingobuddypck.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import androidx.appcompat.widget.SwitchCompat
import androidx.navigation.fragment.NavHostFragment.Companion.findNavController
import androidx.navigation.fragment.findNavController
import com.example.lingobuddypck.R

class RoleSelectionFragment : Fragment() {

    private lateinit var userSpinner: Spinner
    private lateinit var aiSpinner: Spinner
    private lateinit var contextSpinner: Spinner
    private lateinit var swapButton: Button
    private lateinit var startButton: Button
    private lateinit var customSwitch: SwitchCompat
    private lateinit var customAIRoleEditTxt: EditText
    private lateinit var customUserRoleEditTxt: EditText
    private lateinit var customContextEditTxt: EditText

    private var roles = listOf("Doctor", "Customer", "Patient", "Teacher", "Student", "Employee","Staff")
    private var contexts = listOf("In a hospital", "In a classroom", "In a Coffee", "In a Airport" )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_role_selection, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        userSpinner = view.findViewById(R.id.spinner_user_role)
        aiSpinner = view.findViewById(R.id.spinner_ai_role)
        contextSpinner = view.findViewById(R.id.spinner_context)
        swapButton = view.findViewById(R.id.btn_swap_roles)
        startButton = view.findViewById(R.id.btn_start_conversation)
        customSwitch= view.findViewById(R.id.customSwitch)
        customAIRoleEditTxt= view.findViewById(R.id.editTxtAIRole)
        customUserRoleEditTxt= view.findViewById(R.id.editTxtUserRole)
        customContextEditTxt= view.findViewById(R.id.editTxtCustomContext)

        // Set spinners
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, roles)
        userSpinner.adapter = adapter
        aiSpinner.adapter = adapter

        val contextAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, contexts)
        contextSpinner.adapter = contextAdapter

        swapButton.setOnClickListener {
            val userPos = userSpinner.selectedItemPosition
            val aiPos = aiSpinner.selectedItemPosition
            userSpinner.setSelection(aiPos)
            aiSpinner.setSelection(userPos)
        }

        startButton.setOnClickListener {
            val userRole:String
            val aiRole:String
            val context:String
            if(!customSwitch.isChecked)
            {
                userRole = userSpinner.selectedItem.toString()
                aiRole = aiSpinner.selectedItem.toString()
                context = contextSpinner.selectedItem.toString()
            }
            else{
                userRole =  customUserRoleEditTxt.text.toString()
                aiRole = customAIRoleEditTxt.text.toString()
                context = customContextEditTxt.text.toString()
            }
            val bundle = Bundle().apply {
                putString("UserRole", userRole)
                putString("AIRole", aiRole)
                putString("context", context)
            }
            findNavController().navigate(
                R.id.action_roleSelectionFragment_to_rolePlayChatFragment,
                bundle
            )
        }

        customSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                ActiveCustom()
            } else {
                DeActiveCustom()
            }
        }
        DeActiveCustom()
    }
    private fun ActiveCustom()
    {
        customAIRoleEditTxt.isEnabled=true
        customUserRoleEditTxt.isEnabled=true
        customContextEditTxt.isEnabled=true
        userSpinner.isEnabled=false
        aiSpinner.isEnabled=false
        contextSpinner.isEnabled=false
        swapButton.isEnabled=false
    }

    private fun DeActiveCustom()
    {
        customAIRoleEditTxt.isEnabled=false
        customUserRoleEditTxt.isEnabled=false
        customContextEditTxt.isEnabled=false
        userSpinner.isEnabled=true
        aiSpinner.isEnabled=true
        contextSpinner.isEnabled=true
        swapButton.isEnabled=true
    }
}
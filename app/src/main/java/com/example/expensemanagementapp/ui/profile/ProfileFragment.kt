package com.example.expensemanagementapp.ui.profile

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.expensemanagementapp.R
import com.example.expensemanagementapp.data.AppDatabase
import com.example.expensemanagementapp.databinding.FragmentProfileBinding
import com.example.expensemanagementapp.repository.ExpenseRepository
import com.example.expensemanagementapp.viewmodel.ProfileViewModel
import com.example.expensemanagementapp.viewmodel.ViewModelFactory
import com.google.android.material.shape.CornerFamily
import java.util.Calendar
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: ProfileViewModel
    private var selectedImageUri: Uri? = null
    private var selectedDob: Long? = null
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedImageUri = uri
                
                // Persist detailed permission access
                try {
                     requireContext().contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // Might fail depending on source app
                }
                
                binding.ivProfileImage.setImageURI(uri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val dao = AppDatabase.getDatabase(requireContext()).expenseDao()
        val repository = ExpenseRepository(dao)
        val factory = ViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[ProfileViewModel::class.java]

        setupUI()
        observeData()
    }

    private fun setupUI() {
        // Image Picker
        binding.ivProfileImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            imagePickerLauncher.launch(intent)
        }
        



        // Date Picker
        binding.etDob.setOnClickListener {
            val calendar = Calendar.getInstance()
            selectedDob?.let { calendar.timeInMillis = it }
            
            val datePickerDialog = DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    val newDate = Calendar.getInstance()
                    newDate.set(year, month, dayOfMonth)
                    selectedDob = newDate.timeInMillis
                    binding.etDob.setText(dateFormat.format(Date(selectedDob!!)))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            datePickerDialog.datePicker.maxDate = System.currentTimeMillis()
            datePickerDialog.show()
        }

        // Save Button
        binding.btnSaveProfile.setOnClickListener {
            saveProfile()
        }
    }

    private fun observeData() {
        viewModel.userProfile.observe(viewLifecycleOwner) { profile ->
            profile?.let {
                binding.etName.setText(it.name)
                binding.etCity.setText(it.city)
                binding.etState.setText(it.state)
                binding.etMobile.setText(it.mobile)
                
                if (it.dob != null && it.dob != 0L) {
                    selectedDob = it.dob
                    binding.etDob.setText(dateFormat.format(Date(it.dob)))
                }
                
                if (!it.imageUri.isNullOrEmpty()) {
                    selectedImageUri = Uri.parse(it.imageUri)
                    binding.ivProfileImage.setImageURI(selectedImageUri)
                }
                
                if (it.gender == "Male") {
                    binding.rgGender.check(R.id.rbMale)
                } else if (it.gender == "Female") {
                    binding.rgGender.check(R.id.rbFemale)
                }
            }
        }
    }

    private fun saveProfile() {
        val name = binding.etName.text.toString().trim()
        val city = binding.etCity.text.toString().trim()
        val state = binding.etState.text.toString().trim()
        val mobile = binding.etMobile.text.toString().trim()

        // Optional validation
        if (mobile.isNotEmpty() && !mobile.all { it.isDigit() }) {
             binding.tilMobile.error = getString(R.string.digits_only)
             return
        } else {
             binding.tilMobile.error = null
        }



        val gender = when (binding.rgGender.checkedRadioButtonId) {
            R.id.rbMale -> "Male"
            R.id.rbFemale -> "Female"
            else -> null
        }

        viewModel.saveProfile(
            name = name,
            dob = selectedDob,
            city = city,
            state = state,
            mobile = mobile,
            imageUri = selectedImageUri?.toString(),
            gender = gender
        )
        
        Toast.makeText(requireContext(), getString(R.string.profile_updated), Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

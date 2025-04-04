package com.jewelrypos.swarnakhatabook

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.DialogFragment
import com.jewelrypos.swarnakhatabook.R
import com.jewelrypos.swarnakhatabook.databinding.FragmentSignatureDialogBinding
import java.io.File
import java.util.UUID

/**
 * Dialog fragment for capturing handwritten signatures
 */
class SignatureDialogFragment : DialogFragment() {

    private var _binding: FragmentSignatureDialogBinding? = null
    private val binding get() = _binding!!

    private var signatureListener: OnSignatureCapturedListener? = null

    interface OnSignatureCapturedListener {
        fun onSignatureCaptured(signatureUri: Uri)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Try to get parent fragment as listener
        val parentFragment = parentFragment
        if (parentFragment is OnSignatureCapturedListener) {
            signatureListener = parentFragment
        } else if (context is OnSignatureCapturedListener) {
            signatureListener = context
        } else {
            throw RuntimeException("$context must implement OnSignatureCapturedListener")
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_dialog_background)
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignatureDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set title
        binding.titleTextView.text = "Signature"

        // Set up buttons
        binding.clearButton.setOnClickListener {
            binding.signatureView.clear()
        }

        binding.saveButton.setOnClickListener {
            if (!binding.signatureView.hasSignature()) {
                Toast.makeText(context, "Please draw your signature", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            saveSignature()
        }

        binding.cancelButton.setOnClickListener {
            dismiss()
        }
    }

    private fun saveSignature() {
        try {
            // Create a file to save the signature
            val signatureFile = createSignatureFile()

            // Save signature to file
            if (binding.signatureView.saveSignature(signatureFile)) {
                // Get URI for the file
                val signatureUri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.provider",
                    signatureFile
                )

                // Notify listener
                signatureListener?.onSignatureCaptured(signatureUri)

                // Dismiss dialog
                dismiss()
            } else {
                Toast.makeText(
                    context,
                    "Failed to save signature. Please try again.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Error saving signature: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun createSignatureFile(): File {
        // Create a directory for signatures if it doesn't exist
        val signatureDir = File(requireContext().filesDir, "signatures")
        if (!signatureDir.exists()) {
            signatureDir.mkdirs()
        }

        // Create a unique filename
        val fileName = "signature_${UUID.randomUUID()}.png"
        return File(signatureDir, fileName)
    }

    override fun onStart() {
        super.onStart()

        // Make dialog full width
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): SignatureDialogFragment {
            return SignatureDialogFragment()
        }
    }
}
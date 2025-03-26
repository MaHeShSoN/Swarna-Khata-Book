package com.jewelrypos.swarnakhatabook.Utilitys

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.jewelrypos.swarnakhatabook.R


/**
 * A reusable Material 3 dialog class that uses the app's gold theme colors.
 */
class ThemedM3Dialog(private val context: Context) {

    private var dialogBuilder: MaterialAlertDialogBuilder = MaterialAlertDialogBuilder(context)
    private var dialogView: View? = null
    private var title: String = ""

    init {
        // Apply the custom theme styling
        dialogBuilder = MaterialAlertDialogBuilder(context, getDialogThemeStyle())
            .setBackgroundInsetStart(32)
            .setBackgroundInsetEnd(32)
            .setBackgroundInsetTop(24)
            .setBackgroundInsetBottom(24)
    }

    /**
     * Get the custom dialog style with your theme colors
     */
    private fun getDialogThemeStyle(): Int {
        // You should create this style in your styles.xml
        // This is just a placeholder - refer to styles recommendation below
        return R.style.GoldThemeDialog
    }

    /**
     * Set the title of the dialog.
     */
    fun setTitle(title: String): ThemedM3Dialog {
        this.title = title
        return this
    }

    /**
     * Set the custom layout for the dialog.
     */
    fun setLayout(layoutResId: Int): ThemedM3Dialog {
        val inflater = LayoutInflater.from(context)
        dialogView = inflater.inflate(layoutResId, null)

        // Apply theme colors to all TextInputLayouts in the inflated view
        applyThemeToInputFields()

        return this
    }

    fun getDialogView(): View? {
        return dialogView
    }


    /**
     * Apply the theme colors to all TextInputLayout elements in the dialog
     */
    private fun applyThemeToInputFields() {
        dialogView?.let { view ->
            // Find all TextInputLayout elements if any exist
            val textInputLayouts = findTextInputLayoutsRecursively(view)

            textInputLayouts.forEach { inputLayout ->
                // Apply the primary color to the box stroke
                inputLayout.boxStrokeColor = ContextCompat.getColor(context, R.color.my_light_primary)

                // Apply the hint text color
                inputLayout.hintTextColor = ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.my_light_primary)
                )

                // Set default box background color
                inputLayout.boxBackgroundColor = ContextCompat.getColor(context, R.color.my_light_surface)

                // Set cursor and text selection colors for EditText children
                val editText = inputLayout.editText
                if (editText is TextInputEditText) {
                    // Set the cursor color (requires API 29+)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        editText.textCursorDrawable?.setTint(
                            ContextCompat.getColor(context, R.color.my_light_primary)
                        )
                    }

                    // Set text color
                    editText.setTextColor(ContextCompat.getColor(context, R.color.my_light_on_surface))
                }
            }
        }
    }

    /**
     * Recursively find all TextInputLayout instances in the view hierarchy
     */
    private fun findTextInputLayoutsRecursively(view: View): List<TextInputLayout> {
        val result = mutableListOf<TextInputLayout>()

        if (view is TextInputLayout) {
            result.add(view)
        } else if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                result.addAll(findTextInputLayoutsRecursively(child))
            }
        }

        return result
    }

    /**
     * Set the custom view for the dialog directly.
     */
    fun setView(view: View): ThemedM3Dialog {
        dialogView = view
        applyThemeToInputFields()
        return this
    }

    /**
     * Set the positive button with a custom action.
     * The dialogView will be passed to allow accessing any views within the dialog.
     */
    fun setPositiveButton(
        buttonText: String,
        action: ((dialog: android.content.DialogInterface, dialogView: View?) -> Unit)? = null
    ): ThemedM3Dialog {
        dialogBuilder.setPositiveButton(buttonText) { dialog, _ ->
            action?.invoke(dialog, dialogView)
        }
        return this
    }

    /**
     * Set the negative button with a custom action.
     */
    fun setNegativeButton(
        buttonText: String,
        action: ((dialog: android.content.DialogInterface) -> Unit)? = null
    ): ThemedM3Dialog {
        dialogBuilder.setNegativeButton(buttonText) { dialog, _ ->
            action?.invoke(dialog)
        }
        return this
    }

    /**
     * Set the neutral button with a custom action.
     */
    fun setNeutralButton(
        buttonText: String,
        action: ((dialog: android.content.DialogInterface,dialogView: View?) -> Unit)? = null
    ): ThemedM3Dialog {
        dialogBuilder.setNeutralButton(buttonText) { dialog, _ ->
            action?.invoke(dialog, dialogView)
        }
        return this
    }

    /**
     * Helper method to find a view in the dialog layout by ID.
     */
    fun <T : View> findViewById(viewId: Int): T? {
        return dialogView?.findViewById(viewId)
    }

    /**
     * Build and show the dialog.
     */
    fun show() {
        dialogBuilder.setTitle(title)
        dialogView?.let {
            dialogBuilder.setView(it)
        }

        val dialog = dialogBuilder.create()

        // Set the button colors when the dialog is shown
        dialog.setOnShowListener { dialogInterface ->
            val positiveButton = (dialogInterface as AlertDialog ).getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            val negativeButton = (dialogInterface as AlertDialog ).getButton(android.app.AlertDialog.BUTTON_NEGATIVE)
            val neutralButton = (dialogInterface as AlertDialog ).getButton(android.app.AlertDialog.BUTTON_NEUTRAL)

            // Apply custom styling to positive button
            positiveButton?.let {
                if (it is MaterialButton) {
                    it.setTextColor(ContextCompat.getColor(context, R.color.cream_background))
                    it.backgroundTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.my_light_primary)
                    )
                } else {
                    it.setTextColor(ContextCompat.getColor(context, R.color.my_light_primary))
                }
            }

            // Apply custom styling to negative button
            negativeButton?.let {
                if (it is MaterialButton) {
                    it.setTextColor(ContextCompat.getColor(context, R.color.my_light_primary))
                    it.backgroundTintList = ColorStateList.valueOf(
                        Color.TRANSPARENT
                    )
                    it.strokeColor = ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.cream_background)
                    )
                    it.strokeWidth = 1



                    // This will create space between the buttons
                    (it.layoutParams as? LinearLayout.LayoutParams)?.apply {
                        leftMargin = 16.dpToPx(context)

                        // Apply the updated layout params
                        it.layoutParams = this
                    }


                } else {
                    it.setTextColor(ContextCompat.getColor(context, R.color.my_light_secondary))
                }
            }

            // Apply custom styling to neutral button if present
            neutralButton?.let {
                if (it is MaterialButton) {
                    it.setTextColor(ContextCompat.getColor(context, R.color.my_light_on_tertiary))
                    it.backgroundTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.my_light_tertiary)
                    )
                } else {
                    it.setTextColor(ContextCompat.getColor(context, R.color.my_light_tertiary))
                }
            }
        }

        dialog.window?.setBackgroundDrawableResource(R.drawable.dialog_rounded_background)
        dialog.show()
    }

    /**
     * Build and return the dialog without showing it.
     */
    fun create(): AlertDialog {
        dialogBuilder.setTitle(title)
        dialogView?.let {
            dialogBuilder.setView(it)
        }
        return dialogBuilder.create()
    }
    // Extension function to convert dp to pixels
    fun Int.dpToPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }
}
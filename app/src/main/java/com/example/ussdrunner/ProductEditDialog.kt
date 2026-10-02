package com.example.ussdrunner

import android.content.Context
import android.view.LayoutInflater
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText

object ProductEditDialog {

    fun show(ctx: Context, existing: Product?) {
        val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_product_edit, null)
        val etName  = view.findViewById<TextInputEditText>(R.id.etProductName)
        val etPrice = view.findViewById<TextInputEditText>(R.id.etProductPrice)
        val etSteps = view.findViewById<TextInputEditText>(R.id.etProductSteps)
        val etMsg   = view.findViewById<TextInputEditText>(R.id.etProductMessage)
        val swEn    = view.findViewById<MaterialSwitch>(R.id.switchProductEnabled)

        existing?.let {
            etName.setText(it.name)
            etPrice.setText("%.2f".format(it.price))
            etSteps.setText(it.steps.joinToString(","))
            etMsg.setText(it.successMessage)
            swEn.isChecked = it.enabled
        } ?: run { swEn.isChecked = true }

        MaterialAlertDialogBuilder(ctx)
            .setTitle(if (existing == null) "Add product" else "Edit product")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val name = etName.text?.toString()?.trim().orEmpty()
                val price = etPrice.text?.toString()?.trim()?.toDoubleOrNull() ?: 0.0
                val steps = etSteps.text?.toString().orEmpty()
                    .split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val msg = etMsg.text?.toString().orEmpty()
                if (name.isEmpty() || price <= 0.0 || steps.isEmpty()) return@setPositiveButton
                val p = existing?.copy(
                    name = name, price = price, steps = steps,
                    successMessage = msg, enabled = swEn.isChecked
                ) ?: Product(
                    name = name, price = price, steps = steps,
                    successMessage = msg, enabled = swEn.isChecked
                )
                ProductStore.save(p)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

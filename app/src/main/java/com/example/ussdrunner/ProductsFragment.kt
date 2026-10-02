package com.example.ussdrunner

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton

class ProductsFragment : Fragment() {

    private lateinit var llList: LinearLayout
    private lateinit var tvEmpty: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, b: Bundle?
    ): View = inflater.inflate(R.layout.fragment_products, container, false)

    override fun onViewCreated(v: View, b: Bundle?) {
        llList  = v.findViewById(R.id.llProductsList)
        tvEmpty = v.findViewById(R.id.tvProductsEmpty)

        v.findViewById<MaterialButton>(R.id.btnAddProduct)
            .setOnClickListener { ProductEditDialog.show(requireContext(), null) }

        ProductStore.products.observe(viewLifecycleOwner) { render(it) }
    }

    private fun render(list: List<Product>) {
        llList.removeAllViews()
        tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE

        list.forEach { p ->
            val row = layoutInflater.inflate(R.layout.item_product, llList, false)
            row.findViewById<TextView>(R.id.tvProductName).text = p.name
            row.findViewById<TextView>(R.id.tvProductPrice).text = "KSH %.2f".format(p.price)
            row.findViewById<TextView>(R.id.tvProductSteps).text =
                "Steps: ${p.steps.joinToString(" → ")}"
            row.findViewById<TextView>(R.id.tvProductMessage).text =
                if (p.successMessage.isBlank()) "(no success message)"
                else "SMS: ${p.successMessage}"

            row.findViewById<MaterialButton>(R.id.btnProductEdit)
                .setOnClickListener { ProductEditDialog.show(requireContext(), p) }
            row.findViewById<MaterialButton>(R.id.btnProductDelete)
                .setOnClickListener { ProductStore.delete(p.id) }

            llList.addView(row)
        }
    }
}

package com.example.ussdrunner

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FailedFragment : Fragment() {

    private lateinit var llList: LinearLayout
    private lateinit var tvEmpty: TextView

    private val fmt = SimpleDateFormat("d/M/yy HH:mm", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, b: Bundle?
    ): View = inflater.inflate(R.layout.fragment_failed, container, false)

    override fun onViewCreated(v: View, b: Bundle?) {
        llList  = v.findViewById(R.id.llFailedList)
        tvEmpty = v.findViewById(R.id.tvFailedEmpty)

        v.findViewById<MaterialButton>(R.id.btnClearResolved)
            .setOnClickListener { FailedStore.clearResolved() }

        FailedStore.failures.observe(viewLifecycleOwner) { render(it) }
    }

    private fun render(list: List<FailedTransaction>) {
        llList.removeAllViews()
        tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE

        list.forEach { f ->
            val row = layoutInflater.inflate(R.layout.item_failed, llList, false)

            row.findViewById<TextView>(R.id.tvFailProduct).text = f.productName
            row.findViewById<TextView>(R.id.tvFailAmount).text = "KSH %.2f".format(f.tx.amount)
            row.findViewById<TextView>(R.id.tvFailReason).text = "Reason: ${f.reason}"
            row.findViewById<TextView>(R.id.tvFailCustomer).text =
                "${f.tx.name.ifBlank { "Unknown" }}  ·  ${f.tx.phone.ifBlank { "—" }}"
            row.findViewById<TextView>(R.id.tvFailMeta).text = buildString {
                append(fmt.format(Date(f.timestamp)))
                append("  ·  Ref ").append(f.tx.code)
                if (f.retryCount > 0) append("  ·  Retried ").append(f.retryCount).append("×")
                if (f.resolved) append("  ·  RESOLVED")
            }

            val btnResolve = row.findViewById<MaterialButton>(R.id.btnFailResolve)
            val btnRetry   = row.findViewById<MaterialButton>(R.id.btnFailRetry)

            btnResolve.isEnabled = !f.resolved
            btnRetry.isEnabled   = !f.resolved

            btnResolve.setOnClickListener { FailedStore.markResolved(f.id) }
            btnRetry.setOnClickListener   { AutomationEngine.retry(requireContext(), f.id) }

            llList.addView(row)
        }
    }
}

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
import java.util.Calendar
import java.util.Locale

class InboxFragment : Fragment() {

    private lateinit var llList: LinearLayout
    private lateinit var tvEmpty: TextView
    private lateinit var tvTotal: TextView
    private lateinit var tvCount: TextView

    private val dayFmt = SimpleDateFormat("d/M/yy", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, b: Bundle?
    ): View = inflater.inflate(R.layout.fragment_inbox, container, false)

    override fun onViewCreated(v: View, b: Bundle?) {
        llList  = v.findViewById(R.id.llInboxList)
        tvEmpty = v.findViewById(R.id.tvInboxEmpty)
        tvTotal = v.findViewById(R.id.tvInboxTotal)
        tvCount = v.findViewById(R.id.tvInboxCount)

        v.findViewById<MaterialButton>(R.id.btnInboxClear)
            .setOnClickListener { MpesaStore.clear() }

        MpesaStore.transactions.observe(viewLifecycleOwner) { render(it) }
    }

    private fun render(list: List<MpesaTransaction>) {
        llList.removeAllViews()
        tvCount.text = list.size.toString()

        val today = dayFmt.format(Calendar.getInstance().time)
        val todayIn = list.filter {
            it.direction == MpesaTransaction.Direction.IN &&
            it.dateTime.startsWith(today)
        }.sumOf { it.amount }
        tvTotal.text = "KSH %.2f".format(todayIn)

        tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE

        list.take(200).forEach { tx ->
            val row = layoutInflater.inflate(R.layout.item_mpesa, llList, false)
            row.findViewById<TextView>(R.id.tvTxName).text = tx.name.ifBlank { "Unknown sender" }
            row.findViewById<TextView>(R.id.tvTxPhone).text = tx.phone.ifBlank { "—" }
            row.findViewById<TextView>(R.id.tvTxMeta).text =
                "${tx.dateTime}  ·  Bal KSH %.2f".format(tx.balance)
            val amt = row.findViewById<TextView>(R.id.tvTxAmount)
            amt.text = when (tx.direction) {
                MpesaTransaction.Direction.IN  -> "+KSH %.2f".format(tx.amount)
                MpesaTransaction.Direction.OUT -> "-KSH %.2f".format(tx.amount)
                else -> "KSH %.2f".format(tx.amount)
            }
            amt.setTextColor(when (tx.direction) {
                MpesaTransaction.Direction.IN  -> 0xFF10B981.toInt()
                MpesaTransaction.Direction.OUT -> 0xFFEF4444.toInt()
                else -> 0xFF6B7280.toInt()
            })
            row.findViewById<View>(R.id.vDirection).setBackgroundColor(
                when (tx.direction) {
                    MpesaTransaction.Direction.IN  -> 0xFF10B981.toInt()
                    MpesaTransaction.Direction.OUT -> 0xFFEF4444.toInt()
                    else -> 0xFF6B7280.toInt()
                }
            )
            row.findViewById<TextView>(R.id.tvTxCode).text = tx.code
            llList.addView(row)
        }
    }
}

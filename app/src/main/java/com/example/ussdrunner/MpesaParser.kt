package com.example.ussdrunner

object MpesaParser {

    private val codeRe    = Regex("""\b([A-Z0-9]{10})\b""")
    private val amountRe  = Regex("""KSH\s*([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE)
    private val receiveRe = Regex(
        """KSH\s*([\d,]+\.\d{2})\s+received\s+from\s+(\d{9,15})\s+([A-Z][A-Z\s]+?)(?:\.|New|$)""",
        RegexOption.IGNORE_CASE)
    private val sentRe    = Regex(
        """KSH\s*([\d,]+\.\d{2})\s+sent\s+to\s+(\d{9,15})\s+([A-Z][A-Z\s]+?)(?:\.|New|$)""",
        RegexOption.IGNORE_CASE)
    private val dateRe    = Regex(
        """on\s+(\d{1,2}/\d{1,2}/\d{2,4})\s+at\s+(\d{1,2}:\d{2}\s*[AP]M)""",
        RegexOption.IGNORE_CASE)
    private val balanceRe = Regex("""balance is KSH\s*([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE)
    private val costRe    = Regex("""Transaction cost,?\s*KSH\s*([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE)

    fun isMpesaMessage(address: String, body: String): Boolean {
        val a = address.uppercase()
        val senderOk = a.contains("MPESA") || a.contains("M-PESA")
        val bodyOk = body.contains("Confirmed", true) && body.contains("KSH", true)
        return senderOk || bodyOk
    }

    fun parse(body: String): MpesaTransaction? {
        if (!body.contains("Confirmed", true)) return null

        val code = codeRe.find(body)?.groupValues?.get(1) ?: ""
        val dateTime = dateRe.find(body)?.let { "${it.groupValues[1]} ${it.groupValues[2]}" } ?: ""
        val balance = balanceRe.find(body)?.groupValues?.get(1).toAmount()
        val cost = costRe.find(body)?.groupValues?.get(1).toAmount()

        receiveRe.find(body)?.let {
            return MpesaTransaction(code, MpesaTransaction.Direction.IN,
                it.groupValues[1].toAmount(), it.groupValues[2],
                it.groupValues[3].trim(), dateTime, balance, cost, body)
        }
        sentRe.find(body)?.let {
            return MpesaTransaction(code, MpesaTransaction.Direction.OUT,
                it.groupValues[1].toAmount(), it.groupValues[2],
                it.groupValues[3].trim(), dateTime, balance, cost, body)
        }
        val amt = amountRe.find(body)?.groupValues?.get(1).toAmount()
        if (amt == 0.0) return null
        return MpesaTransaction(code, MpesaTransaction.Direction.UNKNOWN, amt,
            "", "", dateTime, balance, cost, body)
    }

    private fun String?.toAmount(): Double =
        this?.replace(",", "")?.toDoubleOrNull() ?: 0.0
}

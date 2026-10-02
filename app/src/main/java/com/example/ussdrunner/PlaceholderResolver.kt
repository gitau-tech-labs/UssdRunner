package com.example.ussdrunner

object PlaceholderResolver {

    fun resolve(step: String, tx: MpesaTransaction): String = step
        .replace("{customer_phone}", tx.phone)
        .replace("{customer_phone_local}", tx.phoneLocal)
        .replace("{phone}", tx.phone)
        .replace("{phone_local}", tx.phoneLocal)
        .replace("{customer_name}", tx.name)
        .replace("{name}", tx.name)
        .replace("{amount}", tx.amount.toInt().toString())
        .replace("{amount_full}", "%.2f".format(tx.amount))
        .replace("{code}", tx.code)

    fun resolveAll(steps: List<String>, tx: MpesaTransaction): List<String> =
        steps.map { resolve(it, tx) }

    fun resolveMessage(msg: String, tx: MpesaTransaction): String = resolve(msg, tx)
}

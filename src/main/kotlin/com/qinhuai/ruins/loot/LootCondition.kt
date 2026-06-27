package com.qinhuai.ruins.loot

import com.qinhuai.corelib.placeholder.PapiBridge
import org.bukkit.entity.Player

object LootCondition {

    private val operators = listOf(">=", "<=", "==", "!=", ">", "<")

    fun eval(player: Player, raw: String?): Boolean {
        val condition = raw?.trim().orEmpty()
        if (condition.isEmpty()) return true
        val op = operators.firstOrNull { condition.contains(it) } ?: return true
        val idx = condition.indexOf(op)
        val left = PapiBridge.apply(player, condition.substring(0, idx).trim()).trim()
        val right = PapiBridge.apply(player, condition.substring(idx + op.length).trim()).trim()

        val ln = left.toDoubleOrNull()
        val rn = right.toDoubleOrNull()
        return if (ln != null && rn != null) compareNumeric(ln, op, rn) else compareString(left, op, right)
    }

    private fun compareNumeric(l: Double, op: String, r: Double): Boolean = when (op) {
        ">=" -> l >= r
        "<=" -> l <= r
        ">" -> l > r
        "<" -> l < r
        "==" -> l == r
        "!=" -> l != r
        else -> true
    }

    private fun compareString(l: String, op: String, r: String): Boolean = when (op) {
        "==" -> l.equals(r, true)
        "!=" -> !l.equals(r, true)
        else -> false
    }
}

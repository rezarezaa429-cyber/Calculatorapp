package com.example.calculator // ← اگر اسم پکیج پروژه‌ات فرق دارد، این خط را همان‌طور که بود نگه دار

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colors) {
                Surface(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        CalculatorScreen()
                    }
                }
            }
        }
    }
}

// ───────────── موتور محاسبه (تجزیه‌گر ریاضی) ─────────────
class Evaluator(private val s: String, private val degrees: Boolean) {
    private var i = 0

    fun eval(): Double {
        val v = expr()
        if (i < s.length) throw IllegalArgumentException("syntax")
        return v
    }

    private fun peek() = if (i < s.length) s[i] else '\u0000'
    private fun eat(c: Char): Boolean { if (peek() == c) { i++; return true }; return false }

    private fun expr(): Double {
        var v = term()
        while (true) {
            v = when {
                eat('+') -> v + term()
                eat('-') -> v - term()
                else -> return v
            }
        }
    }

    private fun term(): Double {
        var v = unary()
        while (true) {
            v = when {
                eat('×') -> v * unary()
                eat('÷') -> v / unary()
                else -> return v
            }
        }
    }

    private fun unary(): Double {
        if (eat('-')) return -unary()
        if (eat('+')) return unary()
        return power()
    }

    private fun power(): Double {
        val base = postfix()
        return if (eat('^')) base.pow(unary()) else base
    }

    private fun postfix(): Double {
        var v = primary()
        while (true) {
            v = when {
                eat('!') -> factorial(v)
                eat('%') -> v / 100.0
                else -> return v
            }
        }
    }

    private fun primary(): Double {
        if (eat('(')) {
            val v = expr()
            eat(')') // پرانتز بسته‌نشده هم قبول می‌شود
            return v
        }
        if (eat('π')) return PI
        if (eat('√')) return sqrt(postfix())
        if (peek().isDigit() || peek() == '.') {
            val start = i
            while (peek().isDigit() || peek() == '.') i++
            return s.substring(start, i).toDouble()
        }
        if (peek().isLetter()) {
            val start = i
            while (peek().isLetter()) i++
            val name = s.substring(start, i)
            if (name == "e") return E
            if (!eat('(')) throw IllegalArgumentException("syntax")
            val x = expr()
            eat(')')
            val rad = if (degrees) Math.toRadians(x) else x
            return when (name) {
                "sin" -> sin(rad).let { if (abs(it) < 1e-15) 0.0 else it }
                "cos" -> cos(rad).let { if (abs(it) < 1e-15) 0.0 else it }
                "tan" -> tan(rad)
                "ln" -> ln(x)
                "log" -> log10(x)
                else -> throw IllegalArgumentException("func")
            }
        }
        throw IllegalArgumentException("syntax")
    }

    private fun factorial(v: Double): Double {
        if (v < 0 || v != floor(v) || v > 170) throw IllegalArgumentException("fact")
        var r = 1.0
        for (k in 2..v.toInt()) r *= k
        return r
    }
}

fun calculate(expression: String, degrees: Boolean): String? {
    return try {
        val v = Evaluator(expression.replace('−', '-'), degrees).eval()
        if (v.isNaN() || v.isInfinite()) null else format(v)
    } catch (e: Exception) {
        null
    }
}

fun format(v: Double): String {
    if (v == 0.0) return "0"
    val a = abs(v)
    return if (a >= 1e15 || a < 1e-9) String.format("%.8e", v)
    else BigDecimal(v).round(MathContext(12)).stripTrailingZeros().toPlainString()
}

// ───────────── رابط کاربری ─────────────
private val funcs = listOf("sin(", "cos(", "tan(", "ln(", "log(")

@Composable
fun CalculatorScreen() {
    var expr by remember { mutableStateOf("") }
    var degrees by remember { mutableStateOf(true) }
    var justEvaluated by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    val preview = if (expr.isEmpty()) "" else (calculate(expr, degrees) ?: "")

    fun onKey(k: String) {
        error = false
        when (k) {
            "C" -> { expr = ""; justEvaluated = false }
            "⌫" -> {
                val f = funcs.firstOrNull { expr.endsWith(it) }
                expr = if (f != null) expr.dropLast(f.length) else expr.dropLast(1)
                justEvaluated = false
            }
            "=" -> {
                if (expr.isNotEmpty()) {
                    val r = calculate(expr, degrees)
                    if (r == null) error = true else { expr = r; justEvaluated = true }
                }
            }
            else -> {
                val isOperator = k in listOf("+", "−", "×", "÷", "^", "!", "%")
                val token = if (k in listOf("sin", "cos", "tan", "ln", "log")) "$k(" else k
                if (justEvaluated && !isOperator) expr = ""
                justEvaluated = false
                expr += token
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(8.dp).systemBarsPadding()) {
        // نمایشگر
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.End
        ) {
            TextButton(onClick = { degrees = !degrees }) { Text(if (degrees) "DEG" else "RAD") }
            Text(
                expr.ifEmpty { "0" },
                fontSize = 44.sp,
                maxLines = 1,
                modifier = Modifier.horizontalScroll(rememberScrollState(), reverseScrolling = true)
            )
            Text(
                if (error) "خطا در عبارت" else if (preview != expr) preview else "",
                fontSize = 26.sp,
                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
        }

        val rows = listOf(
            listOf("sin", "cos", "tan", "ln", "log"),
            listOf("√", "^", "!", "π", "e"),
            listOf("(", ")", "%", "⌫", "C"),
        )
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { CalcButton(it, Modifier.weight(1f).height(48.dp), tonal = true) { onKey(it) } }
            }
        }
        val main = listOf(
            listOf("7", "8", "9", "÷"),
            listOf("4", "5", "6", "×"),
            listOf("1", "2", "3", "−"),
            listOf("0", ".", "=", "+"),
        )
        main.forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach {
                    CalcButton(
                        it, Modifier.weight(1f).height(64.dp),
                        tonal = it.first().isDigit() || it == ".",
                        accent = it == "="
                    ) { onKey(it) }
                }
            }
        }
    }
}

@Composable
fun CalcButton(
    label: String,
    modifier: Modifier,
    tonal: Boolean = false,
    accent: Boolean = false,
    onClick: () -> Unit
) {
    val colors = when {
        accent -> ButtonDefaults.buttonColors()
        tonal -> ButtonDefaults.filledTonalButtonColors()
        else -> ButtonDefaults.outlinedButtonColors()
    }
    Button(
        onClick = onClick,
        modifier = modifier.padding(3.dp),
        contentPadding = PaddingValues(0.dp),
        colors = colors
    ) { Text(label, fontSize = 20.sp) }
}

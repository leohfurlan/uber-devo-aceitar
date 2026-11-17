package br.com.devoaceitar

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.ScrollView
import android.view.WindowManager
import android.view.WindowInsets

internal const val DECIMAL = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL

internal object SettingsColors {
    val canvas = Color.rgb(17, 24, 21)
    val section = Color.rgb(29, 39, 34)
    val control = Color.rgb(22, 31, 26)
    val outline = Color.rgb(72, 91, 81)
    val ink = Color.rgb(244, 248, 245)
    val muted = Color.rgb(186, 204, 194)
    val mint = Color.rgb(172, 235, 197)
    val mintInk = Color.rgb(14, 44, 29)
    val red = Color.rgb(255, 175, 169)
}

internal class SettingsForm(val context: Context, inset: Int = 16) {
    val body = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(inset), dp(4), dp(inset), dp(12))
    }

    fun dp(value: Int) = (value * context.resources.displayMetrics.density + 0.5f).toInt()

    fun label(value: String, size: Int = 14, color: Int = SettingsColors.muted): TextView =
        TextView(context).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
        }.also { body.addView(it) }

    fun section(value: String) = label(value, 18, SettingsColors.ink).apply {
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(4), 0, dp(6))
    }

    fun group(title: String): SettingsForm = SettingsForm(context, inset = 14).apply {
        body.background = shape(SettingsColors.section, 18)
        section(title)
        this@SettingsForm.body.addView(body, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(12)
        })
    }

    fun columns(first: SettingsForm, second: SettingsForm) {
        val row = LinearLayout(context)
        row.addView(first.body, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
        row.addView(second.body, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(row, LinearLayout.LayoutParams(-1, -2))
    }

    fun field(title: String, value: String, type: Int = DECIMAL, caption: String = title,
              captionColor: Int = SettingsColors.muted): EditText {
        val input = EditText(context).apply {
            id = View.generateViewId()
            setSingleLine()
            setRawInputType(type)
            setText(value)
            contentDescription = title
            setTextColor(SettingsColors.ink)
            setHintTextColor(SettingsColors.muted)
            textSize = 17f
            minimumHeight = dp(48)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), shape(SettingsColors.control, 10, SettingsColors.mint))
                addState(intArrayOf(), shape(SettingsColors.control, 10, SettingsColors.outline))
            }
        }
        label(caption, color = captionColor).apply {
            labelFor = input.id
            setPadding(0, dp(6), 0, dp(4))
        }
        body.addView(input, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        return input
    }

    fun picker(title: String, labels: List<String>, selected: Int): Spinner {
        val picker = Spinner(context).apply {
            id = View.generateViewId()
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, labels).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(selected)
            minimumHeight = dp(48)
            setPadding(dp(8), 0, dp(8), 0)
        }
        label(title).labelFor = picker.id
        body.addView(picker, LinearLayout.LayoutParams(-1, -2))
        return picker
    }

    fun button(title: String, primary: Boolean = false, clicked: () -> Unit) =
        control(title, primary, clicked).also {
            body.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }

    fun control(title: String, primary: Boolean = false, clicked: () -> Unit) =
        Button(context).apply {
            text = title
            isAllCaps = false
            textSize = 14f
            minimumHeight = dp(48)
            minWidth = 0
            minimumWidth = 0
            stateListAnimator = null
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setTypeface(typeface, Typeface.BOLD)
            styleControl(this, primary)
            setOnClickListener { clicked() }
        }

    fun styleControl(button: Button, active: Boolean) {
        button.setTextColor(if (active) SettingsColors.mintInk else SettingsColors.ink)
        button.background = RippleDrawable(ColorStateList.valueOf(0x247ECDA1),
            shape(if (active) SettingsColors.mint else SettingsColors.section, 12), null)
    }

    fun checkbox(title: String, checked: Boolean) = android.widget.CheckBox(context).apply {
        text = title
        isChecked = checked
        setTextColor(SettingsColors.ink)
        minimumHeight = dp(48)
        gravity = Gravity.CENTER_VERTICAL
        buttonTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(SettingsColors.mint, SettingsColors.muted),
        )
    }.also { body.addView(it) }

    fun shape(fill: Int, corners: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(corners).toFloat()
        stroke?.let { setStroke(dp(1), it) }
    }
}

internal fun Spinner.onSelectionChanged(change: () -> Unit) {
    onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = change()
        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
    }
}

internal fun EditText.decimalValue(): Double? =
    text.toString().trim().replace(',', '.').toDoubleOrNull()?.takeIf(Double::isFinite)

internal fun EditText.afterChange(change: () -> Unit) = addTextChangedListener(object : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
    override fun afterTextChanged(s: Editable?) = change()
})

internal fun Double?.editableNumber(): String =
    this?.let { java.math.BigDecimal.valueOf(it).stripTrailingZeros().toPlainString().replace('.', ',') }.orEmpty()

internal fun Dialog.expandSettings() {
    window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
}

internal fun Context.settingsDialog(content: View): Dialog =
    Dialog(this, android.R.style.Theme_Material_NoActionBar).apply {
        setContentView(content, ViewGroup.LayoutParams(-1, -1))
        if (Build.VERSION.SDK_INT >= 30) {
            window?.setDecorFitsSystemWindows(false)
            content.setOnApplyWindowInsetsListener { view, insets ->
                val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or
                    WindowInsets.Type.ime())
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
                insets
            }
        }
        show()
        expandSettings()
        content.requestApplyInsets()
    }

internal fun Context.settingsScroll(content: View): ScrollView =
    ScrollView(this).apply { addView(content) }

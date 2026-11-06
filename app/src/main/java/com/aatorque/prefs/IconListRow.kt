package com.aatorque.prefs

import android.content.Context
import android.util.AttributeSet
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.RadioButton

/**
 * Row for [ImageListPreference]'s dialog. Publishes the item's checked state and radio-button
 * role on the row's accessibility node, independent of the ListView's navigation selection state.
 */
class IconListRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    var isChecked = false

    override fun getAccessibilityClassName(): CharSequence = RadioButton::class.java.name

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.isCheckable = true
        info.isChecked = isChecked
    }
}

package com.aatorque.prefs

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import com.aatorque.stats.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Kind { DEFAULT, DRAWABLE, FILE, CHOOSE, DIVIDER }

data class BackgroundRow(val kind: Kind, val title: String, val value: String) {
    enum class Kind { DEFAULT, DRAWABLE, FILE, CHOOSE, DIVIDER }
}

fun buildRows(
    bundled: List<Pair<String, String>>,
    stored: List<String>,
    current: String
): List<BackgroundRow> {
    val rows = mutableListOf<BackgroundRow>()
    rows += BackgroundRow(BackgroundRow.Kind.DEFAULT, "App default", "")
    bundled.forEach { (title, drawableName) ->
        rows += BackgroundRow(
            BackgroundRow.Kind.DRAWABLE,
            title,
            encodeDrawable(drawableName)
        )
    }
    rows += BackgroundRow(BackgroundRow.Kind.DIVIDER, "", "")
    stored.forEachIndexed { index, name ->
        rows += BackgroundRow(
            BackgroundRow.Kind.FILE,
            "Custom image ${index + 1}",
            encodeFile(name)
        )
    }
    rows += BackgroundRow(BackgroundRow.Kind.CHOOSE, "Choose image…", "")
    return rows
}

class BackgroundPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    var value: String? = null
    var onChooseImage: (() -> Unit)? = null

    private fun bundled(): List<Pair<String, String>> {
        val titles = context.resources.getStringArray(R.array.backgroundstrings)
        val drawables = context.resources.getStringArray(R.array.backgrounds)
        val thumbs = context.resources.obtainTypedArray(R.array.thumbs_backgrounds)
        val result = mutableListOf<Pair<String, String>>()
        for (i in drawables.indices) {
            result += titles.getOrElse(i) { drawables[i] } to drawables[i]
        }
        thumbs.recycle()
        return result
    }

    override fun onClick() {
        val current = value ?: ""
        val storedNames = BackgroundStore.list(context)
        val bundledPairs = bundled()
        val rows = buildRows(bundledPairs, storedNames, current)

        // Load thumbnails off the main thread.
        CoroutineScope(Dispatchers.IO).launch {
            val storedThumbs = storedNames.associateWith { BackgroundStore.thumbnail(context, it) }
            withContext(Dispatchers.Main) {
                showDialog(rows, bundledPairs, storedThumbs, current)
            }
        }
    }

    private fun showDialog(
        rows: List<BackgroundRow>,
        bundledPairs: List<Pair<String, String>>,
        storedThumbs: Map<String, android.graphics.Bitmap?>,
        current: String
    ) {
        val thumbsArray = context.resources.obtainTypedArray(R.array.thumbs_backgrounds)
        val bundledThumbRes = bundledPairs.mapIndexed { index, pair ->
            pair.second to thumbsArray.getResourceId(index, 0)
        }.toMap()
        thumbsArray.recycle()

        val lv = ListView(context)
        val adapter = object : BaseAdapter() {
            override fun getCount() = rows.size
            override fun getItem(position: Int) = rows[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getViewTypeCount() = 2
            override fun getItemViewType(position: Int) =
                if (rows[position].kind == BackgroundRow.Kind.DIVIDER) 1 else 0

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = rows[position]
                if (row.kind == BackgroundRow.Kind.DIVIDER) {
                    return convertView
                        ?: LayoutInflater.from(
                            context
                        ).inflate(R.layout.list_divider, parent, false)
                }
                val view = convertView
                    ?: LayoutInflater.from(context).inflate(R.layout.icon_list_row, parent, false)
                val icon = view.findViewById<ImageView>(R.id.icon)
                val title = view.findViewById<TextView>(R.id.title)
                title.text = row.title
                when (row.kind) {
                    BackgroundRow.Kind.DEFAULT -> icon.setImageDrawable(null)

                    BackgroundRow.Kind.DRAWABLE -> {
                        val drawableName = parseDrawableName(row.value)
                        val resId = bundledThumbRes[drawableName] ?: 0
                        if (resId != 0) icon.setImageResource(resId)
                    }

                    BackgroundRow.Kind.FILE -> {
                        val name = parseStoredName(row.value)
                        val bmp = storedThumbs[name]
                        if (bmp != null) icon.setImageBitmap(bmp)
                    }

                    BackgroundRow.Kind.CHOOSE -> icon.setImageResource(
                        android.R.drawable.ic_menu_gallery
                    )

                    else -> {}
                }
                return view
            }
        }
        lv.adapter = adapter

        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(lv)
            .setNegativeButton(android.R.string.cancel) { d, _ -> d.dismiss() }
            .create()
            .show()

        lv.setOnItemClickListener { _, _, which, _ ->
            val row = rows[which]
            when (row.kind) {
                BackgroundRow.Kind.CHOOSE -> {
                    onChooseImage?.invoke()
                }

                BackgroundRow.Kind.DIVIDER -> Unit

                else -> {
                    if (callChangeListener(row.value)) {
                        value = row.value
                        notifyChanged()
                    }
                }
            }
        }
    }

    private fun parseDrawableName(value: String): String =
        if (value.startsWith("resource:")) value.removePrefix("resource:") else value

    private fun parseStoredName(value: String): String =
        if (value.startsWith("file:")) value.removePrefix("file:") else value
}

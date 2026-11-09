package com.lingion.sleepy.ui.screen.imports

import android.app.AlertDialog
import android.content.Context

/** Multiple declarations require an explicit choice before opening the selected login URL. */
internal fun showJwSgAdapterChooser(
    context: Context,
    title: String,
    entries: List<JwSgAssetBridge.SchoolEntry>,
    onSelected: (JwSgAssetBridge.SchoolEntry) -> Unit,
): AlertDialog {
    require(entries.isNotEmpty())
    var selected = -1
    val dialog = AlertDialog.Builder(context).setTitle(title)
        .setSingleChoiceItems(entries.map { "${it.adapterName}\n${it.importUrl}" }.toTypedArray(), -1) { parent, index ->
            selected = index
            (parent as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
        }
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(android.R.string.ok) { _, _ ->
            entries.getOrNull(selected)?.let(onSelected)
        }.create()
    dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false }
    dialog.show()
    return dialog
}

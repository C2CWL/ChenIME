// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.candidates

import android.view.View
import androidx.recyclerview.widget.RecyclerView

class CandidateViewHolder private constructor(
    itemView: View,
    val ui: CandidateItemUi?,
) : RecyclerView.ViewHolder(itemView) {
    constructor(ui: CandidateItemUi) : this(ui.root, ui)

    /** Fixed-width placeholder for a candidate currently outside the bar. */
    constructor(spacer: View) : this(spacer, null)

    var idx = -1
    var text = ""
    var comment = ""
    var highlighted = false
    var bindGeneration = -1
}

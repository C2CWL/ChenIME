/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.schema

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.c2cwl.chenime.R
import com.osfans.trime.util.roundedRippleDrawable
import splitties.dimensions.dp
import splitties.resources.color
import splitties.resources.resolveThemeAttribute
import splitties.resources.styledDimenPxSize
import splitties.views.dsl.constraintlayout.after
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.checkBox
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.wrapContent
import splitties.views.setPaddingDp
import splitties.views.textAppearance

class SchemaListEntryUi(
    override val ctx: Context,
) : Ui {
    val checkBox = checkBox {
        // The row handles the gesture so a tap anywhere on the card toggles once.
        isClickable = false
    }
    val nameText =
        textView {
            setPaddingDp(0, 16, 0, 16)
            textAppearance = ctx.resolveThemeAttribute(android.R.attr.textAppearanceListItem)
            setTextColor(color(R.color.settings_text))
            isClickable = false
        }

    override val root: View =
        constraintLayout {
            layoutParams = RecyclerView.LayoutParams(matchParent, wrapContent).apply {
                bottomMargin = dp(8)
            }
            background = roundedRippleDrawable(
                color(R.color.settings_divider),
                dp(20).toFloat(),
                color(R.color.settings_surface),
            )
            minHeight = styledDimenPxSize(android.R.attr.listPreferredItemHeightSmall)

            val paddingStart = styledDimenPxSize(android.R.attr.listPreferredItemPaddingStart)
            add(
                checkBox,
                lParams {
                    width = dp(30)
                    height = matchConstraints
                    centerVertically()
                    startOfParent(paddingStart)
                },
            )

            add(
                nameText,
                lParams {
                    width = matchConstraints
                    height = wrapContent
                    centerVertically()
                    after(checkBox, paddingStart)
                },
            )
        }.also { row ->
            row.setOnClickListener {
                checkBox.toggle()
            }
        }
}

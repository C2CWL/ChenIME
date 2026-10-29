package com.osfans.trime.ime.keyboard

fun useDistinctLetter(label: String, customFont: Boolean): Boolean = !customFont && (label == "I" || label == "l")

package com.novaboard.ime.suggestion

fun shouldLearnWord(incognito: Boolean, word: String): Boolean =
    !incognito && word.length >= 2
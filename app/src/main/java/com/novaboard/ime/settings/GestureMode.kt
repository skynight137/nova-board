package com.novaboard.ime.settings

enum class GestureMode(val storedValue: String) {
    FLOW("flow"),
    GESTURES("gestures"),
    ;

    companion object {
        fun fromStored(value: String?): GestureMode =
            entries.firstOrNull { it.storedValue == value } ?: FLOW
    }
}
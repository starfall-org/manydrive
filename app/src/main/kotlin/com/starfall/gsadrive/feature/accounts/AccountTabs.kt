package com.starfall.gsadrive.feature.accounts

internal fun isTabEnabled(type: AccountType?, index: Int): Boolean = when (index) {
    0 -> true
    1 -> type == AccountType.GOOGLE || type == AccountType.SERVICE
    2 -> false
    3 -> type == AccountType.GOOGLE || type == AccountType.SERVICE
    4 -> type == AccountType.GOOGLE
    else -> false
}

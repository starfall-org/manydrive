package com.starfall.gsadrive.feature.accounts

internal enum class AccountType(val label: String) { GOOGLE("Google"), S3("S3"), SERVICE("Service Account") }
internal data class AccountEntry(
    val type: AccountType,
    val id: String,
    val name: String,
    val detail: String = "",
    val avatarUrl: String? = null,
    val endpointUrl: String? = null
) {
    val key: String get() = "${type.name}:$id"
    val title: String get() = when (type) {
        AccountType.S3 -> detail.ifBlank { name }
        AccountType.GOOGLE, AccountType.SERVICE -> name.ifBlank { id }.substringBefore('@')
    }
    val subtitle: String get() = when (type) {
        AccountType.S3 -> endpointUrl?.takeIf { it.isNotBlank() } ?: name
        AccountType.GOOGLE, AccountType.SERVICE -> id
    }
}
internal data class AccountUi(
    val entries: List<AccountEntry> = emptyList(),
    val active: AccountEntry? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val revision: Int = 0
)

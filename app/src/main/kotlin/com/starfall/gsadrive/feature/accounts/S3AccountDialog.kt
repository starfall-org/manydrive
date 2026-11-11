package com.starfall.gsadrive.feature.accounts

import com.starfall.gsadrive.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.starfall.gsadrive.data.S3Config
import com.starfall.gsadrive.shared.i18n.localized
import com.starfall.gsadrive.shared.ui.CopyableError
import com.starfall.gsadrive.shared.ui.LoadingIndicator

@Composable
internal fun S3AccountDialog(
    onDismiss: () -> Unit,
    loading: Boolean = false,
    message: String? = null,
    connect: (String, S3Config) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var endpoint by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var bucket by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("us-east-1") }
    val validEndpoint = runCatching {
        val uri = java.net.URI(endpoint.trim())
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null
    }.getOrDefault(false)
    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text(localized(R.string.add_s3_account)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                message?.let { CopyableError(it, color = MaterialTheme.colorScheme.error) }
                if (loading) LoadingIndicator()
                OutlinedTextField(name, { name = it }, label = { Text(localized(R.string.account_name)) }, enabled = !loading, singleLine = true)
                OutlinedTextField(endpoint, { endpoint = it }, label = { Text(localized(R.string.https_endpoint)) }, enabled = !loading, singleLine = true)
                OutlinedTextField(bucket, { bucket = it }, label = { Text(localized(R.string.bucket)) }, enabled = !loading, singleLine = true)
                OutlinedTextField(region, { region = it }, label = { Text(localized(R.string.region)) }, enabled = !loading, singleLine = true)
                OutlinedTextField(key, { key = it }, label = { Text(localized(R.string.access_key)) }, enabled = !loading, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(secret, { secret = it }, label = { Text(localized(R.string.secret_key)) }, enabled = !loading, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation())
            }
        },
        confirmButton = {
            TextButton(
                enabled = !loading && name.isNotBlank() && validEndpoint && key.isNotBlank() && secret.isNotBlank() && bucket.isNotBlank(),
                onClick = { connect(name.trim(), S3Config(endpoint.trim(), key.trim(), secret, bucket.trim(), region.trim().ifBlank { "us-east-1" })) }
            ) { Text(localized(R.string.connect_and_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text(localized(R.string.cancel)) } }
    )
}

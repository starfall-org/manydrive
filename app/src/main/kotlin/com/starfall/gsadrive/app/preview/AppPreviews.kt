package com.starfall.gsadrive.app.preview

import com.starfall.gsadrive.R

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.starfall.gsadrive.app.App
import com.starfall.gsadrive.data.DriveFile
import com.starfall.gsadrive.feature.accounts.AccountEntry
import com.starfall.gsadrive.feature.accounts.AccountType
import com.starfall.gsadrive.feature.accounts.AccountTypeButton
import com.starfall.gsadrive.feature.accounts.AccountUi
import com.starfall.gsadrive.feature.accounts.S3AccountDialog
import com.starfall.gsadrive.feature.browser.DriveRootTopBar
import com.starfall.gsadrive.feature.browser.FileBrowserPage
import com.starfall.gsadrive.shared.i18n.localized
import com.starfall.gsadrive.shared.model.Model
import com.starfall.gsadrive.shared.ui.LoadingIndicator
import com.starfall.gsadrive.shared.ui.theme.ManyDriveTheme

// Keep previews alongside the private UI components so they need no Activity or services.
@Preview(name = "Light", group = "ManyDrive", showBackground = true, widthDp = 400, heightDp = 850)
@Preview(name = "Dark", group = "ManyDrive", showBackground = true, widthDp = 400, heightDp = 850,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
private annotation class ScreenPreviews

@Preview(name = "Light", group = "Components", showBackground = true, widthDp = 400)
@Preview(name = "Dark", group = "Components", showBackground = true, widthDp = 400,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
private annotation class ComponentPreviews

private val previewFiles = listOf(
    DriveFile("folder", localized(R.string.work_documents), "application/vnd.google-apps.folder", null),
    DriveFile("pdf", localized(R.string.project_plan_pdf), "application/pdf", "2026-09-08T09:30:00.000Z"),
    DriveFile("image", localized(R.string.weekend_trip_photo_jpg), "image/jpeg", "2026-09-07T14:15:00.000Z")
)

private val previewModel = Model(
    user = "Minh Anh",
    token = "preview-token",
    files = previewFiles,

)

@Composable
private fun PreviewSurface(content: @Composable () -> Unit) {
    ManyDriveTheme {
        Surface { content() }
    }
}

@ScreenPreviews
@Composable
private fun AppPreview() {
    var selected by remember { mutableIntStateOf(0) }
    PreviewSurface {
        App(
            model = previewModel, selected = selected, select = { selected = it }, reload = {},
            signIn = {}, authorize = {}, connectS3 = { _, _ -> }, signOut = {}, upload = {},
            createFolder = {}, trash = {},
            accounts = AccountUi(
                entries = listOf(AccountEntry(AccountType.GOOGLE, "minhanh@example.com", "Minh Anh"),
                    AccountEntry(AccountType.S3, "preview-s3", localized(R.string.work_warehouse), "documents"),
                    AccountEntry(AccountType.SERVICE, "drive@example.iam.gserviceaccount.com", "drive@example.iam.gserviceaccount.com")),
                active = AccountEntry(AccountType.GOOGLE, "minhanh@example.com", "Minh Anh")
            )
        )
    }
}

@Composable
private fun DrivePagePreviewContent(model: Model) {
    PreviewSurface {
        DrivePage(
            model = model, padding = PaddingValues(0.dp), signIn = {}, authorize = {},
            signOut = {}, upload = {}, createFolder = {}, trash = {}
        )
    }
}

@ScreenPreviews
@Composable
private fun DrivePagePreview() = DrivePagePreviewContent(previewModel)

@ScreenPreviews
@Composable
private fun DriveSignedOutPreview() = DrivePagePreviewContent(Model())

@ScreenPreviews
@Composable
private fun DriveAuthorizationPreview() = DrivePagePreviewContent(previewModel.copy(token = null, files = emptyList()))

@ScreenPreviews
@Composable
private fun DriveEmptyPreview() = DrivePagePreviewContent(previewModel.copy(files = emptyList()))

@ScreenPreviews
@Composable
private fun DriveLoadingPreview() = DrivePagePreviewContent(previewModel.copy(loading = true, files = emptyList()))

@ScreenPreviews
@Composable
private fun DriveErrorPreview() = DrivePagePreviewContent(
    previewModel.copy(message = localized(R.string.unable_to_load_file_list_please_try_again), files = emptyList())
)

@ScreenPreviews
@Composable
private fun S3PagePreview() {
    PreviewSurface {
        StoragePage(Model(files = previewFiles), PaddingValues(0.dp),
            AccountEntry(AccountType.S3, "s3-preview", localized(R.string.work_warehouse), "documents"), {}, {}, {})
    }
}

@ScreenPreviews
@Composable
private fun ServiceAccountPagePreview() {
    PreviewSurface {
        StoragePage(Model(files = previewFiles), PaddingValues(0.dp),
            AccountEntry(AccountType.SERVICE, "drive@example.iam.gserviceaccount.com", "drive@example.iam.gserviceaccount.com"), {}, {}, {})
    }
}

@ScreenPreviews
@Composable
private fun AccountsEmptyPreview() {
    PreviewSurface {
        Column {
            DriveRootTopBar("", {}, {}, {}, null)
            FileBrowserPage(Model(), PaddingValues(0.dp), null, false, "", authorize = {}, openFolder = {}, openFile = { _, _ -> })
        }
    }
}

@ComponentPreviews
@Composable
private fun FileRowPreview() {
    PreviewSurface { FileRow(previewFiles[1], onDelete = {}) }
}

@ComponentPreviews
@Composable
private fun FolderRowPreview() {
    PreviewSurface { FileRow(previewFiles[0]) }
}

@ComponentPreviews
@Composable
private fun LoadingPreview() {
    PreviewSurface { LoadingIndicator() }
}

@ScreenPreviews
@Composable
private fun S3AddAccountPreview() {
    PreviewSurface { S3AccountDialog(onDismiss = {}, connect = { _, _ -> }) }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun AccountTypeButtonsPreview() {
    PreviewSurface {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AccountTypeButton(Icons.Outlined.AccountCircle, "Google", localized(R.string.account_on_this_device)) {}
            AccountTypeButton(Icons.Outlined.Cloud, "S3", localized(R.string.enter_connection_details)) {}
            AccountTypeButton(Icons.Outlined.Key, "Service Account", localized(R.string.import_a_json_file)) {}
        }
    }
}

package com.gtstore

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.gtstore.ui.theme.GTStoreTheme
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class GTStoreScreen {
    DASHBOARD,
    SERVIDOR,
    ARQUIVOS,
    CLOUDFLARE,
    GITHUB,
    DOWNLOADS,
    CONFIGURACOES,
    LOGS
}

data class PackageItem(
    val id: String,
    val name: String,
    val file: String,
    val path: String,
    val sizeBytes: Long,
    val size: String,
    val modified: Long,
    val version: String
)

data class StorageInfo(
    val total: Long,
    val used: Long,
    val free: Long
)

class MainActivity : ComponentActivity() {

    private var selectedFolderUri by mutableStateOf<Uri?>(null)

    private val folderPicker =
        registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri ->

            if (uri != null) {

                try {

                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )

                } catch (_: Exception) {
                }

                getPreferences(MODE_PRIVATE)
                    .edit()
                    .putString(
                        "pkg_folder_uri",
                        uri.toString()
                    )
                    .apply()

                selectedFolderUri = uri
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val savedUri =
            getPreferences(MODE_PRIVATE)
                .getString(
                    "pkg_folder_uri",
                    null
                )

        if (!savedUri.isNullOrBlank()) {

            selectedFolderUri =
                Uri.parse(savedUri)
        }

        setContent {

            GTStoreTheme {

                GTStoreApp(
                    selectedFolderUri = selectedFolderUri,
                    onSelectFolder = {
                        folderPicker.launch(null)
                    }
                )
            }
        }
    }
}

@Composable
fun GTStoreApp(
    selectedFolderUri: Uri?,
    onSelectFolder: () -> Unit
) {

    var currentScreen by remember {
        mutableStateOf(
            GTStoreScreen.DASHBOARD
        )
    }

    when (currentScreen) {

        GTStoreScreen.DASHBOARD -> {

            Dashboard(
                onNavigate = {
                    currentScreen = it
                }
            )
        }

        GTStoreScreen.ARQUIVOS -> {

            FilesScreen(
                selectedFolderUri = selectedFolderUri,
                onSelectFolder = onSelectFolder,
                onBack = {
                    currentScreen =
                        GTStoreScreen.DASHBOARD
                }
            )
        }

        else -> {

            SimpleScreen(
                title = when (currentScreen) {

                    GTStoreScreen.SERVIDOR ->
                        "SERVIDOR"

                    GTStoreScreen.CLOUDFLARE ->
                        "CLOUDFLARE"

                    GTStoreScreen.GITHUB ->
                        "GITHUB"

                    GTStoreScreen.DOWNLOADS ->
                        "DOWNLOADS"

                    GTStoreScreen.CONFIGURACOES ->
                        "CONFIGURAÇÕES"

                    GTStoreScreen.LOGS ->
                        "LOGS"

                    else ->
                        "GTSTORE"
                },
                onBack = {
                    currentScreen =
                        GTStoreScreen.DASHBOARD
                }
            )
        }
    }
}

@Composable
fun Dashboard(
    onNavigate: (GTStoreScreen) -> Unit
) {

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {

        item {

            Text(
                text = "GTSTORE",
                style =
                    MaterialTheme.typography.headlineMedium
            )
        }

        item {

            Text(
                text = "Painel principal",
                style =
                    MaterialTheme.typography.bodyLarge
            )
        }

        item {

            StatusCard(
                title = "SERVIDOR",
                status = "OFFLINE"
            )
        }

        item {

            StatusCard(
                title = "ARMAZENAMENTO",
                status = "VERIFICAR"
            )
        }

        item {

            StatusCard(
                title = "CLOUDFLARE",
                status = "AGUARDANDO"
            )
        }

        item {

            StatusCard(
                title = "GITHUB",
                status = "AGUARDANDO"
            )
        }

        item {

            Button(
                onClick = {
                    onNavigate(
                        GTStoreScreen.SERVIDOR
                    )
                },
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Text("SERVIDOR")
            }
        }

        item {

            Button(
                onClick = {
                    onNavigate(
                        GTStoreScreen.ARQUIVOS
                    )
                },
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Text("ARQUIVOS")
            }
        }

        item {

            Button(
                onClick = {
                    onNavigate(
                        GTStoreScreen.CLOUDFLARE
                    )
                },
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Text("CLOUDFLARE")
            }
        }

        item {

            Button(
                onClick = {
                    onNavigate(
                        GTStoreScreen.GITHUB
                    )
                },
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Text("GITHUB")
            }
        }

        item {

            Button(
                onClick = {
                    onNavigate(
                        GTStoreScreen.DOWNLOADS
                    )
                },
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Text("DOWNLOADS")
            }
        }

        item {

            Button(
                onClick = {
                    onNavigate(
                        GTStoreScreen.CONFIGURACOES
                    )
                },
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Text("CONFIGURAÇÕES")
            }
        }

        item {

            Button(
                onClick = {
                    onNavigate(
                        GTStoreScreen.LOGS
                    )
                },
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Text("LOGS")
            }
        }
    }
}

@Composable
fun FilesScreen(
    selectedFolderUri: Uri?,
    onSelectFolder: () -> Unit,
    onBack: () -> Unit
) {

    var searchText by remember {
        mutableStateOf("")
    }

    var packages by remember {
        mutableStateOf(
            emptyList<PackageItem>()
        )
    }

    var storageInfo by remember {
        mutableStateOf<StorageInfo?>(null)
    }

    var scanning by remember {
        mutableStateOf(false)
    }

    var lastScan by remember {
        mutableStateOf("")
    }

    var refreshCounter by remember {
        mutableIntStateOf(0)
    }

    var lastAddedCount by remember {
        mutableIntStateOf(0)
    }

    var lastRemovedCount by remember {
        mutableIntStateOf(0)
    }

    var lastChangedCount by remember {
        mutableIntStateOf(0)
    }

    LaunchedEffect(
        selectedFolderUri,
        refreshCounter
    ) {

        if (selectedFolderUri == null) {

            packages = emptyList()
            storageInfo = null
            lastScan = ""

            lastAddedCount = 0
            lastRemovedCount = 0
            lastChangedCount = 0

            return@LaunchedEffect
        }

        scanning = true

        val scannedPackages =
            scanPackages(
                selectedFolderUri
            )

        val catalogPackages =
            scannedPackages.map { pkg ->

                CatalogPackage(
                    id = pkg.id,
                    name = pkg.name,
                    file = pkg.file,
                    path = pkg.path,
                    size = pkg.sizeBytes,
                    modified = pkg.modified,
                    version = pkg.version
                )
            }

        val syncResult =
            PackageCatalog.synchronize(
                context =
                    GTStoreApplication.context,
                currentPackages =
                    catalogPackages
            )

        packages = scannedPackages

        lastAddedCount =
            syncResult.added.size

        lastRemovedCount =
            syncResult.removed.size

        lastChangedCount =
            syncResult.changed.size

        storageInfo =
            getStorageInfo(
                selectedFolderUri
            )

        lastScan =
            SimpleDateFormat(
                "dd/MM/yyyy HH:mm:ss",
                Locale.getDefault()
            ).format(
                Date()
            )

        scanning = false
    }

    val filteredPackages =
        packages.filter { pkg ->

            pkg.name.contains(
                searchText,
                ignoreCase = true
            )
        }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {

        item {

            Text(
                text = "ARQUIVOS",
                style =
                    MaterialTheme.typography.headlineMedium
            )
        }

        item {

            Card(
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier =
                        Modifier.padding(16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(6.dp)
                ) {

                    Text(
                        text = "ARMAZENAMENTO",
                        style =
                            MaterialTheme.typography.titleLarge
                    )

                    if (selectedFolderUri == null) {

                        Text(
                            text =
                                "Nenhuma pasta selecionada."
                        )

                    } else {

                        Text(
                            text =
                                "HD: CONECTADO"
                        )

                        storageInfo?.let { info ->

                            Text(
                                text =
                                    "Capacidade: ${
                                        formatFileSize(
                                            info.total
                                        )
                                    }"
                            )

                            Text(
                                text =
                                    "Usado: ${
                                        formatFileSize(
                                            info.used
                                        )
                                    }"
                            )

                            Text(
                                text =
                                    "Livre: ${
                                        formatFileSize(
                                            info.free
                                        )
                                    }"
                            )
                        }

                        Text(
                            text =
                                "PKGs encontrados: ${packages.size}"
                        )

                        if (lastScan.isNotBlank()) {

                            Text(
                                text =
                                    "Última verificação: $lastScan"
                            )
                        }

                        if (
                            lastAddedCount > 0 ||
                            lastRemovedCount > 0 ||
                            lastChangedCount > 0
                        ) {

                            Spacer(
                                modifier =
                                    Modifier.height(4.dp)
                            )

                            Text(
                                text =
                                    "Alterações nesta verificação:"
                            )

                            if (
                                lastAddedCount > 0
                            ) {

                                Text(
                                    text =
                                        "Adicionados: $lastAddedCount"
                                )
                            }

                            if (
                                lastRemovedCount > 0
                            ) {

                                Text(
                                    text =
                                        "Removidos: $lastRemovedCount"
                                )
                            }

                            if (
                                lastChangedCount > 0
                            ) {

                                Text(
                                    text =
                                        "Alterados: $lastChangedCount"
                                )
                            }
                        }
                    }

                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )

                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        Button(
                            onClick = {
                                refreshCounter++
                            },
                            enabled =
                                selectedFolderUri != null &&
                                    !scanning
                        ) {

                            Text(
                                if (scanning)
                                    "VERIFICANDO..."
                                else
                                    "ATUALIZAR"
                            )
                        }

                        Spacer(
                            modifier =
                                Modifier.width(8.dp)
                        )

                        Button(
                            onClick =
                                onSelectFolder
                        ) {

                            Text(
                                "ALTERAR PASTA"
                            )
                        }
                    }

                    Spacer(
                        modifier =
                            Modifier.height(4.dp)
                    )

                    Button(
                        onClick = onBack,
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        Text(
                            "VOLTAR"
                        )
                    }
                }
            }
        }

        if (selectedFolderUri != null) {

            item {

                OutlinedTextField(
                    value = searchText,
                    onValueChange = {
                        searchText = it
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            "Pesquisar PKG"
                        )
                    },
                    singleLine = true
                )
            }
        }

        if (
            selectedFolderUri != null &&
            filteredPackages.isEmpty() &&
            !scanning
        ) {

            item {

                Card(
                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Text(
                        text =
                            if (searchText.isBlank())
                                "Nenhum arquivo .pkg encontrado."
                            else
                                "Nenhum PKG corresponde à pesquisa.",
                        modifier =
                            Modifier.padding(16.dp)
                    )
                }
            }
        }

        items(
            items = filteredPackages,
            key = {
                it.id
            }
        ) { pkg ->

            PackageCard(
                pkg = pkg
            )
        }
    }
}

fun scanPackages(
    folderUri: Uri
): List<PackageItem> {

    val result =
        mutableListOf<PackageItem>()

    return try {

        val root =
            DocumentFile.fromTreeUri(
                GTStoreApplication.context,
                folderUri
            )

        if (root != null) {

            scanDocumentTree(
                root = root,
                result = result
            )
        }

        result.sortedBy {
            it.name.lowercase(
                Locale.getDefault()
            )
        }

    } catch (_: Exception) {

        emptyList()
    }
}

fun scanDocumentTree(
    root: DocumentFile,
    result: MutableList<PackageItem>
) {

    for (file in root.listFiles()) {

        if (file.isDirectory) {

            scanDocumentTree(
                root = file,
                result = result
            )

        } else if (
            file.isFile &&
            file.name
                ?.lowercase(
                    Locale.getDefault()
                )
                ?.endsWith(".pkg") == true
        ) {

            val fileName =
                file.name ?: "PKG"

            val modified =
                file.lastModified()

            val size =
                file.length()

            val id =
                buildPackageId(
                    path =
                        file.uri.toString()
                )

            result.add(
                PackageItem(
                    id = id,
                    name = fileName,
                    file = fileName,
                    path =
                        file.uri.toString(),
                    sizeBytes = size,
                    size =
                        formatFileSize(size),
                    modified = modified,
                    version = ""
                )
            )
        }
    }
}

fun buildPackageId(
    path: String
): String {

    return try {

        val digest =
            MessageDigest.getInstance(
                "SHA-256"
            )

        val hash =
            digest.digest(
                path.toByteArray(
                    Charsets.UTF_8
                )
            )

        hash.joinToString("") {
            "%02x".format(it)
        }

    } catch (_: Exception) {

        path.hashCode()
            .toString()
    }
}

fun getStorageInfo(
    folderUri: Uri
): StorageInfo? {

    return try {

        val volumeName =
            android.provider.DocumentsContract
                .getTreeDocumentId(folderUri)
                ?.substringBefore(":")

        val path =
            if (
                volumeName.equals(
                    "primary",
                    ignoreCase = true
                )
            ) {

                Environment
                    .getExternalStorageDirectory()
                    .absolutePath

            } else {

                "/storage/$volumeName"
            }

        val statFs =
            StatFs(path)

        val blockSize =
            statFs.blockSizeLong

        val total =
            statFs.blockCountLong *
                blockSize

        val free =
            statFs.availableBlocksLong *
                blockSize

        val used =
            total - free

        StorageInfo(
            total = total,
            used = used,
            free = free
        )

    } catch (_: Exception) {

        null
    }
}

fun formatFileSize(
    bytes: Long
): String {

    if (bytes < 1024) {

        return "$bytes B"
    }

    val kb =
        bytes / 1024.0

    if (kb < 1024) {

        return String.format(
            Locale.getDefault(),
            "%.2f KB",
            kb
        )
    }

    val mb =
        kb / 1024.0

    if (mb < 1024) {

        return String.format(
            Locale.getDefault(),
            "%.2f MB",
            mb
        )
    }

    val gb =
        mb / 1024.0

    if (gb < 1024) {

        return String.format(
            Locale.getDefault(),
            "%.2f GB",
            gb
        )
    }

    val tb =
        gb / 1024.0

    return String.format(
        Locale.getDefault(),
        "%.2f TB",
        tb
    )
}

@Composable
fun PackageCard(
    pkg: PackageItem
) {

    Card(
        modifier =
            Modifier.fillMaxWidth()
    ) {

        Column(
            modifier =
                Modifier.padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(4.dp)
        ) {

            Text(
                text = pkg.name,
                style =
                    MaterialTheme.typography.titleMedium
            )

            Text(
                text =
                    "Tamanho: ${pkg.size}"
            )

            if (
                pkg.version.isNotBlank()
            ) {

                Text(
                    text =
                        "Versão: ${pkg.version}"
                )
            }

            Text(
                text =
                    "Tipo: PKG"
            )
        }
    }
}

@Composable
fun StatusCard(
    title: String,
    status: String
) {

    Card(
        modifier =
            Modifier.fillMaxWidth()
    ) {

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalArrangement =
                Arrangement.SpaceBetween
        ) {

            Text(
                text = title,
                style =
                    MaterialTheme.typography.titleMedium
            )

            Text(
                text = status
            )
        }
    }
}

@Composable
fun SimpleScreen(
    title: String,
    onBack: () -> Unit
) {

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement =
            Arrangement.spacedBy(16.dp)
    ) {

        Text(
            text = title,
            style =
                MaterialTheme.typography.headlineMedium
        )

        Text(
            text =
                "Módulo em desenvolvimento."
        )

        Button(
            onClick = onBack,
            modifier =
                Modifier.fillMaxWidth()
        ) {

            Text(
                "VOLTAR"
            )
        }
    }
}

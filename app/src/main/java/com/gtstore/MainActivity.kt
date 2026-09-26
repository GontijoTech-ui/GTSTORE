package com.gtstore

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.gtstore.ui.theme.GTStoreTheme
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
    val name: String,
    val size: String,
    val version: String
)

data class StorageInfo(
    val total: String,
    val used: String,
    val free: String
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
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }

                getPreferences(MODE_PRIVATE)
                    .edit()
                    .putString("pkg_folder_uri", uri.toString())
                    .apply()

                selectedFolderUri = uri
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val savedUri = getPreferences(MODE_PRIVATE)
            .getString("pkg_folder_uri", null)

        if (savedUri != null) {
            selectedFolderUri = Uri.parse(savedUri)
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
        mutableStateOf(GTStoreScreen.DASHBOARD)
    }

    val goBack = {
        currentScreen = GTStoreScreen.DASHBOARD
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {

        when (currentScreen) {

            GTStoreScreen.DASHBOARD -> {
                Dashboard(
                    onNavigate = {
                        currentScreen = it
                    }
                )
            }

            GTStoreScreen.SERVIDOR -> {
                SimpleScreen(
                    title = "SERVIDOR",
                    description = "Gerenciamento do servidor GTSTORE.",
                    onBack = goBack
                )
            }

            GTStoreScreen.ARQUIVOS -> {
                FilesScreen(
                    selectedFolderUri = selectedFolderUri,
                    onSelectFolder = onSelectFolder,
                    onBack = goBack
                )
            }

            GTStoreScreen.CLOUDFLARE -> {
                SimpleScreen(
                    title = "CLOUDFLARE",
                    description = "Configuração da conexão pública.",
                    onBack = goBack
                )
            }

            GTStoreScreen.GITHUB -> {
                SimpleScreen(
                    title = "GITHUB",
                    description = "Sincronização do catálogo e configurações.",
                    onBack = goBack
                )
            }

            GTStoreScreen.DOWNLOADS -> {
                SimpleScreen(
                    title = "DOWNLOADS",
                    description = "Downloads ativos e histórico.",
                    onBack = goBack
                )
            }

            GTStoreScreen.CONFIGURACOES -> {
                SimpleScreen(
                    title = "CONFIGURAÇÕES",
                    description = "Configurações gerais do GTSTORE.",
                    onBack = goBack
                )
            }

            GTStoreScreen.LOGS -> {
                SimpleScreen(
                    title = "LOGS",
                    description = "Eventos e registros do aplicativo.",
                    onBack = goBack
                )
            }
        }
    }
}

@Composable
fun Dashboard(
    onNavigate: (GTStoreScreen) -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text(
            text = "GTSTORE",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        StatusCard(
            title = "SERVIDOR",
            status = "● ONLINE"
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        PinCard()

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        StatusCard(
            title = "HD",
            status = "● AGUARDANDO"
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        StatusCard(
            title = "CLOUDFLARE",
            status = "● AGUARDANDO"
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        StatusCard(
            title = "GITHUB",
            status = "● AGUARDANDO"
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        DashboardRow(
            leftText = "SERVIDOR",
            rightText = "ARQUIVOS",
            leftAction = {
                onNavigate(GTStoreScreen.SERVIDOR)
            },
            rightAction = {
                onNavigate(GTStoreScreen.ARQUIVOS)
            }
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        DashboardRow(
            leftText = "CLOUDFLARE",
            rightText = "GITHUB",
            leftAction = {
                onNavigate(GTStoreScreen.CLOUDFLARE)
            },
            rightAction = {
                onNavigate(GTStoreScreen.GITHUB)
            }
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        DashboardRow(
            leftText = "DOWNLOADS",
            rightText = "CONFIGURAÇÕES",
            leftAction = {
                onNavigate(GTStoreScreen.DOWNLOADS)
            },
            rightAction = {
                onNavigate(GTStoreScreen.CONFIGURACOES)
            }
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Text(
            text = "Arquivos: 0 • Downloads ativos: 0"
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Button(
            onClick = {
                onNavigate(GTStoreScreen.LOGS)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("LOGS")
        }
    }
}

@Composable
fun PinCard() {

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "PIN DE HOJE",
                style = MaterialTheme.typography.labelLarge
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = "483721",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = "Válido até 23:59:59"
            )
        }
    }
}

@Composable
fun DashboardRow(
    leftText: String,
    rightText: String,
    leftAction: () -> Unit,
    rightAction: () -> Unit
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        Button(
            onClick = leftAction,
            modifier = Modifier
                .weight(1f)
                .height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(leftText)
        }

        Button(
            onClick = rightAction,
            modifier = Modifier
                .weight(1f)
                .height(50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(rightText)
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
        mutableStateOf<List<PackageItem>>(emptyList())
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
        mutableStateOf(0)
    }

    LaunchedEffect(
        selectedFolderUri,
        refreshCounter
    ) {

        if (selectedFolderUri != null) {

            scanning = true

            packages = scanPackages(
                selectedFolderUri
            )

            storageInfo = getStorageInfo(
                selectedFolderUri
            )

            lastScan = SimpleDateFormat(
                "dd/MM/yyyy HH:mm:ss",
                Locale.getDefault()
            ).format(Date())

            scanning = false

        } else {

            packages = emptyList()
            storageInfo = null
            lastScan = ""
        }
    }

    val filteredPackages = packages.filter { packageItem ->

        packageItem.name.contains(
            searchText,
            ignoreCase = true
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text(
            text = "ARQUIVOS",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {

            Column(
                modifier = Modifier.padding(16.dp)
            ) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Text(
                        text = "ARMAZENAMENTO",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = onBack
                    ) {
                        Text("VOLTAR")
                    }
                }

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                if (selectedFolderUri == null) {

                    Text(
                        text = "Nenhuma pasta selecionada."
                    )

                    Spacer(
                        modifier = Modifier.height(12.dp)
                    )

                    Button(
                        onClick = onSelectFolder,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("SELECIONAR PASTA DO HD")
                    }

                } else {

                    Text(
                        text = "HD: CONECTADO"
                    )

                    Spacer(
                        modifier = Modifier.height(4.dp)
                    )

                    if (storageInfo != null) {

                        Text(
                            text = "Capacidade: ${storageInfo!!.total}"
                        )

                        Text(
                            text = "Usado: ${storageInfo!!.used}"
                        )

                        Text(
                            text = "Livre: ${storageInfo!!.free}"
                        )
                    }

                    Spacer(
                        modifier = Modifier.height(4.dp)
                    )

                    Text(
                        text = if (scanning) {
                            "Procurando PKGs..."
                        } else {
                            "PKGs encontrados: ${packages.size}"
                        }
                    )

                    if (lastScan.isNotEmpty()) {

                        Text(
                            text = "Última verificação: $lastScan"
                        )
                    }

                    Spacer(
                        modifier = Modifier.height(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        Button(
                            onClick = {
                                refreshCounter++
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("ATUALIZAR")
                        }

                        Button(
                            onClick = onSelectFolder,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("ALTERAR PASTA")
                        }
                    }
                }
            }
        }

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        OutlinedTextField(
            value = searchText,
            onValueChange = {
                searchText = it
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text("Pesquisar arquivos")
            },
            singleLine = true
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            items(filteredPackages) { packageItem ->

                PackageCard(
                    packageItem = packageItem
                )
            }
        }
    }
}

fun scanPackages(
    folderUri: Uri
): List<PackageItem> {

    val result = mutableListOf<PackageItem>()

    return try {

        val root = DocumentFile.fromTreeUri(
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
            it.name.lowercase(Locale.getDefault())
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
                ?.lowercase(Locale.getDefault())
                ?.endsWith(".pkg") == true
        ) {

            val fileName = file.name ?: "PKG"

            val size = formatFileSize(
                file.length()
            )

            result.add(
                PackageItem(
                    name = fileName,
                    size = size,
                    version = "Detectar"
                )
            )
        }
    }
}

fun getStorageInfo(
    folderUri: Uri
): StorageInfo? {

    return try {

        val documentId =
            DocumentsContract.getTreeDocumentId(
                folderUri
            )

        val volumeName =
            documentId.substringBefore(":")

        val path = if (
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

        val statFs = StatFs(path)

        val totalBytes =
            statFs.blockCountLong *
                    statFs.blockSizeLong

        val freeBytes =
            statFs.availableBlocksLong *
                    statFs.blockSizeLong

        val usedBytes =
            totalBytes - freeBytes

        StorageInfo(
            total = formatFileSize(totalBytes),
            used = formatFileSize(usedBytes),
            free = formatFileSize(freeBytes)
        )

    } catch (_: Exception) {

        null
    }
}

fun formatFileSize(
    bytes: Long
): String {

    if (bytes <= 0) {
        return "0 B"
    }

    val units = arrayOf(
        "B",
        "KB",
        "MB",
        "GB",
        "TB"
    )

    var value = bytes.toDouble()
    var index = 0

    while (
        value >= 1024 &&
        index < units.lastIndex
    ) {

        value /= 1024
        index++
    }

    return if (index == 0) {

        "${value.toLong()} ${units[index]}"

    } else {

        String.format(
            Locale.US,
            "%.2f %s",
            value,
            units[index]
        )
    }
}

@Composable
fun PackageCard(
    packageItem: PackageItem
) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text(
                text = packageItem.name,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = "Tamanho: ${packageItem.size}"
            )

            Text(
                text = "Versão: ${packageItem.version}"
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text = "● DISPONÍVEL"
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
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall
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
    description: String,
    onBack: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "GTSTORE",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Text(
            text = description
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        Button(
            onClick = onBack
        ) {
            Text("VOLTAR")
        }
    }
}

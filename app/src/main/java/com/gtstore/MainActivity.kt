package com.gtstore

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.delay
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
    ADMIN
}

data class PackageItem(
    val id: String,
    val name: String,
    val file: String,
    val path: String,
    val sizeBytes: Long,
    val size: String,
    val modified: Long,
    val version: String,

    val title: String = "",
    val contentId: String = "",
    val category: String = "",
    val catalogType: String = "OTHER",
    val icon: ByteArray? = null,
    val iconSize: Int = 0,
    val digest: String = "",
    val digestMatches: Boolean = false,
    val metadataRead: Boolean = false
)

data class StorageInfo(
    val total: Long,
    val used: Long,
    val free: Long
)

data class CatalogGame(
    val key: String,
    val title: String,
    val cover: ByteArray?,
    val game: PackageItem?,
    val updates: List<PackageItem>,
    val dlcs: List<PackageItem>,
    val patches: List<PackageItem>
)

class MainActivity : ComponentActivity() {

    private val gtStoreHttpServer: HttpServer
        get() = (application as GTStoreApplication).httpServer

    private var selectedFolderUri by mutableStateOf<Uri?>(null)

    private val folderPicker = registerForActivityResult(
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
                .putString("pkg_folder_uri", uri.toString())
                .apply()

            getSharedPreferences("GTSTORE", MODE_PRIVATE)
                .edit()
                .putString("pkg_folder_uri", uri.toString())
                .apply()

            selectedFolderUri = uri
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedSavedUri = getSharedPreferences("GTSTORE", MODE_PRIVATE)
            .getString("pkg_folder_uri", null)

        val savedUri = sharedSavedUri
            ?: getPreferences(MODE_PRIVATE).getString("pkg_folder_uri", null)

        if (!savedUri.isNullOrBlank()) {
            selectedFolderUri = Uri.parse(savedUri)

            getSharedPreferences("GTSTORE", MODE_PRIVATE)
                .edit()
                .putString("pkg_folder_uri", savedUri)
                .apply()
        }

        setContent {
            MaterialTheme {
                GTStoreApp(
                    selectedFolderUri = selectedFolderUri,
                    onSelectFolder = {
                        folderPicker.launch(null)
                    },
                    httpServer = gtStoreHttpServer,
                    onStartServer = ::startServerService,
                    onStopServer = ::stopServerService
                )
            }
        }
    }

    private fun startServerService() {
        val intent = Intent(this, GTStoreService::class.java).apply {
            action = GTStoreService.ACTION_START
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopServerService() {
        val intent = Intent(this, GTStoreService::class.java).apply {
            action = GTStoreService.ACTION_STOP
        }
        startService(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}

@Composable
fun GTStoreApp(
    selectedFolderUri: Uri?,
    onSelectFolder: () -> Unit,
    httpServer: HttpServer,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit
) {
    var currentScreen by remember {
        mutableStateOf(GTStoreScreen.DASHBOARD)
    }

    when (currentScreen) {
        GTStoreScreen.DASHBOARD -> {
            Dashboard(
                httpServer = httpServer,
                onNavigate = { currentScreen = it }
            )
        }

        GTStoreScreen.SERVIDOR -> {
            ServerScreen(
                httpServer = httpServer,
                onStartServer = onStartServer,
                onStopServer = onStopServer,
                onBack = { currentScreen = GTStoreScreen.DASHBOARD }
            )
        }

        GTStoreScreen.ARQUIVOS -> {
            FilesScreen(
                selectedFolderUri = selectedFolderUri,
                onSelectFolder = onSelectFolder,
                onBack = { currentScreen = GTStoreScreen.DASHBOARD }
            )
        }

        GTStoreScreen.ADMIN -> {
            AdminScreen(
                httpServer = httpServer,
                onBack = { currentScreen = GTStoreScreen.DASHBOARD }
            )
        }

        else -> {
            SimpleScreen(
                title = when (currentScreen) {
                    GTStoreScreen.CLOUDFLARE -> "CLOUDFLARE"
                    GTStoreScreen.GITHUB -> "GITHUB"
                    GTStoreScreen.DOWNLOADS -> "DOWNLOADS"
                    GTStoreScreen.CONFIGURACOES -> "CONFIGURAÇÕES"
                    else -> "GTSTORE"
                },
                onBack = { currentScreen = GTStoreScreen.DASHBOARD }
            )
        }
    }
}

@Composable
fun Dashboard(
    httpServer: HttpServer,
    onNavigate: (GTStoreScreen) -> Unit
) {
    var serverRunning by remember {
        mutableStateOf(httpServer.isRunning())
    }

    LaunchedEffect(Unit) {
        while (true) {
            serverRunning = httpServer.isRunning()
            delay(1000)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "GTSTORE",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        item {
            Text(
                text = "Painel principal",
                style = MaterialTheme.typography.bodyLarge
            )
        }

        item {
            StatusCard(
                title = "SERVIDOR",
                status = if (serverRunning) "ONLINE" else "OFFLINE"
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
                onClick = { onNavigate(GTStoreScreen.SERVIDOR) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("SERVIDOR")
            }
        }

        item {
            Button(
                onClick = { onNavigate(GTStoreScreen.ARQUIVOS) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("ARQUIVOS")
            }
        }

        item {
            Button(
                onClick = { onNavigate(GTStoreScreen.CLOUDFLARE) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("CLOUDFLARE")
            }
        }

        item {
            Button(
                onClick = { onNavigate(GTStoreScreen.GITHUB) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("GITHUB")
            }
        }

        item {
            Button(
                onClick = { onNavigate(GTStoreScreen.DOWNLOADS) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("DOWNLOADS")
            }
        }

        item {
            Button(
                onClick = { onNavigate(GTStoreScreen.CONFIGURACOES) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("CONFIGURAÇÕES")
            }
        }

        item {
            Button(
                onClick = { onNavigate(GTStoreScreen.ADMIN) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("ADMIN")
            }
        }
    }
}

@Composable
fun AdminScreen(
    httpServer: HttpServer,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var currentPin by remember { mutableStateOf("------") }
    var activePins by remember { mutableStateOf<List<PinEntry>>(emptyList()) }

    LaunchedEffect(Unit) {
        while (true) {
            activePins = httpServer.getActivePinsList()
            delay(2000)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "PAINEL ADMIN",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "GERADOR DE PIN (PS4)",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Text(
                        text = "Gere um PIN de 6 dígitos válido por 10 minutos para liberar o acesso ao catálogo no PS4.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = currentPin,
                        style = MaterialTheme.typography.displayMedium,
                        color = Color(0xFF35C759),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )

                    Button(
                        onClick = {
                            val pin = httpServer.generateAdminPin()
                            currentPin = pin
                            activePins = httpServer.getActivePinsList()

                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("PIN PS4", pin))
                            Toast.makeText(context, "PIN $pin copiado!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("GERAR NOVO PIN (10 MIN)")
                    }

                    Button(
                        onClick = {
                            if (currentPin != "------" && currentPin.isNotBlank()) {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Seu PIN de download da GTSTORE é: *$currentPin*\n\n⚠️ Você tem 10 minutos para iniciar o download antes que o código expire."
                                    )
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Enviar PIN"))
                            } else {
                                Toast.makeText(context, "Gere um PIN primeiro!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ENVIAR NO WHATSAPP")
                    }
                }
            }
        }

        item {
            Text(
                text = "PINs Recentes",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (activePins.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Nenhum PIN gerado recentemente.",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(
                items = activePins,
                key = { it.pin }
            ) { entry ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "PIN: ${entry.pin}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            val elapsed: Long = System.currentTimeMillis() - entry.createdAt
                            val remainingSeconds: Long = ((600_000L - elapsed) / 1000L).coerceAtLeast(0L)
                            Text(
                                text = if (entry.isExpired) "Status: Expirado" else "Expira em: ${remainingSeconds / 60}m ${remainingSeconds % 60}s",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (entry.isExpired) Color(0xFFFF453A) else Color(0xFF35C759)
                            )
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("VOLTAR")
            }
        }
    }
}

@Composable
fun ServerScreen(
    httpServer: HttpServer,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onBack: () -> Unit
) {
    var status by remember {
        mutableStateOf(httpServer.getStatus())
    }

    var message by remember {
        mutableStateOf("")
    }

    LaunchedEffect(Unit) {
        while (true) {
            status = httpServer.getStatus()
            delay(1000)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "SERVIDOR",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (status.running) "STATUS: ONLINE" else "STATUS: OFFLINE",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Text(text = "Porta: ${status.port}")
                    Text(text = "Endereço local: ${status.localAddress}")

                    if (status.running) {
                        Text(text = "URL: http://${status.localAddress}:${status.port}")
                    }

                    Text(text = "Conexões ativas: ${status.activeConnections}")

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            if (httpServer.isRunning()) {
                                onStopServer()
                                message = "Solicitação para parar o servidor enviada."
                            } else {
                                onStartServer()
                                message = "Solicitação para iniciar o servidor enviada."
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (status.running) "PARAR SERVIDOR" else "INICIAR SERVIDOR")
                    }

                    if (message.isNotBlank()) {
                        Text(text = message)
                    }

                    Button(
                        onClick = { status = httpServer.getStatus() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("ATUALIZAR STATUS")
                    }

                    Button(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("VOLTAR")
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "TESTE",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Text(text = "Quando o servidor estiver online, abra a URL exibida no navegador do PS4.")
                    Text(text = "Página principal: /")
                    Text(text = "API de teste: /api/status")
                }
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
    var searchText by remember { mutableStateOf("") }
    var packages by remember { mutableStateOf(emptyList<PackageItem>()) }
    var storageInfo by remember { mutableStateOf<StorageInfo?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var lastScan by remember { mutableStateOf("") }
    var refreshCounter by remember { mutableIntStateOf(0) }
    var lastAddedCount by remember { mutableIntStateOf(0) }
    var lastRemovedCount by remember { mutableIntStateOf(0) }
    var lastChangedCount by remember { mutableIntStateOf(0) }
    var metadataSuccessCount by remember { mutableIntStateOf(0) }
    var metadataFailedCount by remember { mutableIntStateOf(0) }
    var selectedGame by remember { mutableStateOf<CatalogGame?>(null) }
    var sendMessage by remember { mutableStateOf("") }

    LaunchedEffect(selectedFolderUri, refreshCounter) {
        if (selectedFolderUri == null) {
            packages = emptyList()
            storageInfo = null
            lastScan = ""
            lastAddedCount = 0
            lastRemovedCount = 0
            lastChangedCount = 0
            metadataSuccessCount = 0
            metadataFailedCount = 0
            return@LaunchedEffect
        }

        scanning = true

        val scannedPackages = scanPackages(selectedFolderUri)
        val catalogPackages = scannedPackages.map { pkg ->
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

        val syncResult = PackageCatalog.synchronize(
            context = GTStoreApplication.context,
            currentPackages = catalogPackages
        )

        packages = scannedPackages
        lastAddedCount = syncResult.added.size
        lastRemovedCount = syncResult.removed.size
        lastChangedCount = syncResult.changed.size
        metadataSuccessCount = scannedPackages.count { it.metadataRead }
        metadataFailedCount = scannedPackages.count { !it.metadataRead }

        storageInfo = getStorageInfo(selectedFolderUri)

        lastScan = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        scanning = false
    }

    if (selectedGame != null) {
        DlcScreen(
            game = selectedGame!!,
            onBack = {
                selectedGame = null
                sendMessage = ""
            },
            onSend = { pkg ->
                sendMessage = preparePackageForSend(pkg)
            },
            message = sendMessage
        )
        return
    }

    val catalog = buildCatalog(packages)
    val filteredCatalog = catalog.filter { game ->
        if (searchText.isBlank()) {
            true
        } else {
            game.title.contains(searchText, ignoreCase = true) ||
                    game.key.contains(searchText, ignoreCase = true) ||
                    game.game?.contentId?.contains(searchText, ignoreCase = true) == true
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "CATÁLOGO",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "ARMAZENAMENTO",
                        style = MaterialTheme.typography.titleLarge
                    )

                    if (selectedFolderUri == null) {
                        Text(text = "Nenhuma pasta selecionada.")
                    } else {
                        Text(text = "HD: CONECTADO")
                        storageInfo?.let { info ->
                            Text(text = "Capacidade: ${formatFileSize(info.total)}")
                            Text(text = "Usado: ${formatFileSize(info.used)}")
                            Text(text = "Livre: ${formatFileSize(info.free)}")
                        }

                        Text(text = "PKGs encontrados: ${packages.size}")
                        Text(text = "Jogos no catálogo: ${catalog.size}")
                        Text(text = "Metadados lidos: $metadataSuccessCount")

                        if (metadataFailedCount > 0) {
                            Text(text = "Metadados não lidos: $metadataFailedCount")
                        }

                        if (lastScan.isNotBlank()) {
                            Text(text = "Última verificação: $lastScan")
                        }

                        if (lastAddedCount > 0 || lastRemovedCount > 0 || lastChangedCount > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "Alterações nesta verificação:")
                            if (lastAddedCount > 0) Text(text = "Adicionados: $lastAddedCount")
                            if (lastRemovedCount > 0) Text(text = "Removidos: $lastRemovedCount")
                            if (lastChangedCount > 0) Text(text = "Alterados: $lastChangedCount")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { refreshCounter++ },
                            enabled = selectedFolderUri != null && !scanning
                        ) {
                            Text(if (scanning) "VERIFICANDO..." else "ATUALIZAR")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(onClick = onSelectFolder) {
                            Text("ALTERAR PASTA")
                        }
                    }

                    Button(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("VOLTAR")
                    }
                }
            }
        }

        if (selectedFolderUri != null) {
            item {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Pesquisar jogo") },
                    singleLine = true
                )
            }
        }

        if (selectedFolderUri != null && filteredCatalog.isEmpty() && !scanning) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (searchText.isBlank()) "Nenhum PKG com metadata válido encontrado." else "Nenhum jogo corresponde à pesquisa.",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        items(
            items = filteredCatalog,
            key = { it.key }
        ) { game ->
            CatalogGameCard(
                game = game,
                onSend = { pkg ->
                    sendMessage = preparePackageForSend(pkg)
                },
                onOpenDlc = {
                    selectedGame = game
                    sendMessage = ""
                }
            )
        }

        if (sendMessage.isNotBlank()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = sendMessage,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CatalogGameCard(
    game: CatalogGame,
    onSend: (PackageItem) -> Unit,
    onOpenDlc: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PackageIcon(icon = game.cover)

            Text(
                text = game.title,
                style = MaterialTheme.typography.titleLarge
            )

            game.game?.let {
                Button(
                    onClick = { onSend(it) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("GAME")
                }
            }

            if (game.updates.isNotEmpty()) {
                Button(
                    onClick = { onSend(game.updates.first()) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("UPDATE")
                }
            }

            if (game.dlcs.isNotEmpty()) {
                Button(
                    onClick = onOpenDlc,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("DLC")
                }
            }

            if (game.patches.isNotEmpty()) {
                Button(
                    onClick = { onSend(game.patches.first()) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("PATCH")
                }
            }
        }
    }
}

@Composable
fun DlcScreen(
    game: CatalogGame,
    onBack: () -> Unit,
    onSend: (PackageItem) -> Unit,
    message: String
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "DLC",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        item {
            PackageIcon(icon = game.cover)
        }

        item {
            Text(
                text = game.title,
                style = MaterialTheme.typography.titleLarge
            )
        }

        item {
            Text(text = "DLCs encontrados: ${game.dlcs.size}")
        }

        if (game.dlcs.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Nenhum DLC encontrado.",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(
                items = game.dlcs,
                key = { it.id }
            ) { pkg ->
                DlcCard(
                    pkg = pkg,
                    onSend = { onSend(pkg) }
                )
            }
        }

        if (message.isNotBlank()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        item {
            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("VOLTAR")
            }
        }
    }
}

@Composable
fun DlcCard(
    pkg: PackageItem,
    onSend: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PackageIcon(icon = pkg.icon)

            Text(
                text = if (pkg.title.isNotBlank()) pkg.title else "DLC",
                style = MaterialTheme.typography.titleMedium
            )

            if (pkg.contentId.isNotBlank()) {
                Text(text = "Content ID: ${pkg.contentId}")
            }

            Text(text = "Tamanho: ${pkg.size}")

            if (pkg.category.isNotBlank()) {
                Text(text = "CATEGORY: ${pkg.category}")
            }

            Button(
                onClick = onSend,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("ENVIAR")
            }
        }
    }
}

@Composable
fun PackageIcon(icon: ByteArray?) {
    if (icon == null || icon.isEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Capa não disponível",
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        return
    }

    val bitmap = remember(icon) {
        try {
            BitmapFactory.decodeByteArray(icon, 0, icon.size)
        } catch (_: Exception) {
            null
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Capa do jogo",
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            contentScale = ContentScale.Fit
        )
    } else {
        Text(text = "Capa inválida")
    }
}

fun buildCatalog(packages: List<PackageItem>): List<CatalogGame> {
    val validPackages = packages.filter {
        it.metadataRead && it.contentId.isNotBlank()
    }

    val groups = validPackages.groupBy { catalogGroupKey(it) }

    return groups.map { (key, items) ->
        val game = items.firstOrNull { it.catalogType == "GAME" }
        val updates = items.filter { it.catalogType == "UPDATE" }
            .sortedWith(compareBy({ it.title.lowercase(Locale.getDefault()) }, { it.contentId }))
        val dlcs = items.filter { it.catalogType == "DLC" }
            .sortedWith(compareBy({ it.title.lowercase(Locale.getDefault()) }, { it.contentId }))
        val patches = items.filter { it.catalogType == "PATCH" }
            .sortedWith(compareBy({ it.title.lowercase(Locale.getDefault()) }, { it.contentId }))

        val title = game?.title?.takeIf { it.isNotBlank() }
            ?: items.firstOrNull { it.title.isNotBlank() }?.title
            ?: "PKG"

        val cover = game?.icon
            ?: items.firstOrNull { it.icon != null && it.icon.isNotEmpty() }?.icon

        CatalogGame(
            key = key,
            title = title,
            cover = cover,
            game = game,
            updates = updates,
            dlcs = dlcs,
            patches = patches
        )
    }.sortedBy { it.title.lowercase(Locale.getDefault()) }
}

fun catalogGroupKey(pkg: PackageItem): String {
    val contentId = pkg.contentId.uppercase(Locale.getDefault())
    val cusa = Regex("CUSA\\d+").find(contentId)?.value
    if (!cusa.isNullOrBlank()) return cusa
    return contentId.substringBefore("_00-").ifBlank { contentId }
}

fun preparePackageForSend(pkg: PackageItem): String {
    val server = GTStoreApplication.context.let {
        (it.applicationContext as GTStoreApplication).httpServer
    }

    val status = server.getStatus()
    if (!status.running) return "Servidor OFFLINE. Inicie o servidor antes de enviar."
    if (status.localAddress.isBlank() || status.localAddress == "SEM WI-FI") {
        return "Endereço Wi-Fi não disponível."
    }

    val url = "http://${status.localAddress}:${status.port}/download?id=${Uri.encode(pkg.id)}"

    try {
        val clipboard = GTStoreApplication.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("GTSTORE PKG", url))
    } catch (_: Exception) {
    }

    return buildString {
        append("PKG preparado para envio.\n\n")
        append(pkg.title.ifBlank { pkg.name })
        append("\n")
        append("Tipo: ${pkg.catalogType}\n")
        append("Tamanho: ${pkg.size}\n\n")
        append("URL copiada:\n")
        append(url)
    }
}

fun scanPackages(folderUri: Uri): List<PackageItem> {
    val result = mutableListOf<PackageItem>()

    return try {
        val root = DocumentFile.fromTreeUri(GTStoreApplication.context, folderUri)
        if (root != null) {
            scanDocumentTree(root = root, result = result)
        }

        result.sortedWith(
            compareBy(
                { it.title.ifBlank { it.name }.lowercase(Locale.getDefault()) },
                { it.contentId }
            )
        )
    } catch (_: Exception) {
        emptyList()
    }
}

fun scanDocumentTree(root: DocumentFile, result: MutableList<PackageItem>) {
    for (file in root.listFiles()) {
        if (file.isDirectory) {
            scanDocumentTree(root = file, result = result)
        } else if (file.isFile && file.name?.lowercase(Locale.getDefault())?.endsWith(".pkg") == true) {
            val fileName = file.name ?: "PKG"
            val size = file.length()
            val path = file.uri.toString()
            val id = buildPackageId(path)

            val meta = try {
                PkgMetaReader.read(GTStoreApplication.context, file.uri)
            } catch (_: Exception) {
                null
            }

            val catalogType = if (meta != null) classifyPkgCategory(meta.category) else "OTHER"

            result.add(
                PackageItem(
                    id = id,
                    name = fileName,
                    file = fileName,
                    path = path,
                    sizeBytes = size,
                    size = formatFileSize(size),
                    modified = file.lastModified(),
                    version = "",
                    title = meta?.title ?: "",
                    contentId = meta?.contentId ?: "",
                    category = meta?.category ?: "",
                    catalogType = catalogType,
                    icon = meta?.icon,
                    iconSize = meta?.icon?.size ?: 0,
                    digest = meta?.digest ?: "",
                    digestMatches = meta?.digestMatches ?: false,
                    metadataRead = meta != null
                )
            )
        }
    }
}

fun classifyPkgCategory(category: String): String {
    return when (category.lowercase(Locale.getDefault())) {
        "gd" -> "GAME"
        "gp" -> "UPDATE"
        "ac" -> "DLC"
        else -> "OTHER"
    }
}

fun buildPackageId(path: String): String {
    return try {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(path.toByteArray(Charsets.UTF_8))
        hash.joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        path.hashCode().toString()
    }
}

fun getStorageInfo(folderUri: Uri): StorageInfo? {
    return try {
        val volumeName = android.provider.DocumentsContract
            .getTreeDocumentId(folderUri)
            ?.substringBefore(":")

        val path = if (volumeName.equals("primary", ignoreCase = true)) {
            Environment.getExternalStorageDirectory().absolutePath
        } else {
            "/storage/$volumeName"
        }

        val statFs = StatFs(path)
        val blockSize = statFs.blockSizeLong
        val total = statFs.blockCountLong * blockSize
        val free = statFs.availableBlocksLong * blockSize
        val used = total - free

        StorageInfo(total = total, used = used, free = free)
    } catch (_: Exception) {
        null
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.getDefault(), "%.2f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.getDefault(), "%.2f MB", mb)
    val gb = mb / 1024.0
    if (gb < 1024) return String.format(Locale.getDefault(), "%.2f GB", gb)
    val tb = gb / 1024.0
    return String.format(Locale.getDefault(), "%.2f TB", tb)
}

fun shortDigest(digest: String): String {
    if (digest.length <= 16) return digest
    return digest.take(16) + "..."
}

@Composable
fun StatusCard(title: String, status: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = status)
        }
    }
}

@Composable
fun SimpleScreen(title: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        Text(text = "Módulo em desenvolvimento.")
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("VOLTAR")
        }
    }
}

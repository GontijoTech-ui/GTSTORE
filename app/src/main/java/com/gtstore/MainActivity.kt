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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// PALETA DE CORES PRETO ABSOLUTO & VERMELHO GT
val PureBlack = Color(0xFF000000)
val CardBlack = Color(0xFF080808)
val BorderDark = Color(0xFF222222)
val RedAccent = Color(0xFFE50914)
val GreenLed = Color(0xFF35C759)
val RedLed = Color(0xFFFF3B30)
val TextWhite = Color(0xFFFFFFFF)
val TextMuted = Color(0xFFAAAAAA)

enum class GTStoreScreen {
    DASHBOARD,
    SERVIDOR,
    ARQUIVOS,
    ADMIN
}

data class StorageInfo(
    val total: Long,
    val used: Long,
    val free: Long
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
            val colorScheme = darkColorScheme(
                background = PureBlack,
                surface = PureBlack,
                primary = RedAccent,
                onPrimary = TextWhite,
                onBackground = TextWhite,
                onSurface = TextWhite
            )

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = PureBlack
                ) {
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
    }
}

@Composable
fun Dashboard(
    httpServer: HttpServer,
    onNavigate: (GTStoreScreen) -> Unit
) {
    val context = LocalContext.current
    var serverRunning by remember {
        mutableStateOf(httpServer.isRunning())
    }

    // Carrega a logo da raiz (assets)
    val logoBitmap = remember {
        try {
            context.assets.open("logo.jpg").use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (_: Exception) {
            null
        }
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
            .background(PureBlack)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // LOGO CENTRALIZADA NO TOPO
        item {
            if (logoBitmap != null) {
                Image(
                    bitmap = logoBitmap.asImageBitmap(),
                    contentDescription = "GTSTORE Logo",
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(110.dp)
                        .padding(top = 10.dp, bottom = 4.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    text = "GTSTORE",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = RedAccent,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        }

        // CARTOES DE STATUS COM BOLINHAS LED
        item {
            StatusCardLed(
                title = "SERVIDOR",
                status = if (serverRunning) "ONLINE" else "OFFLINE",
                isOnline = serverRunning
            )
        }

        item {
            StatusCardSimple(
                title = "ARMAZENAMENTO",
                status = "PRONTO",
                statusColor = TextWhite
            )
        }

        item {
            StatusCardSimple(
                title = "CLOUDFLARE",
                status = "Aguardando",
                statusColor = TextMuted
            )
        }

        item {
            StatusCardSimple(
                title = "GITHUB",
                status = "Aguardando",
                statusColor = TextMuted
            )
        }

        item {
            Spacer(modifier = Modifier.height(6.dp))
        }

        // BOTOES RESTANTES COM ESTILO VERMELHO GT
        item {
            RedMenuButton(text = "SERVIDOR", onClick = { onNavigate(GTStoreScreen.SERVIDOR) })
        }

        item {
            RedMenuButton(text = "ARQUIVOS", onClick = { onNavigate(GTStoreScreen.ARQUIVOS) })
        }

        item {
            RedMenuButton(text = "ADMIN (SOLICITAÇÕES PIN)", onClick = { onNavigate(GTStoreScreen.ADMIN) })
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
            .background(PureBlack)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(RedAccent)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "PAINEL DO SERVIDOR",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBlack),
                border = BorderStroke(1.dp, BorderDark),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // INDICADOR VISUAL COM BOLINHA
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(if (status.running) GreenLed else RedLed)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (status.running) "SERVIDOR ONLINE" else "SERVIDOR OFFLINE",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (status.running) GreenLed else RedLed
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Porta: ${status.port}",
                        color = TextWhite,
                        fontSize = 15.sp
                    )

                    Text(
                        text = "Endereço Local: ${status.localAddress}",
                        color = TextWhite,
                        fontSize = 15.sp
                    )

                    if (status.running) {
                        Text(
                            text = "URL: http://${status.localAddress}:${status.port}",
                            color = Color(0xFF64B5F6),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        text = "Conexões ativas: ${status.activeConnections}",
                        color = TextMuted,
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

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
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (status.running) Color(0xFF333333) else RedAccent
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (status.running) "PARAR SERVIDOR" else "INICIAR SERVIDOR",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    if (message.isNotBlank()) {
                        Text(
                            text = message,
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }

                    Button(
                        onClick = { status = httpServer.getStatus() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("ATUALIZAR STATUS", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onBack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF141414)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("VOLTAR", color = TextWhite)
                    }
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
    var storageInfo by remember { mutableStateOf<StorageInfo?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var lastScan by remember { mutableStateOf("") }
    var refreshCounter by remember { mutableIntStateOf(0) }
    var totalPkgCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(selectedFolderUri, refreshCounter) {
        if (selectedFolderUri == null) {
            storageInfo = null
            lastScan = ""
            totalPkgCount = 0
            return@LaunchedEffect
        }

        scanning = true
        val root = try {
            DocumentFile.fromTreeUri(GTStoreApplication.context, selectedFolderUri)
        } catch (_: Exception) {
            null
        }

        var count = 0
        if (root != null) {
            fun countPkgs(dir: DocumentFile) {
                for (file in dir.listFiles()) {
                    if (file.isDirectory) countPkgs(file)
                    else if (file.isFile && file.name?.lowercase(Locale.getDefault())?.endsWith(".pkg") == true) count++
                }
            }
            countPkgs(root)
        }

        totalPkgCount = count
        storageInfo = getStorageInfo(selectedFolderUri)
        lastScan = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        scanning = false
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(RedAccent)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "ARMAZENAMENTO",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBlack),
                border = BorderStroke(1.dp, BorderDark),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (selectedFolderUri == null) {
                        Text(
                            text = "Nenhuma pasta selecionada.",
                            color = RedAccent,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "STATUS DO HD: CONECTADO",
                            color = GreenLed,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        storageInfo?.let { info ->
                            Text(text = "Capacidade Total: ${formatFileSize(info.total)}", color = TextWhite)
                            Text(text = "Espaço Usado: ${formatFileSize(info.used)}", color = TextMuted)
                            Text(text = "Espaço Livre: ${formatFileSize(info.free)}", color = GreenLed, fontWeight = FontWeight.SemiBold)
                        }

                        Text(text = "PKGs detectados: $totalPkgCount", color = TextWhite, fontWeight = FontWeight.Bold)

                        if (lastScan.isNotBlank()) {
                            Text(text = "Última verificação: $lastScan", color = TextMuted, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { refreshCounter++ },
                            enabled = selectedFolderUri != null && !scanning,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RedAccent),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (scanning) "LENDO..." else "ATUALIZAR", fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Button(
                            onClick = onSelectFolder,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("ALTERAR PASTA", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = onBack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF141414)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("VOLTAR", color = TextWhite)
                    }
                }
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
    var pinRequests by remember { mutableStateOf<List<PinRequest>>(emptyList()) }

    LaunchedEffect(Unit) {
        while (true) {
            pinRequests = httpServer.getPinRequests()
            delay(1500)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "SOLICITAÇÕES DE PIN",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite
            )
        }

        item {
            Text(
                text = "Quando o usuário clica em 'SOLICITAR PIN' no PS4, a chave exclusiva é gerada aqui para envio no WhatsApp.",
                fontSize = 14.sp,
                color = TextMuted,
                lineHeight = 20.sp
            )
        }

        if (pinRequests.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBlack),
                    border = BorderStroke(1.dp, BorderDark)
                ) {
                    Text(
                        text = "Nenhuma solicitação pendente no momento...",
                        modifier = Modifier.padding(20.dp),
                        color = TextMuted,
                        fontSize = 15.sp
                    )
                }
            }
        } else {
            items(
                items = pinRequests,
                key = { it.id }
            ) { req ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBlack),
                    border = BorderStroke(1.dp, BorderDark)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = req.gameTitle,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )

                        Text(
                            text = "Código: ${req.gameKey}",
                            fontSize = 14.sp,
                            color = Color(0xFF0088F0)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PIN: ${req.pin}",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = if (req.isExpired) RedLed else GreenLed
                            )

                            val elapsed = System.currentTimeMillis() - req.createdAt
                            val remaining = ((HttpServer.PIN_TIMEOUT_MS - elapsed) / 1000L).coerceAtLeast(0L)

                            Text(
                                text = if (req.isExpired) "EXPIRADO" else "${remaining / 60}m ${remaining % 60}s",
                                color = if (req.isExpired) RedLed else Color(0xFFFF9F0A),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Seu PIN de download para *${req.gameTitle}* (${req.gameKey}) na GTSTORE é: *${req.pin}*\n\n⚠️ Válido por 10 minutos."
                                    )
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Enviar PIN no WhatsApp"))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = RedAccent)
                        ) {
                            Text("ENVIAR NO WHATSAPP", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("PIN PS4", req.pin))
                                Toast.makeText(context, "PIN copiado!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222))
                        ) {
                            Text("COPIAR PIN")
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF141414)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("VOLTAR", color = TextWhite)
            }
        }
    }
}

// COMPONENTES REUTILIZÁVEIS
@Composable
fun StatusCardLed(title: String, status: String, isOnline: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBlack),
        border = BorderStroke(1.dp, BorderDark),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(RedAccent)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextWhite
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isOnline) GreenLed else RedLed)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = status,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isOnline) GreenLed else RedLed
                )
            }
        }
    }
}

@Composable
fun StatusCardSimple(title: String, status: String, statusColor: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBlack),
        border = BorderStroke(1.dp, BorderDark),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(RedAccent)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextWhite
                )
            }
            Text(
                text = status,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = statusColor
            )
        }
    }
}

@Composable
fun RedMenuButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = RedAccent,
            contentColor = TextWhite
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp
        )
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

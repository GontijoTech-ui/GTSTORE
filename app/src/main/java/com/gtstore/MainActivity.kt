package com.gtstore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.gtstore.ui.theme.GTStoreTheme

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

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            GTStoreTheme {
                GTStoreApp()
            }
        }
    }
}

@Composable
fun GTStoreApp() {

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
                    onNavigate = { screen ->
                        currentScreen = screen
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
            status = "● CONECTADO"
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        StatusCard(
            title = "CLOUDFLARE",
            status = "● CONECTADO"
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        StatusCard(
            title = "GITHUB",
            status = "● SINCRONIZADO"
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
    onBack: () -> Unit
) {

    var searchText by remember {
        mutableStateOf("")
    }

    val packages = remember {

        listOf(

            PackageItem(
                name = "PS4 Temperature",
                size = "12 MB",
                version = "1.0.0"
            ),

            PackageItem(
                name = "Homebrew Store",
                size = "25 MB",
                version = "2.1.0"
            ),

            PackageItem(
                name = "Payload Example",
                size = "4 MB",
                version = "1.0.2"
            )
        )
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

                Text(
                    text = "HD: CONECTADO"
                )

                Text(
                    text = "Espaço usado: 0 GB"
                )

                Text(
                    text = "Espaço livre: 0 GB"
                )

                Text(
                    text = "PKGs encontrados: ${packages.size}"
                )
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
                text = "Versão: ${packageItem.version}"
            )

            Text(
                text = "Tamanho: ${packageItem.size}"
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

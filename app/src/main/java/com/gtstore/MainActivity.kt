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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

            GTStoreScreen.ARQUIVOS -> {
                FilesScreen(
                    onBack = {
                        currentScreen = GTStoreScreen.DASHBOARD
                    }
                )
            }

            GTStoreScreen.SERVIDOR -> {
                SimpleScreen(
                    title = "SERVIDOR",
                    onBack = {
                        currentScreen = GTStoreScreen.DASHBOARD
                    }
                )
            }

            GTStoreScreen.CLOUDFLARE -> {
                SimpleScreen(
                    title = "CLOUDFLARE",
                    onBack = {
                        currentScreen = GTStoreScreen.DASHBOARD
                    }
                )
            }

            GTStoreScreen.GITHUB -> {
                SimpleScreen(
                    title = "GITHUB",
                    onBack = {
                        currentScreen = GTStoreScreen.DASHBOARD
                    }
                )
            }

            GTStoreScreen.DOWNLOADS -> {
                SimpleScreen(
                    title = "DOWNLOADS",
                    onBack = {
                        currentScreen = GTStoreScreen.DASHBOARD
                    }
                )
            }

            GTStoreScreen.CONFIGURACOES -> {
                SimpleScreen(
                    title = "CONFIGURAÇÕES",
                    onBack = {
                        currentScreen = GTStoreScreen.DASHBOARD
                    }
                )
            }

            GTStoreScreen.LOGS -> {
                SimpleScreen(
                    title = "LOGS",
                    onBack = {
                        currentScreen = GTStoreScreen.DASHBOARD
                    }
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

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {

            Column(
                modifier = Modifier.padding(16.dp)
            ) {

                Text(
                    text = "SERVIDOR",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "● ONLINE",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {

            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    text = "PIN DE HOJE",
                    style = MaterialTheme.typography.labelLarge
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "483721",
                    style = MaterialTheme.typography.headlineMedium
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Válido até 23:59:59",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        StatusCard(
            title = "HD",
            status = "● CONECTADO"
        )

        Spacer(modifier = Modifier.height(8.dp))

        StatusCard(
            title = "CLOUDFLARE",
            status = "● CONECTADO"
        )

        Spacer(modifier = Modifier.height(8.dp))

        StatusCard(
            title = "GITHUB",
            status = "● SINCRONIZADO"
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            DashboardButton(
                text = "SERVIDOR",
                modifier = Modifier.weight(1f),
                onClick = {
                    onNavigate(GTStoreScreen.SERVIDOR)
                }
            )

            DashboardButton(
                text = "ARQUIVOS",
                modifier = Modifier.weight(1f),
                onClick = {
                    onNavigate(GTStoreScreen.ARQUIVOS)
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            DashboardButton(
                text = "CLOUDFLARE",
                modifier = Modifier.weight(1f),
                onClick = {
                    onNavigate(GTStoreScreen.CLOUDFLARE)
                }
            )

            DashboardButton(
                text = "GITHUB",
                modifier = Modifier.weight(1f),
                onClick = {
                    onNavigate(GTStoreScreen.GITHUB)
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            DashboardButton(
                text = "DOWNLOADS",
                modifier = Modifier.weight(1f),
                onClick = {
                    onNavigate(GTStoreScreen.DOWNLOADS)
                }
            )

            DashboardButton(
                text = "CONFIGURAÇÕES",
                modifier = Modifier.weight(1f),
                onClick = {
                    onNavigate(GTStoreScreen.CONFIGURACOES)
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Arquivos: 0 • Downloads ativos: 0",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

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

    val filteredPackages = packages.filter {
        it.name.contains(
            searchText,
            ignoreCase = true
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Button(
                onClick = onBack
            ) {
                Text("VOLTAR")
            }

            Spacer(modifier = Modifier.padding(6.dp))

            Text(
                text = "ARQUIVOS",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {

            Column(
                modifier = Modifier.padding(16.dp)
            ) {

                Text(
                    text = "ARMAZENAMENTO",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(text = "HD: CONECTADO")
                Text(text = "Espaço usado: 0 GB")
                Text(text = "Espaço livre: 0 GB")
                Text(text = "PKGs encontrados: ${packages.size}")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

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

        Spacer(modifier = Modifier.height(12.dp))

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

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Versão: ${packageItem.version}"
            )

            Text(
                text = "Tamanho: ${packageItem.size}"
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "● DISPONÍVEL",
                style = MaterialTheme.typography.bodyMedium
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
                text = status,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun DashboardButton(
    text: String,
    modifier: Modifier,
    onClick: () -> Unit
) {

    Button(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(12.dp)
    ) {

        Text(text)
    }
}

@Composable
fun SimpleScreen(
    title: String,
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

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Esta tela será implementada na próxima etapa.",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onBack
        ) {
            Text("VOLTAR")
        }
    }
}

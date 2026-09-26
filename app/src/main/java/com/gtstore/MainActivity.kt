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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
                    onNavigate = { currentScreen = it }
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

            GTStoreScreen.ARQUIVOS -> {
                SimpleScreen(
                    title = "ARQUIVOS",
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

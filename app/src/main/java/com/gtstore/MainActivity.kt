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
import androidx.compose.foundation.layout.width
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
                    text = "

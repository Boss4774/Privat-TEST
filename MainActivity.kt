package com.friend.ai

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val scheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = scheme) {
                Surface(Modifier.fillMaxSize()) { ChatScreen(viewModel()) }
            }
        }
    }
}

@Composable
fun ChatScreen(vm: ChatVM) {
    val ctx = LocalContext.current
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importModel(uri)
    }
    LaunchedEffect(vm.messages.size, vm.messages.lastOrNull()) {
        if (vm.messages.isNotEmpty()) listState.scrollToItem(vm.messages.lastIndex)
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                when {
                    vm.status.isNotEmpty() -> vm.status
                    vm.ready -> "Друг на зв'язку"
                    else -> "Натисни «Модель» і обери файл .task"
                },
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Модель") }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(vm.messages) { m -> Bubble(m) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Напиши щось…") },
                maxLines = 4
            )
            Spacer(Modifier.width(6.dp))
            Button(
                onClick = { vm.send(input); input = "" },
                enabled = !vm.busy && vm.ready
            ) { Text("→") }
        }
    }

    if (vm.pending.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { vm.pending = emptyList() },
            title = { Text("Виконати дії?") },
            text = { Text(vm.pending.joinToString("\n") { "• " + Actions.describe(it) }) },
            confirmButton = {
                TextButton(onClick = {
                    val r = vm.pending.map { Actions.run(ctx, it) }
                    vm.pending = emptyList()
                    Toast.makeText(ctx, r.joinToString("\n"), Toast.LENGTH_LONG).show()
                }) { Text("Так") }
            },
            dismissButton = { TextButton(onClick = { vm.pending = emptyList() }) { Text("Ні") } }
        )
    }
}

@Composable
fun Bubble(m: Msg) {
    val me = m.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (me) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (me) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(m.text, Modifier.widthIn(max = 300.dp).padding(horizontal = 12.dp, vertical = 8.dp))
        }
    }
}

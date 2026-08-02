package com.example.notecheck

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.UUID

// --- LOCAL STORAGE HELPER (PERSISTENCE) ---
object NoteStorage {
    private const val PREFS_NAME = "notes_prefs"
    private const val NOTES_KEY = "saved_notes"

    // Saves the list as a formatted string (ID|Title|IsDone)
    fun saveNotes(context: Context, notesList: List<TodoItem>) {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val serializedList = notesList.joinToString(";;;") { note ->
            // Replace newlines with a special placeholder to prevent breaking storage structure
            val cleanTitle = note.title.replace("\n", "___NEWLINE___")
            "${note.id}|||${cleanTitle}|||${note.isDone}"
        }
        sharedPreferences.edit().putString(NOTES_KEY, serializedList).apply()
    }

    // Loads the saved notes from memory on startup
    fun loadNotes(context: Context): List<TodoItem> {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedData = sharedPreferences.getString(NOTES_KEY, null) ?: return emptyList()

        if (savedData.isBlank()) return emptyList()

        return try {
            savedData.split(";;;").mapNotNull { itemString ->
                val parts = itemString.split("|||")
                if (parts.size == 3) {
                    val id = parts[0]
                    val title = parts[1].replace("___NEWLINE___", "\n")
                    val isDone = parts[2].toBoolean()
                    TodoItem(id = id, title = title, isDone = isDone)
                } else null
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

// 1. DATA CLASS (The BluePrint)
data class TodoItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    var isDone: Boolean = false
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                TodoApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoApp() {
    val context = LocalContext.current

    var editingTask by remember { mutableStateOf<TodoItem?>(null) }
    var isAddingTask by remember { mutableStateOf(false) }
    var taskToDelete by remember { mutableStateOf<TodoItem?>(null) }

    // --- INITIALIZE LIST FROM STORAGE ---
    val todoList = remember {
        mutableStateListOf<TodoItem>().apply {
            addAll(NoteStorage.loadNotes(context))
        }
    }

    val selectedIds = remember { mutableStateMapOf<String, Boolean>() }
    val isSelectionMode = remember { derivedStateOf { selectedIds.isNotEmpty() } }.value

    // --- SEARCH STATE ---
    var searchQuery by remember { mutableStateOf("") }

    // --- FILTERED LIST LOGIC ---
    val filteredList = remember(searchQuery, todoList.size, todoList.map { it.title + it.isDone }) {
        if (searchQuery.isEmpty()) {
            todoList
        } else {
            todoList.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
    }

    // --- DELETE CONFIRMATION DIALOG ---
    if (taskToDelete != null) {
        AlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = { Text("Delete Note") },
            text = { Text("Are you sure you want to delete '${taskToDelete?.title?.substringBefore("\n")}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        todoList.remove(taskToDelete)
                        NoteStorage.saveNotes(context, todoList) // SAVE CHANGE
                        taskToDelete = null
                    }
                ) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { taskToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (editingTask != null) {
        EditTaskScreen(
            task = editingTask!!,
            onSave = { newTitle ->
                val index = todoList.indexOfFirst { it.id == editingTask!!.id }
                if (index != -1) {
                    todoList[index] = todoList[index].copy(title = newTitle)
                    NoteStorage.saveNotes(context, todoList) // SAVE CHANGE
                }
                editingTask = null
            },
            onCancel = { editingTask = null }
        )
    } else if (isAddingTask) {
        // NEW NOTE SCREEN
        NewNoteScreen(
            onSave = { newTitle ->
                if (newTitle.isNotBlank()) {
                    todoList.add(TodoItem(title = newTitle))
                    NoteStorage.saveNotes(context, todoList) // SAVE CHANGE
                }
                isAddingTask = false
            },
            onCancel = { isAddingTask = false }
        )
    } else {
        // MAIN LIST SCREEN
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        if (isSelectionMode) {
                            IconButton(onClick = { selectedIds.clear() }) {
                                Icon(Icons.Default.Close, contentDescription = "Exit Selection")
                            }
                        }
                    },
                    title = {
                        if (!isSelectionMode) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search notes...") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                                        }
                                    }
                                }
                            )
                        } else {
                            Text("Selected Items")
                        }
                    },
                    actions = {
                        if (isSelectionMode) {
                            TextButton(onClick = { todoList.forEach { selectedIds[it.id] = true } }) {
                                Text("Select All")
                            }
                            IconButton(onClick = {
                                todoList.forEach { task ->
                                    if (selectedIds.containsKey(task.id)) {
                                        val index = todoList.indexOf(task)
                                        if (index != -1) todoList[index] = task.copy(isDone = true)
                                    }
                                }
                                NoteStorage.saveNotes(context, todoList) // SAVE CHANGE
                                selectedIds.clear()
                            }) { Icon(Icons.Default.Check, "Done") }

                            IconButton(onClick = {
                                val idsToRemove = selectedIds.keys.toList()
                                todoList.removeAll { it.id in idsToRemove }
                                NoteStorage.saveNotes(context, todoList) // SAVE CHANGE
                                selectedIds.clear()
                            }) { Icon(Icons.Default.Delete, "Delete") }
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { isAddingTask = true }) {
                    Icon(Icons.Default.Add, contentDescription = "New Note")
                }
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                items(filteredList) { task ->
                    TaskRow(
                        task = task,
                        isSelected = selectedIds.containsKey(task.id),
                        isInSelectionMode = isSelectionMode,
                        onToggleDone = { updated ->
                            val idx = todoList.indexOf(task)
                            if (idx != -1) {
                                todoList[idx] = updated
                                NoteStorage.saveNotes(context, todoList) // SAVE CHANGE
                            }
                        },
                        onDelete = { taskToDelete = task },
                        onLongClick = { if (!isSelectionMode) selectedIds[task.id] = true },
                        onSelectToggle = {
                            if (selectedIds.containsKey(task.id)) selectedIds.remove(task.id)
                            else selectedIds[task.id] = true
                        },
                        onEdit = { editingTask = task }
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TaskRow(
    task: TodoItem,
    isSelected: Boolean,
    isInSelectionMode: Boolean,
    onToggleDone: (TodoItem) -> Unit,
    onDelete: () -> Unit,
    onLongClick: () -> Unit,
    onSelectToggle: () -> Unit,
    onEdit: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (task.isDone) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surface,
        label = "color"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .heightIn(min = 64.dp)
            .combinedClickable(
                onClick = {
                    if (isInSelectionMode) {
                        onSelectToggle()
                    } else {
                        onEdit()
                    }
                },
                onLongClick = onLongClick
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                if (isInSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onSelectToggle() },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }

                Text(
                    text = task.title.substringBefore("\n"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (task.isDone) Color.Gray else Color.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onToggleDone(task.copy(isDone = !task.isDone)) }) {
                    Icon(
                        imageVector = if (task.isDone) Icons.Default.Close else Icons.Default.Check,
                        contentDescription = "Toggle Done",
                        tint = if (task.isDone) Color.Red else Color.Gray
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Task",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTaskScreen(
    task: TodoItem,
    onSave: (String) -> Unit,
    onCancel: () -> Unit
) {
    var textValue by remember {
        mutableStateOf(TextFieldValue(task.title, TextRange(task.title.length)))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Task") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    IconButton(onClick = { onSave(textValue.text) }) {
                        Icon(Icons.Default.Check, "Save", tint = Color(0xFF4CAF50))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            TextField(
                value = textValue,
                onValueChange = { newValue ->
                    textValue = handleSmartLists(textValue, newValue)
                },
                placeholder = { Text("Start typing...") },
                modifier = Modifier.fillMaxSize(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                singleLine = false
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewNoteScreen(
    onSave: (String) -> Unit,
    onCancel: () -> Unit
) {
    var textValue by remember {
        mutableStateOf(TextFieldValue("", TextRange(0)))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Note") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onSave(textValue.text) }) {
                        Icon(Icons.Default.Check, "Done", tint = Color(0xFF4CAF50))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            TextField(
                value = textValue,
                onValueChange = { newValue ->
                    textValue = handleSmartLists(textValue, newValue)
                },
                placeholder = { Text("Enter your note...") },
                modifier = Modifier.fillMaxSize(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                singleLine = false
            )
        }
    }
}

fun handleSmartLists(oldValue: TextFieldValue, newValue: TextFieldValue): TextFieldValue {
    val oldText = oldValue.text
    val newText = newValue.text

    if (newText.length == oldText.length + 1 && newText.endsWith('\n')) {
        val lines = oldText.split("\n")
        val lastLine = lines.lastOrNull() ?: ""

        val numberMatch = Regex("""^(\d+)\.\s+(.+)""").find(lastLine)
        if (numberMatch != null) {
            val nextNum = numberMatch.groupValues[1].toInt() + 1
            val suffix = "$nextNum. "
            val resultText = newText + suffix
            return TextFieldValue(resultText, TextRange(resultText.length))
        }

        val bulletMatch = Regex("""^([\*\-])\s+(.+)""").find(lastLine)
        if (bulletMatch != null) {
            val symbol = bulletMatch.groupValues[1]
            val suffix = "$symbol "
            val resultText = newText + suffix
            return TextFieldValue(resultText, TextRange(resultText.length))
        }
    }
    return newValue
}
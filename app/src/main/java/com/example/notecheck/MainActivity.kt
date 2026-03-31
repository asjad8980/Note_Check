package com.example.notecheck


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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.UUID


// 1. DATA CLASS (The BluePrint)
// This object represents one single task.
data class TodoItem(
    val id: String = UUID.randomUUID().toString(), // Generates a unique ID
    val title: String,
    var isDone: Boolean = false // New state variable
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme { // Added a default theme wrapper
                TodoApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoApp() {
    var editingTask by remember { mutableStateOf<TodoItem?>(null) }
    var isAddingTask by remember { mutableStateOf(false) } // NEW STATE
    // NEW: State to track which task the user clicked 'Delete' on
    var taskToDelete by remember { mutableStateOf<TodoItem?>(null) }

    val todoList = remember { mutableStateListOf<TodoItem>() }
    val selectedIds = remember { mutableStateMapOf<String, Boolean>() }
    val isSelectionMode = remember { derivedStateOf { selectedIds.isNotEmpty() } }.value

    // --- NEW: SEARCH STATE ---
    var searchQuery by remember { mutableStateOf("") }

    // --- NEW: FILTERED LIST LOGIC ---
    // This automatically updates the list as you type in the search bar
    val filteredList = remember(searchQuery, todoList.size, todoList.map { it.title + it.isDone }) {
        if (searchQuery.isEmpty()) {
            todoList
        } else {
            todoList.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
    }

    // --- SCREEN NAVIGATION LOGIC ---

    // --- DELETE CONFIRMATION DIALOG ---
    if (taskToDelete != null) {
        AlertDialog(
            onDismissRequest = { taskToDelete = null }, // Close if user taps outside
            title = { Text("Delete Note") },
            //text = { Text("Are you sure you want to delete '${taskToDelete?.title}'?") },

            // We use substringBefore to only show the first line in the popup
            text = { Text("Are you sure you want to delete '${taskToDelete?.title?.substringBefore("\n")}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        todoList.remove(taskToDelete)
                        taskToDelete = null // Close dialog
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
                    // 1. ADD THE BACK/CLOSE BUTTON
                    navigationIcon = {
                        if (isSelectionMode) {
                            IconButton(onClick = { selectedIds.clear() }) {
                                Icon(Icons.Default.Close, contentDescription = "Exit Selection")
                            }
                        }
                    },
                    title = { //Text("Notes")
                        if (!isSelectionMode) {
                            // THE SEARCH BAR
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
                                selectedIds.clear()
                            }) { Icon(Icons.Default.Check, "Done") }

                            IconButton(onClick = {
                                val idsToRemove = selectedIds.keys.toList()
                                todoList.removeAll { it.id in idsToRemove }
                                selectedIds.clear()
                            }) { Icon(Icons.Default.Delete, "Delete") }
                        }
                    }
                )
            },
            // FLOATING ACTION BUTTON AT BOTTOM RIGHT
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
                            if (idx != -1) todoList[idx] = updated
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
    onEdit: () -> Unit // We still keep this parameter to tell the parent to open the edit screen
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
                        // NOW: Tapping the note opens the edit screen
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

                /*Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (task.isDone) Color.Gray else Color.Black
                )*/
                Text(
                    // Logic: Only show text before the first line break.
                    // If there is no line break, it shows the whole thing.
                    text = task.title.substringBefore("\n"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (task.isDone) Color.Gray else Color.Black,
                    maxLines = 1, // Ensures it stays on one line in the list
                    overflow = TextOverflow.Ellipsis // Adds "..." if the line is too long
                )
            }

            // Icons on the right side
            Row(verticalAlignment = Alignment.CenterVertically) {
                // TOGGLE BUTTON: Changes between Check and Close (X)
                IconButton(onClick = { onToggleDone(task.copy(isDone = !task.isDone)) }) {
                    Icon(
                        // Logic: If done, show 'Close' (X), else show 'Check'
                        imageVector = if (task.isDone) Icons.Default.Close else Icons.Default.Check,
                        contentDescription = "Toggle Done",
                        // Logic: If done, make it Red/Gray to signal "Undo", else Green/Gray
                        tint = if (task.isDone) Color.Red else Color.Gray
                    )
                }

                // Delete button
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
    // 1. Change state to TextFieldValue to track cursor position
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
                    // 2. Use .text to send the string back to the list
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
                    // 3. Call the smart list helper
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
    // Corrected state for a NEW note using TextFieldValue
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

    // Logic: Only trigger if the user typed exactly one newline character
    if (newText.length == oldText.length + 1 && newText.endsWith('\n')) {
        val lines = oldText.split("\n")
        val lastLine = lines.lastOrNull() ?: ""

        // 1. Handle Numbered List (e.g., "1. Task")
        val numberMatch = Regex("""^(\d+)\.\s+(.+)""").find(lastLine)
        if (numberMatch != null) {
            val nextNum = numberMatch.groupValues[1].toInt() + 1
            val suffix = "$nextNum. "
            val resultText = newText + suffix
            return TextFieldValue(resultText, TextRange(resultText.length))
        }

        // 2. Handle Bullet List (e.g., "* Task" or "- Task")
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

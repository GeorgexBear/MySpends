package com.example.myspends

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.myspends.data.Gasto
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

// Función de respaldo (por si es un gasto viejo sin nombre guardado)
fun formatearNombre(email: String?): String {
    if (email.isNullOrEmpty()) return "Desconocido"
    return email.substringBefore("@")
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}

fun crearArchivoImagen(context: Context): Uri {
    val archivo = File(context.cacheDir, "foto_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.provider", archivo)
}

@Composable
fun AppNavigation(viewModel: GastoViewModel) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val nombreUsuario by viewModel.nombreUsuario.collectAsState()
    val emailUsuario by viewModel.emailUsuario.collectAsState()

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(24.dp))
                Column(modifier = Modifier.padding(16.dp)) {
                    Icon(Icons.Default.Person, null, modifier = Modifier.size(64.dp), tint = Color(0xFF2196F3))
                    Spacer(Modifier.height(12.dp))
                    // Hola Nombre
                    Text(text = "Hola, $nombreUsuario", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider()
                NavigationDrawerItem(
                    label = { Text(if (nombreUsuario == "Invitado") "Conectar con Google" else "Cerrar Sesión") },
                    icon = { Icon(if (nombreUsuario == "Invitado") Icons.Default.Person else Icons.Default.Delete, null) },
                    selected = false,
                    onClick = {
                        scope.launch {
                            drawerState.close()
                            if (nombreUsuario == "Invitado") viewModel.iniciarSesionGoogle() else viewModel.cerrarSesion()
                        }
                    }
                )
            }
        }
    ) {
        NavHost(navController = navController, startDestination = "inicio") {
            composable("inicio") {
                PantallaInicio(navController, viewModel, nombreUsuario, emailUsuario) { scope.launch { drawerState.open() } }
            }
            composable("formulario/{uriFoto}") {
                val uri = it.arguments?.getString("uriFoto") ?: ""
                PantallaFormulario(navController, viewModel, uri)
            }
            composable("detalle/{gastoId}") {
                val id = it.arguments?.getString("gastoId")
                val lista by viewModel.listaGastos.collectAsState()
                val gastoEncontrado = lista.find { g -> g.id == id }
                if (gastoEncontrado != null) {
                    PantallaDetalle(navController, viewModel, gastoEncontrado, emailUsuario)
                }
            }
            composable("ver_foto/{uriFoto}") {
                val uriRaw = it.arguments?.getString("uriFoto") ?: ""
                val uri = URLDecoder.decode(uriRaw, StandardCharsets.UTF_8.toString())
                PantallaFotoCompleta(navController, uri)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaInicio(
    navController: NavController,
    viewModel: GastoViewModel,
    nombreUsuario: String,
    emailUsuario: String,
    onOpenDrawer: () -> Unit
) {
    val gastos by viewModel.listaGastos.collectAsState()
    val total by viewModel.totalGastado.collectAsState()
    val context = LocalContext.current

    var uriFotoCapturada by remember { mutableStateOf<Uri?>(null) }

    val camaraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { exito ->
        if (exito && uriFotoCapturada != null) {
            val uriString = URLEncoder.encode(uriFotoCapturada.toString(), "UTF-8")
            navController.navigate("formulario/$uriString")
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { permissionLauncher.launch(Manifest.permission.CAMERA) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MySpends") },
                navigationIcon = { IconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, null, tint = Color.White) } },
                actions = {
                    IconButton(onClick = { viewModel.descargarDatosDeNube() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF2196F3), titleContentColor = Color.White)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val uri = crearArchivoImagen(context)
                uriFotoCapturada = uri
                camaraLauncher.launch(uri)
            }) { Icon(Icons.Default.Add, null) }
        }
    ) { p ->
        Column(modifier = Modifier.padding(p).padding(16.dp)) {
            Card(colors = CardDefaults.cardColors(Color(0xFFE3F2FD)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp)) {
                    Text("Total Gastado")
                    Text(text = "$${"%.2f".format(total ?: 0.0)}", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                }
            }
            Spacer(Modifier.height(16.dp))
            LazyColumn {
                items(gastos) { g ->
                    ItemGastoMejorado(
                        gasto = g,
                        emailUsuario = emailUsuario,
                        onClickDetalle = { navController.navigate("detalle/${g.id}") },
                        onClickFoto = {
                            val rutaParaVer = g.rutaFotoLocal ?: g.foto_url
                            if (rutaParaVer != null) {
                                val encoded = URLEncoder.encode(rutaParaVer, "UTF-8")
                                navController.navigate("ver_foto/$encoded")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ItemGastoMejorado(
    gasto: Gasto,
    emailUsuario: String,
    onClickDetalle: () -> Unit,
    onClickFoto: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClickDetalle() },
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {

                val imagenAMostrar = gasto.rutaFotoLocal ?: gasto.foto_url

                if (imagenAMostrar != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(imagenAMostrar).crossfade(true).build(),
                        contentDescription = null,
                        modifier = Modifier.size(60.dp).padding(end = 10.dp).clickable { onClickFoto() },
                        contentScale = ContentScale.Crop
                    )
                }

                Column(modifier = Modifier.padding(end = 8.dp)) {
                    Text(text = gasto.descripcion, fontSize = 16.sp, maxLines = 1)

                    if (!gasto.emailCompartido.isNullOrEmpty()) {
                        if (gasto.email_creador == emailUsuario) {
                            // "Compartido con" (Aquí seguimos usando el email formateado porque el usuario solo escribe el email)
                            Text(text = "Compartido con: ${formatearNombre(gasto.emailCompartido)}", fontSize = 10.sp, color = Color(0xFF2196F3))
                        } else {
                            // "Compartido por" -> AQUÍ USAMOS EL NOMBRE REAL SI EXISTE
                            val nombreMostrar = gasto.nombreCreador ?: formatearNombre(gasto.email_creador)
                            Text(text = "Compartido por: $nombreMostrar", fontSize = 10.sp, color = Color(0xFF4CAF50))
                        }
                    }
                }
            }
            Text(
                text = "$${"%.2f".format(gasto.monto)}",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaDetalle(navController: NavController, viewModel: GastoViewModel, gasto: Gasto, emailUsuario: String) {
    var mostrarDialogoCompartir by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(topBar = { TopAppBar(title = { Text("Detalle") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) } }) }) { p ->
        Column(Modifier.padding(p).padding(16.dp)) {

            val imagenAMostrar = gasto.rutaFotoLocal ?: gasto.foto_url

            if (imagenAMostrar != null) {
                Card(Modifier.fillMaxWidth().height(250.dp).padding(bottom = 16.dp)) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imagenAMostrar)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clickable {
                            val encoded = URLEncoder.encode(imagenAMostrar, "UTF-8")
                            navController.navigate("ver_foto/$encoded")
                        },
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Text("Monto:", color = Color.Gray)
            Text(text = "$${"%.2f".format(gasto.monto)}", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))

            Spacer(Modifier.height(16.dp))
            Text("Descripción:", color = Color.Gray); Text(gasto.descripcion, fontSize = 18.sp)

            if (gasto.emailCompartido != null) {
                Spacer(Modifier.height(16.dp))
                if (gasto.email_creador == emailUsuario) {
                    Text("Compartido con:", color = Color.Gray)
                    Text(formatearNombre(gasto.emailCompartido), fontSize = 16.sp, color = Color.Blue)
                } else {
                    Text("Compartido por:", color = Color.Gray)
                    // AQUÍ TAMBIÉN USAMOS EL NOMBRE REAL
                    val nombreMostrar = gasto.nombreCreador ?: formatearNombre(gasto.email_creador)
                    Text(nombreMostrar, fontSize = 16.sp, color = Color(0xFF4CAF50))
                }
            }

            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = { mostrarDialogoCompartir = true }) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text("Compartir") }
                Button(colors = ButtonDefaults.buttonColors(Color.Red), onClick = { viewModel.eliminarGasto(gasto); navController.popBackStack() }) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(8.dp)); Text("Borrar") }
            }
        }
    }

    if (mostrarDialogoCompartir) {
        DialogoCompartir(onDismiss = { mostrarDialogoCompartir = false }) { email ->
            viewModel.compartirGasto(gasto, email)
            mostrarDialogoCompartir = false
        }
    }
}

@Composable
fun DialogoCompartir(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var email by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compartir Gasto") },
        text = { OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Correo del amigo") }, singleLine = true) },
        confirmButton = { Button(onClick = { if (email.contains("@")) onConfirm(email) }) { Text("Enviar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun PantallaFormulario(navController: NavController, viewModel: GastoViewModel, uriFoto: String) {
    var monto by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold(topBar = { TopAppBar(title = { Text("Registrar Gasto") }) }) { p ->
        Column(Modifier.padding(p).padding(16.dp)) {
            Card(Modifier.fillMaxWidth().height(200.dp)) { AsyncImage(model = uriFoto, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Spacer(Modifier.height(16.dp))

            // Monto: Tope 100M y Flecha
            OutlinedTextField(
                value = monto,
                onValueChange = {
                    if (it.all { c -> c.isDigit() || c == '.' }) {
                        val numero = it.toDoubleOrNull() ?: 0.0
                        if (numero <= 100000000.0) monto = it
                    }
                },
                label = { Text("Monto") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            // Descripción: Palomita
            OutlinedTextField(
                value = descripcion,
                onValueChange = { descripcion = it },
                label = { Text("Descripción") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))
            Button(modifier = Modifier.fillMaxWidth(), onClick = { if (monto.isNotEmpty()) { viewModel.agregarGasto(monto.toDouble(), descripcion, uriFoto); navController.popBackStack("inicio", false) } }) { Text("Guardar") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaFotoCompleta(navController: NavController, uriFoto: String) {
    val context = LocalContext.current
    Scaffold(topBar = { TopAppBar(title = { Text("Foto de gasto") }, navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) } }) }) { p ->
        Box(Modifier.padding(p).fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(uriFoto).build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}
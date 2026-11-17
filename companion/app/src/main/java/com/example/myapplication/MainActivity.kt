package com.example.myapplication

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.adapter.LinkState
import com.example.myapplication.core.DeviceKind
import com.example.myapplication.heart.*
import com.example.myapplication.ui.HeartButton
import com.example.myapplication.ui.Hairline
import com.example.myapplication.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Independent HeaRt shell: account screens plus four focused destinations. */
class MainActivity: ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            show(WindowInsetsCompat.Type.systemBars())
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContent { HeartTheme { HeartApp() } }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable private fun HeartApp() {
        val app=application as SpikeApp;val engine=app.engine;val scope=rememberCoroutineScope()
        val account by HeartApi.account.collectAsState()
        val collection by engine.collector.state.collectAsState()
        var page by rememberSaveable{mutableIntStateOf(0)}
        var message by remember{mutableStateOf("")};var working by remember{mutableStateOf(false)}
        var records by remember{mutableStateOf<List<JSONObject>>(emptyList())}
        var rows by remember{mutableStateOf<List<JSONObject>>(emptyList())};var selected by remember{mutableStateOf<String?>(null)}
        var cursor by remember{mutableStateOf<String?>(null)}
        val scans by engine.scanResults.collectAsState();val scanning by engine.scanning.collectAsState()
        val devices by engine.devices.collectAsState();val watch by engine.watchState.collectAsState();val ring by engine.ringState.collectAsState()
        val standard by engine.standardState.collectAsState()
        var chosen by rememberSaveable{mutableStateOf("WATCH")}
        var pairingCode by rememberSaveable{mutableStateOf("")}
        var confirmDelete by remember{mutableStateOf<JSONObject?>(null)}
        var label by rememberSaveable{mutableStateOf("Imported recording")};var synthetic by rememberSaveable{mutableStateOf(false)}
        var file by remember{mutableStateOf<ByteArray?>(null)};var preview by remember{mutableStateOf<JSONObject?>(null)}
        val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){result->
            if(result.values.all{it})engine.startScan()else message="Bluetooth permission is needed to find and connect your device."
        }
        fun permissions(): Array<String> = buildList {
            if(Build.VERSION.SDK_INT>=31){add(Manifest.permission.BLUETOOTH_SCAN);add(Manifest.permission.BLUETOOTH_CONNECT)}else add(Manifest.permission.ACCESS_FINE_LOCATION)
            if(Build.VERSION.SDK_INT>=33)add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
        fun scan(){val required=permissions();if(required.all{ContextCompat.checkSelfPermission(this@MainActivity,it)==PackageManager.PERMISSION_GRANTED})engine.startScan()else permission.launch(required)}
        fun loadRecordings(){scope.launch{working=true;try{records=HeartApi.call("/recordings?limit=100").getJSONArray("items").objects()}catch(e:Exception){message=e.friendly()}finally{working=false}}}
        fun loadRows(id:String,next:String?=null){scope.launch{working=true;try{val result=HeartApi.call("/readings?recording_id=$id&limit=100"+(next?.let{"&cursor="+java.net.URLEncoder.encode(it,"UTF-8")}?:""));rows=if(next==null)result.getJSONArray("items").objects()else rows+result.getJSONArray("items").objects();cursor=result.nullable("next_cursor");selected=id}catch(e:Exception){message=e.friendly()}finally{working=false}}}
        val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)scope.launch{
            working=true;message="";try{file=withContext(Dispatchers.IO){contentResolver.openInputStream(uri)!!.use{input->
                val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);var n=input.read(buffer)
                while(n>=0){require(output.size()+n<=5*1024*1024){"Choose a file no larger than 5 MiB."};output.write(buffer,0,n);n=input.read(buffer)}
                HeartCsv.canonical(output.toByteArray())
            }};message="File selected. Preview it before saving."}catch(e:Exception){file=null;message=e.friendly()}finally{working=false}
        }}
        val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")){uri->if(uri!=null)scope.launch{
            working=true;try{withContext(Dispatchers.IO){contentResolver.openOutputStream(uri)!!.use{it.write(engine.collector.store.csv(account.id))}};message="CSV exported."}catch(e:Exception){message=e.friendly()}finally{working=false}
        }}
        LaunchedEffect(page,account.id){message="";if(page==2&&account.id.isNotEmpty())loadRecordings()}
        if(account.id.isEmpty()) { AccountScreen {message=""};return }
        BackHandler(enabled = selected != null) {
            selected = null
            rows = emptyList()
            cursor = null
        }
        val labels=listOf("Devices","Live","Readings","Files")
        val icons=listOf(Icons.Outlined.Watch,Icons.Outlined.FavoriteBorder,Icons.Outlined.Timeline,Icons.Outlined.FolderOpen)
        Scaffold(containerColor=HeartColors.bg,bottomBar={NavigationBar(containerColor=HeartColors.surface){labels.forEachIndexed{i,text->
            NavigationBarItem(selected=page==i,onClick={page=i;selected=null;rows=emptyList()},icon={Icon(icons[i],text)},label={Text(text)})
        }}}) { insets->
            LazyColumn(Modifier.fillMaxSize().padding(insets),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                item {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Image(painterResource(R.drawable.heart_launcher),"HeaRt logo",Modifier.size(32.dp));Spacer(Modifier.width(10.dp))
                    Text("HeaRt",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                    TextButton(onClick={engine.collector.stop();scope.launch{try{HeartApi.signOut()}catch(e:Exception){message=e.friendly()}}}){Text("Sign out")}
                }}
                if(message.isNotEmpty())item{Text(message,color=HeartColors.accentInk,style=MaterialTheme.typography.bodyMedium)}
                if(working)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
                when(page){
                    0->{
                        item{Title("Your devices.","Connect a supported wearable to start collecting.")}
                        item{HeartButton(if(scanning)"Scanning…"else"Find devices",{scan()},enabled=!scanning,modifier=Modifier.fillMaxWidth())}
                        item{Text("ET488 watches, Q520 rings and sensors advertising the standard Bluetooth Heart Rate service. Proprietary watches need a compatible protocol adapter.",color=HeartColors.muted)}
                        devices.forEach{device->item{
                            Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                                Text(device.model,style=MaterialTheme.typography.titleMedium)
                                val state=when(device.kind){DeviceKind.WATCH->watch;DeviceKind.RING->ring;else->standard}
                                Text("${state.name.lowercase().replaceFirstChar{it.uppercase()}} · ${device.battery?.let{"$it% battery"}?:"Battery unavailable"}",color=HeartColors.muted)
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                    OutlinedButton(onClick={engine.reconnect(device.kind)}){Text("Reconnect")}
                                    TextButton(onClick={engine.collector.stop();engine.disconnect(device.kind)}){Text("Disconnect")}
                                };Hairline()
                            }
                        }}
                        scans.forEach{result->item{
                            ListItem(headlineContent={Text(result.name)},supportingContent={Text("${result.kind.name.lowercase()} · ${result.rssi} dBm")},
                                trailingContent={Button(onClick={engine.connect(result)}){Text("Connect")}})
                        }}
                        if(scans.isEmpty()&&!scanning&&devices.isEmpty())item{Text("No device connected yet. Keep your wearable nearby with Bluetooth enabled.",color=HeartColors.muted)}
                    }
                    1->{
                        item{Title("Follow your rhythm.","Collect until you press Stop. Readings save to your account.")}
                        item{FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                            listOf("WATCH" to "ET488 watch","RING" to "Q520 ring","UNKNOWN" to "Bluetooth HR").forEach{(kind,text)->FilterChip(selected=chosen==kind,onClick={if(!collection.active)chosen=kind},label={Text(text)},enabled=!collection.active)}
                        }}
                        item{Column(Modifier.fillMaxWidth().padding(vertical=28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){
                            Icon(Icons.Outlined.FavoriteBorder,"Heart rate",Modifier.size(76.dp),tint=HeartColors.accentInk)
                            Text(collection.bpm?.toString()?:"—",style=displayTextStyle(72.sp));Text("beats per minute",color=HeartColors.muted)
                            Text(collection.message,color=HeartColors.accentInk)
                            Text("${collection.captured} captured · ${collection.sent} sent",color=HeartColors.muted)
                        }}
                        if(!collection.active)item{OutlinedTextField(value=pairingCode,onValueChange={pairingCode=it.take(12)},label={Text("Web pairing code (optional)")},supportingText={Text("Leave empty to start from this phone.")},singleLine=true,modifier=Modifier.fillMaxWidth())}
                        item{HeartButton(if(collection.active)"Stop recording"else"Start recording",{if(collection.active)engine.collector.stop()else engine.collector.start(DeviceKind.valueOf(chosen),pairingCode)},modifier=Modifier.fillMaxWidth())}
                        item{Text("Background collection uses an ongoing notification. Android may stop a force-closed app; reopen and start a new recording. This is heart-rate data, not an ECG or medical diagnosis.",style=MaterialTheme.typography.bodySmall,color=HeartColors.dim)}
                    }
                    2->{
                        item{Title("Your readings.","Saved recordings from your account, including live collection.")}
                        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            OutlinedButton(onClick={selected=null;loadRecordings()}){Text("Refresh collection")}
                            TextButton(onClick={engine.collector.retry()}){Text("Retry upload")}
                        }}
                        if(selected==null){
                            if(records.isEmpty()&&!working)item{Text("No recordings yet. Start collecting or import a CSV.",color=HeartColors.muted)}
                            records.forEach{record->item{Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                                Text(record.getString("label"),style=MaterialTheme.typography.titleMedium)
                                Text("${record.getInt("count")} readings · ${record.optString("source_type")} · avg ${record.optString("average_bpm","—")} bpm",color=HeartColors.muted)
                                Row{TextButton(onClick={loadRows(record.getString("id"))}){Text("View readings")};TextButton(onClick={confirmDelete=record}){Text("Delete")}};Hairline()
                            }}}
                        }else{
                            item{TextButton(onClick={selected=null}){Text("Back to collection")}}
                            rows.forEach{row->item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(row.getString("timestamp").replace('T',' ').take(19)+" UTC",style=MaterialTheme.typography.bodySmall);Text("${row.getInt("heart_rate")} bpm")};Hairline()}}
                            if(cursor!=null)item{Button(onClick={loadRows(selected!!,cursor)},enabled=!working){Text("Load more")}}
                        }
                    }
                    3->{
                        item{Title("Bring your data.","Import a CSV or export readings collected on this phone.")}
                        item{OutlinedButton(onClick={picker.launch(arrayOf("text/*","application/octet-stream"))},enabled=!working,modifier=Modifier.fillMaxWidth()){Text(if(file==null)"Choose CSV file"else"Choose another CSV")}}
                        item{OutlinedTextField(label,{label=it.take(100)},label={Text("Recording name")},singleLine=true,modifier=Modifier.fillMaxWidth())}
                        item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){FilterChip(selected=!synthetic,onClick={synthetic=false},label={Text("Recorded")});FilterChip(selected=synthetic,onClick={synthetic=true},label={Text("Synthetic")})}}
                        item{HeartButton("Preview import",{scope.launch{working=true;try{preview=HeartApi.preview(file!!,label,synthetic)}catch(e:Exception){message=e.friendly()}finally{working=false}}},enabled=file!=null&&label.isNotBlank()&&!working,modifier=Modifier.fillMaxWidth())}
                        item{Text("Accepts timestamp,heart_rate CSV and Vigil heart-rate CSV. Native exports map ts_utc and value; keep the original file for its additional provenance. Up to 5 MiB and 10,000 rows.",color=HeartColors.muted,style=MaterialTheme.typography.bodySmall)}
                        item{Hairline();Spacer(Modifier.height(16.dp));OutlinedButton(onClick={export.launch("HeaRt-readings.csv")},enabled=!working,modifier=Modifier.fillMaxWidth()){Text("Export local readings")}}
                        item{Text("Signed in as ${account.email}",style=MaterialTheme.typography.bodySmall,color=HeartColors.dim)}
                    }
                }
            }
        }
        confirmDelete?.let{record->AlertDialog(onDismissRequest={confirmDelete=null},title={Text("Delete recording?")},text={Text("Delete ${record.getString("label")} and all its readings? This cannot be undone.")},confirmButton={TextButton(onClick={scope.launch{try{HeartApi.call("/recordings/"+record.getString("id"),"DELETE");confirmDelete=null;loadRecordings()}catch(e:Exception){message=e.friendly()}}}){Text("Delete")}},dismissButton={TextButton(onClick={confirmDelete=null}){Text("Cancel")}})}
        preview?.let{data->AlertDialog(onDismissRequest={},title={Text("Review your import")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){Text("${data.getInt("count")} validated readings");Text(data.getString("start_at")+" to "+data.getString("end_at"));Text("Source: "+data.getString("source_type"))}},confirmButton={TextButton(enabled=!working,onClick={scope.launch{working=true;try{HeartApi.call("/imports/"+data.getString("preview_id")+"/confirm","POST");preview=null;file=null;message="Recording imported.";page=2;loadRecordings()}catch(e:Exception){message=e.friendly()}finally{working=false}}}){Text("Save recording")}},dismissButton={TextButton(onClick={scope.launch{runCatching{HeartApi.call("/imports/"+data.getString("preview_id"),"DELETE")};preview=null}}){Text("Discard")}})}
    }

    @Composable private fun AccountScreen(onSignedIn:()->Unit){
        val scope=rememberCoroutineScope();var register by rememberSaveable{mutableStateOf(false)}
        var email by rememberSaveable{mutableStateOf("")};var password by remember{mutableStateOf("")}
        var server by rememberSaveable{mutableStateOf(HeartApi.server)};var show by remember{mutableStateOf(false)}
        var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf("")};var notice by remember{mutableStateOf("")}
        LazyColumn(Modifier.fillMaxSize().background(HeartColors.bg).statusBarsPadding().imePadding(),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
            item{Row(verticalAlignment=Alignment.CenterVertically){Image(painterResource(R.drawable.heart_launcher),"HeaRt logo",Modifier.size(38.dp));Spacer(Modifier.width(10.dp));Text("HeaRt",style=MaterialTheme.typography.titleLarge)}}
            item{Image(painterResource(R.drawable.watch_editorial),"Illustrative smartwatch with green strap",Modifier.fillMaxWidth().height(180.dp),contentScale=ContentScale.Crop)}
            item{Title(if(register)"Make it your own."else"Good to see you.",if(register)"Create an account to start your collection."else"Sign in to collect and explore your readings.")}
            if(notice.isNotEmpty())item{Text(notice,color=HeartColors.accentInk)}
            item{OutlinedTextField(email,{email=it},label={Text("Email address")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email),modifier=Modifier.fillMaxWidth(),enabled=!busy)}
            item{OutlinedTextField(password,{password=it},label={Text("Password")},singleLine=true,visualTransformation=if(show)VisualTransformation.None else PasswordVisualTransformation(),trailingIcon={IconButton(onClick={show=!show}){Icon(if(show)Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,if(show)"Hide password"else"Show password")}},supportingText={if(register)Text("Between 12 and 128 characters")},modifier=Modifier.fillMaxWidth(),enabled=!busy)}
            if(error.isNotEmpty())item{Text(error,color=HeartColors.coralInk)}
            item{HeartButton(if(register)"Create account"else"Sign in",{
                error="";notice=""
                if(!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches())error="Enter a valid email address."
                else if(password.isEmpty()||(register&&password.length !in 12..128))error="Use a password between 12 and 128 characters."
                else scope.launch{busy=true;try{val parsed=server.trim().trimEnd('/').toHttpUrlOrNull();require(parsed!=null&&parsed.encodedPath=="/"&&parsed.query==null&&parsed.fragment==null&&(parsed.scheme=="https"||(BuildConfig.DEBUG&&parsed.host in listOf("127.0.0.1","localhost")))){"Use an HTTPS server, or the local USB address in a debug build."};HeartApi.server=server.trim().trimEnd('/');if(register){HeartApi.register(email,password);register=false;password="";notice="Account created. Sign in to continue."}else{HeartApi.signIn(email,password);password="";onSignedIn()}}catch(e:Exception){error=e.friendly()}finally{busy=false}}
            },loading=busy,enabled=!busy,modifier=Modifier.fillMaxWidth())}
            item{TextButton(onClick={register=!register;password="";error="";notice=""}){Text(if(register)"Already have an account? Sign in"else"New here? Create an account")}}
            item{OutlinedTextField(server,{server=it},label={Text("API server address")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri),modifier=Modifier.fillMaxWidth(),enabled=!busy)}
            item{Text("Local USB: use port 8000 forwarded from your computer. Sessions are kept in memory; sign in again if Android restarts the app.",style=MaterialTheme.typography.bodySmall,color=HeartColors.dim)}
        }
    }
    @Composable private fun Title(title:String,description:String){Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text(title,style=displayTextStyle(32.sp,heading=true));Text(description,color=HeartColors.muted,style=MaterialTheme.typography.bodyMedium)}}
    private fun org.json.JSONArray.objects()=List(length()){getJSONObject(it)}
    private fun JSONObject.nullable(key:String):String?=if(isNull(key))null else optString(key).ifBlank{null}
    private fun Exception.friendly()=when(this){is java.io.IOException->"Cannot reach the server. Check the USB connection or server address.";else->message?:"Something went wrong. Please try again."}
}

// Run with pptxgenjs available on NODE_PATH. See README.md for reproduction.
const fs = require('fs');
const path = require('path');
const PptxGenJS = require('pptxgenjs');
const pptx = new PptxGenJS();
pptx.layout = 'LAYOUT_WIDE';
pptx.author = 'Ambali Ridwan Oladele';
pptx.subject = 'SP-332 coursework deliverables and HeaRt demonstration';
pptx.title = 'HeaRt — Wearable Heart-Rate Data Viewer';
pptx.lang = 'en-GB';
pptx.company = 'CSC403 Software Engineering';
pptx.theme = { headFontFace: 'Arial', bodyFontFace: 'Arial', lang: 'en-GB' };
const THEME = {
  name: 'HeaRt Crimson', headFontFace: 'Arial', bodyFontFace: 'Arial',
  colors: { dk1:'151114', lt1:'FFFFFF', dk2:'6E5D65', lt2:'F8E9ED',
    accent1:'BC2341', accent2:'FF8FA3', accent3:'245744', accent4:'CFBAC4',
    accent5:'C2361A', accent6:'33252D', hlink:'9E1C38', folHlink:'6E5D65' },
};
const C = pptx.SchemeColor;
const S = pptx.ShapeType;
const DIR = __dirname;
const requirements = JSON.parse(fs.readFileSync(path.join(DIR,'requirements.json'),'utf8'));
const assets = JSON.parse(fs.readFileSync(path.join(DIR,'assets.json'),'utf8'));
if (requirements.length !== 13 || requirements.reduce((n,r)=>n+r.marks,0)!==100) throw Error('Workbook total changed');

for (const dark of [false,true]) {
  const color = dark ? C.background1 : C.text1;
  const muted = dark ? C.accent4 : C.text2;
  pptx.defineSlideMaster({
    title: dark ? 'DARK' : 'LIGHT',
    background:{ color:dark ? C.text1 : C.background1 },
    margin:[0.55,0.65,0.55,0.65],
    objects:[
      {placeholder:{options:{ name:'title',type:'title',x:0.65,y:0.5,w:12.0,h:0.8,
        fontSize:36,bold:true,color,margin:0,breakLine:false,align:'left' }}},
      {text:{text:'HeaRt  /  CSC403  /  Ambali Ridwan Oladele',options:{
        x:0.65,y:7.05,w:10,h:0.18,fontSize:10,color:muted,margin:0,isTextBox:true}}},
    ],
    slideNumber:{ x:12.0,y:7.0,w:0.65,h:0.25,color:muted,fontSize:10,align:'right',margin:0 },
  });
}
let section = '';
function startSection(name) { section=name; pptx.addSection({title:name}); }
function slide(title,dark=false,notes='') {
  const s=pptx.addSlide({masterName:dark?'DARK':'LIGHT',sectionTitle:section});
  s.addText(title,{placeholder:'title'});
  if(notes) s.addNotes(notes);
  return s;
}
function text(s,value,x,y,w,h,options={}) {
  s.addText(value,{x,y,w,h,fontSize:20,color:C.text1,margin:0,breakLine:false,
    valign:'mid',isTextBox:true,objectName:value.slice(0,45),...options});
}
function caption(s,value,x,y,w,dark=false) {
  text(s,value,x,y,w,0.4,{fontSize:12,color:dark?C.accent4:C.text2,valign:'top'});
}
function box(s,label,detail,x,y,w,h,dark=false) {
  s.addShape(S.roundRect,{x,y,w,h,rectRadius:0.13,radius:0.13,
    fill:{color:dark?C.accent6:C.background2},line:{color:dark?C.accent6:C.background2},
    objectName:label+' container'});
  text(s,label,x+0.22,y+0.2,w-0.44,0.4,{fontSize:22,bold:true,color:dark?C.background1:C.text1});
  text(s,detail,x+0.22,y+0.75,w-0.44,h-0.9,{fontSize:18,color:dark?C.accent4:C.text2,valign:'top'});
}
function arrow(s,x,y,w,h=0,dark=false) {
  s.addShape(S.line,{x,y,w,h,line:{color:dark?C.accent2:C.accent1,width:2,beginArrowType:'none',endArrowType:'triangle'},objectName:'Data flow'});
}
function image(s,name,x,y,w,h) {
  const a=assets[name];const ratio=a.width/a.height;
  const iw=Math.min(w,h*ratio),ih=iw/ratio;
  s.addImage({path:path.join(DIR,'assets',name),x:x+(w-iw)/2,y:y+(h-ih)/2,w:iw,h:ih,
    altText:name.replace(/[-.]/g,' '),objectName:name});
}
function numbered(s,n,label,detail,x,y,w,dark=false) {
  text(s,String(n),x,y,0.6,0.6,{fontSize:32,bold:true,color:dark?C.accent2:C.accent1});
  text(s,label,x+0.85,y,w-0.85,0.45,{fontSize:22,bold:true,color:dark?C.background1:C.text1});
  text(s,detail,x+0.85,y+0.58,w-0.85,0.7,{fontSize:18,color:dark?C.accent4:C.text2,valign:'top'});
}

startSection('The assignment');
{
  const s=slide('HeaRt',true,'Introduce yourself and the SP-332 project. This presentation distinguishes the coursework requirements, delivered software and evidence still to submit. The final section uses actual captured screens, then hands over to the working application.');
  text(s,'Wearable Heart-Rate\nData Viewer',0.7,1.65,7.2,1.7,{fontSize:42,bold:true,color:C.background1});
  text(s,'From recorded readings to a watch-connected companion',0.7,3.65,6.5,0.9,{fontSize:22,color:C.accent4});
  text(s,'Ambali Ridwan Oladele\nU23CS1019\nCSC403 Software Engineering',0.7,5.15,6.5,1.15,{fontSize:19,color:C.background1,breakLine:false});
  image(s,'heart.png',8.2,1.1,4.2,5.5);
}
{
  const s=slide('The assignment',false,'Source: SE_Personal_Projects_13_Weeks.xlsx, Project Topics row SP-332 and 13-Week Plan. The stack is marked suggested/optional in the workbook; this project follows it. The workbook does not explicitly require continuous real-time heart-rate acquisition. That is an added project increment.');
  text(s,'SP-332',0.7,1.65,2.5,0.7,{fontSize:42,color:C.accent1,bold:true});
  text(s,'IoT & Smart Systems\nIntermediate',0.7,2.55,3.25,1.0,{fontSize:22});
  box(s,'13 weeks · 100 marks','The assessment covers the engineering process as well as the working application.',4.6,1.65,7.95,1.85);
  text(s,'Python / FastAPI + PostgreSQL',4.85,3.95,7.3,0.55,{fontSize:25,bold:true});
  text(s,'Workbook thresholds',0.7,4.35,3.5,0.5,{fontSize:22,bold:true});
  text(s,'10+ functional requirements\n5+ non-functional requirements\n8+ user stories with acceptance criteria',0.7,5.0,5.65,1.3,{fontSize:18});
  text(s,'2+ design patterns\nUnit and integration testing\nCoverage target: at least 60%',7.1,5.0,5.4,1.3,{fontSize:18});
  caption(s,'Source: workbook, Project Topics and 13-Week Plan',0.7,6.6,11.5);
}
for (const [a,b,label] of [[1,4,'Planning and requirements'],[5,8,'Design and prototype'],[9,13,'Quality and final submission']]) {
  const list=requirements.filter(r=>r.week>=a&&r.week<=b);
  const s=slide(`What was asked: weeks ${a}–${b}`,false,
    'Exact deliverable wording and marks are copied from the SP-332 rows in the workbook Weekly Tasks sheet. These are requirements, not completion claims. Cross-reference docs/13-deliverables-tracker.md and docs/00-workbook-requirements.md. '+label+'.');
  const header=['Week','Exact deliverable','Marks'].map(v=>({text:v,options:{bold:true,fill:C.background2,color:C.text1}}));
  s.addTable([header,...list.map(r=>[String(r.week),r.deliverable,String(r.marks)])],{
    x:0.7,y:1.65,w:11.95,colW:[1.1,9.75,1.1],rowH:a===9?0.79:0.94,
    fontSize:18,color:C.text1,margin:[0.13,0.15,0.13,0.15],
    border:{type:'solid',color:C.background2,pt:0.7},fill:C.background1,
    valign:'middle',autoPage:false,objectName:`Workbook weeks ${a} to ${b}`,
  });
  caption(s,'Source: SE_Personal_Projects_13_Weeks.xlsx · Weekly Tasks · SP-332',0.7,6.65,11.9);
}

startSection('The delivered system');
{
  const s=slide('Two ways to bring readings in',false,'The original implementation scope was CSV-first. The Android live companion is a later extension, not an invented workbook requirement. Both paths store timestamped heart-rate readings under the signed-in account. Recorded versus synthetic source must remain visible. One-shot local reads are not automatically saved to the account.');
  box(s,'Recorded data','CSV → validate → preview → confirm\n\nBrowse recordings, filter by time, inspect the chart and exact rows.',0.7,1.8,5.7,3.65);
  box(s,'Watch collection','Watch → Bluetooth → HeaRt → API\n\nRead once locally, or explicitly start a saved recording and stop it when finished.',6.9,1.8,5.7,3.65);
  text(s,'The same account and stored readings connect the web and Android views',0.85,5.95,11.7,0.7,{fontSize:23,bold:true});
}
{
  const s=slide('How the system connects',false,'Implementation: companion SyncEngine/HeartMonitor/HeartCollector and HeartStore; backend routes/services/repositories; PostgreSQL metadata in backend/app/models/tables.py. Only the API receives database credentials. BLE compatibility depends on the protocol adapter: ET488/Veepoo, Q520/Yucheng, or standard Heart Rate service. Q520 hardware was not verified in this project run.');
  box(s,'Web viewer','React + Astryx',0.7,1.85,2.45,1.65);
  box(s,'FastAPI','Accounts · imports\nrecordings · live control',4.0,1.85,3.35,1.65);
  box(s,'PostgreSQL','Owners · recordings\ntimestamped BPM rows',8.2,1.85,4.35,1.65);
  arrow(s,3.25,2.65,0.65);arrow(s,7.45,2.65,0.65);
  box(s,'Wearable','ET488\nVeepoo SDK',0.7,4.45,2.45,1.65);
  box(s,'HeaRt Android','Kotlin + Compose\nBluetooth adapters',4.0,4.45,3.35,1.65);
  box(s,'Local outbox','SQLite before upload',8.2,4.45,4.35,1.65);
  arrow(s,3.25,5.25,0.65);arrow(s,7.45,5.25,0.65);
  s.addShape(S.line,{x:5.65,y:3.65,w:0,h:0.6,line:{color:C.accent1,width:2,beginArrowType:'triangle',endArrowType:'none'},objectName:'Phone uploads to API'});
  caption(s,'Web viewer uses the same FastAPI API; diagram shows logical dependencies, not a hosted deployment',0.7,6.55,11.9);
}
{
  const s=slide('What makes a reading durable',false,'The canonical CSV has timestamp and heart_rate. Server models store measured_at and bpm. recording_sessions.user_id determines ownership; heart_rate_readings.recording_id links exact rows. Stable sample IDs/local outbox and server acknowledgement avoid losing collected samples during short network outages. This is not a clinical monitor.');
  const names=['Validate','Capture','Persist','View again'];
  const descriptions=['Timestamp + integer BPM\nRecorded/synthetic source','CSV preview confirmed\nor explicit saved recording','Owner-scoped PostgreSQL\nNative local outbox','Refresh the dashboard\nOpen the exact rows'];
  names.forEach((name,i)=>{box(s,name,descriptions[i],0.7+i*3.12,2.0,2.7,2.35);if(i<3)arrow(s,3.46+i*3.12,3.13,0.3);});
  text(s,'Passwords are hashed. Account access, CSRF checks and recording ownership stay in the API.',0.85,5.0,11.7,1.0,{fontSize:24,bold:true});
  caption(s,'Source: backend models/auth/import repositories; companion HeartStore and HeartCollector',0.7,6.45,11.9);
}
{
  const s=slide('Verification and remaining evidence',false,'Evidence is not all from the same build. The 44 backend and five browser tests are historical results documented in docs/19-heart-companion.md, not rerun on November 22. The 11 Android JVM tests and offline debug build were rerun for the latest UI/device-control changes. Physical ET488 evidence: 110 samples over about 174 seconds, saved and visible through the API. The latest manual-stop hardware behavior still needs a connected-watch retest. No coverage, CI, metrics or peer-assessment result is invented.');
  const counts=[['44','Backend tests','Earlier integration run; separate test database'],['5','Browser tests','Earlier account/import/view/live checks'],['11','Android tests','Latest CSV, decoding and monitor checks']];
  counts.forEach((r,i)=>{const x=.75+i*4.15;text(s,r[0],x,1.7,3.3,0.85,{fontSize:56,bold:true,color:C.accent1});text(s,r[1],x,2.75,3.5,0.4,{fontSize:22,bold:true});text(s,r[2],x,3.35,3.45,1.0,{fontSize:18,color:C.text2,valign:'top'});});
  box(s,'Still to attach before final submission','Measured coverage (target ≥60%), CI/build scan evidence, cloud deployment URL, metrics, final report/reflection and peer assessment.',0.7,4.95,11.9,1.45);
  caption(s,'Test counts and historical hardware evidence: docs/19-heart-companion.md',0.7,6.6,11.9);
}
{
  const s=slide('A change driven by the actual watch',false,'The user found that automatically starting the detector on connection could leave the watch on its measuring screen. This is a real maintenance/change-request example for Week 12. Changes reach the UI, monitor, adapter cancellation cleanup and service/disconnect lifecycle. Six monitor tests cover the software behavior; physical sensor release remains to be retested on the connected ET488.');
  box(s,'Initial behavior','Connect the watch\n→ automatically repeat measurements\n\nReported issue: watch stayed on the measuring screen.',0.7,1.85,5.45,3.45);
  box(s,'Current behavior','Connect = idle\nRead heart rate = one measurement\nSave a recording = explicit continuous capture\nStop / timeout = sensor cleanup',7.0,1.85,5.6,3.45);
  arrow(s,6.3,3.4,0.55);
  text(s,'The last BPM stays visible with its age; it does not flash back to a dash',0.85,5.85,11.7,0.8,{fontSize:24,bold:true});
}

startSection('Demonstration');
{
  const s=slide('The free hosted-demo plan',false,'This is the proposed topology, not a deployed cloud service. Pricing checked November 22, 2026: Render Free web services sleep after 15 idle minutes and grant 750 instance-hours/workspace/month; Neon Free includes 1 GB and 100 CU-hours/project/month and scales to zero. Sources: https://render.com/docs/free and https://neon.com/pricing. See docs/20-demo-hosting.md for commands, secrets, direct Neon connection, TablePlus and same-origin browser proxy requirements.');
  box(s,'Demo clients','Phone uses the HTTPS API URL\nLaptop browser uses its /api proxy',0.7,1.9,3.45,2.0);
  box(s,'Render Free','FastAPI · one worker\nHTTPS public endpoint',4.95,1.9,3.2,2.0);
  box(s,'Neon Free','PostgreSQL\nDirect SSL connection',8.95,1.9,3.65,2.0);
  arrow(s,4.25,2.9,0.6);arrow(s,8.25,2.9,0.6);
  text(s,'Warm it up before the talk',0.85,4.65,6.1,0.55,{fontSize:27,bold:true});
  text(s,'Check /health and /ready. Keep the local Docker setup and a labelled CSV ready.',0.85,5.45,6.1,0.85,{fontSize:20,color:C.text2});
  text(s,'Cloud deployment pending',8.15,4.85,4.3,0.5,{fontSize:23,bold:true,color:C.accent1});
  text(s,'A restart ends active live control; uploaded database rows remain.',8.15,5.55,4.3,0.85,{fontSize:18,color:C.text2});
  caption(s,'Sources: render.com/docs/free · neon.com/pricing · docs/20-demo-hosting.md',0.7,6.6,11.9);
}
{
  const s=slide('The live demonstration',false,'Suggested sequence: begin with the dependable CSV-first core, then demonstrate the real watch extension. Use labelled synthetic fixtures for the import case. The wearer must have good skin contact. Do not present screenshots or replayed CSV as live measurements. If BLE fails, show the saved recording, explain the hardware limit and continue the stored-data demonstration. Rehearse this build before the assessment.');
  numbered(s,1,'Import and inspect','Sign in → CSV preview → confirm → chart and exact rows',0.8,1.7,11.6);
  numbered(s,2,'Read from the watch','Connect ET488 → Read heart rate → result → idle sensor',0.8,3.15,11.6);
  numbered(s,3,'Save and prove persistence','Start recording → Stop → open stored session → check database rows',0.8,4.6,11.6);
  caption(s,'If Bluetooth is unavailable, demonstrate the clearly labelled recorded-data path',0.8,6.4,11.6);
}
{
  const s=slide('Web viewer: import and inspect',false,'Actual earlier browser captures. The dashboard screenshot uses the synthetic 72/75/78 BPM fixture, not a live sensor feed. The web viewer keeps its forest-green identity; the Android companion has the newer crimson identity. The source images are copied without altering application content.');
  image(s,'web-dashboard.png',0.7,1.65,5.3,4.9);
  image(s,'web-import.png',6.45,1.65,6.15,4.9);
  caption(s,'Dashboard · synthetic fixture with exact rows',0.7,6.6,5.3);
  caption(s,'CSV review · validate before saving',6.45,6.6,6.15);
}
{
  const s=slide('Web viewer: the live entry point',false,'Actual earlier browser capture of the live page in its waiting state. This is a screenshot, not evidence of a current active stream. Earlier HeaRt acquisition evidence is documented separately: 110 samples saved over about 174 seconds. During the demo, switch to the real web page and demonstrate a new recording. The current Android equivalent is Home → Save a recording.');
  image(s,'web-live.png',0.7,1.55,8.0,5.1);
  text(s,'Connect the phone',9.1,2.0,3.5,0.65,{fontSize:26,bold:true});
  text(s,'Start a saved session from HeaRt, or enter the web pairing code on the phone.',9.1,3.0,3.5,1.5,{fontSize:20,color:C.text2,valign:'top'});
  text(s,'Stop, then reopen the recording to show that samples were saved.',9.1,5.0,3.5,1.0,{fontSize:20,color:C.text2,valign:'top'});
  caption(s,'Earlier browser capture · waiting state',0.7,6.65,8.0);
}
{
  const s=slide('HeaRt on Android',true,'Actual Samsung phone captures from November 22, 2026. Home is disconnected; sign-in and device screens are real UI states. Do not imply a reading is currently arriving. The sun/moon switch persists appearance locally. This independent package uses its own storage and does not rely on running Vigil.');
  const shots=[['android-home-dark.png','Home'],['android-account-dark.png','Account'],['android-devices-dark.png','Devices']];
  shots.forEach(([name,label],i)=>{const x=1.0+i*4.2;image(s,name,x,1.45,2.65,5.2);caption(s,label,x,6.7,2.65,true);});
}
{
  const s=slide('Switch to the live app',true,'End of slides. Open the working web viewer and mirror/open HeaRt. Show the same account on both, demonstrate the CSV flow, then an actual manual watch read and saved recording if the watch connects. The left image is an actual captured in-progress read (no BPM received yet), not an animation or successful result. The right image is the real disconnected dark Home. Refer to the hosting guide for preflight checks and the recording recovery path.');
  image(s,'android-home-light.png',0.7,1.45,2.4,5.2);
  image(s,'android-home-dark.png',3.55,1.45,2.4,5.2);
  text(s,'Import. Read. Save.',7.0,2.2,5.4,0.8,{fontSize:36,bold:true,color:C.background1});
  text(s,'Show the data moving through the application, then reopen the stored recording.',7.0,3.6,5.1,1.2,{fontSize:24,color:C.accent4});
  text(s,'Live demonstration',7.0,5.55,5.1,0.5,{fontSize:24,color:C.accent2,bold:true});
  caption(s,'Captured phone states · light and dark modes',0.7,6.7,5.6,true);
}

(async()=>{
  const output=path.join(DIR,'HeaRt-Project-Demonstration.pptx');
  await pptx.writeFile({fileName:output});
  // Supplied skill utility keeps all editable scheme colors tied to one theme.
  const skillRoot=process.env.PPTX_SKILL_ROOT;
  if(!skillRoot) throw Error('Set PPTX_SKILL_ROOT to the installed pptx skill directory');
  const {applyTheme}=require(path.join(skillRoot,'scripts','apply_theme.js'));
  await applyTheme(output,THEME);
  console.log(output);
})().catch(error=>{console.error(error);process.exitCode=1;});

// Run with pptxgenjs available on NODE_PATH. See README.md for reproduction.
const fs = require('fs');
const path = require('path');
const PptxGenJS = require('pptxgenjs');
const pptx = new PptxGenJS();
pptx.layout = 'LAYOUT_WIDE';
pptx.author = 'Ambali Ridwan Oladele';
pptx.subject = 'HeaRt requirements, UML, architecture and Android demonstration';
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
const assets = JSON.parse(fs.readFileSync(path.join(DIR,'assets.json'),'utf8'));

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

function umlClass(s,name,fields,x,y,w,h) {
  s.addShape(S.rect,{x,y,w,h,fill:{color:C.background1},line:{color:C.text2,width:1},objectName:name+' class'});
  text(s,name,x+.13,y+.1,w-.26,.35,{fontSize:18,bold:true});
  s.addShape(S.line,{x,y:y+.58,w,h:0,line:{color:C.text2,width:.8},objectName:name+' compartment'});
  fields.forEach((v,i)=>text(s,v,x+.13,y+.76+i*.34,w-.26,.32,{fontSize:16,color:C.text2}));
}
function association(s,x1,y1,x2,y2) {
  s.addShape(S.line,{x:Math.min(x1,x2),y:Math.min(y1,y2),w:Math.abs(x2-x1),h:Math.abs(y2-y1),flipV:(x2-x1)*(y2-y1)<0,line:{color:C.text2,width:1.2},objectName:'UML association'});
}
function message(s,x1,x2,y,label) {
  s.addShape(S.line,{x:Math.min(x1,x2),y,w:Math.abs(x2-x1),h:0,line:{color:C.accent1,width:1.5,beginArrowType:x2<x1?'triangle':'none',endArrowType:x2>x1?'triangle':'none'},objectName:label});
  text(s,label,Math.min(x1,x2)+.08,y-.3,Math.abs(x2-x1)-.16,.27,{fontSize:16,align:'center'});
}
function smallNode(s,label,x,y,w,h) {
  s.addShape(S.roundRect,{x,y,w,h,rectRadius:.1,fill:{color:C.background2},line:{color:C.background2},objectName:label});
  text(s,label,x+.12,y+.1,w-.24,h-.2,{fontSize:18,align:'center'});
}
function diamond(s,label,x,y,w,h) {
  s.addShape(S.diamond,{x,y,w,h,fill:{color:C.background2},line:{color:C.accent1,width:1},objectName:label+' decision'});
  text(s,label,x+.12,y+.2,w-.24,h-.4,{fontSize:16,align:'center'});
}

startSection('Proposal and requirements');
{
  const s=slide('HeaRt',true,'Ambali Ridwan Oladele, U23CS1019, CSC403 Software Engineering. Present the actual analysis and design established in documents 01–13, then the independent Android application. The original CSV-first baseline was extended with Android/watch acquisition; the presentation demonstrates Android only.');
  text(s,'Wearable Heart-Rate\nData Viewer',0.7,1.65,7.2,1.7,{fontSize:42,bold:true,color:C.background1});
  text(s,'Project design and Android demonstration',0.7,3.8,7.0,0.6,{fontSize:24,color:C.accent4});
  text(s,'Ambali Ridwan Oladele\nU23CS1019\nCSC403 Software Engineering',0.7,5.1,6.8,1.2,{fontSize:19,color:C.background1});
  image(s,'heart.png',8.2,1.15,4.2,5.4);
}
{
  const s=slide('The problem and our solution',false,'Source: docs/01-project-proposal.md. The original problem was exploring timestamped heart-rate files. The approved later increment adds an independent Android client and supported-watch acquisition, documented in docs/18 and 19. This is a data-viewing project, not a diagnostic or emergency-monitoring system.');
  text(s,'Individual measurements need context',0.8,1.7,10.9,0.65,{fontSize:30,bold:true});
  text(s,'A file or a single watch result does not organise readings into a useful, private history.',0.8,2.7,9.0,1.0,{fontSize:24,color:C.text2});
  box(s,'HeaRt','Import supported CSV files, collect from a compatible wearable, and keep timestamped recordings under the correct account.',0.8,4.15,8.7,1.8);
  image(s,'heart.png',10.0,3.9,2.2,2.3);
  caption(s,'Design basis: 01 Project proposal · current Android extension: 18–19',0.8,6.55,11.7);
}
{
  const s=slide('Users and scope',false,'Sources: docs/01 and 03. Stakeholder roles are established from the workbook and student discussion. External user interviews, lecturer validation and peer assessment are not claimed. Fifteen functional requirements, ten non-functional requirements and twelve stories were defined in the SRS.');
  box(s,'Wearable owner','Private account, import preview, saved history and supported-watch measurements.',0.7,1.8,5.65,2.0);
  box(s,'Student and assessor','Repeatable demonstrations, labelled sample data, visible implementation and test evidence.',6.95,1.8,5.65,2.0);
  text(s,'Included',0.85,4.4,5.3,0.5,{fontSize:25,bold:true});
  text(s,'Accounts · CSV validation · recordings\nAndroid acquisition · local CSV recovery',0.85,5.2,5.3,1.0,{fontSize:20});
  text(s,'Outside this system',7.1,4.4,5.3,0.5,{fontSize:25,bold:true});
  text(s,'Diagnosis · emergency alerts · ECG\nUniversal support for proprietary watches',7.1,5.2,5.3,1.0,{fontSize:20});
}
{
  const s=slide('Incremental development',false,'Sources: docs/02-process-model.md and docs/04-project-plan.md. Incremental was chosen to deliver a usable core before exploration and quality work. Full Scrum roles would be disproportionate for a personal project. The 134 hours plus 20 contingency are the original estimate, not measured effort; eight contingency hours were provisionally reserved for React/Astryx. Android acquisition was a separately approved scope extension.');
  const steps=[['Core','Accounts, validation, preview and atomic import'],['Exploration','History, summaries, exact readings and recording management'],['Extension','Independent Android client, watch adapters and local outbox']];
  steps.forEach((r,i)=>{box(s,r[0],r[1],0.7+i*4.15,1.9,3.65,2.5);if(i<2)arrow(s,4.45+i*4.15,3.1,.3);});
  text(s,'Select → design → implement → test → review',0.85,5.1,11.7,.65,{fontSize:28,bold:true});
  caption(s,'Original planning allowance: 134 hours + 20 hours contingency. Actual effort was not recorded.',0.85,6.35,11.7);
}
{
  const s=slide('Priorities and project risks',false,'Source: docs/04-project-plan.md. B-01 through B-10 form the Must baseline. Usability refinements were Should, dashboard preferences Could, and live/Bluetooth was initially Won’t in v1. The user subsequently approved live Android acquisition. Risks shown here are actual entries in the register; their mitigation is a design response, not proof that every scenario has passed.');
  text(s,'MoSCoW baseline',0.8,1.7,5.5,.5,{fontSize:26,bold:true});
  text(s,'Must: accounts, safe import, history, summaries, management\n\nShould: refinements after feedback\n\nCould: dashboard preferences',0.8,2.5,5.3,2.75,{fontSize:20,valign:'top'});
  text(s,'Risks we designed around',7.0,1.7,5.5,.5,{fontSize:26,bold:true});
  text(s,'Vendor exports differ → inspect and convert verified formats\n\nCross-user access → owner-scoped queries and negative tests\n\nDuplicate/partial saves → constraints and transactions',7.0,2.5,5.5,3.4,{fontSize:20,valign:'top'});
  caption(s,'Scope change: live acquisition moved from deferred work into the approved Android increment',0.8,6.45,11.7);
}
{
  const s=slide('Functional requirements',false,'Source: docs/03-srs-and-user-stories.md, FR-01 through FR-15. These concise labels preserve the requirement IDs; full acceptance conditions remain in the SRS. Charts/date filters belong to the original viewer/API scope; the Android demonstration shows native exact readings, import and acquisition, and does not claim a native chart screen.');
  const groups=[['Account and import',['FR-01  Register a unique account','FR-02  Sign in and sign out','FR-03  Isolate each owner’s data','FR-04  Upload CSV for preview','FR-05  Explain validation errors']],['Store and explore',['FR-06  Confirm atomically','FR-07  List recording sessions','FR-08  Chart + exact readings','FR-09  Filter by UTC dates','FR-10  Count, min, max and mean']],['Manage and recover',['FR-11  Rename owned recordings','FR-12  Confirm deletion','FR-13  Identify synthetic data','FR-14  Show actionable states','FR-15  Explain the CSV format']]];
  groups.forEach((g,i)=>{const x=.75+i*4.18;text(s,g[0],x,1.8,3.85,.6,{fontSize:23,bold:true,color:C.accent1});g[1].forEach((v,j)=>text(s,v,x,2.8+j*.59,3.85,.48,{fontSize:18}));});
  caption(s,'SRS baseline: 15 functional requirements. Android/watch features extend that baseline.',0.75,6.55,11.85);
}
{
  const s=slide('Quality requirements',false,'Source: docs/03, NFR-01 through NFR-10. Performance figures and coverage are targets rather than measured results. NFR-03 proposes p95 below two seconds for filtered reads and below ten seconds for a 10,000-row preview on a stated local baseline. No performance pass or coverage percentage is fabricated.');
  const rows=[['Security and integrity','NFR-01  Password hashing, ownership and CSRF\nNFR-02  All-or-nothing transactions'],['Performance and access','NFR-03  Bounded reads and measured response targets\nNFR-04  Labels, focus, clear errors and readable alternatives'],['Maintainability and tests','NFR-05  Separate routes, services and persistence\nNFR-06  Target ≥60% Python line coverage'],['Privacy and setup','NFR-07  Keep secrets and raw uploads out of logs\nNFR-08  Pinned dependencies and repeatable migrations'],['Limits and time','NFR-09  5 MiB / 10,000-row import limits\nNFR-10  Offset-aware timestamps stored as UTC']];
  rows.forEach((r,i)=>{const y=1.65+i*.94;text(s,r[0],.8,y,3.7,.65,{fontSize:21,bold:true});text(s,r[1],4.75,y,7.8,.72,{fontSize:18,color:C.text2});});
}
{
  const s=slide('User stories with acceptance criteria',false,'Source: docs/03, twelve defined user stories. Four representative stories are shown with their actual given/when/then logic. US-02 tests account isolation; US-03 prevents invalid saves; US-04 requires idempotency; US-10 requires confirmed deletion and safe cancel. The remaining eight stories cover access, history, charts, filtering, summaries, rename, source labels and expired-preview recovery.');
  numbered(s,'02','Keep my recordings private','Given accounts A and B, B requesting A’s data receives 404 and changes nothing.',.8,1.65,11.7);
  numbered(s,'03','Explain mistakes before saving','Given a bad timestamp or BPM, preview reports record-level reasons and saves no recording.',.8,2.95,11.7);
  numbered(s,'04','Confirm without duplicate saves','Given a valid preview, confirming twice returns the same single recording.',.8,4.25,11.7);
  numbered(s,'10','Remove only after confirmation','Confirming deletes the recording and readings; cancelling preserves both.',.8,5.55,11.7);
}

startSection('UML and architecture');
{
  const s=slide('UML: use-case model',false,'Source: docs/05-uml-models.md. Actor: registered wearable owner. UC-01 account access; UC-02 import; UC-03 explore; UC-04 manage. Preview includes validation. This is the core data-viewer model adapted to a mobile client; it does not add an admin or clinical actor. Current manual watch reading is a later Android extension.');
  s.addShape(S.ellipse,{x:1.1,y:2.45,w:.5,h:.5,line:{color:C.text1,width:2},fill:{color:C.background1},objectName:'Wearable owner head'});
  for(const [x,y,w,h] of [[1.35,2.95,0,.9],[.95,3.25,.8,0]]) association(s,x,y,x+w,y+h);
  association(s,1.35,3.85,.95,4.3);association(s,1.35,3.85,1.75,4.3);
  text(s,'Wearable\nowner',.55,4.55,1.65,.8,{fontSize:19,align:'center'});
  s.addShape(S.rect,{x:3.7,y:1.55,w:8.85,h:4.95,line:{color:C.text2,width:1},fill:{color:C.background1},objectName:'HeaRt system boundary'});
  text(s,'HeaRt core',4.0,1.68,7.9,.4,{fontSize:20,bold:true});
  const cases=['Account access','Import CSV: preview / confirm','Explore recordings','Rename / delete recording'];
  cases.forEach((name,i)=>{
    const y=2.2+i*1.02;association(s,1.9,3.6,5.1,y+.4);
    s.addShape(S.ellipse,{x:5.1,y,w:6.2,h:.8,line:{color:C.accent1,width:1},fill:{color:C.background2},objectName:name});
    text(s,name,5.3,y+.1,5.8,.6,{fontSize:19,align:'center'});
  });
  caption(s,'Import includes CSV validation. The actor can access only owned previews and recordings.',.75,6.65,11.8);
}
{
  const s=slide('UML: domain class model',false,'Source: docs/05 class diagram. This slide shows its five domain classes, while parser/service/repository dependencies are presented separately under design patterns. User owns sessions, previews and recordings; a recording contains readings; a preview can resolve to one recording. Class names and attributes use the design model naming.');
  umlClass(s,'User',['UUID id','string email','string passwordHash'],.7,1.65,2.6,1.95);
  umlClass(s,'RecordingSession',['UUID id · string label','string sourceType','string fileDigest'],4.7,1.65,3.2,1.95);
  umlClass(s,'HeartRateReading',['long id','datetime measuredAt','int bpm'],9.25,1.65,3.3,1.95);
  umlClass(s,'AccountSession',['string tokenDigest','datetime expiresAt'],.7,4.7,2.9,1.55);
  umlClass(s,'ImportPreview',['UUID id · string status','string fileDigest','datetime expiresAt'],4.7,4.5,3.2,1.95);
  arrow(s,3.4,2.6,1.2);caption(s,'1 owns 0..*',3.4,2.15,1.3);
  s.addShape(S.line,{x:7.9,y:2.6,w:1.35,h:0,line:{color:C.accent1,width:2,beginArrowType:'diamond',endArrowType:'none'},objectName:'Recording composition of readings'});
  caption(s,'1 contains 1..*',7.95,2.0,1.4);
  arrow(s,2.0,3.7,0,.8);caption(s,'1 / 0..*',.8,4.05,1.1);
  arrow(s,3.3,3.6,1.4,.9);caption(s,'owns',3.55,4.2,.9);
  s.addShape(S.line,{x:6.25,y:3.75,w:0,h:.6,line:{color:C.accent1,width:2,beginArrowType:'triangle',endArrowType:'none'},objectName:'Preview resolves to recording'});
  text(s,'A preview resolves to zero or one recording; retries reuse the committed result.',8.65,4.7,3.9,1.0,{fontSize:20,color:C.text2});
}
{
  const s=slide('UML: import sequence',false,'Source: docs/05 sequence diagram, with Android replacing the generic client. The API contains route/service/parser layers. Invalid CSV returns 422 without a recording. Confirmation locks the owned preview and commits recording plus all readings atomically; repeat confirmation resolves the existing recording. Expired/inaccessible previews are handled explicitly.');
  const xs=[1.5,4.3,7.65,11.2];const labels=['Owner','Android','FastAPI service','PostgreSQL'];
  labels.forEach((v,i)=>{text(s,v,xs[i]-1.1,1.6,2.2,.5,{fontSize:20,bold:true,align:'center'});s.addShape(S.line,{x:xs[i],y:2.25,w:0,h:4.15,line:{color:C.text2,width:1,dashType:'dash'},objectName:v+' lifeline'});});
  message(s,xs[0],xs[1],2.5,'Choose CSV');
  message(s,xs[1],xs[2],3.0,'POST preview + CSRF');
  message(s,xs[2],xs[3],3.5,'Validate; store preview');
  message(s,xs[2],xs[1],4.0,'Preview ID + summary');
  message(s,xs[0],xs[1],4.5,'Confirm');
  message(s,xs[1],xs[2],5.0,'POST confirmation');
  message(s,xs[2],xs[3],5.5,'Lock + commit all rows');
  message(s,xs[2],xs[1],6.0,'Recording ID / existing result');
  caption(s,'Invalid file: no recording. Duplicate confirmation: return the existing owned recording.',.75,6.6,11.8);
}
{
  const s=slide('UML: import activity',false,'Source: docs/05 activity diagram. All-or-nothing validation, explicit confirmation, ownership/expiry checks and transaction handling define the flow. The main success path leads to saved history. Invalid input returns to file selection; cancellation discards the candidate.');
  smallNode(s,'Choose CSV',.7,2.0,2.25,.8);smallNode(s,'Validate',3.75,2.0,2.25,.8);
  diamond(s,'Valid?',6.8,1.8,1.45,1.2);smallNode(s,'Preview',9.1,2.0,2.9,.8);
  arrow(s,3.05,2.4,.6);arrow(s,6.1,2.4,.55);arrow(s,8.35,2.4,.6);caption(s,'Yes',8.35,1.95,.6);
  smallNode(s,'Show errors; save nothing',4.6,4.1,3.0,.85);arrow(s,7.5,3.1,0,.8);caption(s,'No',7.65,3.4,.6);
  association(s,1.8,4.525,4.6,4.525);
  s.addShape(S.line,{x:1.8,y:2.8,w:0,h:1.725,line:{color:C.accent1,width:1.5,beginArrowType:'triangle',endArrowType:'none'},objectName:'Correct errors and retry'});
  text(s,'Correct errors\nand retry',2.05,3.5,2.15,.7,{fontSize:17,color:C.text2});
  diamond(s,'Confirm?',9.8,3.65,1.55,1.3);arrow(s,10.6,2.9,0,.55);
  smallNode(s,'Discard',8.0,3.95,1.5,.8);
  s.addShape(S.line,{x:9.5,y:4.3,w:.3,h:0,line:{color:C.accent1,width:1.5,beginArrowType:'triangle',endArrowType:'none'},objectName:'Cancel import'});
  caption(s,'No',9.4,3.5,.5);caption(s,'Yes',10.8,5.1,.5);
  smallNode(s,'Check owner / expiry',9.1,5.65,3.0,.8);arrow(s,10.6,5.05,0,.45);
  smallNode(s,'Commit or reuse recording',4.9,5.65,3.2,.8);
  s.addShape(S.line,{x:8.2,y:6.05,w:.75,h:0,line:{color:C.accent1,width:2,beginArrowType:'triangle',endArrowType:'none'},objectName:'Valid confirmation'});
  smallNode(s,'Open saved history',.7,5.65,3.2,.8);
  s.addShape(S.line,{x:4.0,y:6.05,w:.75,h:0,line:{color:C.accent1,width:2,beginArrowType:'triangle',endArrowType:'none'},objectName:'View after commit'});
  s.addShape(S.ellipse,{x:.8,y:1.6,w:.22,h:.22,line:{color:C.text1},fill:{color:C.text1},objectName:'Activity initial node'});
  arrow(s,.91,1.85,0,.1);
  caption(s,'Expired or inaccessible previews cannot be saved. Successful imports open the stored history.',.8,6.65,11.7);
}
{
  const s=slide('Application architecture',false,'Sources: docs/06 architecture and docs/08 module boundaries. The presentation focuses on the Android client; the existing web implementation is not previewed. Android uses Kotlin/Compose and the shared FastAPI API. The layered backend keeps request handling, domain validation and owner-scoped persistence separate. SDK adapters and local SQLite are the later independent-companion extension.');
  box(s,'HeaRt Android','Compose screens\nAccount / API client',.7,1.85,3.15,1.75);
  box(s,'FastAPI','Routes → services\nparsers → repositories',5.0,1.85,3.25,1.75);
  box(s,'PostgreSQL','Accounts and sessions\nrecordings and readings',9.4,1.85,3.2,1.75);
  arrow(s,3.95,2.7,.9);arrow(s,8.35,2.7,.9);
  box(s,'Watch adapters','ET488 / Veepoo\nStandard Heart Rate service',.7,4.65,3.15,1.75);
  box(s,'Local outbox','SQLite first\nUpload acknowledgements',5.0,4.65,3.25,1.75);
  s.addShape(S.line,{x:2.25,y:3.75,w:0,h:.7,line:{color:C.accent1,width:2,beginArrowType:'triangle',endArrowType:'none'},objectName:'Watch to phone'});
  arrow(s,3.95,5.5,.9);
  text(s,'Database credentials stay on the server. The phone communicates through the API.',9.4,4.75,3.2,1.5,{fontSize:21,bold:true});
}
{
  const s=slide('Database relationships and schema',false,'Source: docs/06 core ERD and backend/app/models/tables.py. The five principal data entities are shown. Authentication throttling adds a supporting auth_rate_limits table beyond this core diagram. UUIDs do not grant access; every resource is checked against the authenticated owner. Times are stored as TIMESTAMPTZ.');
  umlClass(s,'users',['id UUID PK','email UNIQUE','password_hash'],.7,1.65,2.6,1.95);
  umlClass(s,'recording_sessions',['id UUID PK · user_id FK','label · source_type','UNIQUE(user_id, file_digest)'],4.7,1.65,3.5,1.95);
  umlClass(s,'heart_rate_readings',['id BIGINT PK','recording_id FK','measured_at · bpm (1–300)'],9.3,1.65,3.35,1.95);
  umlClass(s,'account_sessions',['token_digest PK','user_id FK · expires_at'],.7,4.7,3.0,1.55);
  umlClass(s,'import_previews',['id UUID PK · user_id FK','recording_id nullable FK','payload · status · expires_at'],4.7,4.5,3.5,1.95);
  arrow(s,3.4,2.6,1.15);caption(s,'1 : many',3.45,2.1,1.2);
  arrow(s,8.3,2.6,.85);caption(s,'1 : many',8.25,2.1,1.0);
  arrow(s,2.0,3.7,0,.8);arrow(s,3.5,3.75,1.0,.5);
  s.addShape(S.line,{x:6.4,y:3.6,w:0,h:.9,line:{color:C.accent1,width:1.5,beginArrowType:'triangle',endArrowType:'none'},objectName:'Preview recording foreign key'});
  caption(s,'0..* resolves to 0..1',6.6,3.95,2.2);
  text(s,'Delete a recording → cascade its readings\n\nOne timestamp per recording; all imports commit atomically.',9.3,4.6,3.35,1.55,{fontSize:19,color:C.text2});
}
{
  const s=slide('Patterns and SOLID boundaries',false,'Source: docs/07. Actual backend references: ImportParser and CanonicalCsvParser in app/parsers/csv.py; create_preview accepts an injected parser in app/services/imports.py; ImportRepository in app/repositories/imports.py. The deployed source uses service functions, not a fabricated ImportService class. SOLID points describe the documented design and visible separation; a full formal SOLID audit is not claimed.');
  box(s,'Strategy','ImportParser → CanonicalCsvParser\n\nA parser can be replaced without rewriting the preview/transaction workflow.',.7,1.8,5.65,2.7);
  box(s,'Repository','ImportRepository / recording queries\n\nPersistence owns SQL, constraints and owner-scoped database operations.',6.95,1.8,5.65,2.7);
  text(s,'Single responsibility',.85,5.05,5.2,.5,{fontSize:24,bold:true});
  text(s,'Parsing, coordination and persistence have separate responsibilities.',.85,5.75,5.2,.85,{fontSize:19,color:C.text2});
  text(s,'Dependency inversion',7.1,5.05,5.2,.5,{fontSize:24,bold:true});
  text(s,'The preview service accepts the parser contract rather than a request-specific implementation.',7.1,5.75,5.2,.85,{fontSize:19,color:C.text2});
}

startSection('Implementation and verification');
{
  const s=slide('Android screen flow',false,'Sources: docs/07 interaction principles and docs/11 user workflows; current Android labels are reconciled against MainActivity. Home supports local one-shot readings without an account. Saving/import/history are account scoped. Devices offers only supported protocol adapters. Files converts compatible Vigil exports and canonical CSV; arbitrary vendor compatibility is not promised.');
  const names=['Home','Devices','History','Files'];const desc=['Read once\nSave / stop recording','Find / connect\nReconnect / disconnect','Account recordings\nExact saved readings','Preview / confirm CSV\nExport local readings'];
  names.forEach((v,i)=>{box(s,v,desc[i],.7+i*3.15,1.9,2.7,2.2);});
  text(s,'Account screens',.85,4.75,4.7,.6,{fontSize:26,bold:true});
  text(s,'Sign in / register share the same backend account. Local viewing remains available without signing in.',.85,5.6,5.7,1.0,{fontSize:20,color:C.text2});
  text(s,'Readable states',7.2,4.75,5.0,.6,{fontSize:26,bold:true});
  text(s,'Labelled inputs, visible errors, safe confirmation, persistent dark mode and a flat tab bar.',7.2,5.6,5.0,1.0,{fontSize:20,color:C.text2});
}
{
  const s=slide('API and implementation boundaries',false,'Source: docs/08 endpoint contract, reconciled with current backend routes and companion API client. Status/auth/import/history are implemented. Source control uses the existing feature branch and meaningful commits. No fabricated pull-request or peer-review history is included.');
  const rows=[['Accounts','POST /api/auth/register · /login · /logout'],['Import','POST /api/imports/preview · /{id}/confirm'],['History','GET /api/recordings · /api/readings'],['Management','PATCH /api/recordings/{id} · DELETE /api/recordings/{id}'],['Operations','GET /health · GET /ready']];
  rows.forEach((r,i)=>{const y=1.7+i*.82;text(s,r[0],.8,y,2.8,.6,{fontSize:22,bold:true});text(s,r[1],3.7,y,8.6,.6,{fontSize:18,color:C.text2});});
  caption(s,'Errors use safe status/code/message responses. Mutations require account, origin and CSRF checks.',.8,6.35,11.8);
}
{
  const s=slide('Testing: design and observed results',false,'Sources: docs/09 test plan and docs/13 traceability; current/historical execution evidence is docs/19. T-03 cross-user access, T-09 repeat confirmation, T-11 rollback, T-14 summary fixture, T-18 deletion. Earlier backend suite: 44 passed. Latest Android suite: 11 passed. The 60% coverage goal is unmeasured; test count is not coverage. Latest manual sensor-release behavior still needs connected-watch confirmation.');
  text(s,'44',.8,1.7,2.5,.9,{fontSize:54,bold:true,color:C.accent1});text(s,'Backend tests\nearlier integration run',.8,2.85,4.0,1.0,{fontSize:21});
  text(s,'11',5.05,1.7,2.5,.9,{fontSize:54,bold:true,color:C.accent1});text(s,'Android tests\nlatest build',5.05,2.85,3.7,1.0,{fontSize:21});
  text(s,'≥60%',9.0,1.7,3.0,.9,{fontSize:48,bold:true,color:C.text2});text(s,'Python coverage target\nnot yet measured',9.0,2.85,3.6,1.0,{fontSize:21});
  text(s,'Critical checks',.85,4.65,4.0,.5,{fontSize:25,bold:true});
  text(s,'Owner isolation · idempotent confirmation · rollback on failure\nCSV boundaries · UTC consistency · deletion · passive watch connection',.85,5.45,11.6,1.0,{fontSize:20,color:C.text2});
}
{
  const s=slide('STRIDE threat model',false,'Source: docs/10-security-and-devops.md. This is the actual threat analysis and its proposed/implemented controls, not an assertion of OWASP certification or a completed security audit. Repudiation requires verified logging evidence; production logging retention is not claimed as configured.');
  const risks=[['Spoofing','Password hashes, random sessions and login throttling'],['Tampering','Owner checks, CSRF, server-owned previews and constraints'],['Repudiation','Safe action/outcome logs; verify retained audit evidence'],['Information disclosure','HTTPS, private credentials and owner-scoped access'],['Denial of service','Bounded file size, rows, previews and query responses'],['Elevation of privilege','Derive owner from the session; no client-selected owner']];
  risks.forEach((r,i)=>{const y=1.6+i*.78;text(s,r[0],.8,y,3.8,.58,{fontSize:21,bold:true});text(s,r[1],4.85,y,7.7,.64,{fontSize:18,color:C.text2});});
}
{
  const s=slide('Maintenance, traceability and delivery',false,'Sources: docs/11 manuals, docs/12 change/report/metrics templates and docs/13 requirement-to-test map. The change is real: the user reported the watch stuck measuring under auto-start. Implemented software changes and tests are documented; physical retest remains. Coverage, complexity, defect density, peer review, CI and deployment evidence are not fabricated. Neon connection has now been configured separately; a live API URL still needs deployment.');
  box(s,'Recorded change','Automatic measurement on connection\n→ explicit Read heart rate\n\nSensor cleanup on completion, timeout and cancellation; retain the last BPM with its age.',.7,1.8,6.0,3.35);
  text(s,'Traceable checks',7.3,1.85,5.0,.5,{fontSize:25,bold:true});
  text(s,'FR-03 → T-03  Owner isolation\nFR-06 → T-09–T-12  Safe import\nFR-12 → T-18  Confirmed deletion',7.3,2.65,5.0,1.55,{fontSize:19});
  text(s,'Remaining evidence',.85,5.65,4.0,.5,{fontSize:24,bold:true});
  text(s,'Measured coverage/metrics, CI scans, cloud smoke test, final report and peer assessment.',5.0,5.6,7.4,.9,{fontSize:20,color:C.text2});
}

startSection('Android demonstration');
{
  const s=slide('HeaRt on Android',true,'Actual November 22 Samsung phone captures. These show dark Home disconnected, the account screen and Devices connecting. They are not a current stream. The independent package has its own storage and does not depend on running Vigil. No browser or web preview is part of this demonstration section.');
  [['android-home-dark.png','Home'],['android-account-dark.png','Account'],['android-devices-dark.png','Devices']].forEach(([name,label],i)=>{const x=1+i*4.2;image(s,name,x,1.45,2.65,5.2);caption(s,label,x,6.7,2.65,true);});
}
{
  const s=slide('Android demonstration',true,'Switch to the actual Android app. Connect the worn ET488, take a manual reading and show that the detector stops; sign in and explicitly save a short recording, Stop, then open History to demonstrate persistence. Demonstrate Files with a labelled fixture if the watch will not connect. The light screenshot is an in-progress read with no value yet; the dark screenshot is disconnected. Screenshots are not live sensor evidence.');
  image(s,'android-home-light.png',.7,1.45,2.4,5.2);image(s,'android-home-dark.png',3.55,1.45,2.4,5.2);
  numbered(s,1,'Read','Connect → Read heart rate → result → idle',7.0,1.95,5.4,true);
  numbered(s,2,'Save','Start recording → Stop → open History',7.0,3.55,5.4,true);
  numbered(s,3,'Import','Files → preview → confirm a labelled CSV',7.0,5.15,5.4,true);
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

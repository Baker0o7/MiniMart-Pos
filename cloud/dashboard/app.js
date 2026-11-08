const $=id=>document.getElementById(id);
const esc=s=>String(s??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
const ls={get:k=>{try{return localStorage.getItem(k)||""}catch(e){return ""}},set:(k,v)=>{try{localStorage.setItem(k,v)}catch(e){}}};
let sb=null,rows=[],myStores=[];
const HOSTED=!!(window.MINIMART_CLOUD&&/^https:\/\//.test(window.MINIMART_CLOUD.url||"")&&(window.MINIMART_CLOUD.key||""));
const cfg=()=>HOSTED?{url:window.MINIMART_CLOUD.url.replace(/\/+$/,""),key:window.MINIMART_CLOUD.key}:{url:ls.get("mm_url"),key:ls.get("mm_key")};
const I18N={ar:{
 h_daily:"المبيعات اليومية",h_stores:"المتاجر",h_mix:"طرق الدفع",h_profit:"الربح والمصروفات",h_low:"مخزون منخفض",h_exp:"تنتهي صلاحيتها قريبًا",h_shifts:"الورديات وتقارير Z",h_vr:"الإلغاءات والمرتجعات",h_top:"أفضل المنتجات (30 يومًا)",
 b_csv:"تصدير CSV",b_refresh:"تحديث",b_out:"تسجيل الخروج",
 today:"اليوم",days:"أيام",sales:"مبيعات",avg:"متوسط السلة",expenses:"المصروفات",profit:"الربح التقديري",credit:"ديون العملاء",stock:"قيمة المخزون (بالتكلفة)",
 store:"المتجر",low:"مخزون منخفض",last:"آخر رفع",share:"حصة المتجر من الإيرادات",product:"المنتج",qty:"الكمية",revenue:"الإيرادات",none:"لا بيانات بعد"}};
let LANG=(()=>{try{return localStorage.getItem("mm_lang")||((navigator.language||"").startsWith("ar")?"ar":"en")}catch(e){return "en"}})();
const t=(k,en)=>LANG==="ar"&&I18N.ar[k]?I18N.ar[k]:(en??k);
function applyLang(){
  document.documentElement.lang=LANG;document.documentElement.dir=LANG==="ar"?"rtl":"ltr";$("lang").value=LANG;
  document.querySelectorAll("[data-t]").forEach(e=>{if(!e.dataset.en)e.dataset.en=e.textContent;e.textContent=t(e.dataset.t,e.dataset.en)});
}
const fmt=n=>Math.round(n||0).toLocaleString();
const dt=ms=>ms?new Date(ms).toLocaleString([], {day:"numeric",month:"short",hour:"2-digit",minute:"2-digit"}):"-";
const ago=ms=>{const h=(Date.now()-ms)/36e5;return h<1?Math.max(1,Math.round(h*60))+" min ago":h<48?Math.round(h)+" h ago":Math.round(h/24)+" d ago"};

function showLogin(){
  $("login").hidden=false;$("app").hidden=true;
  if(HOSTED){$("own").hidden=true;$("hostedActions").hidden=false;$("lhelp").textContent="Sign in, or create a free account to see all your shops in one place."}
  else{$("url").value=ls.get("mm_url");$("key").value=ls.get("mm_key")}
}
async function start(){
  const {url,key}=cfg();
  if(!url||!key||!window.supabase) return showLogin();
  sb=supabase.createClient(url,key);
  const {data}=await sb.auth.getSession();
  if(!data.session) return showLogin();
  $("login").hidden=true;$("app").hidden=false;load();
}
$("go").onclick=async()=>{
  const url=HOSTED?cfg().url:$("url").value.trim().replace(/\/+$/,""),key=HOSTED?cfg().key:$("key").value.trim();
  $("lerr").textContent="";$("linfo").textContent="";
  if(!url.startsWith("https://")||!key){$("lerr").textContent="Enter the project URL and anon key.";return}
  if(!HOSTED){ls.set("mm_url",url);ls.set("mm_key",key)}
  sb=supabase.createClient(url,key);
  const {error}=await sb.auth.signInWithPassword({email:$("email").value.trim(),password:$("pass").value});
  if(error){$("lerr").textContent=error.message;return}
  $("pass").value="";start();
};
$("signup").onclick=async()=>{
  $("lerr").textContent="";$("linfo").textContent="";
  const email=$("email").value.trim(),pw=$("pass").value;
  if(!email||pw.length<8){$("lerr").textContent="Enter your email and a password of at least 8 characters.";return}
  sb=supabase.createClient(cfg().url,cfg().key);
  const {data,error}=await sb.auth.signUp({email,password:pw});
  if(error){$("lerr").textContent=error.message;return}
  $("pass").value="";
  if(data.session) start(); else $("linfo").textContent="Check your email and tap the confirmation link, then sign in here.";
};
$("forgot").onclick=async()=>{
  $("lerr").textContent="";$("linfo").textContent="";
  const email=$("email").value.trim(); if(!email){$("lerr").textContent="Enter your email first.";return}
  sb=supabase.createClient(cfg().url,cfg().key);
  const {error}=await sb.auth.resetPasswordForEmail(email,{redirectTo:location.href.split("#")[0]});
  if(error)$("lerr").textContent=error.message;else $("linfo").textContent="If that email has an account, a reset link is on its way.";
};
$("delacct").onclick=async()=>{
  if(!confirm("Delete your account and ALL shops and uploaded data? This cannot be undone. The phones keep their own data."))return;
  const {error}=await sb.rpc("owner_delete_account"); if(error){alert(error.message);return}
  await sb.auth.signOut();showLogin();
};
$("out").onclick=async()=>{await sb.auth.signOut();showLogin()};
$("refresh").onclick=load;
$("rotate").onclick=async()=>{
  if(HOSTED){
    const {data,error}=await sb.rpc("owner_create_link_code");
    $("newcode").innerHTML=error?'<span class="bad">'+esc(error.message)+'</span>':'Type this code into the shop\'s app (Settings → Cloud dashboard). It works once and expires in 30 minutes:<br><b style="font-size:22px;letter-spacing:.1em">'+esc(data)+'</b>';
    return;
  }
  if(!confirm("Make a new setup code? The old one stops working for new stores. Stores already connected are not affected."))return;
  const {data,error}=await sb.rpc("owner_rotate_setup_code");
  $("newcode").innerHTML=error?'<span class="bad">'+esc(error.message.includes("owner_rotate")?"Run the latest cloud/supabase_setup.sql first.":error.message)+'</span>':'New setup code (shown once — copy it now):<br><b>'+esc(data)+'</b>';
};
$("store").onchange=render;
$("range").onchange=render;
$("lang").onchange=()=>{LANG=$("lang").value;ls.set("mm_lang",LANG);applyLang();if(rows.length)render()};
$("csv").onclick=()=>{
  const q=v=>{v=String(v??"");if(/^[=+\-@\t\r]/.test(v))v="'"+v;return /[",\n]/.test(v)?'"'+v.replace(/"/g,'""')+'"':v};
  const head=["store","day","revenue","transactions","cash","mpesa","card","credit","discounts","voids","voidTotal","refunds","refundTotal","expenses"];
  const lines=[head.join(",")];
  sel().forEach(r=>(r.payload.daily||[]).forEach(x=>lines.push([r.store_name,x.day,...head.slice(2).map(k=>x[k]??0)].map(q).join(","))));
  const a=document.createElement("a");a.href=URL.createObjectURL(new Blob(["\ufeff"+lines.join("\n")],{type:"text/csv"}));
  a.download="minimart-daily-"+new Date().toISOString().slice(0,10)+".csv";document.body.appendChild(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(a.href),1000);
};

async function load(){
  if(HOSTED){
    $("rotate").textContent="Add a shop (get a code)";$("acct").hidden=false;
    $("addHelp").textContent="To connect a shop: press “Add a shop”, then type the code into that shop's app (Settings → Cloud dashboard).";
    const r=await sb.rpc("owner_my_stores"); myStores=r.error?[]:(r.data||[]);
  }
  const {data,error}=await sb.from("store_snapshots").select("store_id,store_name,payload,pushed_at").order("store_name");
  if(error){if(/jwt|expired|not authenticated/i.test(error.message)){await sb.auth.signOut();return showLogin()}$("kpis").innerHTML='<div class="card bad">'+esc(error.message)+'</div>';return}
  rows=data||[];
  const cur=$("store").value;
  $("store").innerHTML='<option value="*">All stores ('+rows.length+')</option>'+rows.map(r=>'<option value="'+esc(r.store_id)+'">'+esc(r.store_name||"Store")+'</option>').join("");
  if([...$("store").options].some(o=>o.value===cur)) $("store").value=cur;
  render();
}
const sel=()=>{const v=$("store").value;return v==="*"?rows:rows.filter(r=>r.store_id===v)};
const sum=(arr,f)=>arr.reduce((a,x)=>a+(+f(x)||0),0);

function render(){
  const rs=sel(), cur=(rs[0]?.payload?.currency)||"KES";
  if(!rows.length){$("kpis").innerHTML='<div class="card empty">'+(HOSTED?'No shops yet. Open “Manage stores” below, press “Add a shop”, then type the code into the shop\'s app (Settings → Cloud dashboard).':'No stores have uploaded yet. In each store\'s app: Settings → Cloud dashboard → Connect & upload.')+'</div>';
    $("manage").innerHTML=myStores.map(m=>'<div style="display:flex;justify-content:space-between;align-items:center;margin:6px 0;gap:8px"><span>'+esc(m.name)+' <span class="sub small">(waiting for first upload)</span></span><button class="ghost" data-del="'+esc(m.id)+'" data-name="'+esc(m.name)+'">Remove</button></div>').join("");
    bindDeletes();return}
  const stale=rs.filter(r=>Date.now()-new Date(r.pushed_at)>864e5);
  $("stale").innerHTML=stale.length?'<div class="card warn">No upload in over 24h from: '+stale.map(r=>esc(r.store_name)+" ("+ago(new Date(r.pushed_at))+")").join(", ")+'</div>':"";
  const today=new Date();const key=d=>d.getFullYear()+"-"+String(d.getMonth()+1).padStart(2,"0")+"-"+String(d.getDate()).padStart(2,"0");
  const N=+$("range").value||30;const days=[];for(let i=N-1;i>=0;i--){const d=new Date(today);d.setDate(d.getDate()-i);days.push(key(d))}
  const agg={};days.forEach(d=>agg[d]={revenue:0,tx:0,expenses:0});
  const tot={cash:0,mpesa:0,card:0,credit:0,voids:0,voidTotal:0,refunds:0,refundTotal:0,discounts:0};
  rs.forEach(r=>(r.payload.daily||[]).forEach(x=>{
    const a=agg[x.day];if(!a)return;a.revenue+=x.revenue;a.tx+=x.transactions;a.expenses+=x.expenses;
    for(const k in tot) tot[k]+=+x[k]||0;
  }));
  const rev=n=>days.slice(-n).reduce((a,d)=>a+agg[d].revenue,0);
  const tx30=days.reduce((a,d)=>a+agg[d].tx,0), exp30=days.reduce((a,d)=>a+agg[d].expenses,0), rev30=rev(N);
  const cr=sum(rs,r=>r.payload.profit?.costedRevenue), cc=sum(rs,r=>r.payload.profit?.costedCost);
  const gross=cr-cc, cov=rev30>0?Math.min(1,cr/rev30):0;
  const tile=(l,v,s)=>'<div class="kpi"><span>'+l+'</span><b>'+v+'</b>'+(s?'<span>'+s+'</span>':'')+'</div>';
  $("kpis").innerHTML=
    tile(t("today","Today"),fmt(agg[days[days.length-1]].revenue)+" "+cur,agg[days[days.length-1]].tx+" "+t("sales","sales"))+
    tile(N+" "+t("days","days"),fmt(rev30),fmt(tx30)+" "+t("sales","sales"))+
    tile(t("avg","Avg basket"),tx30?fmt(rev30/tx30):"-")+
    tile(t("expenses","Expenses")+" ("+N+"d)",fmt(exp30))+
    tile(t("profit","Est. profit")+" ("+N+"d)",fmt(gross-exp30),"gross "+fmt(gross)+" − expenses")+
    tile(t("credit","Credit owed"),fmt(sum(rs,r=>r.payload.customerCredit)))+
    tile(t("stock","Stock value (cost)"),fmt(sum(rs,r=>r.payload.stock?.costValue)),fmt(sum(rs,r=>r.payload.stock?.productCount))+" products");
  chart(days,agg);
  $("manage").innerHTML=rows.map(r=>'<div style="display:flex;justify-content:space-between;align-items:center;margin:6px 0;gap:8px"><span>'+esc(r.store_name)+'</span><button class="ghost" data-del="'+esc(r.store_id)+'" data-name="'+esc(r.store_name)+'">Remove</button></div>').join("")||'<div class="empty">No stores.</div>';
  bindDeletes();
  $("stores").innerHTML='<table><tr><th>'+t("store","Store")+'</th><th class="n">'+t("today","Today")+'</th><th class="n">7d</th><th class="n">'+N+'d</th><th class="n">'+t("low","Low stock")+'</th><th>'+t("last","Last upload")+'</th></tr>'+
    rows.map(r=>{const m={};(r.payload.daily||[]).forEach(x=>m[x.day]=x.revenue);
      const s=n=>days.slice(-n).reduce((a,d)=>a+(m[d]||0),0);const old=Date.now()-new Date(r.pushed_at)>864e5;
      return '<tr><td>'+esc(r.store_name)+'</td><td class="n">'+fmt(s(1))+'</td><td class="n">'+fmt(s(7))+'</td><td class="n">'+fmt(s(N))+'</td><td class="n '+((r.payload.stock?.lowStock||[]).length?'warn':'')+'">'+(r.payload.stock?.lowStock||[]).length+'</td><td class="'+(old?'warn':'sub')+'">'+ago(new Date(r.pushed_at))+'</td></tr>'}).join("")+'</table>';
  const mixT=tot.cash+tot.mpesa+tot.card+tot.credit;
  $("mix").innerHTML=mixT?[["Cash",tot.cash],["M-Pesa",tot.mpesa],["Card",tot.card],["Credit",tot.credit]].map(([l,v])=>
    '<div style="margin:6px 0"><div class="small" style="display:flex;justify-content:space-between"><span>'+l+'</span><span>'+fmt(v)+' · '+Math.round(v/mixT*100)+'%</span></div><div style="height:8px;background:var(--s2);border-radius:6px"><div style="height:8px;width:'+(v/mixT*100)+'%;background:var(--teal);border-radius:6px"></div></div></div>').join(""):'<div class="empty">No sales yet.</div>';
  $("profit").innerHTML='<table><tr><td>Revenue</td><td class="n">'+fmt(rev30)+'</td></tr><tr><td>Gross profit (costed items)</td><td class="n">'+fmt(gross)+'</td></tr><tr><td>Expenses</td><td class="n">'+fmt(exp30)+'</td></tr><tr><td><b>Estimated profit</b></td><td class="n"><b class="'+(gross-exp30<0?'bad':'ok')+'">'+fmt(gross-exp30)+'</b></td></tr></table>'+
    '<p class="small '+(cov<.8?'warn':'sub')+'">Cost prices set for '+Math.round(cov*100)+'% of revenue. '+(cov<.8?'Profit is understated until more products have cost prices.':'')+'</p>';
  const multi=rs.length>1, nm=r=>multi?' <span class="sub small">'+esc(r.store_name)+'</span>':'';
  const low=rs.flatMap(r=>(r.payload.stock?.lowStock||[]).map(x=>({...x,r})));
  $("low").innerHTML=low.length?'<table>'+low.slice(0,40).map(x=>'<tr><td>'+esc(x.name)+nm(x.r)+'</td><td class="n '+(x.stock<=0?'bad':'warn')+'">'+x.stock+' '+esc(x.unit||"")+'</td></tr>').join("")+'</table>':'<div class="empty ok">Nothing low.</div>';
  const ex=rs.flatMap(r=>(r.payload.stock?.expiring||[]).map(x=>({...x,r}))).sort((a,b)=>a.expiryDate-b.expiryDate);
  $("exp").innerHTML=ex.length?'<table>'+ex.slice(0,40).map(x=>'<tr><td>'+esc(x.name)+nm(x.r)+'</td><td class="n '+(x.expiryDate<Date.now()?'bad':'warn')+'">'+new Date(x.expiryDate).toLocaleDateString()+'</td></tr>').join("")+'</table>':'<div class="empty ok">Nothing expiring.</div>';
  const sh=rs.flatMap(r=>(r.payload.shifts||[]).map(x=>({...x,r}))).sort((a,b)=>b.clockIn-a.clockIn);
  $("shifts").innerHTML=sh.length?'<table><tr><th>Cashier</th>'+(multi?'<th>Store</th>':'')+'<th>Opened</th><th>Status</th><th class="n">Sales</th><th class="n">Expected</th><th class="n">Counted</th><th class="n">Variance</th></tr>'+
    sh.slice(0,40).map(x=>{const v=x.variance;return '<tr><td>'+esc(x.cashier)+'</td>'+(multi?'<td>'+esc(x.r.store_name)+'</td>':'')+'<td>'+dt(x.clockIn)+'</td><td class="'+(x.open?'warn':'ok')+'">'+(x.open?'Open':'Closed '+dt(x.clockOut))+'</td><td class="n">'+fmt(x.totalSales)+'</td><td class="n">'+(x.expectedCash==null?'-':fmt(x.expectedCash))+'</td><td class="n">'+(x.countedCash==null?'-':fmt(x.countedCash))+'</td><td class="n '+(v==null?'':Math.abs(v)<1?'ok':'bad')+'">'+(v==null?'-':(v>0?'+':'')+fmt(v))+'</td></tr>'}).join("")+'</table>':'<div class="empty">No shifts uploaded.</div>';
  $("vr").innerHTML='<table><tr><td>Voided sales</td><td class="n">'+fmt(tot.voids)+' · '+fmt(tot.voidTotal)+'</td></tr><tr><td>Refunds</td><td class="n">'+fmt(tot.refunds)+' · '+fmt(tot.refundTotal)+'</td></tr><tr><td>Discounts given</td><td class="n">'+fmt(tot.discounts)+'</td></tr></table>';
  // Best sellers across the selected stores (the phones send their last 30 days).
  const tp={};rs.forEach(r=>(r.payload.topProducts||[]).forEach(x=>{const o=tp[x.name]||(tp[x.name]={name:x.name,qty:0,revenue:0});o.qty+=+x.qty||0;o.revenue+=+x.revenue||0}));
  const topList=Object.values(tp).sort((a,b)=>b.revenue-a.revenue).slice(0,10);
  $("top").innerHTML=topList.length?'<table><tr><th>'+t("product","Product")+'</th><th class="n">'+t("qty","Qty")+'</th><th class="n">'+t("revenue","Revenue")+'</th></tr>'+
    topList.map(x=>'<tr><td>'+esc(x.name)+'</td><td class="n">'+fmt(x.qty)+'</td><td class="n">'+fmt(x.revenue)+'</td></tr>').join("")+'</table>':'<div class="empty">'+t("none","No data yet. It appears after the next upload from the phone.")+'</div>';
  // Store comparison: each shop's share of the revenue in the chosen range.
  const per=rows.map(r=>{const m={};(r.payload.daily||[]).forEach(x=>m[x.day]=x.revenue);return {n:r.store_name,v:days.reduce((a,d)=>a+(m[d]||0),0)}}).sort((a,b)=>b.v-a.v);
  const tv=per.reduce((a,x)=>a+x.v,0);
  $("cmp").innerHTML=per.length>1&&tv>0?'<div class="small sub" style="margin-bottom:4px">'+t("share","Share of revenue")+' ('+N+'d)</div>'+per.map(x=>
    '<div style="margin:5px 0"><div class="small" style="display:flex;justify-content:space-between"><span>'+esc(x.n)+'</span><span>'+fmt(x.v)+' · '+Math.round(x.v/tv*100)+'%</span></div><div style="height:8px;background:var(--s2);border-radius:4px"><div style="height:8px;width:'+Math.round(x.v/tv*100)+'%;background:var(--teal);border-radius:4px"></div></div></div>').join(""):"";
}

function bindDeletes(){
  document.querySelectorAll("[data-del]").forEach(b=>b.onclick=async()=>{
    if(!confirm('Remove "'+b.dataset.name+'" and all of its uploaded data from the cloud? The phone keeps its own data.'))return;
    const {error}=await sb.rpc("owner_delete_store",{p_store_id:b.dataset.del});
    if(error){alert(error.message.includes("owner_delete_store")?"Run the latest cloud SQL first.":error.message);return}
    load();
  });
}
function chart(days,agg){
  const W=1000,H=200,P=24,max=Math.max(1,...days.map(d=>agg[d].revenue));
  const bw=(W-P*2)/days.length;
  let s='<svg viewBox="0 0 '+W+' '+(H+22)+'" width="100%" role="img" aria-label="Daily sales bar chart">';
  [0,.5,1].forEach(f=>{const y=H-f*(H-10);s+='<line x1="'+P+'" x2="'+(W-P)+'" y1="'+y+'" y2="'+y+'" stroke="var(--bd)"/><text x="0" y="'+(y-2)+'">'+(f?fmt(max*f):"0")+'</text>'});
  days.forEach((d,i)=>{const h=agg[d].revenue/max*(H-10),x=P+i*bw;
    s+='<rect x="'+(x+1)+'" y="'+(H-h)+'" width="'+(bw-2)+'" height="'+h+'" rx="2" fill="var(--teal)"><title>'+d+': '+fmt(agg[d].revenue)+' ('+agg[d].tx+' sales)</title></rect>';
    if(i%5===0) s+='<text x="'+x+'" y="'+(H+14)+'">'+d.slice(5)+'</text>'});
  $("chart").innerHTML=s+'</svg>';
}
applyLang();
start();
// Keep an open dashboard (for example on a shop-counter tablet) fresh without a tap.
setInterval(()=>{if(sb&&!document.hidden&&!$("app").hidden)load()},300000);
// Companion mode: lets a phone install the page to its home screen. Only the page itself is cached, never any shop data.
if("serviceWorker" in navigator&&location.protocol==="https:")navigator.serviceWorker.register("sw.js").catch(()=>{});

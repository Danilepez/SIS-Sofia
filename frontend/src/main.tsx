import React,{useState,useRef,useEffect} from 'react';
import {createRoot} from 'react-dom/client';
import {QueryClient,QueryClientProvider,useQuery,useQueryClient} from '@tanstack/react-query';
import '@fontsource/nunito-sans/400.css';
import '@fontsource/nunito-sans/600.css';
import '@fontsource/nunito-sans/700.css';
import '@fontsource/nunito-sans/800.css';
import '@fontsource/ibm-plex-mono/400.css';
import '@fontsource/ibm-plex-mono/500.css';
import './style.css';
type Row=Record<string,any>;
type Catalog={productos:Row[],centros:Row[]};
let auth='';
const queryClient=new QueryClient({defaultOptions:{queries:{retry:false,refetchOnWindowFocus:false}}});
async function api(path:string,body?:Row,key?:string):Promise<any>{
 const r=await fetch('/api/v1'+path,{method:body?'POST':'GET',headers:{Authorization:'Basic '+auth,'Content-Type':'application/json','X-Requested-With':'SIS-SOFIA',...(key?{'Idempotency-Key':key}:{})},...(body?{body:JSON.stringify(body)}:{})});
 const data=await r.json().catch(()=>({}));
 if(!r.ok)throw new Error(r.status===401?'Usuario o contraseña incorrectos.':r.status===403?'Este usuario no tiene permiso para esta operación.':data.detail||'No se pudo completar la consulta. Revise la conexión.');
 return data;
}
const today=()=>new Intl.DateTimeFormat('en-CA',{timeZone:'America/La_Paz',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
const fmt=(n:number|null|undefined,d=2)=>n==null?'Sin datos':new Intl.NumberFormat('es-BO',{maximumFractionDigits:d}).format(n);
// Kg legibles: enteros desde 100 kg; la cifra exacta (3 decimales) queda en el tooltip.
const fmtKg=(n:number|null|undefined)=>n==null?'Sin datos':fmt(n,Math.abs(Number(n))>=100?0:1);
const daysAgo=(day:string,n:number)=>new Date(Date.parse(day+'T00:00Z')-n*864e5).toISOString().slice(0,10);
function App(){
 const [user,setUser]=useState<Row|null>(null),[error,setError]=useState(''),[busy,setBusy]=useState(false),[tab,setTab]=useState('dashboard');
 async function login(e:React.FormEvent<HTMLFormElement>){e.preventDefault();setBusy(true);setError('');const f=new FormData(e.currentTarget);auth=btoa(String(f.get('usuario'))+':'+String(f.get('clave')));try{setUser(await api('/me'));}catch(e){auth='';setError(String((e as Error).message));}finally{setBusy(false);}}
 if(!user)return <main className="login"><section className="login-brand" aria-label="SIS Sofía"><Seal/><h1>Inventario y calidad</h1><p>Merma, rotación FEFO y exactitud de inventario por centro y producto.</p><ul className="login-kpis"><li><code>EST-03</code>Merma física</li><li><code>TAC-02</code>Cumplimiento FEFO</li><li><code>TAC-04</code>Exactitud de inventario</li></ul></section>
 <section className="login-panel"><form onSubmit={login}><h2>Ingresar</h2><label>Usuario<select name="usuario"><option value="consulta">consulta · Lectura</option><option value="operador">operador · Inventario</option><option value="calidad">calidad · Liberación y mermas</option><option value="supervisor">supervisor · Aprobación de ajustes</option></select></label><label>Contraseña<input name="clave" type="password" required autoComplete="current-password"/></label><button className="primary" disabled={busy}>{busy?'Validando…':'Ingresar'}</button><p role="alert" className="error">{error}</p></form></section></main>;
 return <><header><div className="brand"><Seal/><span><b>SIS</b> Inventario y decisiones</span></div><nav aria-label="Principal">{[['dashboard','Dashboard'],['operaciones','Operaciones'],['trazabilidad','Trazabilidad'],['alcance','Alcance y métricas']].map(([v,t])=><button key={v} aria-current={tab===v?'page':undefined} className={tab===v?'active':''} onClick={()=>setTab(v)}>{t}</button>)}</nav><div className="account"><span>{user.usuario}</span><button className="ghost" onClick={()=>{auth='';setUser(null);setTab('dashboard');queryClient.clear();}}>Salir</button></div></header><main className="workspace">{tab==='dashboard'?<Dashboard/>:tab==='operaciones'?<Operations user={user}/>:tab==='trazabilidad'?<Trace/>:<Scope/>}</main></>;
}
// Sello tipográfico de marca (rojo Sofía). Para usar el logotipo oficial, reemplazar este componente por un <img>.
function Seal(){return <span className="seal" aria-label="Sofía">Sofía</span>;}
function useCatalog(){return useQuery<Catalog>({queryKey:['catalogo'],queryFn:()=>api('/catalogo')});}
const KPI_DEFS:[string,string,string,boolean][]=[['merma','Merma física','EST-03',true],['fefo','Cumplimiento FEFO','TAC-02',false],['exactitud','Exactitud de inventario','TAC-04',false]];
const STATE_TEXT:Record<string,string>={good:'En meta',warn:'Fuera de meta',bad:'Desviación crítica',neutral:'No evaluable'};
function evalKpis(d:Row){return KPI_DEFS.map(([k,title,code,lower])=>{const v=d.actual[k],prev=d.anterior[k],goal=d.metas[k];const state=v==null?'neutral':(lower?v<=goal:v>=goal)?'good':(lower?v>goal+0.3:v<goal-(k==='fefo'?2:1))?'bad':'warn';const delta=v!=null&&prev!=null?v-prev:null;const better=delta==null||delta===0?null:lower?delta<0:delta>0;return {k,title,code,lower,v,goal,state,delta,better};});}
// Arco narrativo (What? → So what? → Now what?) construido solo con datos del período.
function story(d:Row,kpis:ReturnType<typeof evalKpis>){
 const off=kpis.filter(x=>x.state==='bad'||x.state==='warn');
 const count=(r:string)=>d.riesgos.filter((x:Row)=>x.riesgo===r);const kgOf=(xs:Row[])=>xs.reduce((s,x)=>s+Number(x.kg||0),0);
 const venc=count('VENCIDO'),ret=count('RETENIDO'),pv=count('POR VENCER');const riskKg=kgOf(d.riesgos);
 const what=off.length===0?(kpis.every(x=>x.state==='neutral')?'Sin datos suficientes para evaluar el período.':'Todos los indicadores están en meta.'):off.map(x=>`${x.title} en ${fmt(x.v)} % (meta ${x.lower?'≤':'≥'} ${fmt(x.goal)} %)`).join(' · ');
 const why:string[]=[];
 if(d.actual.merma!=null&&d.actual.merma>d.metas.merma&&d.causas.length)why.push(`La merma se concentra en “${d.causas[0].motivo}” (${fmtKg(d.causas[0].kg)} kg).`);
 if(d.riesgos.length)why.push(`${fmtKg(riskKg)} kg en ${d.riesgos.length} ${d.riesgos.length===1?'lote':'lotes'} en riesgo${d.stock.fisico?` (${fmt(riskKg/d.stock.fisico*100,1)} % del stock físico)`:''}.`);
 if(d.actual.contados===0)why.push('No hubo conteos físicos en el período.');
 if(!why.length)why.push('Sin lotes en riesgo.');
 const now:[string,string][]=[];
 if(venc.length)now.push(['bad',`Calidad: inmovilizar hoy ${venc.length} ${venc.length===1?'lote vencido':'lotes vencidos'} (${fmtKg(kgOf(venc))} kg).`]);
 if(pv.length)now.push(['warn',`Jefe de CD: despachar primero ${pv.length} ${pv.length===1?'lote que vence':'lotes que vencen'} en ≤ 7 días (${fmtKg(kgOf(pv))} kg).`]);
 if(ret.length)now.push(['warn',`Calidad: resolver ${ret.length} ${ret.length===1?'lote retenido':'lotes retenidos'} (${fmtKg(kgOf(ret))} kg).`]);
 if(d.ajustesPendientes>0)now.push(['warn',`Supervisor: resolver ${d.ajustesPendientes} ${d.ajustesPendientes===1?'ajuste pendiente':'ajustes pendientes'}.`]);
 if(d.actual.contados===0)now.push(['neutral','Operador: contar los lotes de mayor saldo.']);
 if(!now.length)now.push(['good','Sin acciones pendientes.']);
 const worst=kpis.some(x=>x.state==='bad')||venc.length?'bad':off.length||d.riesgos.length?'warn':kpis.every(x=>x.state==='neutral')?'neutral':'good';
 const headline=worst==='good'?'Inventario bajo control':worst==='neutral'?'Sin datos para el período':`${off.length} de 3 indicadores fuera de meta · ${d.riesgos.length} ${d.riesgos.length===1?'lote requiere':'lotes requieren'} acción`;
 return {what,why,now,worst,headline};
}
function Dashboard(){
 const day=today(),[from,setFrom]=useState(daysAgo(day,29)),[to,setTo]=useState(day),[center,setCenter]=useState(''),[product,setProduct]=useState(''),[year,setYear]=useState('1');
 const catalog=useCatalog();
 const q=useQuery<Row>({queryKey:['dashboard',from,to,center,product,year],queryFn:()=>api('/dashboard?'+new URLSearchParams({desde:from,hasta:to,anio:year,...(center?{centro:center}:{}),...(product?{producto:product}:{})}))});
 const d=q.data;
 function reset(){setFrom(daysAgo(day,29));setTo(day);setCenter('');setProduct('');setYear('1');}
 const presets:[string,string][]=[['7 días',daysAgo(day,6)],['30 días',daysAgo(day,29)],['90 días',daysAgo(day,89)],['Este mes',day.slice(0,8)+'01']];
 const activePreset=to===day?presets.find(([,f])=>f===from)?.[0]:undefined;
 const short=(x:string)=>x.slice(8)+'/'+x.slice(5,7);
 const centerName=catalog.data?.centros.find(x=>String(x.id)===String(center))?.nombre,prod=catalog.data?.productos.find(x=>String(x.id)===String(product));
 const chips:[string,()=>void][]=[];
 if(!activePreset)chips.push([from===to?`Día ${short(from)}`:`${short(from)} – ${short(to)}`,()=>{setFrom(daysAgo(day,29));setTo(day);}]);
 if(center)chips.push([centerName||'Centro',()=>setCenter('')]);
 if(product)chips.push([prod?`${prod.sku} · ${prod.nombre}`:'Producto',()=>setProduct('')]);
 if(year!=='1')chips.push([`Meta año ${year}`,()=>setYear('1')]);
 const kpis=d?evalKpis(d):[];const s=d?story(d,kpis):null;
 return <>
 <div className="page-head"><div><h1>Salud del inventario</h1></div><div className="page-actions">{d&&<p className="meta">{d.desde} → {d.hasta} · vs. {d.anteriorDesde} → {d.anteriorHasta}<br/>Actualizado {new Date(d.actualizado).toLocaleTimeString('es-BO',{timeZone:'America/La_Paz',hour:'2-digit',minute:'2-digit'})}</p>}<button className="quiet" onClick={()=>q.refetch()} disabled={q.isFetching}>{q.isFetching?'Consultando…':'Actualizar'}</button></div></div>
 {s&&<p className="headline" data-state={s.worst} role="status"><i aria-hidden="true"/>{s.headline}</p>}
 {q.error&&<p role="alert" className="error">{q.error.message}</p>}{catalog.error&&<p role="alert" className="error">{catalog.error.message}</p>}
 <div className="top-grid">
  <section className="kpis" aria-label="Indicadores del período">{d?kpis.map(x=><article className="kpi" data-state={x.state} key={x.k}><div className="kpi-title"><span>{x.title}</span><code>{x.code}</code></div><div className="value">{fmt(x.v)}{x.v!=null&&<span>%</span>}</div><p className="status"><i aria-hidden="true"/>{STATE_TEXT[x.state]}<span>Meta {x.lower?'≤':'≥'} {fmt(x.goal)} %</span></p><Gauge value={x.v} goal={x.goal} lower={x.lower}/><small className={x.better==null?'':x.better?'delta up':'delta down'}>{x.delta==null?'Sin período previo':x.delta===0?'Sin cambio vs. anterior':`${x.delta>0?'▲':'▼'} ${fmt(Math.abs(x.delta))} pp vs. anterior`}</small><small>{x.k==='merma'?`${fmtKg(d.actual.perdido)} / ${fmtKg(d.actual.ingreso)} kg recibidos`:x.k==='fefo'?`${d.actual.correctas} / ${d.actual.lineas} líneas`:`${d.actual.exactos} / ${d.actual.contados} lotes contados`}</small></article>):[0,1,2].map(i=><article className="kpi skeleton" key={i} aria-hidden="true"/>)}</section>
  <section className="filters" aria-label="Filtros globales">
   <div className="filters-head"><h2>Filtros</h2>{chips.length>0&&<span className="count">{chips.length}</span>}<button className="link" onClick={reset}>Restablecer</button></div>
   <div className="presets" role="group" aria-label="Período">{presets.map(([t,f])=><button key={t} type="button" aria-pressed={activePreset===t} onClick={()=>{setFrom(f);setTo(day);}}>{t}</button>)}</div>
   <div className="filter-row"><label>Desde<input type="date" value={from} max={to} onChange={e=>e.target.value&&setFrom(e.target.value)}/></label><label>Hasta<input type="date" value={to} min={from} max={day} onChange={e=>e.target.value&&setTo(e.target.value)}/></label></div>
   <label>Centro<select aria-label="Centro" value={center} onChange={e=>setCenter(e.target.value)}><option value="">Todos los centros</option>{catalog.data?.centros.map(x=><option key={x.id} value={x.id}>{x.nombre}</option>)}</select></label>
   <label>Producto<select aria-label="Producto" value={product} onChange={e=>setProduct(e.target.value)}><option value="">Todos los productos</option>{catalog.data?.productos.map(x=><option key={x.id} value={x.id}>{x.sku} · {x.nombre}</option>)}</select></label>
   <label>Meta<select aria-label="Meta del caso" value={year} onChange={e=>setYear(e.target.value)}>{[1,2,3].map(x=><option key={x} value={x}>Año {x}</option>)}</select></label>
   {chips.length>0&&<ul className="chips" aria-label="Filtros activos">{chips.map(([t,fn])=><li key={t}><button type="button" onClick={fn} aria-label={`Quitar ${t}`}>{t}<span aria-hidden="true">×</span></button></li>)}</ul>}
  </section>
 </div>
 {q.isPending&&<p role="status" className="muted">Consultando la base de datos…</p>}
 {d&&s&&<>
 <div className="analysis-grid"><section className="panel"><div className="panel-head"><div><h2>Movimientos del período</h2><p className="muted">Kg por día. Clic en un día para filtrar.</p></div><div className="legend" aria-hidden="true"><span><i className="recepcion"/>Recepción</span><span><i className="despacho"/>Despacho</span><span><i className="merma"/>Merma</span></div></div><Trend rows={d.tendencia} from={d.desde} to={d.hasta} select={(date)=>{setFrom(date);setTo(date);}}/></section><section className="panel"><h2>Mermas por producto</h2><p className="muted">Kg perdidos, top 10. Clic para filtrar.</p><Bars rows={d.productos} label="nombre" select={x=>setProduct(x.id)}/></section></div>
 <section className="story" aria-label="Lectura ejecutiva">
  <article data-state={s.worst==='good'?'good':s.worst}><p className="step"><span>1</span>Situación</p><p className="story-main">{s.what}</p></article>
  <article data-state="neutral"><p className="step"><span>2</span>Causa</p>{s.why.map((x,i)=><p key={i}>{x}</p>)}</article>
  <article data-state={s.now[0][0]}><p className="step"><span>3</span>Acciones</p><ul>{s.now.map(([st,t],i)=><li key={i} data-state={st}><i aria-hidden="true"/>{t}</li>)}</ul></article>
 </section>
 <section className="panel"><div className="panel-head"><div><h2>Lotes que requieren atención</h2><p className="muted">Stock al {d.fechaStock}.</p></div><dl className="stock"><div><dt>Físico</dt><dd title={`${fmt(d.stock.fisico,3)} kg`}>{fmtKg(d.stock.fisico)} kg</dd></div><div><dt>Elegible</dt><dd title={`${fmt(d.stock.elegible,3)} kg`}>{fmtKg(d.stock.elegible)} kg</dd></div></dl></div><div className="table-wrap"><table><thead><tr><th>Lote / producto</th><th>Centro</th><th>Vence</th><th className="num">Kg</th><th>Estado</th><th>Acción y responsable</th></tr></thead><tbody>{d.riesgos.map((x:Row)=><tr key={x.id}><td><strong>{x.codigo}</strong><small>{x.sku} · {x.nombre}</small></td><td>{x.centro}</td><td className="mono">{x.vence}</td><td className="num" title={`${fmt(x.kg,3)} kg`}>{fmtKg(x.kg)}</td><td><span className="pill" data-state={x.riesgo==='VENCIDO'?'bad':'warn'}>{x.riesgo}</span></td><td>{x.riesgo==='VENCIDO'?'Calidad: inmovilizar y evaluar disposición hoy.':x.riesgo==='RETENIDO'?'Calidad: revisar evidencia antes de liberar.':'Jefe de CD: priorizar FEFO en las próximas 24 h.'}</td></tr>)}</tbody></table></div>{!d.riesgos.length&&<p className="empty">Sin lotes vencidos, retenidos o por vencer en 7 días.</p>}{d.riesgos.length>=200&&<small>Se muestran los primeros 200 lotes.</small>}</section>
 </>}
 </>;
}
// Barra de progreso contra la meta: la marca vertical es la meta; el relleno, el valor del período.
function Gauge({value,goal,lower}:{value:number|null,goal:number,lower:boolean}){const max=lower?Math.max(goal*2,value??0)*1.1:100;const pct=(x:number)=>Math.min(100,x/max*100)+'%';return <div className="gauge" aria-hidden="true"><span className="fill" style={{width:value==null?0:pct(value)}}/><span className="goal" style={{left:pct(goal)}}/></div>;}
function Bars({rows,label,select}:{rows:Row[],label:string,select:(x:Row)=>void}){const max=Math.max(1,...rows.map(x=>Number(x.kg)));return rows.length?<div className="bars">{rows.map((x,i)=><button className="bar-row" key={i} onClick={()=>select(x)}><span>{x[label]}</span><span className="track"><span style={{width:(Number(x.kg)/max*100)+'%'}}/></span><strong title={`${fmt(x.kg,3)} kg`}>{fmtKg(x.kg)} kg</strong></button>)}</div>:<p className="empty">Sin mermas registradas para los filtros seleccionados.</p>;}
const series=['recepcion','despacho','merma'];
function Trend({rows,from,to,select}:{rows:Row[],from:string,to:string,select:(date:string)=>void}){
 // Mide el ancho real para dibujar en píxeles: el texto del eje no se encoge en pantallas angostas.
 const box=useRef<HTMLDivElement>(null),[W,setW]=useState(720);
 useEffect(()=>{const el=box.current;if(!el)return;const ro=new ResizeObserver(([e])=>setW(Math.max(280,Math.round(e.contentRect.width))));ro.observe(el);return()=>ro.disconnect();},[rows.length>0]);
 if(!rows.length)return <p className="empty">El gráfico aparecerá al registrar movimientos.</p>;
 // Eje continuo del período completo: los días sin operaciones se ven como huecos, no desaparecen.
 const byDate=new Map(rows.map(x=>[x.fecha,x]));const days:string[]=[];for(let t=Date.parse(from+'T00:00Z');t<=Date.parse(to+'T00:00Z');t+=864e5)days.push(new Date(t).toISOString().slice(0,10));
 const raw=Math.max(1,...rows.flatMap(x=>series.map(k=>Number(x[k]||0))));const step=10**Math.floor(Math.log10(raw));const max=Math.ceil(raw/step)*step;
 const H=230,L=44,B=24,T=10,plotW=W-L-8,plotH=H-B-T,slot=plotW/days.length,bar=Math.max(1.5,Math.min(16,slot*.8/3));const every=Math.ceil(days.length/Math.max(3,Math.floor(plotW/70)));
 return <div ref={box}><svg className="trend" width={W} height={H} viewBox={`0 0 ${W} ${H}`} role="group" aria-label="Kilogramos diarios por tipo de movimiento">
  {[0,.5,1].map(f=><g key={f}><line x1={L} x2={W-8} y1={T+plotH*(1-f)} y2={T+plotH*(1-f)} className={f?'grid':'axis'}/><text x={L-8} y={T+plotH*(1-f)+4} textAnchor="end">{fmt(max*f,0)}</text></g>)}
  {days.map((date,i)=>{const x=byDate.get(date),cx=L+slot*i+slot/2;return <g key={date} className="day" tabIndex={x?0:-1} role={x?'button':undefined} aria-label={x?`${date}: recepción ${x.recepcion||0}, despacho ${x.despacho||0}, merma ${x.merma||0} kg`:undefined} onClick={()=>x&&select(date)} onKeyDown={e=>{if(x&&(e.key==='Enter'||e.key===' ')){e.preventDefault();select(date);}}}>
   <rect className="hit" x={L+slot*i} y={T} width={slot} height={plotH}/>
   {x&&series.map((k,j)=>{const h=Number(x[k]||0)/max*plotH;return <rect key={k} className={k} x={cx-bar*1.5+j*bar} y={T+plotH-h} width={Math.max(1,bar-1)} height={h}><title>{`${date} · ${k}: ${fmt(x[k]||0,3)} kg`}</title></rect>;})}
   {((i%every===0&&days.length-1-i>=every*.6)||i===days.length-1)&&<text x={cx} y={H-6} textAnchor="middle">{date.slice(8)}/{date.slice(5,7)}</text>}
  </g>;})}
 </svg></div>;
}
type Field={name:string,label:string,type?:string,options?:Row[],valueKey?:string,labelKey?:string,min?:string,max?:string,step?:string};
function EntryForm({title,path,fields,onDone}:{title:string,path:string,fields:Field[],onDone:()=>void}){
 const [busy,setBusy]=useState(false),[message,setMessage]=useState(''),[failed,setFailed]=useState(false);const pending=useRef({payload:'',key:''});
 async function submit(e:React.FormEvent<HTMLFormElement>){e.preventDefault();const form=e.currentTarget;const body:Row=Object.fromEntries(new FormData(form));for(const f of fields)if(f.type==='number')body[f.name]=Number(body[f.name]);const payload=JSON.stringify(body);if(pending.current.payload!==payload)pending.current={payload,key:crypto.randomUUID()};setBusy(true);setMessage('');try{const r=await api(path,body,pending.current.key);setFailed(false);setMessage(r.mensaje||'Registro guardado.');pending.current={payload:'',key:''};form.reset();onDone();}catch(e){setFailed(true);setMessage((e as Error).message);}finally{setBusy(false);}}
 return <form className="entry" onSubmit={submit}><h2>{title}</h2><div className="form-fields">{fields.map(f=><label key={f.name}>{f.label}{f.options?<select aria-label={f.label} required name={f.name} defaultValue=""><option value="" disabled>Seleccionar…</option>{f.options.map(x=><option key={x[f.valueKey||'id']} value={x[f.valueKey||'id']}>{x[f.labelKey||'nombre']}</option>)}</select>:<input aria-label={f.label} required name={f.name} type={f.type||'text'} min={f.min} max={f.max} step={f.step} maxLength={f.name==='motivo'?240:f.name==='documento'?80:f.name==='sku'?40:120}/>}</label>)}</div><button disabled={busy}>{busy?'Guardando…':'Guardar '+title.toLowerCase()}</button><p role="status" className={failed?'error':'good'}>{message}</p></form>;
}
function Operations({user}:{user:Row}){
 const catalog=useCatalog(),lots=useQuery<Row[]>({queryKey:['lotes'],queryFn:()=>api('/lotes')}),client=useQueryClient();const done=()=>{client.invalidateQueries();};
 const role=user.roles.map((x:Row)=>x.authority);const products=catalog.data?.productos||[],centers=catalog.data?.centros||[],lotOptions=lots.data?.map(x=>({...x,nombre:`${x.codigo} · ${x.sku} · ${x.centro} · ${fmt(x.kg,3)} kg · ${x.estado}`}))||[];
 const product:Field={name:'producto',label:'Producto',options:products},center:Field={name:'centro',label:'Centro',options:centers},lot:Field={name:'lote',label:'Lote',options:lotOptions};const kg:Field={name:'kg',label:'Cantidad en kg',type:'number',step:'0.001',min:'0.001',max:'99999999999'};const doc:Field={name:'documento',label:'Documento de respaldo'},reason:Field={name:'motivo',label:'Motivo documentado'};
 return <><h1>Operaciones de inventario</h1><p>Recepciones, despachos, conteos y mermas con su documento de respaldo.</p>{(catalog.error||lots.error)&&<p className="error">Error al consultar los catálogos. Revise la conexión.</p>}<div className="forms-grid">
 {role.includes('ROLE_OPERADOR')&&<><EntryForm title="Centro" path="/centros" fields={[{name:'nombre',label:'Nombre del centro'}]} onDone={done}/><EntryForm title="Producto" path="/productos" fields={[{name:'sku',label:'SKU'},{name:'nombre',label:'Nombre'},{name:'familia',label:'Familia'}]} onDone={done}/><EntryForm title="Recepción" path="/recepciones" fields={[product,center,{name:'codigo',label:'Código de lote'},{name:'vence',label:'Vencimiento',type:'date',min:today()},kg,doc]} onDone={done}/><EntryForm title="Despacho FEFO" path="/despachos" fields={[product,center,kg,{name:'vidaMinima',label:'Vida útil mínima requerida en días',type:'number',min:'1',max:'3650',step:'1'},doc]} onDone={done}/><EntryForm title="Conteo físico" path="/conteos" fields={[lot,{...kg,min:'0'}]} onDone={done}/></>}
 {role.includes('ROLE_CALIDAD')&&<><EntryForm title="Decisión de Calidad" path="/decisiones" fields={[lot,{name:'estado',label:'Decisión',options:[{id:'LIBERADO',nombre:'Liberar'},{id:'RETENIDO',nombre:'Retener'}]},reason]} onDone={done}/><EntryForm title="Merma" path="/mermas" fields={[lot,kg,reason,doc]} onDone={done}/></>}
 {(role.includes('ROLE_OPERADOR')||role.includes('ROLE_CALIDAD'))&&<AdjustmentForm lots={lotOptions} types={role.includes('ROLE_OPERADOR')?['RECEPCION','DESPACHO']:['MERMA']} onDone={done}/>}
 {!role.includes('ROLE_OPERADOR')&&!role.includes('ROLE_CALIDAD')&&<p className="empty">{role.includes('ROLE_SUPERVISOR')?'Revisa los ajustes pendientes más abajo.':'Tu perfil es de solo lectura.'}</p>}</div><Adjustments canResolve={role.includes('ROLE_SUPERVISOR')} user={user.usuario} onDone={done}/><section className="panel"><h2>Saldos actuales por lote</h2><div className="table-wrap"><table><thead><tr><th>Lote</th><th>Producto</th><th>Centro</th><th>Vence</th><th>Estado</th><th>Kg físicos</th></tr></thead><tbody>{lots.data?.map(x=><tr key={x.id}><td>{x.codigo}</td><td>{x.sku}</td><td>{x.centro}</td><td>{x.vence}</td><td>{x.estado}</td><td>{fmt(x.kg,3)}</td></tr>)}</tbody></table></div>{!lots.data?.length&&<p className="empty">Aún no hay lotes. Primero crea un centro y un producto, luego registra una recepción.</p>}</section></>;
}
function useCommand(){
 const [busy,setBusy]=useState(false),[message,setMessage]=useState(''),[failed,setFailed]=useState(false);const pending=useRef({payload:'',key:''});
 // Reutiliza la clave idempotente mientras el contenido no cambie, igual que EntryForm.
 async function send(path:string,body:Row,onOk:()=>void){const payload=path+JSON.stringify(body);if(pending.current.payload!==payload)pending.current={payload,key:crypto.randomUUID()};setBusy(true);setMessage('');try{const r=await api(path,body,pending.current.key);setFailed(false);setMessage(r.mensaje||'Registro guardado.');pending.current={payload:'',key:''};onOk();}catch(e){setFailed(true);setMessage((e as Error).message);}finally{setBusy(false);}}
 return {busy,message,failed,send};
}
function AdjustmentForm({lots,types,onDone}:{lots:Row[],types:string[],onDone:()=>void}){
 const [lot,setLot]=useState('');const cmd=useCommand();
 const moves=useQuery<Row[]>({queryKey:['movimientos',lot],queryFn:()=>api(`/lotes/${lot}/movimientos`),enabled:!!lot});
 const options=(moves.data||[]).filter(x=>types.includes(x.tipo)&&Number(x.kg_efectivo)>0);
 function submit(e:React.FormEvent<HTMLFormElement>){e.preventDefault();const form=e.currentTarget;const f=Object.fromEntries(new FormData(form));cmd.send('/ajustes',{movimiento:f.movimiento,kgCorrecto:Number(f.kgCorrecto),motivo:f.motivo,documento:f.documento},()=>{form.reset();setLot('');onDone();});}
 return <form className="entry" onSubmit={submit}><h2>Ajuste de movimiento</h2><p className="muted">Corrige un error de captura. Lo aprueba otro supervisor. {types.includes('DESPACHO')?'Un despacho solo puede reducirse o anularse (0 kg).':''}</p><div className="form-fields">
 <label>Lote<select aria-label="Lote del ajuste" required value={lot} onChange={e=>setLot(e.target.value)}><option value="" disabled>Seleccionar…</option>{lots.map(x=><option key={x.id} value={x.id}>{x.nombre}</option>)}</select></label>
 <label>Movimiento a corregir<select aria-label="Movimiento a corregir" required name="movimiento" defaultValue="" key={lot} disabled={!lot}><option value="" disabled>{lot&&!moves.isPending&&!options.length?'Sin movimientos corregibles':'Seleccionar…'}</option>{options.map(x=><option key={x.id} value={x.id}>{x.tipo} · {fmt(x.kg_efectivo,3)} kg · {x.documento} · {new Date(x.creado).toLocaleDateString('es-BO',{timeZone:'America/La_Paz'})}</option>)}</select></label>
 <label>Cantidad correcta en kg<input aria-label="Cantidad correcta en kg" required name="kgCorrecto" type="number" min="0" step="0.001" max="99999999999"/></label>
 <label>Motivo documentado<input aria-label="Motivo del ajuste" required name="motivo" maxLength={240}/></label>
 <label>Documento de respaldo<input aria-label="Documento del ajuste" required name="documento" maxLength={80}/></label></div>
 <button disabled={cmd.busy}>{cmd.busy?'Guardando…':'Solicitar ajuste'}</button><p role="status" className={cmd.failed?'error':'good'}>{cmd.message}</p></form>;
}
function Adjustments({canResolve,user,onDone}:{canResolve:boolean,user:string,onDone:()=>void}){
 const q=useQuery<Row[]>({queryKey:['ajustes'],queryFn:()=>api('/ajustes')});const cmd=useCommand();const [comment,setComment]=useState<Record<string,string>>({});
 function resolve(id:string,aprobado:boolean){const comentario=(comment[id]||'').trim();if(!comentario)return;cmd.send(`/ajustes/${id}/resolucion`,{aprobado,comentario},()=>{setComment(c=>({...c,[id]:''}));onDone();});}
 return <section className="panel"><h2>Ajustes y contramovimientos</h2><p className="muted">El registro original se conserva; al aprobarse se usa la cantidad corregida.</p>{q.error&&<p className="error">{q.error.message}</p>}<div className="table-wrap"><table><thead><tr><th>Lote / movimiento</th><th>Kg registrados → correctos</th><th>Motivo y respaldo</th><th>Solicitado por</th><th>Estado</th>{canResolve&&<th>Resolución</th>}</tr></thead><tbody>{q.data?.map(x=><tr key={x.id}><td><strong>{x.lote}</strong><small>{x.tipo} · {x.sku} · {x.centro} · {x.fecha_movimiento}</small></td><td>{fmt(x.kg_original,3)} → {fmt(x.kg_correcto,3)}</td><td>{x.motivo}<small>{x.documento}</small></td><td>{x.usuario}</td><td><span className={x.estado==='APROBADO'?'good':x.estado==='RECHAZADO'?'bad':'warn'}>{x.estado}</span>{x.resuelto_por&&<small>{x.resuelto_por}: {x.comentario}</small>}</td>{canResolve&&<td>{x.estado==='PENDIENTE'?(x.usuario===user?<small>No puedes resolver tu propia solicitud.</small>:<div className="resolve"><input aria-label={`Comentario para ${x.lote}`} placeholder="Comentario obligatorio" maxLength={240} value={comment[x.id]||''} onChange={e=>setComment(c=>({...c,[x.id]:e.target.value}))}/><button disabled={cmd.busy||!(comment[x.id]||'').trim()} onClick={()=>resolve(x.id,true)}>Aprobar</button><button className="quiet" disabled={cmd.busy||!(comment[x.id]||'').trim()} onClick={()=>resolve(x.id,false)}>Rechazar</button></div>):'—'}</td>}</tr>)}</tbody></table></div>{!q.data?.length&&<p className="empty">No hay ajustes solicitados.</p>}<p role="status" className={cmd.failed?'error':'good'}>{cmd.message}</p></section>;
}
function Trace(){const [lot,setLot]=useState('');const lots=useQuery<Row[]>({queryKey:['lotes'],queryFn:()=>api('/lotes')});const q=useQuery<Row>({queryKey:['trace',lot],queryFn:()=>api(`/lotes/${lot}/trazabilidad`),enabled:!!lot});function download(){const url=URL.createObjectURL(new Blob([JSON.stringify(q.data,null,2)],{type:'application/json'}));const a=document.createElement('a');a.href=url;a.download=`trazabilidad-${lot}.json`;a.click();URL.revokeObjectURL(url);}return <><h1>Trazabilidad operativa</h1><p>Recepción, despachos, mermas, conteos y decisiones de Calidad de cada lote.</p><label className="trace-select">Lote<select aria-label="Lote" value={lot} onChange={e=>setLot(e.target.value)}><option value="">Selecciona un lote</option>{lots.data?.map(x=><option key={x.id} value={x.id}>{x.codigo} · {x.sku} · {x.centro}</option>)}</select></label>{q.error&&<p className="error">{q.error.message}</p>}{q.data&&<><button className="quiet" onClick={download}>Descargar evidencia JSON</button>{['movimientos','ajustes','calidad','conteos'].map(section=><section className="panel" key={section}><h2>{section}</h2><div className="table-wrap"><table><thead><tr>{(q.data![section][0]?Object.keys(q.data![section][0]).filter(k=>!['id','lote_id'].includes(k)):[]).map(k=><th key={k}>{k}</th>)}</tr></thead><tbody>{q.data![section].map((row:Row,i:number)=><tr key={i}>{Object.entries(row).filter(([k])=>!['id','lote_id'].includes(k)).map(([k,v])=><td key={k}>{String(v??'—')}</td>)}</tr>)}</tbody></table></div>{!q.data![section].length&&<p className="empty">Sin registros.</p>}</section>)}</>}</>;}
function Scope(){return <article className="reading"><h1>Cómo leer este dashboard</h1><h2>Unidades y comparaciones</h2><p>Todo se mide en kilogramos de producto terminado, con tres decimales. Los porcentajes se calculan en el servidor, nunca promediando porcentajes de centros. Un denominador cero produce “Sin datos”. La diferencia temporal se expresa en puntos porcentuales.</p><h2>Tres indicadores verificables</h2><p><strong>EST-03:</strong> kg de merma / kg recibidos en el período × 100. Las pérdidas pueden corresponder a inventario recibido antes del período; la tasa puede superar 100 % y debe investigarse.</p><p><strong>TAC-02:</strong> líneas de asignación despachadas según FEFO / líneas despachadas × 100. Los lotes deben estar liberados, no vencer hoy y cumplir la vida útil mínima indicada en el despacho.</p><p><strong>TAC-04:</strong> lotes cuyo último conteo del período coincide con el saldo esperado, con tolerancia ±0,5 % / lotes contados × 100. Si el saldo esperado es cero, el conteo debe ser exactamente cero.</p><h2>Decisiones y seguridad</h2><p>El operador recibe, despacha y cuenta; Calidad libera, retiene y declara mermas. Los movimientos no se editan ni se borran. Un error de captura se corrige con un ajuste: el operador (recepción o despacho) o Calidad (merma) lo solicita con motivo y respaldo, y un supervisor distinto lo aprueba o rechaza. El registro original se conserva; al aprobarse, saldo e indicadores usan la cantidad corregida en la fecha original. Los conteos no ajustan existencias automáticamente.</p><h2>Origen de las metas</h2><p>Año 1/2/3: merma ≤3,6/3,1/2,8 %; FEFO ≥95/97/98 %; exactitud ≥95/96,5/97 %.</p></article>;}
createRoot(document.getElementById('root')!).render(<React.StrictMode><QueryClientProvider client={queryClient}><App/></QueryClientProvider></React.StrictMode>);

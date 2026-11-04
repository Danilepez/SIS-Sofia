const {chromium}=require('../frontend/node_modules/playwright');
const fs=require('fs');const path=require('path');const assert=require('assert');
const root=path.resolve(__dirname,'..');
process.chdir(root);fs.mkdirSync('work',{recursive:true});
const env=Object.fromEntries(fs.readFileSync(path.join(root,'.env'),'utf8').trim().split(/\r?\n/).map(l=>{const i=l.indexOf('=');return [l.slice(0,i),l.slice(i+1)];}));
const passwords={operador:env.OPERATOR_PASSWORD,calidad:env.QUALITY_PASSWORD,consulta:env.READER_PASSWORD};
async function api(route,body,user='operador',key=crypto.randomUUID()){
 const r=await fetch('http://localhost:8080/api/v1'+route,{method:body?'POST':'GET',headers:{Authorization:'Basic '+Buffer.from(user+':'+passwords[user]).toString('base64'),'Content-Type':'application/json','X-Requested-With':'SIS-SOFIA','Idempotency-Key':key},...(body?{body:JSON.stringify(body)}:{})});
 const txt=await r.text();const d=txt?JSON.parse(txt):{};if(!r.ok)throw new Error(JSON.stringify({route,status:r.status,data:d}));return d;
}
(async()=>{
 const identity=await api('/me',undefined,'consulta');assert.equal(identity.entornoPruebas,true,'ABORTADO: el backend debe conectarse a una base _test');
 const stamp=Date.now();const day=new Intl.DateTimeFormat('en-CA',{timeZone:'America/La_Paz'}).format(new Date());const later=n=>{let d=new Date(day+'T12:00:00Z');d.setUTCDate(d.getUTCDate()+n);return d.toISOString().slice(0,10)};
 const c=await api('/centros',{nombre:'PRUEBA QA '+stamp});
 const p=await api('/productos',{sku:'QA-'+stamp,nombre:'Pollo fresco · PRUEBA',familia:'Fresco'});
 const p2=await api('/productos',{sku:'QB-'+stamp,nombre:'Procesado · PRUEBA',familia:'Procesado'});
 const l1=await api('/recepciones',{producto:p.id,centro:c.id,codigo:'QA-01',vence:later(3),kg:500,documento:'PRUEBA QA'});
 const l2=await api('/recepciones',{producto:p.id,centro:c.id,codigo:'QA-02',vence:later(20),kg:500,documento:'PRUEBA QA'});
 const l3=await api('/recepciones',{producto:p2.id,centro:c.id,codigo:'QA-03',vence:later(5),kg:200,documento:'PRUEBA QA'});
 for(const l of [l1,l2])await api('/decisiones',{lote:l.id,estado:'LIBERADO',motivo:'Liberación exclusiva de prueba'},'calidad');
 await api('/despachos',{producto:p.id,centro:c.id,kg:400,vidaMinima:1,documento:'QA-DESPACHO'});
 await api('/mermas',{lote:l1.id,kg:20,motivo:'Daño de empaque de prueba',documento:'QA-MERMA'},'calidad');
 await api('/mermas',{lote:l3.id,kg:30,motivo:'Daño de empaque de prueba',documento:'QA-MERMA'},'calidad');
 const browser=await chromium.launch({channel:'msedge',headless:true});
 const page=await browser.newPage({viewport:{width:1440,height:1050}});const consoleErrors=[];page.on('pageerror',e=>consoleErrors.push(e.message));
 await page.goto('http://127.0.0.1:5173');
 await page.getByLabel('Usuario').selectOption('operador');await page.getByLabel('Contraseña').fill(passwords.operador);await page.getByRole('button',{name:'Ingresar',exact:true}).click();await page.getByRole('heading',{name:'Salud del inventario'}).waitFor();
 await page.getByRole('button',{name:'Operaciones',exact:true}).click();
 const count=page.locator('form').filter({has:page.getByRole('heading',{name:'Conteo físico',exact:true})});
 await count.getByLabel('Lote',{exact:true}).selectOption(l1.id);await count.getByLabel('Cantidad en kg').fill('80');await count.getByRole('button',{name:'Guardar conteo físico'}).click();await count.getByText('Conteo registrado sin alterar el saldo').waitFor();
 const count2=page.locator('form').filter({has:page.getByRole('heading',{name:'Conteo físico',exact:true})});
 await count2.getByLabel('Lote',{exact:true}).selectOption(l2.id);await count2.getByLabel('Cantidad en kg').fill('510');await count2.getByRole('button',{name:'Guardar conteo físico'}).click();await count2.getByText('Conteo registrado sin alterar el saldo').waitFor();
 await page.getByRole('button',{name:'Dashboard',exact:true}).click();await page.getByLabel('Centro',{exact:true}).selectOption(c.id);
 await page.waitForFunction(()=>document.querySelectorAll('.kpi .value')[2]?.textContent?.includes('50'));
 await page.screenshot({path:'work/dashboard.png',fullPage:true});
 const values=await page.locator('.kpi .value').allTextContents();assert(values[0].includes('4,17')||values[0].includes('4.17'));assert(values[1].includes('100'));assert(values[2].includes('50'));
 // Cross filtering by product must update filter and KPI denominator.
 await page.locator('.bar-row').filter({hasText:'Procesado'}).click();await page.waitForFunction(()=>document.querySelector('.kpi>small')?.textContent?.includes('200'));
 assert.equal(await page.getByLabel('Producto',{exact:true}).inputValue(),p2.id);
 await page.getByRole('button',{name:'Restablecer',exact:true}).click();
 await page.getByRole('button',{name:'Trazabilidad',exact:true}).click();await page.getByLabel('Lote',{exact:true}).selectOption(l1.id);await page.getByRole('heading',{name:'movimientos',exact:true}).waitFor();
 await page.screenshot({path:'work/trace.png',fullPage:true});
 await page.getByRole('button',{name:'Salir',exact:true}).click();await page.getByLabel('Usuario').selectOption('consulta');await page.getByLabel('Contraseña').fill(passwords.consulta);await page.getByRole('button',{name:'Ingresar',exact:true}).click();await page.getByRole('heading',{name:'Salud del inventario'}).waitFor();
 await page.setViewportSize({width:390,height:844});await page.screenshot({path:'work/mobile.png',fullPage:true});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
 assert.deepEqual(consoleErrors,[]);
 // Concurrent stock commands run against the real HTTP server and PostgreSQL.
 const cp=await api('/productos',{sku:'QC-'+stamp,nombre:'Concurrencia PRUEBA',familia:'QA'});const cl=await api('/recepciones',{producto:cp.id,centro:c.id,codigo:'QA-CONCURRENT',vence:later(20),kg:10,documento:'PRUEBA QA'});await api('/decisiones',{lote:cl.id,estado:'LIBERADO',motivo:'Prueba de concurrencia'},'calidad');
 const concurrent=await Promise.allSettled([api('/despachos',{producto:cp.id,centro:c.id,kg:8,vidaMinima:1,documento:'C-1'}),api('/despachos',{producto:cp.id,centro:c.id,kg:8,vidaMinima:1,documento:'C-2'})]);assert.equal(concurrent.filter(x=>x.status==='fulfilled').length,1);
 const samples=[];for(let i=0;i<30;i++){const start=performance.now();await api('/dashboard?desde='+day+'&hasta='+day+'&centro='+c.id,undefined,'consulta');samples.push(performance.now()-start);}
 samples.sort((a,b)=>a-b);const result={date:day,tests:'UI login, conteos persistidos, KPIs, filtro cruzado, trazabilidad, logout, móvil, concurrencia HTTP',samples:30,p50Ms:samples[14],p95Ms:samples[28],maxMs:samples[29],dataset:'Base sofia_test; registros QA identificados; sin datos empresariales',center:c.id,consoleErrors};
 fs.writeFileSync('work/browser-results.json',JSON.stringify(result,null,2));console.log(JSON.stringify(result));await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});

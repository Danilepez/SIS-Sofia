package bo.sofia.sis.analitica;
import bo.sofia.sis.inventario.InventoryService;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.math.*;
import java.util.*;
@RestController @RequestMapping("/api/v1/dashboard")
public class DashboardController {
 private final JdbcTemplate db;
 public DashboardController(JdbcTemplate db){this.db=db;}
 public static BigDecimal ratio(BigDecimal n,BigDecimal d){return d.signum()==0?null:n.multiply(BigDecimal.valueOf(100)).divide(d,4,RoundingMode.HALF_UP);}
 @GetMapping @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
 public Map<String,Object> dashboard(@RequestParam LocalDate desde,@RequestParam LocalDate hasta,@RequestParam(required=false) UUID centro,@RequestParam(required=false) UUID producto,@RequestParam(defaultValue="1") int anio){
  long start=System.nanoTime();
  if(hasta.isBefore(desde)||ChronoUnit.DAYS.between(desde,hasta)>366||hasta.isAfter(InventoryService.today()))throw new IllegalArgumentException("Período inválido: máximo 367 días y sin fechas futuras");
  if(anio<1||anio>3) throw new IllegalArgumentException("Año de meta debe ser 1, 2 o 3");
  String filter="";List<Object> args=new ArrayList<>();
  if(centro!=null){filter+=" AND centro_id=?";args.add(centro);}
  if(producto!=null){filter+=" AND producto_id=?";args.add(producto);}
  var result=new LinkedHashMap<String,Object>();
  long days=ChronoUnit.DAYS.between(desde,hasta)+1;
  result.put("actual",metrics(desde,hasta,filter,args));
  result.put("anterior",metrics(desde.minusDays(days),desde.minusDays(1),filter,args));
  result.put("metas",Map.of("merma",new double[]{3.6,3.1,2.8}[anio-1],"fefo",new int[]{95,97,98}[anio-1],"exactitud",new double[]{95,96.5,97}[anio-1]));
  var period=new ArrayList<Object>(List.of(desde,hasta));period.addAll(args);
  result.put("tendencia",db.queryForList("SELECT fecha,sum(kg) FILTER(WHERE tipo='RECEPCION') recepcion,sum(kg) FILTER(WHERE tipo='MERMA') merma,sum(kg) FILTER(WHERE tipo='DESPACHO') despacho FROM analitica.hecho_movimiento WHERE fecha BETWEEN ? AND ?"+filter+" GROUP BY fecha ORDER BY fecha",period.toArray()));
  result.put("causas",db.queryForList("SELECT motivo,sum(kg) kg FROM analitica.hecho_movimiento WHERE tipo='MERMA' AND fecha BETWEEN ? AND ?"+filter+" GROUP BY motivo ORDER BY kg DESC,motivo LIMIT 10",period.toArray()));
  result.put("productos",db.queryForList("SELECT p.id,p.sku,p.nombre,sum(h.kg) kg FROM analitica.hecho_movimiento h JOIN catalogo.producto p ON p.id=h.producto_id WHERE tipo='MERMA' AND fecha BETWEEN ? AND ?"+filter+" GROUP BY p.id ORDER BY kg DESC,p.sku LIMIT 10",period.toArray()));
  // Current stock deliberately ignores period: an explicit current snapshot, not a historical balance.
  result.put("riesgos",db.queryForList("SELECT l.id,l.codigo,l.vence,l.estado,p.sku,p.nombre,c.nombre centro,s.kg,CASE WHEN l.vence<=? THEN 'VENCIDO' WHEN l.estado<>'LIBERADO' THEN 'RETENIDO' ELSE 'POR VENCER' END riesgo FROM inventario.lote l JOIN inventario.saldo s ON s.id=l.id JOIN catalogo.producto p ON p.id=l.producto_id JOIN catalogo.centro c ON c.id=l.centro_id WHERE s.kg>0 AND (l.vence<=? OR l.estado<>'LIBERADO')"+filter+" ORDER BY l.vence,l.codigo LIMIT 200",concat(List.of(InventoryService.today(),InventoryService.today().plusDays(7)),args)));
  result.put("stock",db.queryForMap("SELECT coalesce(sum(s.kg),0) fisico,coalesce(sum(s.kg) FILTER(WHERE l.estado='LIBERADO' AND l.vence>?),0) elegible FROM inventario.lote l JOIN inventario.saldo s ON s.id=l.id WHERE true"+filter,concat(List.of(InventoryService.today()),args)));
  result.put("desde",desde);result.put("hasta",hasta);result.put("anteriorDesde",desde.minusDays(days));result.put("anteriorHasta",desde.minusDays(1));
  result.put("actualizado",Instant.now());result.put("fechaStock",InventoryService.today());result.put("msServidor",(System.nanoTime()-start)/1_000_000d);
  return result;
 }
 private Object[] concat(List<?> first,List<Object> second){var r=new ArrayList<Object>(first);r.addAll(second);return r.toArray();}
 private Map<String,Object> metrics(LocalDate from,LocalDate to,String filter,List<Object> args){
  Object[] params=concat(List.of(from,to),args);
  var m=db.queryForMap("SELECT coalesce(sum(kg) FILTER(WHERE tipo='RECEPCION'),0) ingreso,coalesce(sum(kg) FILTER(WHERE tipo='MERMA'),0) perdido,count(*) FILTER(WHERE tipo='DESPACHO') lineas,count(*) FILTER(WHERE tipo='DESPACHO' AND fefo) correctas FROM analitica.hecho_movimiento WHERE fecha BETWEEN ? AND ?"+filter,params);
  var c=db.queryForMap("SELECT count(*) contados,count(*) FILTER(WHERE exacto) exactos FROM (SELECT DISTINCT ON(lote_id) lote_id,exacto FROM analitica.hecho_conteo WHERE fecha BETWEEN ? AND ?"+filter+" ORDER BY lote_id,creado DESC,id DESC) t",params);
  var out=new LinkedHashMap<String,Object>();out.putAll(m);out.putAll(c);
  out.put("merma",ratio((BigDecimal)m.get("perdido"),(BigDecimal)m.get("ingreso")));
  out.put("fefo",ratio(new BigDecimal(m.get("correctas").toString()),new BigDecimal(m.get("lineas").toString())));
  out.put("exactitud",ratio(new BigDecimal(c.get("exactos").toString()),new BigDecimal(c.get("contados").toString())));
  return out;
 }
}

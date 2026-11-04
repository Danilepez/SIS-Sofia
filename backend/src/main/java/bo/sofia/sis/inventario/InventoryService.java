package bo.sofia.sis.inventario;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
@Service
public class InventoryService {
 private final JdbcTemplate db; private final ObjectMapper json;
 public InventoryService(JdbcTemplate db,ObjectMapper json){this.db=db;this.json=json;}
 public static LocalDate today(){return LocalDate.now(ZoneId.of("America/La_Paz"));}
 @Transactional public Map<String,Object> command(UUID key,String actor,String type,Object input,Supplier<Map<String,Object>> work) {
  // V0.1 serializes stock commands; prevents concurrent overselling and quality races.
  db.execute("SELECT pg_advisory_xact_lock(736463)");
  try {
   String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((actor+type+json.writeValueAsString(input)).getBytes(StandardCharsets.UTF_8)));
   var old=db.queryForList("SELECT huella,respuesta FROM soporte.comando WHERE clave=?",key);
   if(!old.isEmpty()) {
    if(!hash.equals(old.getFirst().get("huella"))) throw new IllegalStateException("La clave idempotente ya corresponde a otra operación");
    return json.readValue(old.getFirst().get("respuesta").toString(),Map.class);
   }
   Map<String,Object> result=work.get();
   db.update("INSERT INTO soporte.comando(clave,huella,respuesta) VALUES (?,?,?)",key,hash,json.writeValueAsString(result));
   return result;
  } catch(IllegalArgumentException | IllegalStateException e){throw e;} catch(Exception e){throw new IllegalStateException("No se completó la transacción",e);}
 }
 public Map<String,Object> receive(UUID product,UUID center,String code,LocalDate expiry,BigDecimal kg,String document,String actor) {
  if(!expiry.isAfter(today())) throw new IllegalArgumentException("El vencimiento debe ser posterior a hoy");
  UUID id=UUID.randomUUID();
  db.update("INSERT INTO inventario.lote(id,producto_id,centro_id,codigo,vence) VALUES (?,?,?,?,?)",id,product,center,code,expiry);
  movement(id,"RECEPCION",kg,"Recepción retenida para Calidad",document,null,actor);
  return Map.of("id",id,"mensaje","Recepción registrada. Lote RETENIDO.");
 }
 public Map<String,Object> quality(UUID id,String status,String reason,String actor){
  var lot=lot(id);
  if(!Set.of("RETENIDO","LIBERADO").contains(status)) throw new IllegalArgumentException("Estado inválido");
  if(status.equals("LIBERADO")&&!((java.sql.Date)lot.get("vence")).toLocalDate().isAfter(today())) throw new IllegalStateException("No se puede liberar un lote vencido o que vence hoy");
  db.update("UPDATE inventario.lote SET estado=? WHERE id=?",status,id);
  db.update("INSERT INTO calidad.decision(id,lote_id,estado,motivo,usuario) VALUES (?,?,?,?,?)",UUID.randomUUID(),id,status,reason,actor);
  return Map.of("mensaje","Decisión de Calidad registrada");
 }
 public Map<String,Object> dispatch(UUID product,UUID center,BigDecimal kg,int minDays,String document,String actor){
  var lots=db.queryForList("SELECT l.*,s.kg FROM inventario.lote l JOIN inventario.saldo s ON s.id=l.id WHERE l.producto_id=? AND l.centro_id=? AND l.estado='LIBERADO' AND l.vence>=? AND s.kg>0 ORDER BY l.vence,l.creado,l.id",product,center,today().plusDays(Math.max(1,minDays)));
  BigDecimal available=lots.stream().map(x->(BigDecimal)x.get("kg")).reduce(BigDecimal.ZERO,BigDecimal::add);
  if(available.compareTo(kg)<0) throw new IllegalStateException("ATP insuficiente: "+available+" kg elegibles");
  BigDecimal remaining=kg; var allocations=new ArrayList<Map<String,Object>>();
  for(var lot:lots) {
   if(remaining.signum()==0) break;
   var take=remaining.min((BigDecimal)lot.get("kg")); var id=(UUID)lot.get("id");
   movement(id,"DESPACHO",take,"FEFO automático; vida mínima "+minDays+" días",document,true,actor);
   allocations.add(Map.of("lote",lot.get("codigo"),"kg",take)); remaining=remaining.subtract(take);
  }
  return Map.of("mensaje","Despacho registrado","asignaciones",allocations);
 }
 public Map<String,Object> loss(UUID id,BigDecimal kg,String reason,String document,String actor) {
  lot(id); var balance=balance(id);
  if(kg.compareTo(balance)>0) throw new IllegalStateException("La merma supera el saldo físico");
  movement(id,"MERMA",kg,reason,document,null,actor); return Map.of("mensaje","Merma registrada y saldo actualizado");
 }
 public Map<String,Object> count(UUID id,BigDecimal counted,String actor) {
  lot(id); var expected=balance(id);
  db.update("INSERT INTO inventario.conteo(id,lote_id,esperado,contado,usuario) VALUES (?,?,?,?,?)",UUID.randomUUID(),id,expected,counted,actor);
  return Map.of("mensaje","Conteo registrado sin alterar el saldo","esperado",expected,"contado",counted);
 }
 private Map<String,Object> lot(UUID id) {var rows=db.queryForList("SELECT * FROM inventario.lote WHERE id=?",id);if(rows.isEmpty())throw new IllegalArgumentException("Lote inexistente");return rows.getFirst();}
 private BigDecimal balance(UUID id){return db.queryForObject("SELECT kg FROM inventario.saldo WHERE id=?",BigDecimal.class,id);}
 private void movement(UUID id,String type,BigDecimal kg,String reason,String document,Boolean fefo,String actor){
  db.update("INSERT INTO inventario.movimiento(id,lote_id,tipo,kg,motivo,documento,fefo,usuario) VALUES (?,?,?,?,?,?,?,?)",UUID.randomUUID(),id,type,kg,reason,document,fefo,actor);
 }
 public List<Map<String,Object>> lots(){return db.queryForList("SELECT l.*,p.sku,p.nombre,c.nombre centro,s.kg FROM inventario.lote l JOIN catalogo.producto p ON p.id=l.producto_id JOIN catalogo.centro c ON c.id=l.centro_id JOIN inventario.saldo s ON s.id=l.id ORDER BY l.vence,l.codigo");}
 public Map<String,Object> trace(UUID id){return Map.of("lote",lot(id),"movimientos",db.queryForList("SELECT * FROM inventario.movimiento WHERE lote_id=? ORDER BY creado,id",id),"calidad",db.queryForList("SELECT * FROM calidad.decision WHERE lote_id=? ORDER BY creado,id",id),"conteos",db.queryForList("SELECT * FROM inventario.conteo WHERE lote_id=? ORDER BY creado,id",id));}
}

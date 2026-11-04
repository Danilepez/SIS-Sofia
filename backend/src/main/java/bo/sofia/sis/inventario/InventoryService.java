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
  } catch(IllegalArgumentException | IllegalStateException | SecurityException e){throw e;} catch(Exception e){throw new IllegalStateException("No se completó la transacción",e);}
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
 // Contramovimiento: quien registró el tipo de hecho solicita; un supervisor distinto aprueba. Nada se borra ni se edita.
 public Map<String,Object> requestAdjustment(UUID movementId,BigDecimal correct,String reason,String document,String actor,Set<String> roles) {
  var m=effectiveMovement(movementId); String type=(String)m.get("tipo"); BigDecimal current=(BigDecimal)m.get("kg_efectivo");
  if(!roles.contains(type.equals("MERMA")?"ROLE_CALIDAD":"ROLE_OPERADOR")) throw new SecurityException(type.equals("MERMA")?"Solo Calidad corrige mermas":"Solo Operador corrige recepciones y despachos");
  if(correct.compareTo(current)==0) throw new IllegalArgumentException("La cantidad correcta es igual a la vigente");
  if(type.equals("DESPACHO")&&correct.compareTo(current)>0) throw new IllegalArgumentException("Un despacho solo se reduce o anula; una salida adicional se registra como nuevo despacho FEFO");
  if(pending(movementId)) throw new IllegalStateException("El movimiento ya tiene un ajuste pendiente de aprobación");
  checkBalance(m,current,correct);
  UUID id=UUID.randomUUID();
  db.update("INSERT INTO inventario.ajuste(id,movimiento_id,kg_original,kg_correcto,motivo,documento,usuario) VALUES (?,?,?,?,?,?,?)",id,movementId,current,correct,reason,document,actor);
  return Map.of("id",id,"mensaje","Ajuste solicitado. Pendiente de aprobación del supervisor.");
 }
 public Map<String,Object> resolveAdjustment(UUID id,boolean approved,String comment,String actor) {
  var rows=db.queryForList("SELECT a.*,r.ajuste_id resuelto FROM inventario.ajuste a LEFT JOIN inventario.ajuste_resolucion r ON r.ajuste_id=a.id WHERE a.id=?",id);
  if(rows.isEmpty()) throw new IllegalArgumentException("Ajuste inexistente");
  var a=rows.getFirst();
  if(a.get("resuelto")!=null) throw new IllegalStateException("El ajuste ya fue resuelto");
  if(actor.equals(a.get("usuario"))) throw new IllegalStateException("Quien solicita un ajuste no puede aprobarlo");
  if(approved) {
   var m=effectiveMovement((UUID)a.get("movimiento_id")); BigDecimal current=(BigDecimal)m.get("kg_efectivo");
   if(current.compareTo((BigDecimal)a.get("kg_original"))!=0) throw new IllegalStateException("El movimiento cambió desde la solicitud; rechace y solicite de nuevo");
   checkBalance(m,current,(BigDecimal)a.get("kg_correcto"));
  }
  db.update("INSERT INTO inventario.ajuste_resolucion(ajuste_id,aprobado,comentario,usuario) VALUES (?,?,?,?)",id,approved,comment,actor);
  return Map.of("mensaje",approved?"Ajuste aprobado. El saldo y los indicadores usan la cantidad corregida.":"Ajuste rechazado. Se conserva la cantidad vigente.");
 }
 public List<Map<String,Object>> adjustments(){return db.queryForList("SELECT a.id,a.movimiento_id,m.tipo,m.documento documento_original,(m.creado AT TIME ZONE 'America/La_Paz')::date fecha_movimiento,l.codigo lote,p.sku,c.nombre centro,a.kg_original,a.kg_correcto,a.motivo,a.documento,a.usuario,a.creado,CASE WHEN r.ajuste_id IS NULL THEN 'PENDIENTE' WHEN r.aprobado THEN 'APROBADO' ELSE 'RECHAZADO' END estado,r.usuario resuelto_por,r.comentario FROM inventario.ajuste a JOIN inventario.movimiento m ON m.id=a.movimiento_id JOIN inventario.lote l ON l.id=m.lote_id JOIN catalogo.producto p ON p.id=l.producto_id JOIN catalogo.centro c ON c.id=l.centro_id LEFT JOIN inventario.ajuste_resolucion r ON r.ajuste_id=a.id ORDER BY r.ajuste_id IS NOT NULL,a.creado DESC LIMIT 200");}
 public List<Map<String,Object>> movements(UUID lot){lot(lot);return db.queryForList("SELECT id,tipo,kg,kg_efectivo,documento,motivo,usuario,creado FROM inventario.movimiento_efectivo WHERE lote_id=? ORDER BY creado,id",lot);}
 private Map<String,Object> effectiveMovement(UUID id){var rows=db.queryForList("SELECT * FROM inventario.movimiento_efectivo WHERE id=?",id);if(rows.isEmpty())throw new IllegalArgumentException("Movimiento inexistente");return rows.getFirst();}
 private boolean pending(UUID movementId){return db.queryForObject("SELECT count(*) FROM inventario.ajuste a LEFT JOIN inventario.ajuste_resolucion r ON r.ajuste_id=a.id WHERE a.movimiento_id=? AND r.ajuste_id IS NULL",Integer.class,movementId)>0;}
 private void checkBalance(Map<String,Object> m,BigDecimal current,BigDecimal correct){
  // Una recepción menor o una merma/despacho mayor reducen el saldo; nunca puede quedar negativo.
  BigDecimal delta=correct.subtract(current); if(!m.get("tipo").equals("RECEPCION")) delta=delta.negate();
  if(balance((UUID)m.get("lote_id")).add(delta).signum()<0) throw new IllegalStateException("El ajuste dejaría saldo negativo en el lote");
 }
 private Map<String,Object> lot(UUID id) {var rows=db.queryForList("SELECT * FROM inventario.lote WHERE id=?",id);if(rows.isEmpty())throw new IllegalArgumentException("Lote inexistente");return rows.getFirst();}
 private BigDecimal balance(UUID id){return db.queryForObject("SELECT kg FROM inventario.saldo WHERE id=?",BigDecimal.class,id);}
 private void movement(UUID id,String type,BigDecimal kg,String reason,String document,Boolean fefo,String actor){
  db.update("INSERT INTO inventario.movimiento(id,lote_id,tipo,kg,motivo,documento,fefo,usuario) VALUES (?,?,?,?,?,?,?,?)",UUID.randomUUID(),id,type,kg,reason,document,fefo,actor);
 }
 public List<Map<String,Object>> lots(){return db.queryForList("SELECT l.*,p.sku,p.nombre,c.nombre centro,s.kg FROM inventario.lote l JOIN catalogo.producto p ON p.id=l.producto_id JOIN catalogo.centro c ON c.id=l.centro_id JOIN inventario.saldo s ON s.id=l.id ORDER BY l.vence,l.codigo");}
 public Map<String,Object> trace(UUID id){return Map.of("lote",lot(id),"movimientos",db.queryForList("SELECT id,tipo,kg kg_registrado,kg_efectivo,motivo,documento,fefo,usuario,creado FROM inventario.movimiento_efectivo WHERE lote_id=? ORDER BY creado,id",id),"ajustes",db.queryForList("SELECT a.id,m.tipo,a.kg_original,a.kg_correcto,a.motivo,a.documento,a.usuario solicitado_por,a.creado,CASE WHEN r.ajuste_id IS NULL THEN 'PENDIENTE' WHEN r.aprobado THEN 'APROBADO' ELSE 'RECHAZADO' END estado,r.usuario resuelto_por,r.comentario,r.creado resuelto FROM inventario.ajuste a JOIN inventario.movimiento m ON m.id=a.movimiento_id LEFT JOIN inventario.ajuste_resolucion r ON r.ajuste_id=a.id WHERE m.lote_id=? ORDER BY a.creado,a.id",id),"calidad",db.queryForList("SELECT * FROM calidad.decision WHERE lote_id=? ORDER BY creado,id",id),"conteos",db.queryForList("SELECT * FROM inventario.conteo WHERE lote_id=? ORDER BY creado,id",id));}
}

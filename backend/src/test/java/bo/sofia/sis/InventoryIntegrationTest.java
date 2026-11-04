package bo.sofia.sis;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.*;
import bo.sofia.sis.inventario.InventoryService;
import bo.sofia.sis.analitica.DashboardController;
import java.util.*;
import java.math.BigDecimal;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest @AutoConfigureMockMvc @Transactional
class InventoryIntegrationTest {
 @Autowired org.springframework.core.env.Environment env;
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate db;
 UUID product,center;
 @BeforeEach void setup(){
  assertTrue(db.queryForObject("SELECT current_database()",String.class).endsWith("_test"),"Use una base terminada en _test");
  product=UUID.randomUUID();center=UUID.randomUUID();
  db.update("INSERT INTO catalogo.producto(id,sku,nombre,familia) VALUES (?,?,?,?)",product,"TEST-"+product.toString().substring(0,8),"Producto de prueba","Fresco");
  db.update("INSERT INTO catalogo.centro(id,nombre) VALUES (?,?)",center,"TEST-"+center);
 }
 ResultActions postAs(String path,Map<String,Object> body,String role,UUID key)throws Exception {
  return mvc.perform(post("/api/v1"+path).with(user(role.toLowerCase()).roles(role,"LECTOR"))
   .header("X-Requested-With","SIS-SOFIA").header("Idempotency-Key",key).contentType("application/json").content(json.writeValueAsBytes(body)));
 }
 Map<String,Object> receipt(String code,int days,int kg){return Map.of("producto",product,"centro",center,"codigo",code,"vence",InventoryService.today().plusDays(days).toString(),"kg",kg,"documento","TEST-RECEPCION");}
 UUID receive(String code,int days,int kg)throws Exception {return UUID.fromString(json.readTree(postAs("/recepciones",receipt(code,days,kg),"OPERADOR",UUID.randomUUID()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asText());}
 void release(UUID lot)throws Exception {postAs("/decisiones",Map.of("lote",lot,"estado","LIBERADO","motivo","Prueba de autorización"),"CALIDAD",UUID.randomUUID()).andExpect(status().isOk());}
 Map<String,Object> dispatch(int kg){return Map.of("producto",product,"centro",center,"kg",kg,"vidaMinima",1,"documento","TEST-DESPACHO");}
 JsonNode dashboard()throws Exception {String d=InventoryService.today().toString();return json.readTree(mvc.perform(get("/api/v1/dashboard").with(user("consulta").roles("LECTOR")).param("desde",d).param("hasta",d).param("centro",center.toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
 @Test void completeFlowAndExactKpis()throws Exception {
  UUID first=receive("A",10,500),second=receive("B",20,500);release(first);release(second);
  postAs("/despachos",dispatch(600),"OPERADOR",UUID.randomUUID()).andExpect(status().isOk());
  assertEquals(0,db.queryForObject("SELECT kg FROM inventario.saldo WHERE id=?",BigDecimal.class,first).compareTo(BigDecimal.ZERO));
  postAs("/mermas",Map.of("lote",second,"kg",10,"motivo","Daño de prueba","documento","TEST-MERMA"),"CALIDAD",UUID.randomUUID()).andExpect(status().isOk());
  postAs("/conteos",Map.of("lote",second,"kg",390),"OPERADOR",UUID.randomUUID()).andExpect(status().isOk());
  var d=dashboard();assertEquals(1.0,d.at("/actual/merma").asDouble());assertEquals(100,d.at("/actual/fefo").asDouble());assertEquals(100,d.at("/actual/exactitud").asDouble());assertEquals(2,d.at("/actual/lineas").asInt());assertEquals(390,d.at("/stock/fisico").asDouble());
 }
 @Test void retainedLotCannotBeDispatchedAndFailureDoesNotPersist()throws Exception {
  receive("RET",10,50);postAs("/despachos",dispatch(1),"OPERADOR",UUID.randomUUID()).andExpect(status().isConflict());assertEquals(0,dashboard().at("/actual/lineas").asInt());
 }
 @Test void idempotentReceiptAndPayloadConflict()throws Exception {
  UUID key=UUID.randomUUID();var body=receipt("IDEMP",10,100);
  var a=postAs("/recepciones",body,"OPERADOR",key).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  var b=postAs("/recepciones",body,"OPERADOR",key).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertEquals(json.readTree(a),json.readTree(b));
  postAs("/recepciones",receipt("OTHER",10,200),"OPERADOR",key).andExpect(status().isConflict());assertEquals(100,dashboard().at("/actual/ingreso").asDouble());
 }
 @Test void insufficientStockIsAtomic()throws Exception {var id=receive("ATP",10,10);release(id);postAs("/despachos",dispatch(11),"OPERADOR",UUID.randomUUID()).andExpect(status().isConflict());assertEquals(10,dashboard().at("/stock/fisico").asDouble());}
 @Test void exactnessToleranceAndLatestCount()throws Exception {var id=receive("COUNT",10,100);postAs("/conteos",Map.of("lote",id,"kg",100.5),"OPERADOR",UUID.randomUUID()).andExpect(status().isOk());assertEquals(100,dashboard().at("/actual/exactitud").asDouble());postAs("/conteos",Map.of("lote",id,"kg",100.501),"OPERADOR",UUID.randomUUID()).andExpect(status().isOk());assertEquals(0,dashboard().at("/actual/exactitud").asDouble());assertEquals(1,dashboard().at("/actual/contados").asInt());}
 @Test void zeroDenominatorsAreNull()throws Exception {var d=dashboard();assertTrue(d.at("/actual/merma").isNull());assertTrue(d.at("/actual/fefo").isNull());assertTrue(d.at("/actual/exactitud").isNull());}
 @Test void rolesAndAuthentication()throws Exception {mvc.perform(get("/api/v1/catalogo")).andExpect(status().isUnauthorized());postAs("/recepciones",receipt("NO",10,10),"LECTOR",UUID.randomUUID()).andExpect(status().isForbidden());var lot=receive("ROLE",10,10);postAs("/decisiones",Map.of("lote",lot,"estado","LIBERADO","motivo","No autorizado"),"OPERADOR",UUID.randomUUID()).andExpect(status().isForbidden());}
 @Test void invalidQuantityAndExpiredReceipt()throws Exception {postAs("/recepciones",receipt("ZERO",10,0),"OPERADOR",UUID.randomUUID()).andExpect(status().isBadRequest());postAs("/recepciones",receipt("PAST",-1,10),"OPERADOR",UUID.randomUUID()).andExpect(status().isBadRequest());}
 @Test void minimumShelfLifeFiltersFefo()throws Exception {var a=receive("SHORT",2,10);var b=receive("LONG",20,10);release(a);release(b);var body=new HashMap<>(dispatch(5));body.put("vidaMinima",10);postAs("/despachos",body,"OPERADOR",UUID.randomUUID()).andExpect(status().isOk());assertEquals(10,db.queryForObject("SELECT kg FROM inventario.saldo WHERE id=?",BigDecimal.class,a).intValue());}
 @Test void lossCannotExceedStock()throws Exception {var id=receive("LOSS",10,10);postAs("/mermas",Map.of("lote",id,"kg",11,"motivo","Prueba","documento","TEST"),"CALIDAD",UUID.randomUUID()).andExpect(status().isConflict());assertEquals(10,dashboard().at("/stock/fisico").asDouble());}
 @Test void filterExcludesOtherCentersAndValidatesDates()throws Exception {receive("FILTER",10,10);String date=InventoryService.today().toString();mvc.perform(get("/api/v1/dashboard").with(user("r").roles("LECTOR")).param("desde",date).param("hasta",date).param("centro",UUID.randomUUID().toString())).andExpect(status().isOk()).andExpect(jsonPath("$.actual.ingreso").value(0));mvc.perform(get("/api/v1/dashboard").with(user("r").roles("LECTOR")).param("desde",date).param("hasta",InventoryService.today().minusDays(1).toString())).andExpect(status().isBadRequest());}
 @Test void ratiosUseWeightedNumeratorNotAverage(){assertEquals(new BigDecimal("10.0000"),DashboardController.ratio(new BigDecimal("10"),new BigDecimal("100")));assertNull(DashboardController.ratio(BigDecimal.ONE,BigDecimal.ZERO));}
 UUID movement(UUID lot,String type){return db.queryForObject("SELECT id FROM inventario.movimiento WHERE lote_id=? AND tipo=? ORDER BY creado LIMIT 1",UUID.class,lot,type);}
 UUID requestAdjustment(UUID movement,double kg,String role)throws Exception {return UUID.fromString(json.readTree(postAs("/ajustes",Map.of("movimiento",movement,"kgCorrecto",kg,"motivo","Error de digitación","documento","TEST-AJUSTE"),role,UUID.randomUUID()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("id").asText());}
 ResultActions resolve(UUID adjustment,boolean approved,String role)throws Exception {return postAs("/ajustes/"+adjustment+"/resolucion",Map.of("aprobado",approved,"comentario","Revisado con respaldo"),role,UUID.randomUUID());}
 BigDecimal balance(UUID lot){return db.queryForObject("SELECT kg FROM inventario.saldo WHERE id=?",BigDecimal.class,lot);}
 @Test void approvedReceiptCorrectionRestatesBalanceAndKpiWithoutEditingHistory()throws Exception {
  var lot=receive("ADJ",10,100);release(lot);
  postAs("/mermas",Map.of("lote",lot,"kg",5,"motivo","Daño","documento","TEST-MERMA"),"CALIDAD",UUID.randomUUID()).andExpect(status().isOk());
  var adj=requestAdjustment(movement(lot,"RECEPCION"),90,"OPERADOR");
  assertEquals(95,balance(lot).intValue(),"Pendiente no altera saldo");assertEquals(5.0,dashboard().at("/actual/merma").asDouble());
  resolve(adj,true,"SUPERVISOR").andExpect(status().isOk());
  assertEquals(85,balance(lot).intValue());var d=dashboard();assertEquals(90,d.at("/actual/ingreso").asDouble());assertEquals(new BigDecimal("5.5556"),d.at("/actual/merma").decimalValue());
  assertEquals(100,db.queryForObject("SELECT kg FROM inventario.movimiento WHERE lote_id=? AND tipo='RECEPCION'",BigDecimal.class,lot).intValue(),"El hecho original se conserva");
  resolve(adj,true,"SUPERVISOR").andExpect(status().isConflict());
 }
 @Test void rejectedAdjustmentKeepsQuantityAndAnnulledDispatchLeavesFefoDenominator()throws Exception {
  var lot=receive("ANUL",10,50);release(lot);postAs("/despachos",dispatch(20),"OPERADOR",UUID.randomUUID()).andExpect(status().isOk());
  var dispatchLine=movement(lot,"DESPACHO");
  resolve(requestAdjustment(dispatchLine,10,"OPERADOR"),false,"SUPERVISOR").andExpect(status().isOk());assertEquals(30,balance(lot).intValue());
  resolve(requestAdjustment(dispatchLine,0,"OPERADOR"),true,"SUPERVISOR").andExpect(status().isOk());
  assertEquals(50,balance(lot).intValue());assertEquals(0,dashboard().at("/actual/lineas").asInt());
 }
 @Test void adjustmentRulesSegregationAndNonNegativeBalance()throws Exception {
  var lot=receive("RULES",10,100);release(lot);postAs("/despachos",dispatch(60),"OPERADOR",UUID.randomUUID()).andExpect(status().isOk());
  var receipt=movement(lot,"RECEPCION");
  postAs("/ajustes",Map.of("movimiento",receipt,"kgCorrecto",30,"motivo","Saldo negativo","documento","T"),"OPERADOR",UUID.randomUUID()).andExpect(status().isConflict());
  postAs("/ajustes",Map.of("movimiento",movement(lot,"DESPACHO"),"kgCorrecto",70,"motivo","Aumento","documento","T"),"OPERADOR",UUID.randomUUID()).andExpect(status().isBadRequest());
  postAs("/ajustes",Map.of("movimiento",receipt,"kgCorrecto",90,"motivo","Rol","documento","T"),"CALIDAD",UUID.randomUUID()).andExpect(status().isForbidden());
  var adj=requestAdjustment(receipt,90,"OPERADOR");
  postAs("/ajustes",Map.of("movimiento",receipt,"kgCorrecto",80,"motivo","Duplicado","documento","T"),"OPERADOR",UUID.randomUUID()).andExpect(status().isConflict());
  resolve(adj,true,"OPERADOR").andExpect(status().isForbidden());resolve(adj,true,"CALIDAD").andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/ajustes/"+adj+"/resolucion").with(user("operador").roles("SUPERVISOR","LECTOR")).header("X-Requested-With","SIS-SOFIA").header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(json.writeValueAsBytes(Map.of("aprobado",true,"comentario","Autoaprobación")))).andExpect(status().isConflict());
  assertEquals(40,balance(lot).intValue());
  assertThrows(Exception.class,()->db.update("DELETE FROM inventario.ajuste WHERE id=?",adj));
 }
 @Test void actualPasswordAuthentication()throws Exception {
  mvc.perform(get("/api/v1/me").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("consulta",env.getProperty("sofia.reader-password")))).andExpect(status().isOk()).andExpect(jsonPath("$.usuario").value("consulta"));
  mvc.perform(get("/api/v1/me").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("consulta","incorrecta"))).andExpect(status().isUnauthorized());
 }
}

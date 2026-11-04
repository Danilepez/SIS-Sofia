package bo.sofia.sis.inventario;
import bo.sofia.sis.catalogo.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
@RestController @RequestMapping("/api/v1")
public class InventoryController {
 private final InventoryService service;private final JdbcTemplate db;private final ProductoRepository products;
 public InventoryController(InventoryService service,JdbcTemplate db,ProductoRepository products){this.service=service;this.db=db;this.products=products;}
 public record Product(@NotBlank @Size(max=40) String sku,@NotBlank @Size(max=120) String nombre,@NotBlank @Size(max=60) String familia){}
 public record Center(@NotBlank @Size(max=100) String nombre){}
 public record Receipt(@NotNull UUID producto,@NotNull UUID centro,@NotBlank @Size(max=60) String codigo,@NotNull LocalDate vence,@NotNull @DecimalMin("0.001") @Digits(integer=11,fraction=3) BigDecimal kg,@NotBlank @Size(max=80) String documento){}
 public record Quality(@NotNull UUID lote,@Pattern(regexp="LIBERADO|RETENIDO") @NotNull String estado,@NotBlank @Size(max=240) String motivo){}
 public record Dispatch(@NotNull UUID producto,@NotNull UUID centro,@NotNull @DecimalMin("0.001") @Digits(integer=11,fraction=3) BigDecimal kg,@Min(1) @Max(3650) int vidaMinima,@NotBlank @Size(max=80) String documento){}
 public record Loss(@NotNull UUID lote,@NotNull @DecimalMin("0.001") @Digits(integer=11,fraction=3) BigDecimal kg,@NotBlank @Size(max=240) String motivo,@NotBlank @Size(max=80) String documento){}
 public record Count(@NotNull UUID lote,@NotNull @DecimalMin("0") @Digits(integer=11,fraction=3) BigDecimal kg){}
 @GetMapping("/me") Object me(Authentication a){return Map.of("usuario",a.getName(),"roles",a.getAuthorities(),"entornoPruebas",db.queryForObject("SELECT current_database()",String.class).endsWith("_test"));}
 @GetMapping("/catalogo") Object catalog(){return Map.of("productos",products.findAll(),"centros",db.queryForList("SELECT * FROM catalogo.centro ORDER BY nombre"));}
 @PostMapping("/productos") @PreAuthorize("hasRole('OPERADOR')") Object product(@Valid @RequestBody Product p){var x=new Producto();x.id=UUID.randomUUID();x.sku=p.sku().strip();x.nombre=p.nombre().strip();x.familia=p.familia().strip();return products.saveAndFlush(x);}
 @PostMapping("/centros") @PreAuthorize("hasRole('OPERADOR')") Object center(@Valid @RequestBody Center p){var id=UUID.randomUUID();db.update("INSERT INTO catalogo.centro(id,nombre) VALUES (?,?)",id,p.nombre().strip());return Map.of("id",id);}
 @GetMapping("/lotes") Object lots(){return service.lots();}
 @GetMapping("/lotes/{id}/trazabilidad") Object trace(@PathVariable UUID id){return service.trace(id);}
 @PostMapping("/recepciones") @PreAuthorize("hasRole('OPERADOR')") Object receipt(@Valid @RequestBody Receipt p,@RequestHeader("Idempotency-Key") UUID k,Authentication a){return service.command(k,a.getName(),"recepcion",p,()->service.receive(p.producto(),p.centro(),p.codigo(),p.vence(),p.kg(),p.documento(),a.getName()));}
 @PostMapping("/decisiones") @PreAuthorize("hasRole('CALIDAD')") Object quality(@Valid @RequestBody Quality p,@RequestHeader("Idempotency-Key") UUID k,Authentication a){return service.command(k,a.getName(),"calidad",p,()->service.quality(p.lote(),p.estado(),p.motivo(),a.getName()));}
 @PostMapping("/despachos") @PreAuthorize("hasRole('OPERADOR')") Object dispatch(@Valid @RequestBody Dispatch p,@RequestHeader("Idempotency-Key") UUID k,Authentication a){return service.command(k,a.getName(),"despacho",p,()->service.dispatch(p.producto(),p.centro(),p.kg(),p.vidaMinima(),p.documento(),a.getName()));}
 @PostMapping("/mermas") @PreAuthorize("hasRole('CALIDAD')") Object loss(@Valid @RequestBody Loss p,@RequestHeader("Idempotency-Key") UUID k,Authentication a){return service.command(k,a.getName(),"merma",p,()->service.loss(p.lote(),p.kg(),p.motivo(),p.documento(),a.getName()));}
 @PostMapping("/conteos") @PreAuthorize("hasRole('OPERADOR')") Object count(@Valid @RequestBody Count p,@RequestHeader("Idempotency-Key") UUID k,Authentication a){return service.command(k,a.getName(),"conteo",p,()->service.count(p.lote(),p.kg(),a.getName()));}
}

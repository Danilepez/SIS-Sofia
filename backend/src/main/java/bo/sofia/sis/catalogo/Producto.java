package bo.sofia.sis.catalogo;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="producto",schema="catalogo")
public class Producto {
 @Id public UUID id;
 @Column(nullable=false,length=40) public String sku;
 @Column(nullable=false,length=120) public String nombre;
 @Column(nullable=false,length=60) public String familia;
 @Column(nullable=false,length=2) public String unidad="kg";
 public Producto() {}
}

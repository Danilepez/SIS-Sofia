package bo.sofia.sis.catalogo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface ProductoRepository extends JpaRepository<Producto,UUID> {}

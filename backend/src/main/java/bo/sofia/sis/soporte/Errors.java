package bo.sofia.sis.soporte;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
@RestControllerAdvice
public class Errors {
 @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<ProblemDetail> bad(IllegalArgumentException e) {return response(400,e.getMessage());}
 @ExceptionHandler(IllegalStateException.class) ResponseEntity<ProblemDetail> conflict(IllegalStateException e) {return response(409,e.getMessage());}
 @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<ProblemDetail> integrity(Exception e) {return response(409,"Registro duplicado o referencia inexistente. Revise los datos.");}
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException e) {return response(400,"Campos inválidos: "+ e.getBindingResult().getFieldErrors().stream().map(x->x.getField()+" "+x.getDefaultMessage()).distinct().toList());}
 private ResponseEntity<ProblemDetail> response(int status,String detail) {return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status),detail));}
}

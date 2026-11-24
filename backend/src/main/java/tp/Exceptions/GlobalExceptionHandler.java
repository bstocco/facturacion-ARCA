package tp.Exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RecursoNoEncontradoException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse manejarRecursoNoEncontrado(RecursoNoEncontradoException ex) {
        return new ErrorResponse(404, ex.getMessage());
    }

    @ExceptionHandler(ReglaNegocioException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse manejarReglaNegocio(ReglaNegocioException ex) {
        return new ErrorResponse(409, ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ErrorResponse manejarErrorExterno(IllegalStateException ex) {
        return new ErrorResponse(502, ex.getMessage());
    }
}

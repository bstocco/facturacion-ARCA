package tp.DTO;

import java.util.List;

/** Una pagina de resultados (pagina base 0). */
public record PaginaDTO<T>(List<T> contenido, int pagina, int tamanio, long totalElementos, int totalPaginas) {

    public static <T> PaginaDTO<T> de(List<T> contenido, int pagina, int tamanio, long totalElementos) {
        int totalPaginas = tamanio <= 0 ? 0 : (int) ((totalElementos + tamanio - 1) / tamanio);
        return new PaginaDTO<>(contenido, pagina, tamanio, totalElementos, totalPaginas);
    }
}

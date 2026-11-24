package tp.Negocio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import tp.DTO.PaginaDTO;

class PaginaDTOTest {

    @Test
    void calculaLaCantidadDePaginasRedondeandoHaciaArriba() {
        PaginaDTO<String> p = PaginaDTO.de(List.of("a"), 0, 10, 21);
        assertEquals(3, p.totalPaginas());
        assertEquals(21, p.totalElementos());
    }

    @Test
    void sinElementosHayCeroPaginas() {
        assertEquals(0, PaginaDTO.de(List.<String>of(), 0, 10, 0).totalPaginas());
    }
}

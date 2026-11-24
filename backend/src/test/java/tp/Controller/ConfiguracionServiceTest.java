package tp.Controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tp.Entidad.Configuracion;

@ExtendWith(MockitoExtension.class)
class ConfiguracionServiceTest {

    @Mock
    EntityManager em;
    @InjectMocks
    ConfiguracionService service;

    @Test
    void sinValorGuardadoUsaElDeLaPropiedad() {
        ReflectionTestUtils.setField(service, "valorInicial", true);
        when(em.find(Configuracion.class, ConfiguracionService.CLAVE_EMISOR_RI)).thenReturn(null);
        assertTrue(service.isEmisorResponsableInscripto());
    }

    @Test
    void elValorGuardadoGanaSobreLaPropiedad() {
        ReflectionTestUtils.setField(service, "valorInicial", true);
        when(em.find(Configuracion.class, ConfiguracionService.CLAVE_EMISOR_RI))
                .thenReturn(new Configuracion(ConfiguracionService.CLAVE_EMISOR_RI, "false"));
        assertFalse(service.isEmisorResponsableInscripto());
    }

    @Test
    void cambiarElSwitchActualizaElValorExistente() {
        Configuracion c = new Configuracion(ConfiguracionService.CLAVE_EMISOR_RI, "false");
        when(em.find(Configuracion.class, ConfiguracionService.CLAVE_EMISOR_RI)).thenReturn(c);
        service.setEmisorResponsableInscripto(true);
        assertEquals("true", c.getValor());
        verify(em, never()).persist(any());
    }

    @Test
    void cambiarElSwitchSinValorPrevioLoCrea() {
        when(em.find(Configuracion.class, ConfiguracionService.CLAVE_EMISOR_RI)).thenReturn(null);
        service.setEmisorResponsableInscripto(true);
        verify(em).persist(any(Configuracion.class));
    }
}

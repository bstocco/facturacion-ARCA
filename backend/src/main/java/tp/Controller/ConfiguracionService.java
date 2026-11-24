package tp.Controller;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tp.Entidad.Configuracion;

@Service
public class ConfiguracionService {

    public static final String CLAVE_EMISOR_RI = "emisorResponsableInscripto";
    private static final Logger log = LoggerFactory.getLogger(ConfiguracionService.class);

    @PersistenceContext
    private EntityManager em;

    // Valor inicial: se usa mientras nadie haya tocado el switch en el sitio.
    @Value("${arca.emisor-responsable-inscripto:false}")
    private boolean valorInicial;

    @Transactional(readOnly = true)
    public boolean isEmisorResponsableInscripto() {
        Configuracion c = em.find(Configuracion.class, CLAVE_EMISOR_RI);
        return c == null ? valorInicial : Boolean.parseBoolean(c.getValor());
    }

    @Transactional
    public void setEmisorResponsableInscripto(boolean valor) {
        Configuracion c = em.find(Configuracion.class, CLAVE_EMISOR_RI);
        if (c == null) {
            em.persist(new Configuracion(CLAVE_EMISOR_RI, String.valueOf(valor)));
        } else {
            c.setValor(String.valueOf(valor));
        }
        log.warn("Emisor Responsable Inscripto cambiado a {}", valor);
    }
}

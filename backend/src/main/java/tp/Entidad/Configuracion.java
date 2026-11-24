package tp.Entidad;

import jakarta.persistence.*;
import lombok.*;

/** Parametros editables desde el sitio (clave/valor). */
@Entity
@Table(name = "configuracion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Configuracion {
    @Id
    @Column(length = 100)
    private String clave;
    @Column(nullable = false)
    private String valor;
}

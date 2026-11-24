package tp.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** El precio unitario no se recibe: el backend lo toma de la lista de precios. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FacturaDetalleRequestDTO {
    private Long listaPrecioArticuloId;
    private String descripcion;
    private double cantidad;
    private double porcentajeBonificacion;
}

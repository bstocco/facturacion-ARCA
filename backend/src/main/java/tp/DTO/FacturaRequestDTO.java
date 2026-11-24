package tp.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.List;

/** El numero y el estado los define el backend; el cliente nunca los envia. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FacturaRequestDTO {
    private Date fechaEmision;
    private Long clienteId;
    private Long puntoVentaId;
    private Long tipoMonedaId;
    /** Lista de precios elegida (mayorista, minorista...): todos los renglones deben ser de esta lista. */
    private Long listaPrecioId;
    private String observaciones;
    private List<FacturaDetalleRequestDTO> detalles;
}

package tp.DTO;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
public class FacturaReporteDTO {
    private Long id;
    private Long numeroFactura;
    private Date fechaEmision;
    private String clienteDenominacion;
    private String condicionIva;
    private String puntoVentaDescripcion;
    private double importeTotal;
    private long cantidadItems;
    private String estado;
    private Integer tipoComprobante;
    private String motivoRechazo;
    /** Primer articulo de la factura (si hay mas, ver itemsAdicionales). */
    private String articulo;
    private int itemsAdicionales;
    private double cantidadTotal;

    // Constructor usado por la consulta JPQL del listado
    public FacturaReporteDTO(Long id, Long numeroFactura, Date fechaEmision, String clienteDenominacion,
            String condicionIva, String puntoVentaDescripcion, double importeTotal, long cantidadItems,
            String estado, Integer tipoComprobante, String motivoRechazo) {
        this.id = id;
        this.numeroFactura = numeroFactura;
        this.fechaEmision = fechaEmision;
        this.clienteDenominacion = clienteDenominacion;
        this.condicionIva = condicionIva;
        this.puntoVentaDescripcion = puntoVentaDescripcion;
        this.importeTotal = importeTotal;
        this.cantidadItems = cantidadItems;
        this.estado = estado;
        this.tipoComprobante = tipoComprobante;
        this.motivoRechazo = motivoRechazo;
    }
}

package tp.Negocio;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * El precio unitario YA incluye IVA. Subtotal = cantidad x precio x (1 - bonif).
 * Si el comprobante discrimina IVA (A/B) se separa neto e IVA; si no (C), todo es neto.
 */
public final class CalculadoraImportes {

    public static final BigDecimal ALICUOTA = new BigDecimal("1.21");

    public record Importes(double neto, double iva, double subtotal) {
    }

    private CalculadoraImportes() {
    }

    public static Importes calcular(double cantidad, double precioConIva, double bonificacion,
            boolean discriminaIva) {
        BigDecimal bruto = BigDecimal.valueOf(cantidad).multiply(BigDecimal.valueOf(precioConIva));
        BigDecimal factor = BigDecimal.ONE.subtract(
                BigDecimal.valueOf(bonificacion).divide(BigDecimal.valueOf(100)));
        BigDecimal subtotal = bruto.multiply(factor).setScale(2, RoundingMode.HALF_UP);

        if (!discriminaIva) {
            return new Importes(subtotal.doubleValue(), 0.0, subtotal.doubleValue());
        }
        BigDecimal neto = subtotal.divide(ALICUOTA, 2, RoundingMode.HALF_UP);
        BigDecimal iva = subtotal.subtract(neto);
        return new Importes(neto.doubleValue(), iva.doubleValue(), subtotal.doubleValue());
    }
}

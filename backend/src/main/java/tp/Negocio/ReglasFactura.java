package tp.Negocio;

import tp.Entidad.FacturaVenta;

/** Reglas de ciclo de vida: tener numero = ya informada a ARCA. */
public final class ReglasFactura {

    public static final String BORRADOR = "BORRADOR";
    public static final String INFORMADA = "INFORMADA";
    public static final String RECHAZADA = "RECHAZADA";
    public static final String ANULADA = "ANULADA";
    public static final String ELIMINADA = "ELIMINADA";

    private ReglasFactura() {
    }

    private static boolean anulada(FacturaVenta f) {
        return ANULADA.equalsIgnoreCase(f.getEstado());
    }

    private static boolean eliminada(FacturaVenta f) {
        return ELIMINADA.equalsIgnoreCase(f.getEstado());
    }

    public static boolean puedeEditar(FacturaVenta f) {
        return f.getNumero() == null && !anulada(f) && !eliminada(f);
    }

    public static boolean puedeEliminar(FacturaVenta f) {
        return f.getNumero() == null && !eliminada(f);
    }

    public static boolean puedeAnular(FacturaVenta f) {
        return f.getNumero() != null && !anulada(f) && !eliminada(f);
    }

    public static boolean puedeInformar(FacturaVenta f) {
        return f.getNumero() == null
                && (BORRADOR.equalsIgnoreCase(f.getEstado()) || RECHAZADA.equalsIgnoreCase(f.getEstado()));
    }
}

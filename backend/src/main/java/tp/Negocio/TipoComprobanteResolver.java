package tp.Negocio;

/** Deduce el tipo de comprobante ARCA (1=A, 6=B, 11=C) segun emisor y receptor. */
public final class TipoComprobanteResolver {

    public static final int FACTURA_A = 1;
    public static final int FACTURA_B = 6;
    public static final int FACTURA_C = 11;

    private TipoComprobanteResolver() {
    }

    /**
     * @param emisorResponsableInscripto true si quien factura es Responsable Inscripto
     * @param codigoAfipReceptor         codigo AFIP de la condicion IVA del receptor
     *                                   (1 RI, 6 Monotributo, 13 Monot. social, 16 Monot. promovido)
     */
    public static int resolver(boolean emisorResponsableInscripto, int codigoAfipReceptor) {
        if (!emisorResponsableInscripto) {
            return FACTURA_C;
        }
        boolean receptorDiscrimina = codigoAfipReceptor == 1 || codigoAfipReceptor == 6
                || codigoAfipReceptor == 13 || codigoAfipReceptor == 16;
        return receptorDiscrimina ? FACTURA_A : FACTURA_B;
    }

    /** A y B informan neto + IVA a ARCA; C no. */
    public static boolean discriminaIva(int tipo) {
        return tipo != 11 && tipo != 12 && tipo != 13;
    }

    public static String letra(int tipo) {
        return switch (tipo) {
            case 1 -> "A";
            case 6 -> "B";
            case 11 -> "C";
            default -> String.valueOf(tipo);
        };
    }
}

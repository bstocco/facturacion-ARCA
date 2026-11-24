package tp.Negocio;

/** Los guiones del CUIT son solo visuales: se guarda y se busca con digitos. */
public final class FormatoCuit {

    private FormatoCuit() {
    }

    public static String soloDigitos(String cuit) {
        return cuit == null ? "" : cuit.replaceAll("[^0-9]", "");
    }

    /** XX-XXXXXXXX-X si son 11 digitos; si no, el valor original. */
    public static String visual(String cuit) {
        if (cuit == null) {
            return "";
        }
        String d = soloDigitos(cuit);
        if (d.length() != 11) {
            return cuit;
        }
        return d.substring(0, 2) + "-" + d.substring(2, 10) + "-" + d.substring(10);
    }
}

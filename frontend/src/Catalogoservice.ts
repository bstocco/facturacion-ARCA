import {
    ArticuloExploradorDTO,
    ClienteExploradorDTO,
    ComprobanteTipoDTO,
    OpcionDTO,
    OpcionesArticuloDTO,
    PaginaDTO
} from './models/factura.dto.js';

const API_BASE_URL = 'http://localhost:8080/api';

function query(params: Record<string, string | number | undefined>): string {
    const p = new URLSearchParams();
    Object.entries(params).forEach(([k, v]) => {
        if (v !== undefined && v !== null && String(v).trim() !== '') p.append(k, String(v));
    });
    return p.toString();
}

export class CatalogoService {

    private async obtener<T>(ruta: string): Promise<T> {
        const respuesta = await fetch(`${API_BASE_URL}/${ruta}`);
        if (!respuesta.ok) {
            throw new Error(`Error ${respuesta.status} al pedir ${ruta}`);
        }
        return respuesta.json() as Promise<T>;
    }

    obtenerPuntosVenta(): Promise<OpcionDTO[]> {
        return this.obtener('puntos-venta');
    }

    obtenerCondicionesIva(): Promise<OpcionDTO[]> {
        return this.obtener('condiciones-iva');
    }

    obtenerListasPrecio(): Promise<OpcionDTO[]> {
        return this.obtener('listas-precio');
    }

    obtenerTiposMoneda(): Promise<OpcionDTO[]> {
        return this.obtener('tipos-moneda');
    }

    obtenerTipoComprobante(clienteId: number | null): Promise<ComprobanteTipoDTO> {
        return this.obtener(clienteId === null ? 'comprobante-tipo' : `comprobante-tipo?clienteId=${clienteId}`);
    }

    obtenerOpcionesArticulos(): Promise<OpcionesArticuloDTO> {
        return this.obtener('lista-precio-articulos/opciones');
    }

    obtenerEmisor(): Promise<{ responsableInscripto: boolean }> {
        return this.obtener('configuracion/emisor');
    }

    async cambiarEmisor(responsableInscripto: boolean): Promise<void> {
        const r = await fetch(`${API_BASE_URL}/configuracion/emisor`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ responsableInscripto })
        });
        if (!r.ok) throw new Error(`Error ${r.status} al cambiar el emisor`);
    }

    buscarClientes(filtros: Record<string, string>, pagina: number, tamanio: number): Promise<PaginaDTO<ClienteExploradorDTO>> {
        return this.obtener(`clientes/buscar?${query({ ...filtros, pagina, tamanio })}`);
    }

    buscarArticulos(filtros: Record<string, string>, pagina: number, tamanio: number): Promise<PaginaDTO<ArticuloExploradorDTO>> {
        return this.obtener(`lista-precio-articulos/buscar?${query({ ...filtros, pagina, tamanio })}`);
    }
}

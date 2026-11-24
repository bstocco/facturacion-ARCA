import { PaginaDTO } from './models/factura.dto.js';

declare const bootstrap: any;

export interface OpcionFiltro { valor: string; etiqueta: string; }

/**
 * texto: libre | numerico: solo dígitos (sin flechas) |
 * combo: escribir con sugerencias del desplegable | select: solo elegir.
 */
export interface CampoFiltro {
    id: string;
    etiqueta: string;
    tipo?: 'texto' | 'numerico' | 'combo' | 'select';
    opciones?: string[];
    opcionesSelect?: OpcionFiltro[];
}

export interface Columna<T> { etiqueta: string; valor: (fila: T) => string; alineacion?: string; }

export interface ConfigExplorador<T> {
    titulo: string;
    filtros: CampoFiltro[];
    columnas: Columna<T>[];
    buscar: (filtros: Record<string, string>, pagina: number, tamanio: number) => Promise<PaginaDTO<T>>;
    alSeleccionar: (fila: T) => void;
}

const TAMANIO = 8;

function escapar(s: string): string {
    return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function crearCampo(f: CampoFiltro, indice: number): HTMLElement {
    const col = document.createElement('div');
    col.className = 'col-md-4';
    const etiqueta = document.createElement('label');
    etiqueta.className = 'form-label small mb-1';
    etiqueta.textContent = f.etiqueta;
    col.appendChild(etiqueta);

    let campo: HTMLInputElement | HTMLSelectElement;
    if (f.tipo === 'select') {
        const sel = document.createElement('select');
        sel.className = 'form-select form-select-sm';
        (f.opcionesSelect ?? []).forEach(o => {
            const op = document.createElement('option');
            op.value = o.valor;
            op.textContent = o.etiqueta;
            sel.appendChild(op);
        });
        campo = sel;
    } else {
        const inp = document.createElement('input');
        inp.type = 'text';
        inp.className = 'form-control form-control-sm';
        inp.autocomplete = 'off';
        if (f.tipo === 'numerico') {
            // Sin flechas ni signos: solo dígitos, también al pegar
            inp.inputMode = 'numeric';
            inp.maxLength = 12;
            inp.addEventListener('input', () => { inp.value = inp.value.replace(/\D/g, ''); });
        }
        if (f.tipo === 'combo') {
            const lista = document.createElement('datalist');
            lista.id = `explorador-lista-${indice}`;
            (f.opciones ?? []).forEach(o => {
                const op = document.createElement('option');
                op.value = o;
                lista.appendChild(op);
            });
            inp.setAttribute('list', lista.id);
            col.appendChild(lista);
        }
        campo = inp;
    }
    campo.dataset.filtro = f.id;
    col.appendChild(campo);
    return col;
}

/** Explorador genérico en pop-up (modal Bootstrap): filtros + grilla paginada + seleccionar. */
export function abrirExplorador<T>(config: ConfigExplorador<T>): void {
    const elModal = document.getElementById('modal-explorador') as HTMLDivElement;
    const modal = bootstrap.Modal.getOrCreateInstance(elModal);
    (document.getElementById('explorador-titulo') as HTMLElement).textContent = config.titulo;

    const form = document.getElementById('explorador-filtros') as HTMLFormElement;
    form.innerHTML = '';
    config.filtros.forEach((f, i) => form.appendChild(crearCampo(f, i)));
    const botones = document.createElement('div');
    botones.className = 'col-12';
    botones.innerHTML = `<button type="submit" class="btn btn-sm btn-primary">Buscar</button>
        <button type="button" class="btn btn-sm btn-outline-secondary" id="explorador-limpiar">Limpiar</button>`;
    form.appendChild(botones);

    const thead = document.getElementById('explorador-thead') as HTMLTableSectionElement;
    thead.innerHTML = '<tr>' + config.columnas.map(c => `<th class="${c.alineacion ?? ''}">${escapar(c.etiqueta)}</th>`).join('')
        + '<th></th></tr>';
    const tbody = document.getElementById('explorador-tbody') as HTMLTableSectionElement;
    const pie = document.getElementById('explorador-paginacion') as HTMLElement;
    const ncols = config.columnas.length + 1;

    const leerFiltros = (): Record<string, string> => {
        const r: Record<string, string> = {};
        form.querySelectorAll<HTMLInputElement | HTMLSelectElement>('[data-filtro]').forEach(i => {
            r[i.dataset.filtro!] = i.value.trim();
        });
        return r;
    };

    // Un combo con texto que no figura entre las opciones es un error de búsqueda
    const validar = (filtros: Record<string, string>): string | null => {
        for (const f of config.filtros) {
            if (f.tipo !== 'combo') continue;
            const v = filtros[f.id];
            if (v && !(f.opciones ?? []).some(o => o.toLowerCase() === v.toLowerCase())) {
                return `Error en la búsqueda: "${v}" no es una opción válida de ${f.etiqueta}. No se encontró nada.`;
            }
        }
        return null;
    };

    const cargar = async (pagina: number): Promise<void> => {
        const filtros = leerFiltros();
        const error = validar(filtros);
        if (error) {
            tbody.innerHTML = `<tr><td colspan="${ncols}" class="text-center text-danger fw-bold py-3">${escapar(error)}</td></tr>`;
            pie.innerHTML = '';
            return;
        }
        tbody.innerHTML = `<tr><td colspan="${ncols}" class="text-center">Cargando...</td></tr>`;
        try {
            const res = await config.buscar(filtros, pagina, TAMANIO);
            tbody.innerHTML = '';
            if (res.contenido.length === 0) {
                tbody.innerHTML = `<tr><td colspan="${ncols}" class="text-center">Sin resultados.</td></tr>`;
            }
            res.contenido.forEach(fila => {
                const tr = document.createElement('tr');
                config.columnas.forEach(c => {
                    const td = document.createElement('td');
                    td.className = c.alineacion ?? '';
                    td.textContent = c.valor(fila);
                    tr.appendChild(td);
                });
                const tdSel = document.createElement('td');
                const btn = document.createElement('button');
                btn.type = 'button';
                btn.className = 'btn btn-sm btn-success';
                btn.textContent = 'Seleccionar';
                btn.addEventListener('click', () => { config.alSeleccionar(fila); modal.hide(); });
                tdSel.appendChild(btn);
                tr.appendChild(tdSel);
                tbody.appendChild(tr);
            });
            renderizarPaginacion(pie, res, cargar);
        } catch (e) {
            tbody.innerHTML = `<tr><td colspan="${ncols}" class="text-danger text-center">${escapar((e as Error).message)}</td></tr>`;
        }
    };

    form.onsubmit = (ev) => { ev.preventDefault(); void cargar(0); };
    (document.getElementById('explorador-limpiar') as HTMLButtonElement).onclick = () => { form.reset(); void cargar(0); };

    modal.show();
    void cargar(0);
}

/** Controles Anterior / páginas / Siguiente. También los usa el listado de facturas. */
export function renderizarPaginacion<T>(contenedor: HTMLElement, pagina: PaginaDTO<T>, irA: (p: number) => void): void {
    contenedor.innerHTML = '';
    if (pagina.totalPaginas <= 1) {
        contenedor.innerHTML = `<small class="text-muted">${pagina.totalElementos} resultado(s)</small>`;
        return;
    }
    const ul = document.createElement('ul');
    ul.className = 'pagination pagination-sm mb-0';
    const item = (texto: string, destino: number, deshabilitado: boolean, activo = false) => {
        const li = document.createElement('li');
        li.className = `page-item${deshabilitado ? ' disabled' : ''}${activo ? ' active' : ''}`;
        const a = document.createElement('button');
        a.type = 'button';
        a.className = 'page-link';
        a.textContent = texto;
        a.addEventListener('click', () => irA(destino));
        li.appendChild(a);
        ul.appendChild(li);
    };
    item('Anterior', pagina.pagina - 1, pagina.pagina === 0);
    const desde = Math.max(0, Math.min(pagina.pagina - 2, pagina.totalPaginas - 5));
    const hasta = Math.min(pagina.totalPaginas, desde + 5);
    for (let i = desde; i < hasta; i++) item(String(i + 1), i, false, i === pagina.pagina);
    item('Siguiente', pagina.pagina + 1, pagina.pagina >= pagina.totalPaginas - 1);

    const info = document.createElement('small');
    info.className = 'text-muted me-3';
    info.textContent = `Página ${pagina.pagina + 1} de ${pagina.totalPaginas} · ${pagina.totalElementos} resultado(s)`;
    const wrap = document.createElement('div');
    wrap.className = 'd-flex align-items-center justify-content-between flex-wrap gap-2';
    wrap.appendChild(info);
    wrap.appendChild(ul);
    contenedor.appendChild(wrap);
}

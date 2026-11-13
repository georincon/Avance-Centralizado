(function () {
    var manual = document.getElementById('origen-manual'), qr = document.getElementById('origen-qr');
    if (!manual) return;
    var panel = document.getElementById('panel-qr'), zona = document.getElementById('qr-zona'), estado = document.getElementById('qr-estado');
    var VERIFIER = location.origin + '/verifier-uis';
    var timer = null;
    function parar() { if (timer) { clearInterval(timer); timer = null; } }
    function mostrar() { panel.style.display = qr.checked ? 'block' : 'none'; if (!qr.checked) parar(); }
    manual.addEventListener('change', mostrar); qr.addEventListener('change', mostrar);
    function b64url(t) { t = t.replace(/-/g, '+').replace(/_/g, '/'); while (t.length % 4) t += '='; return decodeURIComponent(escape(atob(t))); }
    function divulgados(sdjwt) {
        var d = {};
        sdjwt.split('~').slice(1, -1).forEach(function (p) {
            if (!p) return;
            try { var v = JSON.parse(b64url(p)); if (v.length === 3 && !(v[1] in d)) d[v[1]] = v[2]; } catch (e) {}
        });
        return d;
    }
    function poner(id, v) { var el = document.getElementById(id); if (el && v) { el.value = v; el.dispatchEvent(new Event('change')); } }
    function llenar(d) {
        poner('tipoIdentificacion', 'CC');
        poner('numeroIdentificacion', String(d.personal_administrative_number || ''));
        poner('nombres', d.given_name);
        poner('apellidos', d.family_name);
        poner('correoElectronico', d.email);
        poner('numeroContacto', d.phone_number);
        estado.textContent = 'Datos cargados desde el PID. Revísalos y guarda.';
        estado.className = 'small mt-2 text-success';
    }
    function error(m) { estado.textContent = m; estado.className = 'small mt-2 text-danger'; }
    document.getElementById('pedir-qr').addEventListener('click', async function () {
        parar(); zona.innerHTML = ''; estado.className = 'small mt-2'; estado.textContent = 'Generando la solicitud…';
        try {
            var uso = await (await fetch(VERIFIER + '/ui/intended-uses')).json();
            var r = await fetch(VERIFIER + '/ui/presentations/v2', {
                method: 'POST', headers: { 'content-type': 'application/json' },
                body: JSON.stringify({
                    dcql_query: { credentials: [{ id: 'doc', format: 'dc+sd-jwt', meta: { vct_values: ['urn:eudi:pid:1'] },
                        claims: [['personal_administrative_number'], ['given_name'], ['family_name'], ['email'], ['phone_number']].map(function (c) { return { path: c }; }) }] },
                    nonce: Math.random().toString(36).slice(2) + Date.now().toString(36), profile: 'haip',
                    intended_use_id: uso.intended_uses[0].intended_use_id, request_uri_method: 'get'
                })
            });
            var t = await r.json();
            if (!r.ok) throw new Error('el verificador respondió ' + r.status);
            zona.innerHTML = '<img alt="QR para la wallet" width="240" height="240" src="' + location.pathname.replace(/\/estudiantes.*$/, '') + '/estudiantes/qr?data=' + encodeURIComponent(t.authorization_request_uri) + '">'
                + '<a class="btn btn-outline-secondary btn-sm" href="' + t.authorization_request_uri + '">Abrir en la wallet de este dispositivo</a>';
            estado.textContent = 'Esperando a que la wallet comparta el PID…';
            var inicio = Date.now();
            timer = setInterval(async function () {
                if (Date.now() - inicio > 5 * 60 * 1000) { parar(); return error('La solicitud venció. Genera otro QR.'); }
                try {
                    var q = await fetch(VERIFIER + '/ui/presentations/' + encodeURIComponent(t.transaction_id));
                    if (q.status !== 200) return;
                    var c = await q.json();
                    if (c.error) { parar(); return error(c.error === 'access_denied' ? 'El estudiante no aceptó compartir los datos.' : (c.error_description || c.error)); }
                    var vp = [].concat((c.vp_token || {}).doc || [])[0];
                    if (typeof vp !== 'string') return;
                    parar(); zona.innerHTML = ''; llenar(divulgados(vp));
                } catch (e) {}
            }, 2000);
        } catch (e) { error('No se pudo generar el QR: ' + e.message); }
    });
})();

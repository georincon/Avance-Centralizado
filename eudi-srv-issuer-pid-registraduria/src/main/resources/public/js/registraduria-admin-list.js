// Confirmación SÍ / NO antes de revocar credenciales, borrar un ciudadano, desbloquear o marcar un aviso.
// Los botones son de tipo "button": sin este script no envían nada (no se puede revocar por error).
(function () {
    var dialog = document.getElementById('confirm-dialog');
    if (!dialog || typeof dialog.showModal !== 'function') {
        return;
    }
    var title = document.getElementById('confirm-title');
    var text = document.getElementById('confirm-text');
    var yes = document.getElementById('confirm-yes');
    var no = document.getElementById('confirm-no');
    // Campos opcionales (solo en el cuadro de revocar): motivo y bloqueo de nuevas emisiones
    var fields = document.getElementById('confirm-fields');
    var reason = document.getElementById('confirm-reason');
    var block = document.getElementById('confirm-block');
    var pending = null;

    document.querySelectorAll('.js-confirm-open').forEach(function (button) {
        button.addEventListener('click', function () {
            pending = button.closest('form');
            title.textContent = pending.dataset.title;
            text.textContent = pending.dataset.text;
            yes.textContent = pending.dataset.yes;
            yes.disabled = false;
            if (fields) {
                var withFields = pending.dataset.fields === 'revoke';
                fields.hidden = !withFields;
                reason.value = 'other';
                block.checked = false;
            }
            dialog.showModal();
            no.focus(); // el foco inicial siempre en NO: Enter no confirma por accidente
        });
    });

    no.addEventListener('click', function () {
        pending = null;
        dialog.close();
    });

    yes.addEventListener('click', function () {
        var form = pending;
        pending = null;
        yes.disabled = true; // evita un segundo envío
        if (form && fields && form.dataset.fields === 'revoke') {
            form.querySelector('input[name=reason]').value = reason.value;
            form.querySelector('input[name=block]').value = block.checked ? 'true' : 'false';
        }
        dialog.close();
        if (form) {
            form.submit();
        }
    });

    // Esc o clic fuera del cuadro = NO
    dialog.addEventListener('cancel', function () {
        pending = null;
    });
    dialog.addEventListener('click', function (event) {
        if (event.target === dialog) {
            pending = null;
            dialog.close();
        }
    });
})();

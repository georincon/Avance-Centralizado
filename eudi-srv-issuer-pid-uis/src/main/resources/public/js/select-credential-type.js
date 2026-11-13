var formatData = {
    'vc_sd_jwt': { name: 'SD-JWT VC', desc: 'JSON Web Token con Selective Disclosure - Amplia compatibilidad con billeteras digitales', color: 'var(--uis-green)' },
    'mso_mdoc': { name: 'mso_mdoc (ISO 18013.5)', desc: 'Documento Móvel Estándar - Estándar ISO para documentos de identidad digitales', color: 'var(--uis-blue)' },
    'eudi_learning': { name: 'EUDI Learning Credential', desc: 'Credencial Europea de Aprendizaje - Optimizada para educación en la UE', color: '#f57f17' }
};

function selectType(type) {
    // Update hidden input
    document.getElementById('credentialTypeInput').value = type;

    // Reset all cards
    document.querySelectorAll('.credential-card').forEach(function(card) {
        card.style.borderColor = '#dee2e6';
        card.classList.remove('border-success', 'border-primary', 'border-warning');
    });

    // Highlight selected card
    var selected = document.getElementById('card-' + type);
    selected.style.borderColor = formatData[type].color;
    selected.style.borderWidth = '3px';

    // Update info
    document.getElementById('selected-format-name').textContent = formatData[type].name;
    document.getElementById('selected-format-desc').textContent = formatData[type].desc;
}

document.querySelectorAll('.credential-card').forEach(function(card) {
    card.addEventListener('click', function() {
        selectType(card.getAttribute('data-type'));
    });
});

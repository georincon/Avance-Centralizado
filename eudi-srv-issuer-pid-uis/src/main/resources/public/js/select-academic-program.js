document.addEventListener('DOMContentLoaded', function() {
    var cards = document.querySelectorAll('.program-card');

    cards.forEach(function(card) {
        card.addEventListener('click', function(event) {
            // Un clic en un enlace de la tarjeta (CARNET) no selecciona la tarjeta
            if (event.target.closest('a')) {
                return;
            }

            // Remove selection from all cards
            cards.forEach(function(c) {
                c.style.borderColor = '#dee2e6';
                c.style.boxShadow = 'none';
            });

            // Select this card
            this.style.borderColor = 'var(--uis-green)';
            this.style.boxShadow = '0 4px 12px rgba(0,104,55,0.2)';

            // Set hidden fields
            document.getElementById('selectedHistorialId').value = this.getAttribute('data-historial');
            document.getElementById('selectedProgramaId').value = this.getAttribute('data-programa');

            // Enable button
            document.getElementById('selectButton').disabled = false;
        });
    });
});

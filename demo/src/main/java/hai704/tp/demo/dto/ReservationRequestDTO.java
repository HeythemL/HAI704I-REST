package hai704.tp.demo.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record ReservationRequestDTO(
        @NotBlank(message = "L'identifiant de l'offre est obligatoire")
        String offreId,

        @NotBlank(message = "Le nom du client est obligatoire")
        @Size(min = 2, max = 200, message = "Le nom doit contenir entre 2 et 200 caractères")
        String nomClient,

        @NotNull(message = "La date de début est obligatoire")
        LocalDate dateDebut,

        @NotNull(message = "La date de fin est obligatoire")
        LocalDate dateFin
) {
    public boolean isDateRangeValid() {
        return dateDebut != null && dateFin != null && !dateDebut.isAfter(dateFin);
    }
}
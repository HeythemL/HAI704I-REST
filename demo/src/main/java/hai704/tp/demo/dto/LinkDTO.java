package hai704.tp.demo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "rel", "href", "type", "method" })
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LinkDTO(
        String rel,           // Relation : "self", "next", "prev", "photo", "reserver", "offre", "cancel"
        String href,          // URI absolue ou relative
        String type,          // Media type : "application/json", "image/jpeg", etc.
        String method         // Méthode HTTP : "GET", "POST", "PUT", "DELETE", "PATCH"
) {
    // Factories pour relations standardisées
    public static LinkDTO self(String href) {
        return new LinkDTO("self", href, "application/json", "GET");
    }

    public static LinkDTO next(String href) {
        return new LinkDTO("next", href, "application/json", "GET");
    }

    public static LinkDTO prev(String href) {
        return new LinkDTO("prev", href, "application/json", "GET");
    }

    public static LinkDTO photo(String href) {
        return new LinkDTO("photo", href, "image/jpeg", "GET");
    }

    public static LinkDTO reserver(String href) {
        return new LinkDTO("reserver", href, "application/json", "POST");
    }

    public static LinkDTO offre(String href) {
        return new LinkDTO("offre", href, "application/json", "GET");
    }

    public static LinkDTO cancel(String href) {
        return new LinkDTO("cancel", href, "application/json", "DELETE");
    }

    public static LinkDTO update(String href) {
        return new LinkDTO("update", href, "application/json", "PUT");
    }
}
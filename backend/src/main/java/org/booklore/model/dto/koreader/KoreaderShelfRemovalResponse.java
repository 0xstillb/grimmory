package org.booklore.model.dto.koreader;

public record KoreaderShelfRemovalResponse(
        Long shelfId,
        Long bookId,
        boolean removedFromShelf,
        boolean deletedFromLibrary
) {
}

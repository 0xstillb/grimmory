package org.booklore.service.koreader;

import lombok.RequiredArgsConstructor;
import org.booklore.config.security.userdetails.KoreaderUserDetails;
import org.booklore.exception.ApiError;
import org.booklore.model.dto.BookLoreUser;
import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KoreaderSecurityContextService {

    private final UserRepository userRepository;

    public AuthenticatedReader requireCurrentReader(boolean requireSyncEnabled) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw ApiError.GENERIC_UNAUTHORIZED.createException("Authentication required");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof BookLoreUser user) {
            BookLoreUserEntity userEntity = userRepository.findByIdWithDetails(user.getId()).orElse(null);
            boolean syncWithWebReader = userEntity != null
                    && userEntity.getKoreaderUser() != null
                    && userEntity.getKoreaderUser().isSyncWithWebReader();
            return new AuthenticatedReader(user.getId(), user.getUsername(), false, true, syncWithWebReader);
        }

        if (principal instanceof KoreaderUserDetails details) {
            if (requireSyncEnabled && !details.isSyncEnabled()) {
                throw ApiError.GENERIC_UNAUTHORIZED.createException("Sync is disabled for this user");
            }
            if (details.getBookLoreUserId() == null) {
                throw ApiError.GENERIC_UNAUTHORIZED.createException("KOReader user is not linked to a Grimmory user");
            }
            return new AuthenticatedReader(
                    details.getBookLoreUserId(),
                    details.getUsername(),
                    true,
                    details.isSyncEnabled(),
                    details.isSyncWithWebReader()
            );
        }

        throw ApiError.GENERIC_UNAUTHORIZED.createException("Unsupported authentication principal");
    }

    public BookLoreUserEntity requireCurrentReaderEntity(boolean requireSyncEnabled) {
        AuthenticatedReader reader = requireCurrentReader(requireSyncEnabled);
        return userRepository.findByIdWithDetails(reader.userId())
                .orElseThrow(() -> ApiError.GENERIC_UNAUTHORIZED.createException("Authenticated user no longer exists"));
    }

    public record AuthenticatedReader(
            Long userId,
            String username,
            boolean koreaderAuth,
            boolean syncEnabled,
            boolean syncWithWebReader
    ) {
    }
}

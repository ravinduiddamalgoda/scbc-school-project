package com.scbck.dto;

import java.util.List;
import java.util.Map;

/**
 * Everything the client needs about the signed-in user in one payload:
 * identity, roles, and the full privilege matrix.
 *
 * The matrix lets the SPA hide menu entries and disable buttons without a
 * round trip per module. It is advisory only - every mutating endpoint
 * re-checks the same privilege server-side.
 */
public record AuthUserResponse(
        Integer id,
        String username,
        String email,
        String photo,
        List<String> roles,
        Map<String, ModulePrivilege> privileges) {
}

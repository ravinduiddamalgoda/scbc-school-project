package com.scbck.dto;

/**
 * The four CRUD flags a role holds against one module.
 */
public record ModulePrivilege(
        boolean select,
        boolean insert,
        boolean update,
        boolean delete) {

    public static ModulePrivilege all() {
        return new ModulePrivilege(true, true, true, true);
    }

    public static ModulePrivilege none() {
        return new ModulePrivilege(false, false, false, false);
    }
}

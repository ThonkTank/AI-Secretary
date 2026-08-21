package de.thonktank.autosecretary.domain.repository;

import de.thonktank.autosecretary.domain.model.TaskCatalog;

/** Narrow read port for the management inventory. */
public interface TaskCatalogQuery {
    TaskCatalog execute();
}

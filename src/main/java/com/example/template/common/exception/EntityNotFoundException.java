package com.example.template.common.exception;

import org.springframework.http.HttpStatus;

/**
* Thrown when a lookup by id (or other unique key) finds nothing.
* Maps to HTTP 404.
*/
public class EntityNotFoundException extends DomainException {
    
    public EntityNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "entity-not-found", message);
    }

    public static EntityNotFoundException forId(String entityName, Object id) {
        return new EntityNotFoundException(entityName + " not found with id: " + id);
    }

}

/**
 * Cross-cutting infrastructure shared across all feature packages:
 * exception handling, security configuration, and application-wide config.
 * Nothing business-specific belongs here. if it's tied to a single
 * domain concept (user, auth, product...), it belongs in that feature's
 * own package instead.
 */
package com.example.template.common;
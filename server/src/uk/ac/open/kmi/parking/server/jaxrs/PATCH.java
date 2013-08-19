package uk.ac.open.kmi.parking.server.jaxrs;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.ws.rs.HttpMethod;

/**
 * copied from http://cxf.apache.org/docs/jax-rs-basics.html
 * annotation for HTTP patch methods
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@HttpMethod("PATCH")
public @interface PATCH { /* nothing */ }

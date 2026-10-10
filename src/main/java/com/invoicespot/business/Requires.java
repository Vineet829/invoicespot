package com.invoicespot.business;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// The permission an endpoint needs in the business it works in. Every
// BusinessAccess parameter carries one; the resolver refuses the request
// before any service code runs when the member's role lacks it.
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Requires {
    Permission value();
}

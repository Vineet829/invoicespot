package com.invoicespot.common;

// The page of a list asked for, from the first up to the last one a database
// query can still start at: a page past the list's end, such as a page number
// typed into an address, is simply empty, never an error.
public final class Pages {

    private Pages() {}

    public static int number(int requested, int size) {
        return Math.min(Math.max(requested, 1), Integer.MAX_VALUE / Math.max(size, 1));
    }
}

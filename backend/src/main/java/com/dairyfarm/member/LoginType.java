package com.dairyfarm.member;

/** How a member signs in. */
public enum LoginType {
    /** No sign-in; the owner records work on their behalf. */
    NONE,
    /** The owner created the login and set the password. */
    PASSWORD,
    /** An invite email was sent; the person sets their own password. */
    INVITE,
    /** The person created their own account (e.g. the farm creator). */
    SELF
}

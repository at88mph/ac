package org.opencadc.posix.mapper;

import java.util.Objects;

/**
 * Represents a mapping between a user and a POSIX UID.  The issuer and subject fields are used to uniquely identify the user, and the username field is used to generate the POSIX entry.  The uid field is used to store the POSIX UID.
 */
public class User {
    private final String issuer;
    private final String subject;
    private final String username;
    private int uid;

    public User(final String issuer, final String subject, final String username) {
        this.issuer = issuer;
        this.subject = subject;
        this.username = username;
    }

    public void setUID(int uid) {
        this.uid = uid;
    }

    public String getIssuer() {
        return issuer;
    }

    public String getSubject() {
        return subject;
    }

    public String getUsername() {
        return username;
    }

    public int getUID() {
        return uid;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        User user = (User) o;
        return Objects.equals(issuer, user.issuer) && Objects.equals(subject, user.subject);
    }

    @Override
    public int hashCode() {
        return Objects.hash(issuer, subject);
    }

    @Override
    public String toString() {
        return "User{" +
                "issuer='" + issuer + '\'' +
                ", subject='" + subject + '\'' +
                ", uid=" + uid +
                '}';
    }
}
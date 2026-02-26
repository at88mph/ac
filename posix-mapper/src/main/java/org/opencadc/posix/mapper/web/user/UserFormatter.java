package org.opencadc.posix.mapper.web.user;

import org.opencadc.posix.mapper.User;

public interface UserFormatter {
    /**
     * Format the given User as a String.  The exact format is determined by the implementation.
     * @param user  User to format, never null.
     * @return  String formatted.
     */
    String format(final User user);
}

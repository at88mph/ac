package org.opencadc.posix.mapper.web.user;

import org.opencadc.posix.mapper.User;

public class AsciiUserFormatter implements UserFormatter {
    private static final String POSIX_FORMAT_STRING = "%s:x:%s:%s:::";
    @Override
    public String format(User user) {
        return String.format(AsciiUserFormatter.POSIX_FORMAT_STRING, user.getUsername(), user.getUID(), user.getUID());
    }
}

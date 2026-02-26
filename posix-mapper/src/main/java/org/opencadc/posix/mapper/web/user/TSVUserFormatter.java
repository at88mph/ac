package org.opencadc.posix.mapper.web.user;

import org.opencadc.posix.mapper.User;

public class TSVUserFormatter implements UserFormatter {
    private static final String FORMAT_STRING = "%s\t%d\t%d";
    @Override
    public String format(User user) {
        return String.format(TSVUserFormatter.FORMAT_STRING, user.getUsername(), user.getUID(), user.getUID());
    }
}

package org.opencadc.posix.mapper.web.group;

import org.opencadc.posix.mapper.Group;

public interface GroupFormatter {
    String UNSET_GID = "(Not yet persisted)";

    /**
     * Format the given Group as a String.  The exact format is determined by the implementation.
     * @param group Group to format, never null.
     * @return String formatted.
     */
    String format(final Group group);
}

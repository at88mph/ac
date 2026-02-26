package org.opencadc.posix.mapper;

import org.opencadc.gms.GroupURI;

public class Group {
    private final GroupURI groupURI;
    private Integer gid;

    public Group(GroupURI groupURI) {
        this.groupURI = groupURI;
    }

    public void setGid(Integer gid) {
        this.gid = gid;
    }

    public GroupURI getGroupURI() {
        return groupURI;
    }

    public Integer getGID() {
        return gid;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Group g)) {
            return false;
        } else {
            return g.groupURI.equals(groupURI);
        }
    }

    @Override
    public String toString() {
        return "Group{" +
                "gid=" + gid +
                ", groupURI='" + groupURI + '\'' +
                '}';
    }
}
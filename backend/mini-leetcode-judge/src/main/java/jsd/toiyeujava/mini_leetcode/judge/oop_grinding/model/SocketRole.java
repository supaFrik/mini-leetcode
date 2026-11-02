package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum SocketRole {
    CLIENT,
    SERVER;

    @JsonCreator
    public static SocketRole fromString(String val) {
        if (val == null) {
            return CLIENT;
        }
        String upper = val.trim().toUpperCase();
        if (upper.contains("SERVER")) {
            return SERVER;
        }
        return CLIENT;
    }
}

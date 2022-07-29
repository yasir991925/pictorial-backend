package com.pictorial;

import java.util.UUID;
import java.util.regex.Pattern;

public class Trials {
    public static void main(String[] args) {

        for (int i = 0; i < 100; i++) {
            String s = UUID.randomUUID().toString();
            Pattern p = Pattern.compile("(([a-zA-Z0-9]+)-){4}([a-zA-Z0-9]+)");
            System.out.println(p.matcher(s).pattern());
        }
    }
}

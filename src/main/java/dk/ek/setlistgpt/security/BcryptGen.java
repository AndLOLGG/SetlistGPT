package dk.ek.setlistgpt.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BcryptGen {
    public static void main(String[] args) {
        String pwd = args.length > 0 ? args[0] : "1234";
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
        System.out.println(enc.encode(pwd));
    }
}
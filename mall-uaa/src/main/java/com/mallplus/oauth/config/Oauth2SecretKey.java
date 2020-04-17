package com.mallplus.oauth.config;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * @ClassName Oauth2SecretKey
 * @Description
 * @Author liningwang
 * @Date 2020-03-16 16:28
 * @Version 1.0
 */
public class Oauth2SecretKey {
    public static void main(String[] args){
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        BCryptPasswordEncoder encoder1 = new BCryptPasswordEncoder();
        String secret = encoder.encode("admin");
        System.out.println(secret);
        if(encoder1.matches("admin","$2a$10$Q1Y1qE/iFQk1bFMfwFwYIuG2TE1VAg9hUQ6PBZyh8DJ4zjSHa2guC")){
            System.out.println("true");
        } else {
            System.out.println("false");
        }



    }
}

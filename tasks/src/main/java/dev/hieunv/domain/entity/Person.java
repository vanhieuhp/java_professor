package dev.hieunv.domain.entity;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
public class Person implements Serializable {

    private int id;
    private String name;
    private int age;
    private String email;
    private String phone;
    private String address;
    private String city;
    private String country;
    private String jobTitle;
    private double salary;

    public Person() {}

    public Person(int id, String name, int age, String email, String phone, String address,
                  String city, String country, String jobTitle, double salary) {
        this.id = id;
        this.name = name;
        this.age = age;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.city = city;
        this.country = country;
        this.jobTitle = jobTitle;
        this.salary = salary;
    }
}

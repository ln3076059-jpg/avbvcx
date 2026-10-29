package com.pms.propertymanagement.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "provinces")
public class Province {
    @Id
    private String code;

    @Column(unique = true, nullable = false, length = 255)
    private String name;

    public Province(String code, String name) {
        this.code = code;
        this.name = name;
    }

    @OneToMany(mappedBy = "province", cascade = CascadeType.ALL)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private List<Ward> wards = new ArrayList<>();

}

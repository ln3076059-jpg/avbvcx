package com.pms.propertymanagement.entity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "wards")
public class Ward {

    @Id
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    public Ward(String code, String name, Province province) {
        this.code = code;
        this.name = name;
        this.province = province;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "province_code", nullable = false)
    @JsonIgnore
    private Province province;

    @OneToMany(mappedBy = "ward")
    @JsonIgnore
    private List<Property> properties;

}

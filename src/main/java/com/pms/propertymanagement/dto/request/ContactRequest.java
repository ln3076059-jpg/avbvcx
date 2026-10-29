package com.pms.propertymanagement.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ContactRequest
{
    private String name;
    private String phone;
    private String note;
}

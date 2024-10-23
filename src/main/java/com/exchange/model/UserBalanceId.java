package com.exchange.model;

import lombok.EqualsAndHashCode;

import java.io.Serializable;

@EqualsAndHashCode
public class UserBalanceId implements Serializable {
    private String userId;
    private String currency;
}

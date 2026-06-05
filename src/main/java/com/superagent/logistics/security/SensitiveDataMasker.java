package com.superagent.logistics.security;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class SensitiveDataMasker {

    public String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    public String maskAmount(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        return amount.compareTo(new BigDecimal("1000")) >= 0 ? "已脱敏" : amount.toPlainString();
    }

    public String maskText(String text) {
        if (text == null) {
            return null;
        }
        return text.replaceAll("(1[3-9]\\d)\\d{4}(\\d{4})", "$1****$2");
    }
}
